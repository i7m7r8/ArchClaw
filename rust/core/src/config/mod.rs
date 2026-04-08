//! Configuration management for ArchClaw

use serde::{Deserialize, Serialize};
use std::path::PathBuf;

use crate::{AIProvider, Architecture, DesktopEnvironment};

/// Main configuration structure
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct Config {
    /// Architecture target
    pub architecture: Architecture,
    
    /// Desktop environment to use
    pub desktop_environment: DesktopEnvironment,
    
    /// AI Provider configuration
    pub ai_provider: Option<AIProviderConfig>,
    
    /// Hardware capabilities
    pub hardware: HardwareConfig,
    
    /// Development tools
    pub dev_tools: DevToolsConfig,
    
    /// Path configurations
    pub paths: PathConfig,
    
    /// Performance settings
    pub performance: PerformanceConfig,
}

impl Default for Config {
    fn default() -> Self {
        Self {
            architecture: Architecture::ARM64,
            desktop_environment: DesktopEnvironment::XFCE,
            ai_provider: None,
            hardware: HardwareConfig::default(),
            dev_tools: DevToolsConfig::default(),
            paths: PathConfig::default(),
            performance: PerformanceConfig::default(),
        }
    }
}

/// AI Provider configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct AIProviderConfig {
    pub provider: AIProvider,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub api_key: Option<String>,
    #[serde(skip_serializing_if = "Option::is_none")]
    pub api_url: Option<String>,
    pub model: String,
}

/// Hardware capabilities configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct HardwareConfig {
    pub camera: bool,
    pub location: bool,
    pub screen_record: bool,
    pub sensors: bool,
    pub flash: bool,
    pub haptic: bool,
    pub canvas: bool,
}

impl Default for HardwareConfig {
    fn default() -> Self {
        Self {
            camera: false,
            location: false,
            screen_record: false,
            sensors: false,
            flash: false,
            haptic: false,
            canvas: false,
        }
    }
}

/// Development tools configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct DevToolsConfig {
    pub go: bool,
    pub rust: bool,
    pub homebrew: bool,
    pub openssh: bool,
    pub build_essential: bool,
}

impl Default for DevToolsConfig {
    fn default() -> Self {
        Self {
            go: false,
            rust: false,
            homebrew: false,
            openssh: true,
            build_essential: false,
        }
    }
}

/// Path configurations
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PathConfig {
    /// Base directory for ArchClaw data
    pub base_dir: PathBuf,
    
    /// Rootfs directory location
    pub rootfs_dir: PathBuf,
    
    /// Configuration file location
    pub config_file: PathBuf,
    
    /// Log directory
    pub log_dir: PathBuf,
    
    /// Shared storage between Android and Arch
    pub shared_storage: PathBuf,
}

impl Default for PathConfig {
    fn default() -> Self {
        let base = PathBuf::from("/data/data/io.archclaw/files");
        Self {
            base_dir: base.clone(),
            rootfs_dir: base.join("archlinux"),
            config_file: base.join("config.json"),
            log_dir: base.join("logs"),
            shared_storage: base.join("shared"),
        }
    }
}

/// Performance configuration
#[derive(Debug, Clone, Serialize, Deserialize)]
pub struct PerformanceConfig {
    /// Maximum RAM usage in MB
    pub max_ram_mb: u32,
    
    /// Target FPS
    pub target_fps: u8,
    
    /// Enable hardware acceleration
    pub hw_acceleration: bool,
    
    /// Number of worker threads
    pub worker_threads: Option<usize>,
}

impl Default for PerformanceConfig {
    fn default() -> Self {
        Self {
            max_ram_mb: 500,
            target_fps: 60,
            hw_acceleration: true,
            worker_threads: None, // Auto-detect
        }
    }
}

impl Config {
    /// Load configuration from file
    pub fn load(path: &PathBuf) -> Result<Self, anyhow::Error> {
        if !path.exists() {
            return Ok(Self::default());
        }
        
        let content = std::fs::read_to_string(path)?;
        let config: Config = serde_json::from_str(&content)?;
        Ok(config)
    }
    
    /// Save configuration to file
    pub fn save(&self) -> Result<(), anyhow::Error> {
        let content = serde_json::to_string_pretty(self)?;
        
        // Ensure parent directory exists
        if let Some(parent) = self.paths.config_file.parent() {
            std::fs::create_dir_all(parent)?;
        }
        
        std::fs::write(&self.paths.config_file, content)?;
        Ok(())
    }
    
    /// Create default configuration
    pub fn create_default() -> Self {
        Self::default()
    }
}
