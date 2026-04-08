//! Hardware protocol module - Android capabilities exposed to AI

use anyhow::Result;
use tracing::info;

/// Hardware capability enumeration
#[derive(Debug, Clone)]
pub enum Capability {
    Camera,
    Flash,
    Location,
    ScreenRecord,
    Accelerometer,
    Gyroscope,
    Magnetometer,
    Haptic,
    Canvas,
}

impl Capability {
    pub fn as_str(&self) -> &'static str {
        match self {
            Self::Camera => "camera",
            Self::Flash => "flash",
            Self::Location => "location",
            Self::ScreenRecord => "screen_record",
            Self::Accelerometer => "accelerometer",
            Self::Gyroscope => "gyroscope",
            Self::Magnetometer => "magnetometer",
            Self::Haptic => "haptic",
            Self::Canvas => "canvas",
        }
    }
}

/// Hardware protocol manager
pub struct HardwareProtocol {
    enabled_capabilities: Vec<Capability>,
    websocket_port: u16,
}

impl HardwareProtocol {
    pub fn new(port: u16) -> Self {
        Self {
            enabled_capabilities: Vec::new(),
            websocket_port: port,
        }
    }

    /// Enable a hardware capability
    pub fn enable(&mut self, capability: Capability) {
        if !self.enabled_capabilities.contains(&capability) {
            info!("Enabling capability: {:?}", capability);
            self.enabled_capabilities.push(capability);
        }
    }

    /// Disable a hardware capability
    pub fn disable(&mut self, capability: &Capability) {
        info!("Disabling capability: {:?}", capability);
        self.enabled_capabilities.retain(|c| c != capability);
    }

    /// Check if capability is enabled
    pub fn is_enabled(&self, capability: &Capability) -> bool {
        self.enabled_capabilities.contains(capability)
    }

    /// Start WebSocket server
    pub async fn start(&self) -> Result<()> {
        info!("Starting hardware WebSocket server on port {}", self.websocket_port);
        // TODO: Implement WebSocket server
        // 1. Create WebSocket server
        // 2. Handle capability requests
        // 3. Stream sensor data
        Ok(())
    }

    /// Stop WebSocket server
    pub async fn stop(&self) -> Result<()> {
        info!("Stopping hardware WebSocket server");
        // TODO: Implement server shutdown
        Ok(())
    }

    /// Get list of enabled capabilities
    pub fn capabilities(&self) -> &[Capability] {
        &self.enabled_capabilities
    }
}
