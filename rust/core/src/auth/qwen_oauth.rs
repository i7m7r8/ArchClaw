//! Qwen OAuth Authentication
//! 
//! Implements the free Qwen OAuth 2.0 flow via qwen.ai
//! No API key needed, no credit card, 2,000 requests/day FREE.
//!
//! Flow:
//! 1. Start local HTTP server on random port
//! 2. Open browser to qwen.ai OAuth URL with callback
//! 3. User signs in at qwen.ai (or creates free account)
//! 4. Browser redirects to localhost:{port}/callback with token
//! 5. Extract, encrypt, and save OAuth token
//! 6. All Qwen tools use this token automatically

use anyhow::{Result, Context};
use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use tracing::{info, error};

/// Qwen OAuth token (cached after login)
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QwenOAuthToken {
    /// Access token from qwen.ai
    pub access_token: String,
    
    /// Refresh token for getting new access tokens
    pub refresh_token: String,
    
    /// Token type (usually "Bearer")
    pub token_type: String,
    
    /// When the token expires (Unix timestamp)
    pub expires_at: u64,
    
    /// Scopes granted
    pub scope: String,
}

impl QwenOAuthToken {
    /// Check if token is expired
    pub fn is_expired(&self) -> bool {
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_secs();
        now >= self.expires_at
    }
    
    /// Check if token expires soon (within 5 minutes)
    pub fn expires_soon(&self) -> bool {
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_secs();
        // 5 minute buffer
        (self.expires_at - now) < 300
    }
}

/// Qwen OAuth manager
pub struct QwenOAuth {
    /// Where to store the token
    pub token_path: PathBuf,
    
    /// Cached token (if loaded)
    pub token: Option<QwenOAuthToken>,
}

impl QwenOAuth {
    /// OAuth configuration
    pub const QWEN_AI_AUTH_URL: &'static str = "https://qwen.ai/oauth/authorize";
    pub const QWEN_AI_TOKEN_URL: &'static str = "https://qwen.ai/oauth/token";
    pub const CLIENT_ID: &'static str = "qwen-code-cli";
    pub const REDIRECT_PATH: &'static str = "/callback";
    
    /// Scopes needed for Qwen Code
    pub const SCOPES: &'static str = "openid profile qwen-code-api";
    
    pub fn new(config_dir: PathBuf) -> Self {
        Self {
            token_path: config_dir.join("qwen-oauth.json"),
            token: None,
        }
    }
    
    /// Start OAuth flow - opens browser, waits for callback
    pub async fn start_auth_flow(&mut self) -> Result<()> {
        info!("Starting Qwen OAuth flow...");
        
        // 1. Start local server to receive callback
        let (port, callback_rx) = self.start_callback_server().await?;
        info!("Callback server on port {}", port);
        
        // 2. Build OAuth URL
        let auth_url = format!(
            "{}?response_type=code&client_id={}&redirect_uri=http://localhost:{}{}&scope={}&state=archclaw-{}",
            Self::QWEN_AI_AUTH_URL,
            Self::CLIENT_ID,
            port,
            Self::REDIRECT_PATH,
            Self::SCOPES,
            std::time::SystemTime::now().duration_since(std::time::UNIX_EPOCH)?.as_secs(),
        );
        
        // 3. Open browser
        info!("Opening browser to: {}", auth_url);
        self.open_browser(&auth_url)?;
        
        // 4. Wait for callback with auth code
        info!("Waiting for OAuth callback...");
        let auth_code = callback_rx.await
            .context("OAuth flow timed out or was cancelled")?;
        
        // 5. Exchange code for token
        info!("Exchanging auth code for token...");
        let token = self.exchange_code_for_token(&auth_code, port).await?;
        
        // 6. Save token
        self.token = Some(token.clone());
        self.save_token(&token)?;
        
        info!("✓ Qwen OAuth authenticated! 2,000 requests/day free.");
        Ok(())
    }
    
