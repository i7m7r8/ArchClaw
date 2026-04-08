//! Configuration management

use serde::{Deserialize, Serialize};
use std::path::PathBuf;

use crate::AIProvider;

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    pub architecture: crate::Architecture,
    pub ai_provider: Option<AIProvider>,
    pub auto_start_tools: Vec<String>,
    pub paths: PathConfig,
}

#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PathConfig {
    pub base_dir: PathBuf,
    pub rootfs_dir: PathBuf,
    pub config_file: PathBuf,
    pub shared_storage: PathBuf,
}

impl Default for Config {
    fn default() -> Self {
        let base = PathBuf::from("/data/data/io.archclaw/files");
        Self {
            architecture: crate::Architecture::ARM64,
            ai_provider: Some(AIProvider::QwenOAuth),
            auto_start_tools: vec![],
            paths: PathConfig {
                base_dir: base.clone(),
                rootfs_dir: base.join("rootfs"),
                config_file: base.join("config.json"),
                shared_storage: base.join("shared"),
            },
        }
    }
}

impl Config {
    pub fn load(path: &PathBuf) -> Result<Self, anyhow::Error> {
        if !path.exists() {
            return Ok(Self::default());
        }
        let content = std::fs::read_to_string(path)?;
        Ok(serde_json::from_str(&content)?)
    }

    pub fn save(&self) -> Result<(), anyhow::Error> {
        if let Some(parent) = self.paths.config_file.parent() {
            std::fs::create_dir_all(parent)?;
        }
        let content = serde_json::to_string_pretty(self)?;
        std::fs::write(&self.paths.config_file, content)?;
        Ok(())
    }
}
