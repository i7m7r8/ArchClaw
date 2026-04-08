#!/bin/bash
# ArchClaw Token Setup - Run this in Termux to share Qwen OAuth with ArchClaw app
# Usage: bash ~/.archclaw-setup-token.sh

set -e

echo "🐉 ArchClaw - Qwen Token Setup"
echo "================================"

# Check if Qwen Code is authenticated
CREDS_FILE="$HOME/.qwen/oauth_creds.json"

if [ ! -f "$CREDS_FILE" ]; then
    echo ""
    echo "❌ No Qwen Code token found."
    echo "Run 'qwen' in Termux first and complete login."
    exit 1
fi

# Check if token is expired
EXPIRY=$(grep -o '"expiry_date":[0-9]*' "$CREDS_FILE" | grep -o '[0-9]*')
NOW=$(date +%s%3N)

if [ "$NOW" -ge "$EXPIRY" ]; then
    echo "⚠️  Token expired. Re-authenticate in Termux: qwen /auth"
    exit 1
fi

# Copy to shared storage for ArchClaw app
SHARED_DIR="/data/data/io.archclaw/files"
mkdir -p "$SHARED_DIR" 2>/dev/null || true

# Try direct copy (works if root or same UID)
if cp "$CREDS_FILE" "$SHARED_DIR/qwen_oauth.json" 2>/dev/null; then
    echo ""
    echo "✅ Token shared with ArchClaw app!"
    echo "Open ArchClaw → it will auto-detect the token."
else
    # Fallback: copy to shared storage
    ALT_DIR="$HOME/storage/shared/ArchClaw"
    mkdir -p "$ALT_DIR"
    cp "$CREDS_FILE" "$ALT_DIR/qwen_oauth.json"
    echo ""
    echo "✅ Token copied to: ~/storage/shared/ArchClaw/qwen_oauth.json"
    echo ""
    echo "In ArchClaw app:"
    echo "1. Tap 'Login with Qwen'"
    echo "2. Select the qwen_oauth.json file"
fi
