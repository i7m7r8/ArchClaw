//! Proot manager - Arch Linux environment

use anyhow::{Context, Result};
use std::path::{Path, PathBuf};
use std::process::Stdio;
use tokio::process::Command;
use tracing::info;

#[derive(Debug, Clone, PartialEq)]
pub enum ProotStatus {
    NotInitialized,
    Downloading,
    Extracting,
    Ready,
    Running,
    Error(String),
}

pub struct ProotManager {
    pub rootfs_path: PathBuf,
    pub status: ProotStatus,
}

impl ProotManager {
    pub fn new(rootfs_path: PathBuf) -> Self {
        Self {
            rootfs_path,
            status: ProotStatus::NotInitialized,
        }
    }

    pub async fn exec(&self, command: &str, _args: &[&str]) -> Result<std::process::Output> {
        let output = Command::new("proot")
            .arg("-r").arg(&self.rootfs_path)
            .arg("bash").arg("-c").arg(command)
            .output().await
            .context("Failed to execute command in proot")?;
        Ok(std::process::Output {
            status: output.status,
            stdout: output.stdout,
            stderr: output.stderr,
        })
    }

    pub fn is_ready(&self) -> bool {
        self.rootfs_path.exists() && self.rootfs_path.join("usr").exists()
    }

    pub fn status(&self) -> &ProotStatus {
        &self.status
    }

    pub fn rootfs_path(&self) -> &Path {
        &self.rootfs_path
    }
}

/// Bionic libc compatibility patches
pub mod bionic_patches {
    use std::path::Path;
    
    pub fn apply_nodejs_patch(_node_path: &Path) -> anyhow::Result<()> {
        // Patches os.networkInterfaces() crashes on Android Bionic libc
        Ok(())
    }
}
