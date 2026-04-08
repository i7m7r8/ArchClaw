//! ArchClaw - OpenClaw & AI Tools Manager for Android
//! 
//! Core engine that automatically sets up and runs AI development tools
//! on Android via Arch Linux PRoot environment.

pub mod config;
pub mod tools;
pub mod proot;
pub mod gateway;
pub mod hardware;

pub use config::Config;
pub use tools::{AITool, ToolManager};
pub use proot::ProotManager;
pub use gateway::GatewayManager;

/// Application version
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// Supported AI tools registry
pub const SUPPORTED_TOOLS: &[AIToolInfo] = &[
    AIToolInfo {
        id: "openclaw",
        name: "OpenClaw",
        description: "AI gateway with 15 Android hardware capabilities",
        install_type: InstallType::Npm,
        package: "openclaw",
        command: "openclaw",
        args: &["start"],
        port: Some(18789),
        provider: AIProvider::Anthropic,
    },
    AIToolInfo {
        id: "claude",
        name: "Claude Code",
        description: "Anthropic's AI coding agent for terminal",
        install_type: InstallType::Npm,
        package: "@anthropic-ai/claude-code",
        command: "claude",
        args: &[],
        port: None,
        provider: AIProvider::Anthropic,
    },
    AIToolInfo {
        id: "codex",
        name: "Codex CLI",
        description: "OpenAI's coding CLI for autonomous code generation",
        install_type: InstallType::Npm,
        package: "@openai/codex",
        command: "codex",
        args: &[],
        port: None,
        provider: AIProvider::OpenAI,
    },
    AIToolInfo {
        id: "aider",
        name: "Aider",
        description: "AI pair programming in your terminal",
        install_type: InstallType::Pip,
        package: "aider-chat",
        command: "aider",
        args: &[],
        port: None,
        provider: AIProvider::Anthropic, // Supports multiple
    },
    AIToolInfo {
        id: "continue",
        name: "Continue",
        description: "Open-source AI coding assistant (IDE)",
        install_type: InstallType::Custom,
        package: "continue",
        command: "continue",
        args: &["ide"],
        port: Some(4000),
        provider: AIProvider::OpenAI,
    },
    AIToolInfo {
        id: "goose",
        name: "Goose",
        description: "Block's AI coding agent",
        install_type: InstallType::Custom,
        package: "goose",
        command: "goose",
        args: &["run"],
        port: None,
        provider: AIProvider::Anthropic,
    },
    AIToolInfo {
        id: "amp",
        name: "Amp",
        description: "AI coding agent by Amp.ai",
        install_type: InstallType::Npm,
        package: "@amp-ai/amp",
        command: "amp",
        args: &[],
        port: None,
        provider: AIProvider::Anthropic,
    },
];

/// AI Tool installation type
#[derive(Debug, Clone, PartialEq)]
pub enum InstallType {
    Npm,    // npm install -g
    Pip,    // pip install
    Cargo,  // cargo install
    Custom, // Custom install script
}

/// AI Provider
#[derive(Debug, Clone, PartialEq)]
pub enum AIProvider {
    Anthropic,
    OpenAI,
    Gemini,
    OpenRouter,
    NvidiaNIM,
    DeepSeek,
    XAI,
}

impl AIProvider {
    pub fn env_var(&self) -> &'static str {
        match self {
            Self::Anthropic => "ANTHROPIC_API_KEY",
            Self::OpenAI => "OPENAI_API_KEY",
            Self::Gemini => "GEMINI_API_KEY",
            Self::OpenRouter => "OPENROUTER_API_KEY",
            Self::NvidiaNIM => "NVIDIA_API_KEY",
            Self::DeepSeek => "DEEPSEEK_API_KEY",
            Self::XAI => "XAI_API_KEY",
        }
    }
    
    pub fn default_model(&self) -> &'static str {
        match self {
            Self::Anthropic => "claude-sonnet-4-20250514",
            Self::OpenAI => "gpt-4o",
            Self::Gemini => "gemini-2.5-pro",
            Self::OpenRouter => "anthropic/claude-sonnet-4",
            Self::NvidiaNIM => "meta/llama-3.1-405b-instruct",
            Self::DeepSeek => "deepseek-chat",
            Self::XAI => "grok-3",
        }
    }
}

/// AI Tool information
pub struct AIToolInfo {
    pub id: &'static str,
    pub name: &'static str,
    pub description: &'static str,
    pub install_type: InstallType,
    pub package: &'static str,
    pub command: &'static str,
    pub args: &'static [&'static str],
    pub port: Option<u16>,
    pub provider: AIProvider,
}
