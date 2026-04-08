#!/bin/bash
# ArchClaw Arch Linux Bootstrap
# Sets up minimal Arch Linux environment optimized for AI development tools
# Run inside proot-distro: proot-distro login archlinux -- bash /opt/archclaw/bootstrap.sh

set -e

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
NC='\033[0m'

info() { echo -e "${BLUE}[bootstrap]${NC} $1"; }
success() { echo -e "${GREEN}[✓]${NC} $1"; }
warn() { echo -e "${YELLOW}[!]${NC} $1"; }

echo ""
echo "🐉 ArchClaw Bootstrap - Setting up AI tools environment..."
echo ""

# Ensure running as root in proot
if [ "$(id -u)" -ne 0 ]; then
    echo "Error: Must run as root in proot"
    exit 1
fi

# Update system
info "Updating Arch Linux..."
pacman -Syu --noconfirm --needed 2>/dev/null || true
success "System updated"

# Install core dependencies
info "Installing core dependencies..."
pacman -S --noconfirm --needed \
    base-devel \
    git \
    curl \
    wget \
    vim \
    nano \
    sudo \
    openssh \
    htop \
    tmux \
    jq \
    unzip \
    zip \
    tar \
    gzip \
    ca-certificates 2>/dev/null || true
success "Core dependencies installed"

# Install Node.js (for OpenClaw, Claude Code, Codex, etc)
info "Setting up Node.js..."
if ! command -v node &>/dev/null; then
    pacman -S --noconfirm --needed nodejs npm 2>/dev/null || true
fi
success "Node.js $(node --version 2>/dev/null || echo 'installed')"

# Install Python (for Aider, Continue, etc)
info "Setting up Python..."
if ! command -v python &>/dev/null; then
    pacman -S --noconfirm --needed python python-pip python-venv 2>/dev/null || true
fi
success "Python $(python --version 2>/dev/null || echo 'installed')"

# Install Rust (for some AI tools)
info "Setting up Rust (optional)..."
if ! command -v rustc &>/dev/null; then
    pacman -S --noconfirm --needed rust cargo 2>/dev/null || true
fi
if command -v rustc &>/dev/null; then
    success "Rust $(rustc --version)"
else
    warn "Rust installation skipped"
fi

# Create archclaw user
info "Setting up user..."
if ! id archclaw &>/dev/null; then
    useradd -m -s /bin/bash archclaw
    echo "archclaw ALL=(ALL) NOPASSWD:ALL" >> /etc/sudoers
    success "User 'archclaw' created"
else
    success "User 'archclaw' exists"
fi

# Create directories
info "Creating directories..."
mkdir -p /opt/archclaw/scripts
mkdir -p /opt/archclaw/config
mkdir -p /opt/archclaw/data
mkdir -p /home/archclaw/projects
chown -R archclaw:archclaw /home/archclaw/projects
success "Directories created"

# Install AI Tools
info "Installing AI tools..."

# 1. OpenClaw
info "  Installing OpenClaw..."
npm install -g openclaw 2>/dev/null && success "  ✓ OpenClaw" || warn "  ✗ OpenClaw (will install on first use)"

# 2. Claude Code
info "  Installing Claude Code..."
npm install -g @anthropic-ai/claude-code 2>/dev/null && success "  ✓ Claude Code" || warn "  ✗ Claude Code (will install on first use)"

# 3. Codex CLI
info "  Installing Codex CLI..."
npm install -g @openai/codex 2>/dev/null && success "  ✓ Codex CLI" || warn "  ✗ Codex CLI (will install on first use)"

# 4. Aider
info "  Installing Aider..."
pip install --break-system-packages aider-chat 2>/dev/null && success "  ✓ Aider" || warn "  ✗ Aider (will install on first use)"

# 5. Continue
info "  Installing Continue..."
npm install -g continue 2>/dev/null && success "  ✓ Continue" || warn "  ✗ Continue (will install on first use)"

echo ""
success "AI tools installation complete!"

# Create convenience scripts
info "Creating convenience scripts..."

# OpenClaw launcher
cat > /opt/archclaw/scripts/start-openclaw.sh <<'EOF'
#!/bin/bash
PORT=${1:-18789}
echo "🚀 Starting OpenClaw Gateway on port $PORT..."
openclaw start --port $PORT &
sleep 3
echo "✓ Gateway started"
echo "  Dashboard: http://localhost:$PORT"
echo "  Health: http://localhost:$PORT/health"
EOF
chmod +x /opt/archclaw/scripts/start-openclaw.sh

# Claude Code launcher
cat > /opt/archclaw/scripts/start-claude.sh <<'EOF'
#!/bin/bash
echo "🚀 Starting Claude Code..."
if [ -z "$ANTHROPIC_API_KEY" ]; then
    echo "⚠ ANTHROPIC_API_KEY not set!"
    echo "Set it with: export ANTHROPIC_API_KEY='sk-ant-...'"
    exit 1
fi
claude "$@"
EOF
chmod +x /opt/archclaw/scripts/start-claude.sh

# Aider launcher
cat > /opt/archclaw/scripts/start-aider.sh <<'EOF'
#!/bin/bash
echo "🚀 Starting Aider..."
PROVIDER=${1:-anthropic}

case $PROVIDER in
    anthropic)
        [ -z "$ANTHROPIC_API_KEY" ] && { echo "Set ANTHROPIC_API_KEY"; exit 1; }
        aider --anthropic "${@:2}"
        ;;
    openai)
        [ -z "$OPENAI_API_KEY" ] && { echo "Set OPENAI_API_KEY"; exit 1; }
        aider --openai "${@:2}"
        ;;
    gemini)
        [ -z "$GEMINI_API_KEY" ] && { echo "Set GEMINI_API_KEY"; exit 1; }
        aider --gemini "${@:2}"
        ;;
    *)
        echo "Unknown provider: $PROVIDER"
        echo "Use: anthropic, openai, or gemini"
        exit 1
        ;;
esac
EOF
chmod +x /opt/archclaw/scripts/start-aider.sh

success "Convenience scripts created"

# Setup SSH (optional)
info "Setting up SSH (optional)..."
if [ ! -f /etc/ssh/ssh_host_rsa_key ]; then
    ssh-keygen -A
fi
success "SSH ready"

# Configure git
info "Configuring git..."
git config --system --add safe.directory "*" 2>/dev/null || true
success "Git configured"

# Create welcome message
cat > /etc/motd <<'WELCOME'
╔══════════════════════════════════════════════╗
║   🐉 Welcome to ArchClaw AI Environment!     ║
║                                              ║
║   Quick Start:                               ║
║   archclaw openclaw start    # Gateway       ║
║   archclaw claude            # Claude Code   ║
║   archclaw aider             # Aider         ║
║   archclaw status            # Check status  ║
║                                              ║
║   Need help? archclaw help                   ║
╚══════════════════════════════════════════════╝
WELCOME

success "Welcome message created"

echo ""
echo -e "${GREEN}╔══════════════════════════════════════════════╗"
echo -e "║     🎉 ArchClaw Environment Ready!           ║"
echo -e "╚══════════════════════════════════════════════╝"
echo ""
echo "Next steps:"
echo "  1. Exit Arch shell: exit"
echo "  2. Configure AI: archclaw config ai"
echo "  3. Start tool: archclaw claude"
echo ""
