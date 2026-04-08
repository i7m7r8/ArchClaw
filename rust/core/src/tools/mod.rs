//! AI Tool Manager - Install, run, and manage AI development tools
//! 
//! Handles automatic installation of OpenClaw, Claude Code, Codex CLI,
//! Aider, Continue, and other AI tools in the Arch Linux environment.

use anyhow::{Result, Context};
use std::collections::HashMap;
use std::process::Stdio;
use tokio::process::Command;
use tracing::{info, error, warn};

use crate::{AIToolInfo, InstallType, AIProvider, SUPPORTED_TOOLS};

/// Status of an AI tool
#[derive(Debug, Clone, PartialEq)]
pub enum ToolStatus {
    NotInstalled,
    Installing,
    Installed,
    Running,
    Stopped,
    Error(String),
}

/// Running AI tool instance
pub struct ToolInstance {
    pub tool_id: String,
    pub status: ToolStatus,
    pub pid: Option<u32>,
    pub port: Option<u16>,
    pub log_output: String,
}

/// Tool manager - handles installation and execution of AI tools
pub struct ToolManager {
    pub tools: HashMap<String, ToolStatus>,
    pub running_instances: HashMap<String, ToolInstance>,
    pub api_keys: HashMap<String, String>,
    pub proot_path: String,
}

impl ToolManager {
    pub fn new(proot_path: &str) -> Self {
        Self {
            tools: HashMap::new(),
            running_instances: HashMap::new(),
            api_keys: HashMap::new(),
            proot_path: proot_path.to_string(),
        }
    }

    /// Check installation status of all tools
    pub async fn check_all_tools(&mut self) -> Result<HashMap<String, ToolStatus>> {
        info!("Checking installation status of all AI tools...");
        
        for tool_info in SUPPORTED_TOOLS {
            let status = self.check_tool_installed(tool_info.id).await?;
            self.tools.insert(tool_info.id.to_string(), status);
        }
        
        Ok(self.tools.clone())
    }

    /// Check if a specific tool is installed
    async fn check_tool_installed(&self, tool_id: &str) -> Result<ToolStatus> {
        let tool_info = match SUPPORTED_TOOLS.iter().find(|t| t.id == tool_id) {
            Some(t) => t,
            None => return Ok(ToolStatus::Error("Unknown tool".into())),
        };

        // Check if command exists
        let output = Command::new("proot")
            .arg("-r")
            .arg(&self.proot_path)
            .arg("which")
            .arg(tool_info.command)
            .output()
            .await;

        match output {
            Ok(out) if out.status.success() => Ok(ToolStatus::Installed),
            Ok(_) => Ok(ToolStatus::NotInstalled),
            Err(e) => Ok(ToolStatus::Error(format!("Check failed: {}", e))),
        }
    }

    /// Install an AI tool
    pub async fn install_tool(&mut self, tool_id: &str) -> Result<()> {
        let tool_info = match SUPPORTED_TOOLS.iter().find(|t| t.id == tool_id) {
            Some(t) => t,
            None => return Err(anyhow::anyhow!("Unknown tool: {}", tool_id)),
        };

        info!("Installing {} ({})...", tool_info.name, tool_info.package);
        self.tools.insert(tool_id.to_string(), ToolStatus::Installing);

        let result = match tool_info.install_type {
            InstallType::Npm => self.install_via_npm(tool_info).await,
            InstallType::Pip => self.install_via_pip(tool_info).await,
            InstallType::Cargo => self.install_via_cargo(tool_info).await,
            InstallType::Custom => self.install_custom(tool_info).await,
        };

        match result {
            Ok(_) => {
                info!("✓ {} installed successfully", tool_info.name);
                self.tools.insert(tool_id.to_string(), ToolStatus::Installed);
                Ok(())
            }
            Err(e) => {
                error!("✗ Failed to install {}: {}", tool_info.name, e);
                self.tools.insert(tool_id.to_string(), ToolStatus::Error(e.to_string()));
                Err(e)
            }
        }
    }

    /// Install tool via npm
    async fn install_via_npm(&self, tool: &AIToolInfo) -> Result<()> {
        info!("  npm install -g {}", tool.package);
        
        let output = Command::new("proot")
            .arg("-r")
            .arg(&self.proot_path)
            .arg("bash")
            .arg("-c")
            .arg(format!("npm install -g {}", tool.package))
            .output()
            .await
            .context("Failed to run npm install")?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(anyhow::anyhow!("npm install failed: {}", stderr));
        }

