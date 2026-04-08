#!/bin/bash
# ArchClaw One-Line Install Script
# Usage: curl -fsSL https://archclaw.dev/install.sh | bash
# Or:    wget -qO- https://archclaw.dev/install.sh | bash

set -e

# Colors
RED='\033[0;31m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
BLUE='\033[0;34m'
CYAN='\033[0;36m'
NC='\033[0m'

info() { echo -e "${BLUE}[ArchClaw]${NC} $1"; }
success() { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }
error() { echo -e "${RED}[✗]${NC} $1"; }

echo ""
echo -e "${CYAN}╔══════════════════════════════════════════╗"
echo -e "║     🐉 ArchClaw Installer v0.1.0          ║"
echo -e "║  OpenClaw + AI Tools on Android, Made Easy ║"
echo -e "╚══════════════════════════════════════════╝"
echo ""

# Check if running in Termux
if [ -z "$PREFIX" ]; then
    error "This script must run in Termux!"
    echo "Install Termux from: https://f-droid.org/packages/com.termux/"
    exit 1
fi

# Check architecture
ARCH=$(uname -m)
info "Architecture: $ARCH"
if [ "$ARCH" != "aarch64" ] && [ "$ARCH" != "x86_64" ]; then
    error "Unsupported architecture: $ARCH"
    exit 1
fi

# Install dependencies
info "Checking dependencies..."

install_pkg() {
    if ! command -v $2 &>/dev/null; then
        info "Installing $1..."
        pkg install -y $1
    else
        success "$1 already installed"
    fi
}

install_pkg proot proot
install_pkg proot-distro proot-distro

# Check if Node.js needed (for OpenClaw)
if ! command -v node &>/dev/null; then
    info "Installing Node.js..."
    pkg install -y nodejs
    success "Node.js installed ($(node --version))"
fi

# Check if Python needed (for Aider, etc)
if ! command -v python &>/dev/null; then
    info "Installing Python..."
    pkg install -y python
    success "Python installed ($(python --version))"
fi

# Check if Rust needed
if ! command -v rustc &>/dev/null; then
    info "Installing Rust (optional, for some tools)..."
    pkg install -y rust
    success "Rust installed ($(rustc --version))"
fi

# Setup Arch Linux via proot-distro
info "Setting up Arch Linux environment..."

if ! proot-distro list 2>/dev/null | grep -q archlinux; then
    info "Installing Arch Linux (first time, ~5 min)..."
    proot-distro install archlinux
    success "Arch Linux installed"
else
    success "Arch Linux already installed"
fi

# Install ArchClaw CLI
info "Installing ArchClaw CLI..."

# Create install directory
INSTALL_DIR="$PREFIX/lib/node_modules/archclaw"
mkdir -p "$INSTALL_DIR"

# Copy CLI files (from current directory if building from source)
SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
if [ -f "$SCRIPT_DIR/cli/package.json" ]; then
    cp -r "$SCRIPT_DIR/cli/"* "$INSTALL_DIR/"
else
    # Download from GitHub
    info "Downloading ArchClaw CLI from GitHub..."
    curl -fsSL https://github.com/archclaw/archclaw/archive/main.tar.gz | tar xz -C /tmp/
    cp -r /tmp/archclaw-main/cli/* "$INSTALL_DIR/"
fi

cd "$INSTALL_DIR"
npm install

# Create symlink
if [ -L "$PREFIX/bin/archclaw" ]; then
    rm "$PREFIX/bin/archclaw"
fi
ln -s "$INSTALL_DIR/bin/archclaw.js" "$PREFIX/bin/archclaw"
chmod +x "$PREFIX/bin/archclaw"

success "ArchClaw CLI installed!"

# Setup Arch Linux with AI tools
info "Setting up AI tools environment..."

proot-distro login archlinux -- bash <<'ARCH_SETUP'
#!/bin/bash

# Update pacman
echo "  Updating package manager..."
pacman -Syu --noconfirm --needed base-devel

# Install Node.js if not present
if ! command -v node &>/dev/null; then
    echo "  Installing Node.js..."
    pacman -S --noconfirm --needed nodejs npm
fi

# Install Python if not present
if ! command -v python &>/dev/null; then
    echo "  Installing Python..."
    pacman -S --noconfirm --needed python python-pip
fi

# Install common dependencies
echo "  Installing dependencies..."
pacman -S --noconfirm --needed \
    git \
    curl \
    wget \
    vim \
    sudo \
    openssh \
    htop \
    tmux

# Create archclaw user
if ! id archclaw &>/dev/null; then
    echo "  Creating user..."
    useradd -m -s /bin/bash archclaw
    echo "archclaw:archclaw" | chpasswd
    echo "archclaw ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers
fi

echo "  ✓ Arch Linux environment ready"
ARCH_SETUP

success "Arch Linux environment configured!"

# Create shared directory
SHARED_DIR="/data/data/com.termux/files/home/archclaw-shared"
mkdir -p "$SHARED_DIR"

# Install ArchClaw APK builder (optional)
info "Setting up APK builder..."
cat > "$PREFIX/bin/archclaw-build-apk" <<'BUILD_SCRIPT'
#!/bin/bash
# Quick APK build script
echo "🐉 Building ArchClaw APK..."

cd "$HOME"
if [ ! -d "archclaw" ]; then
    echo "Cloning repository..."
    git clone https://github.com/archclaw/archclaw.git
    cd archclaw
else
    cd archclaw
    git pull
fi

echo "Building..."
./scripts/build-apk.sh

echo ""
echo "✓ APK built at: flutter_app/build/app/outputs/flutter-apk/app-release.apk"
BUILD_SCRIPT

chmod +x "$PREFIX/bin/archclaw-build-apk"
success "APK builder installed (run: archclaw-build-apk)"

# Create quick start guide
echo ""
echo -e "${GREEN}╔══════════════════════════════════════════╗"
echo -e "║       🎉 ArchClaw Installed!            ║"
echo -e "╚══════════════════════════════════════════╝"
echo ""
echo -e "${CYAN}Quick Start:${NC}"
echo ""
echo "  1. Start OpenClaw Gateway:"
echo -e "     ${GREEN}archclaw openclaw start${NC}"
echo ""
echo "  2. Run Claude Code:"
echo -e "     ${GREEN}archclaw claude${NC}"
echo ""
echo "  3. Run Aider:"
echo -e "     ${GREEN}archclaw aider${NC}"
echo ""
echo "  4. List all tools:"
echo -e "     ${GREEN}archclaw tools list${NC}"
echo ""
echo "  5. Configure AI:"
echo -e "     ${GREEN}archclaw config ai${NC}"
echo ""
echo -e "${YELLOW}Need help? Run: archclaw help${NC}"
echo ""
echo -e "${BLUE}🐉 Enjoy AI development on Android!${NC}"
echo ""
