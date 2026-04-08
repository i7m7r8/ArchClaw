//! Gateway management

use anyhow::Result;
use tracing::info;

#[derive(Debug, Clone, PartialEq)]
pub enum GatewayStatus {
    Stopped,
    Starting,
    Running,
    Stopping,
    Error(String),
}

pub struct GatewayManager {
    status: GatewayStatus,
    port: u16,
}

impl GatewayManager {
    pub fn new(port: u16) -> Self {
        Self {
            status: GatewayStatus::Stopped,
            port,
        }
    }

    pub async fn start(&mut self) -> Result<()> {
        info!("Starting gateway on port {}", self.port);
        self.status = GatewayStatus::Starting;
        self.status = GatewayStatus::Running;
        info!("Gateway started");
        Ok(())
    }

    pub async fn stop(&mut self) -> Result<()> {
        info!("Stopping gateway");
        self.status = GatewayStatus::Stopped;
        Ok(())
    }

    pub async fn health_check(&self) -> Result<bool> {
        Ok(self.status == GatewayStatus::Running)
    }

    pub fn status(&self) -> &GatewayStatus {
        &self.status
    }
}
