//! ArchClaw Core - Qwen AI tools manager for Android
//!
//! Manages installation, OAuth authentication, and execution
//! of AI development tools on Android via Arch Linux PRoot.

pub mod config;
pub mod tools;
pub mod proot;
pub mod gateway;
pub mod hardware;
pub mod auth;

pub use config::Config;
pub use tools::ToolManager;
pub use proot::ProotManager;
pub use gateway::GatewayManager;
pub use auth::qwen_oauth::QwenOAuth;

/// Application version
pub const VERSION: &str = env!("CARGO_PKG_VERSION");

/// Application name
pub const APP_NAME: &str = "ArchClaw";

/// Default gateway port
pub const DEFAULT_GATEWAY_PORT: u16 = 18789;

/// Supported architectures
#[derive(Debug, Clone, Copy, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub enum Architecture {
    ARM64,
    X86_64,
}

impl Architecture {
    pub fn from_str(s: &str) -> Option<Self> {
        match s {
            "aarch64" | "arm64" => Some(Architecture::ARM64),
            "x86_64" | "amd64" => Some(Architecture::X86_64),
            _ => None,
        }
    }

    pub fn as_str(&self) -> &'static str {
        match self {
            Architecture::ARM64 => "aarch64",
            Architecture::X86_64 => "x86_64",
        }
    }
}

/// AI Provider
#[derive(Debug, Clone, PartialEq, Eq, serde::Serialize, serde::Deserialize)]
pub enum AIProvider {
    QwenOAuth,
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
            Self::QwenOAuth => "QWEN_ACCESS_TOKEN",
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
            Self::QwenOAuth => "qwen3-coder-plus",
            Self::Anthropic => "claude-sonnet-4-20250514",
            Self::OpenAI => "gpt-4o",
            Self::Gemini => "gemini-2.5-pro",
            Self::OpenRouter => "anthropic/claude-sonnet-4",
            Self::NvidiaNIM => "meta/llama-3.1-405b-instruct",
            Self::DeepSeek => "deepseek-chat",
            Self::XAI => "grok-3",
        }
    }

    pub fn is_free(&self) -> bool {
        matches!(self, Self::QwenOAuth)
    }
}

/// AI Tool installation type
#[derive(Debug, Clone, PartialEq, Eq)]
pub enum InstallType {
    Npm,
    Pip,
    Cargo,
    Custom,
}

/// AI Tool information
#[derive(Debug, Clone)]
pub struct AIToolInfo {
    pub id: &'static str,
    pub name: &'static str,
    pub description: &'static str,
    pub install_type: InstallType,
    pub package: &'static str,
    pub command: &'static str,
    pub port: Option<u16>,
    pub provider: AIProvider,
    pub is_free: bool,
}

/// All supported AI tools
pub const SUPPORTED_TOOLS: &[AIToolInfo] = &[
    AIToolInfo {
        id: "qwen",
        name: "Qwen Code",
        description: "Qwen's official coding CLI",
        install_type: InstallType::Npm,
        package: "@qwen-code/qwen-code",
        command: "qwen",
        port: None,
        provider: AIProvider::QwenOAuth,
        is_free: true,
    },
    AIToolInfo {
        id: "zeroclaw",
        name: "ZeroClaw",
        description: "Lightweight AI agent (<5MB RAM)",
        install_type: InstallType::Custom,
        package: "zeroclaw",
        command: "zeroclaw",
        port: Some(3000),
        provider: AIProvider::QwenOAuth,
        is_free: true,
    },
    AIToolInfo {
        id: "openclaw",
        name: "OpenClaw",
        description: "Full AI gateway + hardware",
        install_type: InstallType::Npm,
        package: "openclaw",
        command: "openclaw",
        port: Some(18789),
        provider: AIProvider::QwenOAuth,
        is_free: true,
    },
    AIToolInfo {
        id: "aider",
        name: "Aider",
        description: "AI pair programming",
        install_type: InstallType::Pip,
        package: "aider-chat",
        command: "aider",
        port: None,
        provider: AIProvider::QwenOAuth,
        is_free: true,
    },
    AIToolInfo {
        id: "claude",
        name: "Claude Code",
        description: "Anthropic's coding agent",
        install_type: InstallType::Npm,
        package: "@anthropic-ai/claude-code",
        command: "claude",
        port: None,
        provider: AIProvider::Anthropic,
        is_free: false,
    },
    AIToolInfo {
        id: "gemini",
        name: "Gemini CLI",
        description: "Google's coding CLI",
        install_type: InstallType::Npm,
        package: "@anthropic-ai/gemini-cli",
        command: "gemini",
        port: None,
        provider: AIProvider::Gemini,
        is_free: false,
    },
];

impl AIToolInfo {
    pub fn find(id: &str) -> Option<&'static Self> {
        SUPPORTED_TOOLS.iter().find(|t| t.id == id)
    }
}