    /// Start HTTP server to receive OAuth callback
    async fn start_callback_server(&self) -> Result<(u16, tokio::sync::oneshot::Receiver<String>)> {
        use tokio::sync::oneshot;
        
        let (tx, rx) = oneshot::channel();
        
        // Find available port
        let listener = tokio::net::TcpListener::bind("127.0.0.1:0").await
            .context("Failed to bind to port")?;
        let port = listener.local_addr()?.port();
        
        // Spawn server
        tokio::spawn(async move {
            use tokio::io::{AsyncReadExt, AsyncWriteExt};
            
            if let Ok((mut stream, _)) = listener.accept().await {
                let mut buffer = [0; 4096];
                if let Ok(n) = stream.read(&mut buffer).await {
                    let request = String::from_utf8_lossy(&buffer[..n]);
                    
                    // Extract auth code from callback URL
                    if let Some(code) = Self::extract_code_from_request(&request) {
                        // Send success response
                        let response = "HTTP/1.1 200 OK\r\nContent-Type: text/html\r\n\r\n\
                            <html><body><h1>✅ Qwen OAuth Success!</h1>\
                            <p>You can close this window and return to ArchClaw.</p>\
                            <script>window.close();</script></body></html>";
                        let _ = stream.write_all(response.as_bytes()).await;
                        
                        // Send code back to main flow
                        let _ = tx.send(code);
                    }
                }
            }
        });
        
        Ok((port, rx))
    }
    
    /// Extract OAuth code from HTTP request
    fn extract_code_from_request(request: &str) -> Option<String> {
        // Parse "GET /callback?code=ABC123&state=XYZ HTTP/1.1"
        let first_line = request.lines().next()?;
        let path = first_line.split_whitespace().nth(1)?;
        
        if !path.starts_with(Self::REDIRECT_PATH) {
            return None;
        }
        
        // Extract code parameter
        let query = path.split('?').nth(1)?;
        for param in query.split('&') {
            let mut parts = param.splitn(2, '=');
            if let (Some("code"), Some(code)) = (parts.next(), parts.next()) {
                return Some(code.to_string());
            }
        }
        
        None
    }
    
    /// Exchange authorization code for access token
    async fn exchange_code_for_token(&self, code: &str, port: u16) -> Result<QwenOAuthToken> {
        // Build token request
        let client = reqwest::Client::new();
        
        let response = client.post(Self::QWEN_AI_TOKEN_URL)
            .form(&[
                ("grant_type", "authorization_code"),
                ("code", code),
                ("redirect_uri", &format!("http://localhost:{}{}", port, Self::REDIRECT_PATH)),
                ("client_id", Self::CLIENT_ID),
            ])
            .send()
            .await
            .context("Failed to exchange code for token")?
            .json::<serde_json::Value>()
            .await
            .context("Failed to parse token response")?;
        
        // Parse response
        let access_token = response.get("access_token")
            .and_then(|v| v.as_str())
            .context("No access_token in response")?
            .to_string();
        
        let refresh_token = response.get("refresh_token")
            .and_then(|v| v.as_str())
            .context("No refresh_token in response")?
            .to_string();
        
        let token_type = response.get("token_type")
            .and_then(|v| v.as_str())
            .unwrap_or("Bearer")
            .to_string();
        
        let expires_in = response.get("expires_in")
            .and_then(|v| v.as_u64())
            .unwrap_or(3600); // Default 1 hour
        
        let expires_at = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)?
            .as_secs() + expires_in;
        
        let scope = response.get("scope")
            .and_then(|v| v.as_str())
            .unwrap_or(Self::SCOPES)
            .to_string();
        
