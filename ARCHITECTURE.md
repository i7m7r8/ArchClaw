# 🏛️ ArchClaw Architecture Deep Dive

## System Overview

ArchClaw is a multi-layered application that combines:
- **Flutter/Dart** for the user interface
- **Kotlin** for Android system integration
- **Rust** for performance-critical components
- **Arch Linux** as the guest operating system
- **Node.js** for the AI gateway runtime

---

## Layer Architecture

```
┌──────────────────────────────────────────────────────────────────┐
│                        USER LAYER                                 │
│  ┌────────────┐  ┌──────────────┐  ┌───────────┐  ┌──────────┐  │
│  │ Home Screen│  │ Setup Wizard │  │ Dashboard │  │ Settings │  │
│  └────────────┘  └──────────────┘  └───────────┘  └──────────┘  │
└────────────────────────┬─────────────────────────────────────────┘
                         │ Flutter MethodChannel
┌────────────────────────▼─────────────────────────────────────────┐
│                   ANDROID NATIVE LAYER (Kotlin)                   │
│  ┌──────────────────┐  ┌────────────────┐  ┌──────────────────┐ │
│  │ MainActivity     │  │ Foreground     │  │ Permission       │ │
│  │ Platform Bridge  │  │ Service        │  │ Manager          │ │
│  └────────┬─────────┘  └────────┬───────┘  └────────┬─────────┘ │
│           │                     │                     │           │
│  ┌────────▼─────────┐  ┌────────▼───────┐  ┌────────▼─────────┐ │
│  │ Camera API       │  │ Battery        │  │ Storage          │ │
│  │ Location API     │  │ Optimization   │  │ File Manager     │ │
│  │ Sensor API       │  │ Wake Lock      │  │ SSH Manager      │ │
│  └──────────────────┘  └────────────────┘  └──────────────────┘ │
└────────────────────────┬─────────────────────────────────────────┘
                         │ JNI / FFI Bindings
┌────────────────────────▼─────────────────────────────────────────┐
│                      RUST CORE ENGINE                              │
│  ┌──────────────────┐  ┌────────────────┐  ┌──────────────────┐ │
│  │ Wayland          │  │ PRoot          │  │ Hardware         │ │
│  │ Compositor       │  │ Manager        │  │ Protocol         │ │
│  │ + Xwayland       │  │ + Arch Rootfs  │  │ WebSocket        │ │
│  └────────┬─────────┘  └────────┬───────┘  └────────┬─────────┘ │
│           │                     │                     │           │
│  ┌────────▼─────────┐  ┌────────▼───────┐  ┌────────▼─────────┐ │
│  │ Display Output   │  │ Filesystem     │  │ Camera Stream    │ │
│  │ Input Handling   │  │ Process Mgmt   │  │ Sensor Data      │ │
│  │ Clipboard        │  │ DNS/Network    │  │ Location Data    │ │
│  └──────────────────┘  └────────────────┘  └──────────────────┘ │
└────────────────────────┬─────────────────────────────────────────┘
                         │ PRoot (ptrace-based chroot)
┌────────────────────────▼─────────────────────────────────────────┐
│                  ARCH LINUX GUEST ENVIRONMENT                     │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐  │
│  │ Desktop      │  │ X11/Wayland  │  │ Development Tools     │  │
│  │ Environment  │  │ Applications │  │ (pacman, git, etc.)   │  │
│  │ (XFCE/KDE)   │  │              │  │                       │  │
│  └──────────────┘  └──────────────┘  └───────────────────────┘  │
│  ┌──────────────┐  ┌──────────────┐  ┌───────────────────────┐  │
│  │ Node.js 22   │  │ OpenClaw     │  │ Optional: Go, Rust,   │  │
│  │ Runtime      │  │ Gateway      │  │ Homebrew, OpenSSH     │  │
│  └──────────────┘  └──────────────┘  └───────────────────────┘  │
└──────────────────────────────────────────────────────────────────┘
```

---

## Data Flow

### 1. User Interaction Flow
```
User taps "Start Desktop"
  ↓
Flutter UI calls MethodChannel
  ↓
Kotlin MainActivity receives call
  ↓
Calls Rust FFI: compositor.start()
  ↓
Rust starts Wayland compositor
  ↓
Rust launches PRoot with Arch Linux
  ↓
Arch Linux starts XFCE desktop
  ↓
Status updates flow back up the chain
  ↓
UI shows "Desktop Running"
```

### 2. AI Hardware Protocol Flow
```
AI sends request via WebSocket
  ↓
Rust Hardware Protocol receives
  ↓
Calls Kotlin Camera API
  ↓
Android captures photo
  ↓
Photo data flows back through Kotlin → Rust
  ↓
Rust sends to WebSocket
  ↓
AI receives image data
```

