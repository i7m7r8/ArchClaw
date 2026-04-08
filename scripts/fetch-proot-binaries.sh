#!/bin/bash
# Fetch proot, libtalloc, and loader binaries from Termux packages
# Places them in jniLibs/<abi>/lib*.so so Android auto-extracts them
# with execute permission (bypasses W^X restriction).

set -euo pipefail

SCRIPT_DIR="$(cd "$(dirname "$0")" && pwd)"
JNILIBS_DIR="$SCRIPT_DIR/src/main/jniLibs"
TMP_DIR=$(mktemp -d)
trap 'rm -rf "$TMP_DIR"' EXIT

TERMUX_REPO="https://packages-cf.termux.dev/apt/termux-main"

fetch_termux_pkg() {
    local pkg_name="$1"
    local deb_arch="$2"
    local extract_dir="$3"

    echo "  Fetching $pkg_name for $deb_arch..."
    local pkg_url
    pkg_url=$(curl -fsSL "${TERMUX_REPO}/dists/stable/main/binary-${deb_arch}/Packages" \
        | grep -A 20 "^Package: ${pkg_name}\$" \
        | grep "^Filename:" \
        | head -1 \
        | awk '{print $2}')

    if [ -z "$pkg_url" ]; then
        echo "  WARN: $pkg_name not found"
        return 1
    fi

    local deb_file="$TMP_DIR/${pkg_name}-${deb_arch}.deb"
    curl -fsSL "${TERMUX_REPO}/${pkg_url}" -o "$deb_file"
    mkdir -p "$extract_dir"
    cd "$extract_dir"
    ar x "$deb_file"
    if [ -f data.tar.xz ]; then tar xf data.tar.xz
    elif [ -f data.tar.gz ]; then tar xf data.tar.gz
    elif [ -f data.tar.zst ]; then zstd -d data.tar.zst -o data.tar && tar xf data.tar
    fi
    cd "$SCRIPT_DIR"
}

fetch_for_abi() {
    local jni_abi="$1"
    local deb_arch="$2"
    local out_dir="$JNILIBS_DIR/$jni_abi"
    local extract_base="$TMP_DIR/extract-$jni_abi"

    mkdir -p "$out_dir"
    echo "[$jni_abi]"

    local proot_dir="$extract_base/proot"
    if ! fetch_termux_pkg "proot" "$deb_arch" "$proot_dir"; then return 1; fi

    local talloc_dir="$extract_base/talloc"
    if ! fetch_termux_pkg "libtalloc" "$deb_arch" "$talloc_dir"; then return 1; fi

    # Copy proot binary
    local proot_bin
    proot_bin=$(find "$proot_dir" -name "proot" -path "*/bin/*" -type f | head -1)
    if [ -z "$proot_bin" ]; then echo "  ERROR: proot binary not found"; return 1; fi
    cp "$proot_bin" "$out_dir/libproot.so"
    chmod 755 "$out_dir/libproot.so"

    # Copy loader
    local loader
    loader=$(find "$proot_dir" -name "loader" -not -name "loader32" -path "*/proot/*" -type f | head -1)
    if [ -n "$loader" ]; then
        cp "$loader" "$out_dir/libprootloader.so"
        chmod 755 "$out_dir/libprootloader.so"
    fi

    # Copy loader32
    local loader32
    loader32=$(find "$proot_dir" -name "loader32" -path "*/proot/*" -type f | head -1)
    if [ -n "$loader32" ]; then
        cp "$loader32" "$out_dir/libprootloader32.so"
        chmod 755 "$out_dir/libprootloader32.so"
    fi

    # Copy libtalloc
    local talloc_lib
    talloc_lib=$(find "$talloc_dir" -name "libtalloc.so.*" -not -name "*.py" -type f | head -1)
    if [ -z "$talloc_lib" ]; then
        talloc_lib=$(find "$talloc_dir" -name "libtalloc.so" -type f | head -1)
    fi
    if [ -n "$talloc_lib" ]; then
        cp -L "$talloc_lib" "$out_dir/libtalloc.so"
        chmod 755 "$out_dir/libtalloc.so"
    fi

    echo "  OK: $(ls "$out_dir"/ | tr '\n' ' ')"
}

echo "=== Fetching PRoot binaries from Termux packages ==="
for entry in "arm64-v8a:aarch64" "x86_64:x86_64"; do
    IFS=':' read -r abi deb_arch <<< "$entry"
    fetch_for_abi "$abi" "$deb_arch" || echo "  [$abi] FAILED"
done
