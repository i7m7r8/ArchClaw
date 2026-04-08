#!/bin/bash
# ArchClaw Build Script for Termux
# Builds the APK directly on Android device

set -e

echo "🐉 ArchClaw Build Script"
echo "========================"

# Colors
BLUE='\033[0;34m'
GREEN='\033[0;32m'
YELLOW='\033[1;33m'
RED='\033[0;31m'
NC='\033[0m' # No Color

print_info() {
    echo -e "${BLUE}[INFO]${NC} $1"
}

print_success() {
    echo -e "${GREEN}[SUCCESS]${NC} $1"
}

print_warning() {
    echo -e "${YELLOW}[WARNING]${NC} $1"
}

print_error() {
    echo -e "${RED}[ERROR]${NC} $1"
}

# Check if running in Termux
if [ ! -d "$PREFIX" ]; then
    print_error "This script must be run in Termux"
    exit 1
fi

# Check dependencies
print_info "Checking dependencies..."

check_command() {
    if ! command -v $1 &> /dev/null; then
        print_error "$1 is not installed"
        print_info "Install with: pkg install $2"
        exit 1
    fi
}

check_command cargo rust
check_command flutter flutter
check_command gradle gradle
check_command llvm llvm

# Install Node.js for CLI tools
if ! command -v node &> /dev/null; then
    print_warning "Node.js not found, installing..."
    pkg install nodejs -y
fi

print_success "All dependencies found"

# Build Rust libraries
print_info "Building Rust libraries..."
cd rust/core
cargo build --release --target aarch64-linux-android
cd ../..
print_success "Rust libraries built"

# Build Flutter APK
print_info "Building Flutter APK..."
cd flutter_app
flutter build apk --release --target-platform android-arm64
cd ..
print_success "Flutter APK built"

# Output location
APK_PATH="flutter_app/build/app/outputs/flutter-apk/app-release.apk"
print_success "APK created at: $APK_PATH"

# Install CLI tools globally
print_info "Installing CLI tools..."
cd cli
npm install -g .
cd ..
print_success "CLI tools installed"

print_success "Build complete! 🎉"
echo ""
echo "Install the APK on your device:"
echo "  adb install $APK_PATH"
echo ""
echo "Or use CLI commands:"
echo "  archclaw setup"
echo "  archclaw start"
echo "  archclaw shell"
