//! Hardware protocol - Android capabilities exposed to AI

use anyhow::Result;
use tracing::info;

#[derive(Debug, Clone, PartialEq)]
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

pub struct HardwareProtocol {
    enabled: Vec<Capability>,
    port: u16,
}

impl HardwareProtocol {
    pub fn new(port: u16) -> Self {
        Self {
            enabled: Vec::new(),
            port,
        }
    }

    pub fn enable(&mut self, cap: Capability) {
        if !self.enabled.contains(&cap) {
            info!("Enabling: {:?}", cap);
            self.enabled.push(cap);
        }
    }

    pub fn disable(&mut self, cap: &Capability) {
        info!("Disabling: {:?}", cap);
        self.enabled.retain(|c| c != cap);
    }

    pub fn is_enabled(&self, cap: &Capability) -> bool {
        self.enabled.contains(cap)
    }

    pub async fn start(&self) -> Result<()> {
        info!("Starting hardware WebSocket on port {}", self.port);
        Ok(())
    }

    pub async fn stop(&self) -> Result<()> {
        info!("Stopping hardware WebSocket");
        Ok(())
    }

    pub fn capabilities(&self) -> &[Capability] {
        &self.enabled
    }
}