### 3. Terminal Execution Flow
```
User types command in terminal
  ↓
Flutter terminal widget sends to Kotlin
  ↓
Kotlin calls Rust: proot.exec(command)
  ↓
Rust spawns process in PRoot
  ↓
Arch Linux executes command
  ↓
Output flows back: Arch → Rust → Kotlin → Flutter
  ↓
Terminal displays output
```

---

## Component Details

### Flutter UI Layer

**State Management**: Provider + ChangeNotifier
```dart
// Example: Gateway state flow
GatewayProvider.start()
  ↓
Calls MethodChannel.invoke('startGateway')
  ↓
Kotlin receives and calls Rust FFI
  ↓
Rust starts gateway process
  ↓
Updates status via StreamChannel
  ↓
Flutter UI rebuilds with new status
```

**Key Widgets**:
- `TerminalView` - PTY terminal emulator
- `DashboardWebView` - Embedded gateway dashboard
- `StatusCard` - Service status indicators
- `HardwareControls` - Capability toggles

---

### Rust Core Engine

**Module Structure**:
```rust
archclaw-core/
├── config/       # Configuration management
│   ├── mod.rs    # Config struct and serialization
│   └── encryption.rs # API key encryption
│
├── wayland/      # Display server
│   ├── mod.rs    # Compositor manager
│   ├── compositor.rs # Wayland compositor
│   └── xwayland.rs   # X11 bridge
│
├── proot/        # Linux environment
│   ├── mod.rs    # PRoot manager
│   ├── arch_rootfs.rs # Arch Linux setup
│   ├── mounts.rs  # Filesystem mounts
│   └── bionic.rs  # Bionic patches
│
├── gateway/      # AI gateway
│   ├── mod.rs    # Gateway manager
│   ├── openclaw.rs # OpenClaw integration
│   └── health.rs  # Health checks
│
└── hardware/     # Hardware protocol
    ├── mod.rs    # Protocol manager
    ├── protocol.rs # WebSocket server
    └── sensors.rs  # Sensor streaming
```

**Key Design Patterns**:
- Async/await with Tokio runtime
- Error handling with `anyhow` + `thiserror`
- Configuration with `serde` serialization
- Tracing for structured logging

---

### Android Native Layer

**Kotlin Package Structure**:
```kotlin
io.archclaw/
├── MainActivity.kt          # Entry point, method channels
├── bridge/
│   ├── PlatformBridge.kt   # Native bridge logic
│   ├── HardwareProtocol.kt # Hardware capability APIs
│   └── ProotBridge.kt      # PRoot communication
├── service/
│   ├── ForegroundService.kt # Persistent service
│   ├── GatewayService.kt    # Gateway lifecycle
│   └── DesktopService.kt    # Desktop management
├── permissions/
│   ├── HardwarePermissions.kt # Permission handling
│   └── BatteryOptimization.kt # Doze mode
└── utils/
    ├── FileHelper.kt        # File operations
    ├── NetworkHelper.kt     # Network utilities
    └── Logger.kt            # Logging
```

**Android Services**:
- **ForegroundService**: Keeps app alive, shows notification
- **GatewayService**: Manages Node.js/OpenClaw process
- **DesktopService**: Manages Wayland/XFCE lifecycle

---

## Memory Architecture

```
┌─────────────────────────────────────┐
│       Android Process (~100MB)       │
│  ┌───────────────────────────────┐  │
│  │ Flutter Engine (~80MB)        │  │
│  │  - Dart VM                    │  │
│  │  - UI widgets                 │  │
│  │  - State management           │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ Kotlin Layer (~20MB)          │  │
│  │  - Activities                 │  │
│  │  - Services                   │  │
│  │  - Platform channels          │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│       Rust Process (~150MB)          │
│  ┌───────────────────────────────┐  │
│  │ Wayland Compositor (~50MB)    │  │
│  │  - wlroots backend            │  │
│  │  - Input handling             │  │
│  │  - Xwayland bridge            │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ Hardware Protocol (~30MB)     │  │
│  │  - WebSocket server           │  │
│  │  - Sensor streaming           │  │
│  │  - Camera handling            │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ PRoot Manager (~70MB)         │  │
│  │  - Process isolation          │  │
│  │  - Filesystem mounts          │  │
│  │  - Network configuration      │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘

┌─────────────────────────────────────┐
│    Arch Linux Guest (~250MB)         │
│  ┌───────────────────────────────┐  │
│  │ Desktop Environment (~120MB)  │  │
│  │  - XFCE/KDE/LXQt              │  │
│  │  - Window manager             │  │
│  │  - Panels, icons, etc.        │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ Node.js + OpenClaw (~80MB)    │  │
│  │  - V8 engine                  │  │
│  │  - Gateway server             │  │
│  │  - AI provider SDKs           │  │
│  └───────────────────────────────┘  │
│  ┌───────────────────────────────┐  │
│  │ Applications (~50MB)          │  │
│  │  - Terminal, file manager     │  │
│  │  - Dev tools, utilities       │  │
│  └───────────────────────────────┘  │
└─────────────────────────────────────┘

Total: ~500MB RAM (typical usage)
```

