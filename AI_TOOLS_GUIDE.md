# 🤖 ArchClaw AI Tools Guide

> **Complete guide to using OpenClaw, Claude Code, Codex CLI, Aider, and more on Android**

---

## Quick Reference

```bash
# One-liner to remember
archclaw <tool>     # Launch any AI tool
archclaw openclaw   # Start OpenClaw gateway
archclaw claude     # Launch Claude Code
archclaw aider      # Launch Aider
archclaw status     # Check everything
```

---

## 📦 Supported AI Tools

### 1. OpenClaw (Gateway + Hardware)

**What it does**: AI gateway that connects to 7 providers and exposes 15 Android hardware capabilities

**Install**:
```bash
archclaw openclaw start
# Auto-installs on first run
```

**Use**:
```bash
# Start gateway
archclaw openclaw start

# Check status
archclaw openclaw status

# Open dashboard
archclaw openclaw dashboard
# Opens http://localhost:18789

# Stop gateway
archclaw openclaw stop
```

**Configure AI Provider**:
```bash
# Set provider
archclaw config set provider anthropic
archclaw config set key "sk-ant-..."

# Test connection
archclaw config test
```

**Hardware Capabilities** (15 total):
- 📷 **Camera** - AI can take photos via Android Camera2 API
- 📍 **Location** - GPS/network location data
- 📸 **Screen Record** - AI sees your screen via MediaProjection
- 📱 **Sensors** - Accelerometer, gyroscope, magnetometer
- 🔦 **Flash** - Torch control
- 📳 **Haptic** - Vibration feedback
- 🎨 **Canvas** - Draw overlays on screen

---

### 2. Claude Code (Anthropic)

**What it does**: Anthropic's official AI coding agent for terminal. Reads your codebase, writes code, runs commands.

**Requirements**: Anthropic API key

**Launch**:
```bash
# Basic
archclaw claude

# With API key
archclaw claude --key "sk-ant-..."

# Specific model
archclaw claude --model claude-sonnet-4-20250514
```

**In Claude Code**:
```
> Read this codebase and explain the architecture
> Add error handling to all API endpoints
> Write tests for the auth module
> Refactor this function to be more efficient
```

**Set API key permanently**:
```bash
export ANTHROPIC_API_KEY="sk-ant-..."  # In ~/.bashrc
```

---

### 3. Codex CLI (OpenAI)

**What it does**: OpenAI's coding CLI for autonomous code generation and editing

**Requirements**: OpenAI API key

**Launch**:
```bash
# Basic
archclaw codex

# With API key
archclaw codex --key "sk-proj-..."
```

**Use**:
```
> Create a REST API with Express
> Add user authentication
> Implement rate limiting
```

---

### 4. Aider (Multi-Provider)

**What it does**: AI pair programming in your terminal. Works with ANY provider.

**Requirements**: API key for your chosen provider

**Launch**:
```bash
# With Anthropic (default)
archclaw aider
archclaw aider --anthropic

# With OpenAI
archclaw aider --openai --key "sk-proj-..."

# With Gemini
archclaw aider --gemini --key "AIza..."

# Point to specific files
archclaw aider --anthropic src/main.rs src/config.rs
```

**In Aider**:
```
> Add input validation to the login function
> Write unit tests for utils.py
> Refactor this to use async/await
> /drop file.py  # Remove file from chat
> /clear         # Clear chat history
> /undo          # Undo last change
```

**Pro tips**:
- Works with git (auto-commits changes)
- Can edit multiple files at once
- Supports images: paste image URL or path

---

### 5. Continue (IDE)

**What it does**: Full IDE in browser with AI coding assistance

**Launch**:
```bash
archclaw continue
# Opens IDE at http://localhost:4000
```

**Features**:
- Code completion
- Chat with AI about your code
- Edit code with AI
- Multiple model support

---

### 6. Goose (Block)

**What it does**: Block's autonomous AI coding agent

**Launch**:
```bash
archclaw goose
```

---

### 7. Amp (Amp.ai)

**What it does**: AI coding agent by Amp.ai

**Launch**:
```bash
archclaw amp
```

---

## 🔧 Managing Tools

### List All Tools
```bash
archclaw tools list

# Output:
# 📦 AI Tools Status:
# 
# Tool              Status    Description
# ─────────────────────────────────────────
# OpenClaw          ✓ Installed  AI gateway with hardware
# Claude Code       ✓ Installed  Anthropic's coding agent
# Codex CLI         Not installed  OpenAI's coding CLI
# Aider             ✓ Installed  AI pair programming
# Continue          Not installed  AI coding IDE
# Goose             Not installed  Block's AI agent
# Amp               Not installed  AI coding agent
```

### Install Tools
```bash
# Install all tools
archclaw tools install

# Install specific tool
archclaw tools install claude
archclaw tools install aider
archclaw tools install codex

# Update all tools
archclaw tools update

# Remove a tool
archclaw tools remove codex
```

