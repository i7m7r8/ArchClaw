//! Qwen OAuth Authentication

use anyhow::{Result, Context};
use serde::{Deserialize, Serialize};
use std::path::PathBuf;
use tracing::info;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct QwenOAuthToken {
    pub access_token: String,
    pub refresh_token: String,
    pub token_type: String,
    pub expires_at: u64,
    pub scope: String,
}

impl QwenOAuthToken {
    pub fn is_expired(&self) -> bool {
        let now = std::time::SystemTime::now()
            .duration_since(std::time::UNIX_EPOCH)
            .unwrap()
            .as_secs();
        now >= self.expires_at
    }
}

pub struct QwenOAuth {
    pub token_path: PathBuf,
    pub token: Option<QwenOAuthToken>,
}

impl QwenOAuth {
    pub fn new(config_dir: PathBuf) -> Self {
        Self {
            token_path: config_dir.join("qwen-oauth.json"),
            token: None,
        }
    }

    pub fn load_token(&mut self) -> Result<Option<QwenOAuthToken>> {
        if !self.token_path.exists() {
            return Ok(None);
        }
        let content = std::fs::read_to_string(&self.token_path)?;
        let token: QwenOAuthToken = serde_json::from_str(&content)?;
        if token.is_expired() {
            info!("Token expired");
            return Ok(None);
        }
        self.token = Some(token.clone());
        Ok(Some(token))
    }

    pub fn save_token(&self, token: &QwenOAuthToken) -> Result<()> {
        if let Some(parent) = self.token_path.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let json = serde_json::to_string_pretty(token)?;
        std::fs::write(&self.token_path, json)?;
        info!("Token saved");
        Ok(())
    }

    pub fn is_authenticated(&mut self) -> bool {
        self.load_token().map(|t| t.is_some()).unwrap_or(false)
    }

    pub fn get_access_token(&mut self) -> Result<String> {
        if let Some(token) = &self.token {
            if !token.is_expired() {
                return Ok(token.access_token.clone());
            }
        }
        if let Some(token) = self.load_token()? {
            return Ok(token.access_token.clone());
        }
        anyhow::bail!("Not authenticated")
    }

    pub fn logout(&mut self) -> Result<()> {
        if self.token_path.exists() {
            std::fs::remove_file(&self.token_path)?;
        }
        self.token = None;
        Ok(())
    }
}
