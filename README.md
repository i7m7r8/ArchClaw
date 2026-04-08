# 🐉 ArchClaw - Standalone AI Tools APK

> **Install APK → Setup wizard → Use Qwen Code, ZeroClaw, OpenClaw. No Termux, no root, no dependencies.**

[![License: GPL-3.0](https://img.shields.io/badge/License-GPL--3.0-blue.svg)](LICENSE)
[![APK Size](https://img.shields.io/badge/APK%20Size-~150MB-orange.svg)]()
[![Platform](https://img.shields.io/badge/Platform-Android_10+-green.svg)]()

## 🎯 How It Works

```
Install APK (150MB)
  ↓
First Launch: Setup Wizard
  ├─ Downloads Arch Linux rootfs (~300MB)
  ├─ Sets up proot environment
  ├─ Installs Node.js, Python
  ├─ Installs Qwen Code, ZeroClaw, OpenClaw, Aider
  └─ Takes ~5-10 minutes (one-time)
  ↓
Ready to Use:
  ├─ Tap "Qwen Code" → Start coding
  ├─ Tap "ZeroClaw" → Start lightweight gateway
  ├─ Tap "OpenClaw" → Start full gateway
  └─ All FREE with Qwen OAuth (2,000 req/day)
```

## 🚀 Quick Start

### 1. Install APK
```bash
# Download from releases
adb install archclaw.apk
# Or transfer to device and tap to install
```

### 2. First Launch → Setup Wizard
```
┌──────────────────────────────────┐
│    🐉 Welcome to ArchClaw        │
│                                  │
│    Setup will install:           │
│    ✓ Arch Linux environment      │
│    ✓ Qwen Code, ZeroClaw, etc    │
│    ✓ ~300MB download (one-time)  │
│                                  │
│       [ Start Setup ]            │
└──────────────────────────────────┘
```

### 3. Setup Progress
```
┌──────────────────────────────────┐
│    Setting up...                 │
│                                  │
│    ▓▓▓▓▓▓▓▓░░░░░░░░  45%       │
│                                  │
│    Downloading Arch Linux...     │
│    (3 of 5 steps)                │
│                                  │
│    ETA: 4 minutes                │
└──────────────────────────────────┘
```

### 4. Qwen OAuth Login
```
┌──────────────────────────────────┐
│    🔐 Login with Qwen (FREE)     │
│    2,000 requests/day           │
│    No credit card needed         │
│                                  │
│    [ Login with Qwen ]           │
│    (Opens WebView → qwen.ai)     │
└──────────────────────────────────┘
```

### 5. Use AI Tools
```
┌──────────────────────────────────┐
│    🐉 ArchClaw                   │
│    ✓ Qwen OAuth Active           │
│                                  │
│    [▶ Qwen Code]   [▶ ZeroClaw]  │
│    [▶ OpenClaw]    [▶ Aider]     │
│    [▶ Claude]      [▶ Gemini]    │
│                                  │
│    [ Terminal ]  [ Settings ]    │
└──────────────────────────────────┘
```

## 🤖 Supported AI Tools

| Tool | Type | Auth | Size |
|------|------|------|------|
| **Qwen Code** | CLI | ✅ FREE OAuth | ~50MB |
| **ZeroClaw** | Gateway | ✅ FREE OAuth | ~9MB |
| **OpenClaw** | Gateway | ✅ FREE OAuth | ~100MB |
| **Aider** | Pair | ✅ FREE OAuth | ~30MB |
| Claude Code | CLI | ❌ API key | ~50MB |
| Gemini CLI | CLI | ❌ API key | ~30MB |
| Codex CLI | CLI | ❌ API key | ~40MB |

## ✨ Features

### 📦 Standalone APK
- **No Termux needed** - everything bundled
- **No root needed** - uses proot (user-space chroot)
- **One-time setup** - downloads ~300MB on first launch
- **Background service** - tools keep running

### 🔐 Qwen OAuth (FREE)
- **Built-in WebView** - no browser switching
- **2,000 requests/day** - completely free
- **No credit card** - just qwen.ai account
- **Auto token refresh** - stays logged in

### 🖥️ Built-in Terminal
- **Full PTY support** - real terminal
- **Extra key row** - Ctrl, Alt, Tab, Esc, etc.
- **Copy/paste** - long press to select
- **Multiple sessions** - tab support

### 📱 Android Integration
- **Foreground service** - tools run in background
- **Notifications** - shows active tools
- **File sharing** - shared folder between Android ↔ Arch
- **Battery optimization** - handles Doze mode

## 🏗️ Architecture

```
┌─────────────────────────────────────────────┐
│              ArchClaw APK                    │
│                                              │
│  ┌─────────────────────────────────────┐    │
│  │         Flutter UI Layer            │    │
│  │  • Setup Wizard                     │    │
│  │  • Tool Launcher                    │    │
│  │  • Terminal Emulator                │    │
│  │  • OAuth WebView                    │    │
│  │  • Settings                         │    │
│  └──────────────┬──────────────────────┘    │
│                 │ Platform Channel          │
│  ┌──────────────▼──────────────────────┐    │
│  │       Kotlin Native Layer           │    │
│  │  • ProotManager (JNI)               │    │
│  │  • ForegroundService                │    │
│  │  • OAuth WebView                    │    │
│  │  • File Manager                     │    │
│  └──────────────┬──────────────────────┘    │
│                 │ Process                   │
│  ┌──────────────▼──────────────────────┐    │
│  │      Arch Linux Environment         │    │
│  │  (Extracted to app storage)         │    │
│  │  • proot binary                     │    │
│  │  • Node.js 22 → Qwen Code, etc      │    │
│  │  • Python 3.12 → Aider              │    │
│  │  • Base system (pacman, git, etc)   │    │
│  └─────────────────────────────────────┘    │
└─────────────────────────────────────────────┘
```

## 📖 Setup Process (Detailed)

### Step 1: Download Rootfs
```
Downloads Arch Linux ARM64 from mirror
Size: ~300MB compressed
Source: https://mirror.archlinuxarm.org/
Verifies: SHA256 checksum
```

### Step 2: Extract
```
Extracts to: /data/data/io.archclaw/files/archlinux/
Uses: tar xzf (streaming decompression)
Time: ~2-3 minutes
```

### Step 3: Bootstrap
```
Inside proot:
- pacman -Syu (update system)
- Install: nodejs, npm, python, pip, git
- Install AI tools: Qwen Code, ZeroClaw, etc
Time: ~3-5 minutes
```

### Step 4: Configure
```
- Setup DNS resolution
- Create shared storage folder
- Configure OAuth directory
- Save setup complete flag
```

### Total Setup Time: ~5-10 minutes (depends on network)

## 🔐 Qwen OAuth Flow (Built-in)

```
1. User taps "Login with Qwen"
2. App opens WebView to qwen.ai/oauth
3. User signs in (or creates account)
4. OAuth callback captured by app
5. Token encrypted → saved securely
6. All Qwen tools unlocked

No browser switching. No Termux. All in-app.
```

## 📊 Storage Usage

| Component | Size |
|-----------|------|
| APK (compressed) | ~150MB |
| APK (installed) | ~300MB |
| Arch Linux rootfs | ~800MB |
| AI tools | ~400MB |
| **Total on device** | **~1.5GB** |

## 🛠️ Build from Source

### Prerequisites
```bash
# On any platform with Docker:
docker pull archclaw/build:latest

# Or native:
# - Android SDK + NDK
# - Flutter 3.16+
# - Gradle 8+
```

### Build APK
```bash
git clone https://github.com/archclaw/archclaw.git
cd archclaw/android

# Debug build
./gradlew assembleDebug

# Release build
./gradlew assembleRelease
# Output: app/build/outputs/apk/release/app-release.apk
```

### GitHub Actions
```
Push to main → Auto-build → APK artifact
PR → Auto-build + test
```

## 📚 Documentation

- **[QUICKSTART.md](QUICKSTART.md)** - 5-min setup guide
- **[QWEN_OAUTH_GUIDE.md](QWEN_OAUTH_GUIDE.md)** - OAuth reference
- **[AI_TOOLS_GUIDE.md](AI_TOOLS_GUIDE.md)** - All tools
- **[MASTERPLAN.md](MASTERPLAN.md)** - Full roadmap
- **[ARCHITECTURE.md](ARCHITECTURE.md)** - Technical details

## 🤝 Credits

- **[Qwen Code](https://github.com/QwenLM/qwen-code)** - Qwen CLI
- **[qwen.ai](https://qwen.ai)** - Free OAuth
- **[LocalDesktop](https://github.com/localdesktop/localdesktop.github.io)** - proot APK architecture
- **[OpenClaw](https://github.com/openclaw/openclaw)** - AI gateway
- **[ZeroClaw](https://github.com/zeroclaw-labs/zeroclaw)** - Lightweight agent

## 📄 License

GPL-3.0 - See [LICENSE](LICENSE)

---

**Install. Setup. Code with Qwen. All in one APK.** 🐉