### Check Status
```bash
archclaw status

# Output:
# 📊 ArchClaw Status:
# 
#   Arch Linux:      ✓ Ready
#   Node.js:         ✓ v22.x
#   
#   AI Tools:
#     OpenClaw          ✓
#     Claude Code       ✓
#     Codex CLI         —
#     Aider             ✓
#     Continue          —
#     Goose             —
#     Amp               —
#   
#   Services:
#     OpenClaw Gateway: ✓ Running (port 18789)
```

---

## ⚙️ Configuration

### Set AI Provider
```bash
# Interactive setup
archclaw config ai

# Set directly
archclaw config set provider anthropic
archclaw config set key "sk-ant-..."

# Supported providers:
# - anthropic (Claude)
# - openai (GPT)
# - gemini (Google)
# - openrouter (multi-model)
# - nvidia-nim
# - deepseek
# - xai (Grok)
```

### Test Connection
```bash
archclaw config test

# Output:
# 🧪 Testing AI connection...
# Testing anthropic connection...
# ✓ Connected to Anthropic Claude
```

### Switch Providers
```bash
# Easily switch between providers
archclaw config set provider openai
archclaw config test
# ✓ Connected to OpenAI GPT

archclaw config set provider gemini
archclaw config test
# ✓ Connected to Google Gemini
```

---

## 💡 Common Workflows

### Workflow 1: Start Coding with Claude
```bash
# 1. Setup (first time only)
archclaw setup

# 2. Configure AI
archclaw config set provider anthropic
archclaw config set key "sk-ant-..."

# 3. Start coding
cd /path/to/project
archclaw claude

# Claude is now reading your codebase!
```

### Workflow 2: Pair Program with Aider
```bash
# 1. Navigate to project
cd /path/to/project

# 2. Launch Aider with specific files
archclaw aider --anthropic src/main.rs lib.rs

# 3. Start pairing
> Add error handling to the API client
> Refactor this function
> Write tests
```

### Workflow 3: Use OpenClaw with Hardware
```bash
# 1. Start gateway
archclaw openclaw start

# 2. Open dashboard
archclaw openclaw dashboard

# 3. AI can now:
#    - Take photos with your camera
#    - Get your location
#    - Record your screen
#    - Access sensors
#    - And more!
```

### Workflow 4: Try Multiple AI Models
```bash
# Test same prompt with different models
archclaw aider --anthropic    # Claude
archclaw aider --openai       # GPT-4
archclaw aider --gemini       # Gemini
archclaw aider --deepseek     # DeepSeek
```

---

## 🚨 Troubleshooting

### Tool won't install
```bash
# Check internet connection
ping google.com

# Check Arch Linux
archclaw shell
pacman -Syu

# Try manual install
proot-distro login archlinux
npm install -g @anthropic-ai/claude-code
```

### API key not working
```bash
# Verify key is set
archclaw config get key

# Test connection
archclaw config test

# Re-set key
archclaw config set key "your-new-key"
```

### Out of storage
```bash
# Check usage
archclaw shell
df -h

# Remove unused tools
archclaw tools remove continue
archclaw tools remove goose

# Clean npm cache
proot-distro login archlinux
npm cache clean --force
```

### Gateway won't start
```bash
# Check if port is in use
archclaw shell
lsof -i :18789

# Kill existing process
kill $(lsof -t -i:18789)

# Restart
archclaw openclaw start
```

---

## 📊 Tool Comparison

| Feature | OpenClaw | Claude | Codex | Aider | Continue |
|---------|----------|--------|-------|-------|----------|
| **Type** | Gateway | Agent | Agent | Pair | IDE |
| **Providers** | 7 | 1 | 1 | 7+ | 7+ |
| **Hardware** | ✓ 15 caps | ✗ | ✗ | ✗ | ✗ |
| **Terminal** | ✗ | ✓ | ✓ | ✓ | ✗ |
| **IDE** | WebView | ✗ | ✗ | ✗ | ✓ |
| **Multi-file** | ✗ | ✓ | ✓ | ✓ | ✓ |
| **Best for** | Hardware | Coding | Code gen | Pair prog | Full IDE |

---

## 🔐 Security

### API Keys
- Stored encrypted in app storage
- Never transmitted except to AI provider APIs
- Can be rotated anytime via `archclaw config set key`

### PRoot Isolation
- All tools run in isolated Arch Linux environment
- No root access required
- Can't access Android app data without permission

### Hardware Permissions
- Each capability requires explicit Android permission
- Can be disabled anytime in settings
- Permissions shown when first used

---

## 📚 Resources

- **OpenClaw Docs**: https://github.com/openclaw/openclaw
- **Claude Code**: https://docs.anthropic.com/en/docs/claude-code
- **Codex CLI**: https://github.com/openai/codex
- **Aider**: https://aider.chat/
- **Continue**: https://docs.continue.dev/

---

**Need help?** Run `archclaw help` or check [QUICKSTART.md](QUICKSTART.md)

🐉 *Happy AI coding on Android!*
