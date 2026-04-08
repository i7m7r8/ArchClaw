//! Tool manager - install and launch AI tools

use anyhow::{Context, Result};
use std::collections::HashMap;
use std::process::Stdio;
use tokio::process::Command;
use tracing::{info, warn};

use crate::{AIToolInfo, InstallType, SUPPORTED_TOOLS};

#[derive(Debug, Clone, PartialEq)]
pub enum ToolStatus {
    NotInstalled,
    Installing,
    Installed,
    Running,
    Error(String),
}

pub struct ToolInstance {
    pub tool_id: String,
    pub status: ToolStatus,
    pub pid: Option<u32>,
}

pub struct ToolManager {
    pub tools: HashMap<String, ToolStatus>,
    pub instances: HashMap<String, ToolInstance>,
    pub api_keys: HashMap<String, String>,
    pub proot_path: String,
}

impl ToolManager {
    pub fn new(proot_path: &str) -> Self {
        Self {
            tools: HashMap::new(),
            instances: HashMap::new(),
            api_keys: HashMap::new(),
            proot_path: proot_path.to_string(),
        }
    }

    pub async fn check_all(&mut self) -> Result<HashMap<String, ToolStatus>> {
        for tool in SUPPORTED_TOOLS {
            let status = self.check_installed(tool).await;
            self.tools.insert(tool.id.to_string(), status);
        }
        Ok(self.tools.clone())
    }

    async fn check_installed(&self, tool: &AIToolInfo) -> ToolStatus {
        let output = Command::new("proot")
            .arg("-r").arg(&self.proot_path)
            .arg("which").arg(tool.command)
            .output().await;
        
        match output {
            Ok(out) if out.status.success() => ToolStatus::Installed,
            _ => ToolStatus::NotInstalled,
        }
    }

    pub async fn install(&mut self, tool_id: &str) -> Result<()> {
        let tool = AIToolInfo::find(tool_id)
            .ok_or_else(|| anyhow::anyhow!("Unknown tool: {}", tool_id))?;

        info!("Installing {}...", tool.name);
        self.tools.insert(tool_id.to_string(), ToolStatus::Installing);

        let result = match tool.install_type {
            InstallType::Npm => self.install_npm(tool).await,
            InstallType::Pip => self.install_pip(tool).await,
            InstallType::Cargo => self.install_cargo(tool).await,
            InstallType::Custom => self.install_custom(tool).await,
        };

        match result {
            Ok(()) => {
                info!("✓ {} installed", tool.name);
                self.tools.insert(tool_id.to_string(), ToolStatus::Installed);
                Ok(())
            }
            Err(e) => {
                warn!("✗ {} failed: {}", tool.name, e);
                self.tools.insert(tool_id.to_string(), ToolStatus::Error(e.to_string()));
                Err(e)
            }
        }
    }

    async fn install_npm(&self, tool: &AIToolInfo) -> Result<()> {
        let output = Command::new("proot")
            .arg("-r").arg(&self.proot_path)
            .arg("bash").arg("-c")
            .arg(format!("npm install -g {}", tool.package))
            .output().await.context("npm install failed")?;

        if !output.status.success() {
            return Err(anyhow::anyhow!("npm failed: {}", String::from_utf8_lossy(&output.stderr)));
        }
        Ok(())
    }

    async fn install_pip(&self, tool: &AIToolInfo) -> Result<()> {
        let output = Command::new("proot")
            .arg("-r").arg(&self.proot_path)
            .arg("bash").arg("-c")
            .arg(format!("pip install --break-system-packages {}", tool.package))
            .output().await.context("pip install failed")?;

        if !output.status.success() {
            return Err(anyhow::anyhow!("pip failed: {}", String::from_utf8_lossy(&output.stderr)));
        }
        Ok(())
    }

    async fn install_cargo(&self, tool: &AIToolInfo) -> Result<()> {
        let output = Command::new("proot")
            .arg("-r").arg(&self.proot_path)
            .arg("bash").arg("-c")
            .arg(format!("cargo install {}", tool.package))
            .output().await.context("cargo install failed")?;

        if !output.status.success() {
            return Err(anyhow::anyhow!("cargo failed: {}", String::from_utf8_lossy(&output.stderr)));
        }
        Ok(())
    }

    async fn install_custom(&self, tool: &AIToolInfo) -> Result<()> {
        let script = format!("/opt/archclaw/scripts/install_{}.sh", tool.id);
        let output = Command::new("proot")
            .arg("-r").arg(&self.proot_path)
            .arg("bash").arg(&script)
            .output().await;

        match output {
            Ok(out) if out.status.success() => Ok(()),
            Ok(out) => Err(anyhow::anyhow!("Custom install failed: {}", String::from_utf8_lossy(&out.stderr))),
            Err(e) => Err(anyhow::anyhow!("Failed to run script: {}", e)),
        }
    }

    pub fn set_api_key(&mut self, provider_env_var: &str, key: String) {
        self.api_keys.insert(provider_env_var.to_string(), key);
    }

    pub fn list(&self) -> Vec<(&str, &ToolStatus)> {
        self.tools.iter().map(|(k, v)| (k.as_str(), v)).collect()
    }

    pub async fn install_all(&mut self) -> Result<()> {
        for tool in SUPPORTED_TOOLS {
            let _ = self.install(tool.id).await;
        }
        Ok(())
    }
}
