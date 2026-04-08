# 🚀 ArchClaw Quick Start Guide

## Prerequisites

### For Building (Termux)
```bash
# Install required packages
pkg update && pkg upgrade
pkg install rust cargo flutter gradle llvm lld git nodejs
```

### For Users (Android Device)
- Android 10+ (API 29)
- 2GB+ RAM recommended
- 1GB+ free storage

---

## 📦 Installation

### Option 1: Download Pre-built APK
```bash
# Visit releases page
https://github.com/archclaw/archclaw/releases

# Download latest APK
wget https://github.com/archclaw/archclaw/releases/download/v0.1.0/archclaw.apk

# Install on device
adb install archclaw.apk
```

### Option 2: Build from Source (Termux)
```bash
# Clone repository
git clone https://github.com/archclaw/archclaw.git
cd archclaw

# Build APK
./scripts/build-apk.sh

# Or manually:
cd rust/core && cargo build --release --target aarch64-linux-android
cd ../../flutter_app && flutter build apk --release
```

### Option 3: Cross-Platform Build
```bash
# On Linux/macOS/Windows
cargo install xbuild

x build --release --platform android --arch arm64 --format apk
```

---

## 🎯 First Launch

1. **Open ArchClaw app** on your Android device
2. **Setup Wizard** will automatically:
   - Download Arch Linux rootfs (~300MB)
   - Install Node.js 22 and OpenClaw gateway
   - Configure Wayland compositor
   - Set up XFCE desktop (default)

3. **Configure AI Provider**:
   - Choose from 7 providers (Anthropic, OpenAI, Gemini, etc.)
   - Enter your API key (encrypted and stored securely)
   - Test connection

4. **Start Services**:
   - Tap "Start Gateway" to enable AI capabilities
   - Tap "Start Desktop" to launch Linux desktop
   - Open Terminal for command-line access

---

## 💻 CLI Usage

After installing CLI tools (`npm install -g` in `cli/`):

```bash
# Full setup
archclaw setup --desktop xfce

# Start services
archclaw start --gateway --desktop

# Enter Arch Linux shell
archclaw shell

# Check status
archclaw status

# Configure AI
archclaw onboarding

# Install dev tools
archclaw install rust
archclaw install go
archclaw install homebrew
```

---

## 🛠️ Configuration

### Desktop Environment
```bash
# In app settings or CLI:
archclaw config set desktop xfce     # Lightweight (default)
archclaw config set desktop kde      # Full-featured
archclaw config set desktop lxqt     # Ultra-light
archclaw config set desktop gnome    # Modern (resource-heavy)
```

### AI Provider
```bash
# Configure provider
archclaw config set ai.provider anthropic
archclaw config set ai.api_key "sk-ant-..."

# Supported providers:
# - anthropic (Claude)
# - openai (GPT-4/3.5)
# - gemini (Google)
# - openrouter (Multi-model)
# - nvidia-nim
# - deepseek
# - xai (Grok)
```

### Hardware Capabilities
```bash
# Enable hardware features for AI
archclaw hardware enable camera
archclaw hardware enable location
archclaw hardware enable screen-record
archclaw hardware enable sensors
```

---

## 📊 Project Structure

```
archclaw/
├── rust/                    # Rust core engine
│   ├── core/               # Main library
│   ├── wayland/            # Display server
│   ├── proot/              # Linux environment
│   ├── gateway/            # AI gateway
│   └── hardware/           # Hardware protocol
│
├── flutter_app/            # Flutter UI
│   ├── lib/
│   │   ├── main.dart      # Entry point
│   │   ├── providers/     # State management
│   │   └── screens/       # UI screens
│   └── pubspec.yaml
│
├── android/                # Android native layer
│   └── app/src/main/kotlin/
│
├── cli/                    # Termux CLI tools
│   └── bin/archclaw.js
│
├── scripts/                # Build scripts
│   └── build-apk.sh
│
└── .github/workflows/      # CI/CD
    └── build-apk.yml
```

---

## 🔧 Development

### Run in Debug Mode
```bash
# Flutter hot reload
cd flutter_app
flutter run

# Rust tests
cd rust
cargo test
```

### Build for Release
```bash
# Termux
./scripts/build-apk.sh

# Cross-platform
x build --release --platform android --arch arm64 --format aab
```

---

## 🐛 Troubleshooting

### App crashes on startup
- Check Android version (requires Android 10+)
- Ensure storage permissions granted
- Check logcat: `adb logcat | grep archclaw`

### Gateway won't start
- Verify Node.js is installed in Arch
- Check port 18789 is not in use
- Review gateway logs in app

### Desktop not rendering
- Ensure Wayland compositor started
- Check Xwayland bridge is active
- Try switching to terminal-only mode

### Performance issues
- Lower target FPS in settings
- Reduce max RAM usage
- Switch to lighter desktop (LXQt)
- Close unused apps in Arch

---

## 📚 Resources

- **Documentation**: [Wiki](https://github.com/archclaw/archclaw/wiki)
- **Issues**: [GitHub Issues](https://github.com/archclaw/archclaw/issues)
- **Discussions**: [GitHub Discussions](https://github.com/archclaw/archclaw/discussions)
- **Master Plan**: [MASTERPLAN.md](MASTERPLAN.md)

---

## 🤝 Contributing

1. Fork the repository
2. Create feature branch (`git checkout -b feature/amazing-feature`)
3. Commit changes (`git commit -m 'Add amazing feature'`)
4. Push to branch (`git push origin feature/amazing-feature`)
5. Open Pull Request

See [CONTRIBUTING.md](CONTRIBUTING.md) for detailed guidelines.

---

## 📄 License

GPL-3.0 - See [LICENSE](LICENSE) file

---

**Happy hacking with ArchClaw!** 🐉