---

## Network Architecture

```
┌─────────────────────────────────────────┐
│         External Internet                │
│  ┌─────────────┐  ┌──────────────────┐ │
│  │ AI Providers│  │ Package Mirrors  │ │
│  │ (HTTP API)  │  │ (HTTP/FTP)       │ │
│  └──────┬──────┘  └────────┬─────────┘ │
└─────────┼──────────────────┼───────────┘
          │                  │
          │ HTTPS            │ HTTPS
          │                  │
┌─────────▼──────────────────▼───────────┐
│         Android Device                  │
│  ┌──────────────────────────────────┐  │
│  │  ArchClaw App                     │  │
│  │  ┌────────────────────────────┐  │  │
│  │  │ OpenClaw Gateway           │  │  │
│  │  │ localhost:18789            │  │  │
│  │  │  ↕ HTTP/WebView            │  │  │
│  │  │  ↕ WebSocket (Hardware)    │  │  │
│  │  └────────────────────────────┘  │  │
│  └──────────────────────────────────┘  │
│                                         │
│  ┌──────────────────────────────────┐  │
│  │  SSH Server (optional)           │  │
│  │  localhost:2222                  │  │
│  │  ↕ SSH from network              │  │
│  └──────────────────────────────────┘  │
└─────────────────────────────────────────┘
```

---

## Build Pipeline

```
Developer pushes code to GitHub
  ↓
GitHub Actions triggered
  ↓
┌──────────────────────────────────┐
│  CI/CD Pipeline                   │
│  1. Checkout code                 │
│  2. Setup Rust + Flutter + NDK    │
│  3. Build Rust libraries          │
│     cargo build --release         │
│  4. Build Flutter APK             │
│     flutter build apk             │
│  5. Run tests                     │
│     cargo test + flutter test     │
│  6. Upload APK artifact           │
└──────────────────────────────────┘
  ↓
APK available for download
  ↓
Users install and test
```

---

## Security Model

```
┌─────────────────────────────────────┐
│         Security Layers              │
│                                     │
│  1. Android Sandbox                 │
│     - App-specific storage          │
│     - Permission-based access       │
│     - SELinux policies              │
│                                     │
│  2. PRoot Isolation                 │
│     - User-space chroot             │
│     - No root privileges            │
│     - Filesystem namespace          │
│                                     │
│  3. API Key Encryption              │
│     - Encrypted storage (AES-GCM)   │
│     - Never transmitted in plaintext│
│     - Rotated regularly             │
│                                     │
│  4. Network Security                │
│     - Localhost only (no exposure)  │
│     - HTTPS for external APIs       │
│     - WebSocket authentication      │
│                                     │
│  5. Code Security                   │
│     - Rust memory safety            │
│     - No unsafe by default          │
│     - Regular audits                │
└─────────────────────────────────────┘
```

---

## Performance Optimization Strategies

### 1. **Startup Time**
- Lazy loading of components
- Pre-download rootfs in background
- Parallel initialization of services
- Cache frequently used data

### 2. **Memory Management**
- Configurable RAM limits
- Garbage collection tuning
- Object pooling for terminal buffers
- Lazy desktop environment loading

### 3. **Rendering Performance**
- Hardware-accelerated Wayland
- Frame rate targeting (60 FPS)
- Buffer optimization
- Reduce overdraw in Flutter UI

### 4. **Network Efficiency**
- Compressed WebSocket messages
- Connection pooling
- Request batching
- Smart caching

---

## Future Architecture Enhancements

### Phase 2 (v2.0)
- Multi-window support
- GPU passthrough for gaming
- Audio system integration
- Bluetooth device support

### Phase 3 (v3.0)
- Container support (Podman)
- Kubernetes node capability
- Multi-user support
- Cloud sync for configurations

### Phase 4 (v4.0+)
- Custom kernel compilation
- Bare-metal Linux installation
- Dual-boot support
- Remote desktop server

---

**Architecture Version**: 1.0.0
**Last Updated**: April 8, 2026
**Status**: Implementation In Progress