        Ok(())
    }

    /// Install tool via pip
    async fn install_via_pip(&self, tool: &AIToolInfo) -> Result<()> {
        info!("  pip install {}", tool.package);
        
        let output = Command::new("proot")
            .arg("-r")
            .arg(&self.proot_path)
            .arg("bash")
            .arg("-c")
            .arg(format!("pip install --break-system-packages {}", tool.package))
            .output()
            .await
            .context("Failed to run pip install")?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(anyhow::anyhow!("pip install failed: {}", stderr));
        }

        Ok(())
    }

    /// Install tool via cargo
    async fn install_via_cargo(&self, tool: &AIToolInfo) -> Result<()> {
        info!("  cargo install {}", tool.package);
        
        let output = Command::new("proot")
            .arg("-r")
            .arg(&self.proot_path)
            .arg("bash")
            .arg("-c")
            .arg(format!("cargo install {}", tool.package))
            .output()
            .await
            .context("Failed to run cargo install")?;

        if !output.status.success() {
            let stderr = String::from_utf8_lossy(&output.stderr);
            return Err(anyhow::anyhow!("cargo install failed: {}", stderr));
        }

        Ok(())
    }

    /// Install tool with custom script
    async fn install_custom(&self, tool: &AIToolInfo) -> Result<()> {
        info!("  Running custom install for {}", tool.name);
        
        // Check if custom install script exists
        let script_path = format!("/opt/archclaw/scripts/install_{}.sh", tool.id);
        
        let output = Command::new("proot")
            .arg("-r")
            .arg(&self.proot_path)
            .arg("bash")
            .arg(&script_path)
            .output()
            .await;

        match output {
            Ok(out) if out.status.success() => Ok(()),
            Ok(out) => {
                let stderr = String::from_utf8_lossy(&out.stderr);
                Err(anyhow::anyhow!("Custom install failed: {}", stderr))
            }
            Err(e) => Err(anyhow::anyhow!("Failed to run install script: {}", e)),
        }
    }

    /// Run an AI tool
    pub async fn run_tool(&mut self, tool_id: &str, extra_args: &[String]) -> Result<()> {
        let tool_info = match SUPPORTED_TOOLS.iter().find(|t| t.id == tool_id) {
            Some(t) => t,
            None => return Err(anyhow::anyhow!("Unknown tool: {}", tool_id)),
        };

        // Check if installed
        if self.tools.get(tool_id) != Some(&ToolStatus::Installed) {
            info!("{} not installed, installing...", tool_info.name);
            self.install_tool(tool_id).await?;
        }

        info!("▶ Starting {}...", tool_info.name);
        
        // Build command with API keys
        let mut cmd = Command::new("proot");
        cmd.arg("-r")
           .arg(&self.proot_path)
           .arg("--bind")
           .arg("/data/data/io.archclaw/files/shared:/shared") // Share files
           .arg("env");

        // Set API keys from config
        if let Some(key) = self.api_keys.get(tool_info.provider.env_var()) {
            cmd.env(tool_info.provider.env_var(), key);
        }

        cmd.arg(tool_info.command)
           .args(tool_info.args)
           .args(extra_args)
           .stdin(Stdio::piped())
           .stdout(Stdio::piped())
           .stderr(Stdio::piped());

        // Spawn process
        let child = cmd.spawn()
            .context("Failed to start tool")?;

        let pid = child.id();
        
        let instance = ToolInstance {
            tool_id: tool_id.to_string(),
            status: ToolStatus::Running,
            pid,
            port: tool_info.port,
            log_output: String::new(),
        };

        self.running_instances.insert(tool_id.to_string(), instance);

        info!("✓ {} started (PID: {:?})", tool_info.name, pid);
        if let Some(port) = tool_info.port {
            info!("  Access at: http://localhost:{}", port);
        }

        Ok(())
    }

    /// Stop a running tool
    pub async fn stop_tool(&mut self, tool_id: &str) -> Result<()> {
        if let Some(instance) = self.running_instances.get(tool_id) {
            if let Some(pid) = instance.pid {
                info!("Stopping {} (PID: {})...", tool_id, pid);
                
                // Send SIGTERM
                let _ = Command::new("kill")
                    .arg(pid.to_string())
                    .output()
                    .await;
                
                self.running_instances.remove(tool_id);
                info!("✓ {} stopped", tool_id);
            }
        }
        Ok(())
    }

    /// Install all supported tools at once
    pub async fn install_all(&mut self) -> Result<()> {
        info!("🚀 Installing all AI tools...");
        
        for tool_info in SUPPORTED_TOOLS {
            match self.install_tool(tool_info.id).await {
                Ok(_) => info!("  ✓ {}", tool_info.name),
                Err(e) => warn!("  ✗ {}: {}", tool_info.name, e),
            }
        }
        
        info!("All tools installation complete!");
        Ok(())
    }

    /// Get list of all tools with status
    pub fn list_tools(&self) -> Vec<(&str, &ToolStatus)> {
        self.tools.iter()
            .map(|(id, status)| (id.as_str(), status))
            .collect()
    }

    /// Set API key for a provider
    pub fn set_api_key(&mut self, provider: AIProvider, key: String) {
        self.api_keys.insert(provider.env_var().to_string(), key);
    }

    /// Get running tools
    pub fn running_tools(&self) -> Vec<&ToolInstance> {
        self.running_instances.values().collect()
    }
}