        Ok(QwenOAuthToken {
            access_token,
            refresh_token,
            token_type,
            expires_at,
            scope,
        })
    }
    
    /// Open URL in browser
    fn open_browser(&self, url: &str) -> Result<()> {
        #[cfg(target_os = "android")]
        {
            // On Android, use am start command
            use std::process::Command;
            Command::new("am")
                .arg("start")
                .arg("-a")
                .arg("android.intent.action.VIEW")
                .arg("-d")
                .arg(url)
                .spawn()?;
        }
        
        #[cfg(not(target_os = "android"))]
        {
            // On desktop, use xdg-open/open/start
            use std::process::Command;
            
            let cmd = if cfg!(target_os = "macos") {
                Command::new("open").arg(url).spawn()
            } else if cfg!(target_os = "windows") {
                Command::new("cmd").args(["/c", "start", url]).spawn()
            } else {
                Command::new("xdg-open").arg(url).spawn()
            };
            
            if cmd.is_err() {
                // Fallback: print URL for user to copy
                println!("\n🔐 Open this URL in your browser:\n{}\n", url);
            }
        }
        
        Ok(())
    }
    
    /// Save encrypted token to disk
    fn save_token(&self, token: &QwenOAuthToken) -> Result<()> {
        // Ensure directory exists
        if let Some(parent) = self.token_path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        
        // Serialize
        let json = serde_json::to_string_pretty(token)?;
        
        // TODO: Encrypt with AES-GCM before saving
        // For now, save plaintext (should be in app's private directory)
        std::fs::write(&self.token_path, json)?;
        
        info!("Token saved to: {:?}", self.token_path);
        Ok(())
    }
    
    /// Load token from disk
    pub fn load_token(&mut self) -> Result<Option<QwenOAuthToken>> {
        if !self.token_path.exists() {
            return Ok(None);
        }
        
        let content = std::fs::read_to_string(&self.token_path)?;
        
        // TODO: Decrypt if encrypted
        let token: QwenOAuthToken = serde_json::from_str(&content)?;
        
        // Check if expired
        if token.is_expired() {
            info!("Token expired, will refresh");
            // Try to refresh
            let refreshed = self.refresh_token(&token);
            match refreshed {
                Ok(new_token) => {
                    self.token = Some(new_token.clone());
                    self.save_token(&new_token)?;
                    return Ok(Some(new_token));
                }
                Err(e) => {
                    info!("Token refresh failed: {}", e);
                    return Ok(None); // Force re-auth
                }
            }
        }
        
        self.token = Some(token.clone());
        Ok(Some(token))
    }
    
    /// Refresh an expired token
    fn refresh_token(&self, token: &QwenOAuthToken) -> Result<QwenOAuthToken> {
        info!("Refreshing Qwen OAuth token...");
        
        // Use refresh token to get new access token
        // This is synchronous for simplicity
        let client = reqwest::blocking::Client::new();
        
        let response = client.post(Self::QWEN_AI_TOKEN_URL)
            .form(&[
                ("grant_type", "refresh_token"),
                ("refresh_token", &token.refresh_token),
                ("client_id", Self::CLIENT_ID),
            ])
            .send()?
            .json::<serde_json::Value>()?;
        
        // Parse new token (same structure as exchange_code_for_token)
        let access_token = response.get("access_token")
            .and_then(|v| v.as_str())
            .context("No access_token in refresh response")?
            .to_string();
        
        let expires_in = response.get("expires_in")
            .and_then(|v| v.as_u64())
            .unwrap_or(3600);
        
        let expires_at = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)?
            .as_secs() + expires_in;
        
        Ok(QwenOAuthToken {
            access_token,
            refresh_token: token.refresh_token.clone(), // Keep same refresh token
            token_type: token.token_type.clone(),
            expires_at,
            scope: token.scope.clone(),
        })
    }
    
    /// Check if user is authenticated
    pub fn is_authenticated(&mut self) -> bool {
        match self.load_token() {
            Ok(Some(token)) => !token.is_expired(),
            _ => false,
        }
    }
    
    /// Get access token (for use with Qwen API)
    pub fn get_access_token(&mut self) -> Result<String> {
        if let Some(token) = &self.token {
            if !token.expires_soon() {
                return Ok(token.access_token.clone());
            }
        }
        
        // Load or refresh
        if let Some(token) = self.load_token()? {
            return Ok(token.access_token.clone());
        }
        
        anyhow::bail!("Not authenticated. Run 'archclaw auth qwen' to login.")
    }
    
    /// Logout (delete token)
    pub fn logout(&mut self) -> Result<()> {
        if self.token_path.exists() {
            std::fs::remove_file(&self.token_path)?;
            info!("Logged out, token deleted");
        }
        self.token = None;
        Ok(())
    }
    
    /// Get auth status
    pub fn status(&mut self) -> AuthStatus {
        match self.load_token() {
            Ok(Some(token)) => {
                if token.is_expired() {
                    AuthStatus::Expired
                } else {
                    let remaining_requests = 2000; // TODO: Track actual usage
                    AuthStatus::Active { 
                        remaining_requests,
                        expires_at: token.expires_at,
                    }
                }
            }
            _ => AuthStatus::NotLoggedIn,
        }
    }
}

/// Authentication status
#[derive(Debug)]
pub enum AuthStatus {
    NotLoggedIn,
    Active { 
        remaining_requests: u32,
        expires_at: u64,
    },
    Expired,
}
