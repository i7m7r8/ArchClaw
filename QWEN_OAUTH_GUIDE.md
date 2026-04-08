# 🔐 Qwen OAuth Guide - FREE, No Credit Card

> **Qwen OAuth gives you 2,000 coding requests/day for FREE. Just sign in with your qwen.ai account. No API key. No credit card. No payment.**

---

## 💰 What is Qwen OAuth?

Qwen OAuth is a **free authentication method** for Qwen Code and related tools:

| Feature | Details |
|---------|---------|
| **Cost** | **FREE** |
| **Daily Limit** | **2,000 requests/day** |
| **Credit Card** | **NOT required** |
| **Account** | qwen.ai (free to create) |
| **Models** | qwen3-coder-plus, qwen-max, qwen-plus |

**This is NOT DashScope API keys.** DashScope costs money. Qwen OAuth is completely free.

---

## 🚀 Quick Setup

### In Termux (with ArchClaw)
```bash
# One command
archclaw auth qwen

# What happens:
# 1. Browser opens to qwen.ai
# 2. You sign in (or create free account)
# 3. OAuth token saved automatically
# 4. Done! Start coding
```

### In APK
```
1. Open ArchClaw app
2. Tap "Login with Qwen"
3. WebView opens → sign in
4. Done! All Qwen tools unlocked
```

---

## 📖 How Qwen OAuth Works

### The Flow
```
1. You run: archclaw auth qwen
          or tap "Login with Qwen" in APK

2. ArchClaw starts a local server on your device
   (listens on 127.0.0.1:RANDOM_PORT)

3. Browser opens: https://qwen.ai/oauth/authorize
   ?client_id=qwen-code-cli
   &redirect_uri=http://127.0.0.1:PORT/callback
   &scope=openid+profile+qwen-code-api

4. You sign in at qwen.ai
   (or create account - takes 30 seconds)

5. qwen.ai redirects to: http://127.0.0.1:PORT/callback?code=ABC123

6. ArchClaw extracts the code
   Exchanges it for an OAuth token

7. Token encrypted → saved to:
   ~/.archclaw/qwen-oauth.json

8. All Qwen tools use this token automatically
```

### Token Details
```json
{
  "access_token": "eyJhbGc...",
  "refresh_token": "eyJhbGc...",
  "token_type": "Bearer",
  "expires_at": 1712345678,
  "scope": "openid profile qwen-code-api"
}
```

**Auto-refresh**: Token refreshes before expiration. You stay logged in.

---

## 🎯 Using Qwen Code (After OAuth)

```bash
# Just run it
archclaw qwen

# That's it. OAuth token used automatically.

# Inside Qwen Code:
# > Write a REST API in Rust
# > Add error handling to all functions
# > Create tests for the auth module
# > /auth               # Check OAuth status
# > /help               # See all commands
```

**No API key configuration needed.** OAuth handles everything.

---

## 🤖 All Tools with Qwen OAuth

### 1. Qwen Code (Official CLI)
```bash
archclaw qwen
# ✓ Uses Qwen OAuth automatically
# ✓ Model: qwen3-coder-plus (default)
# ✓ 2,000 requests/day free
```

### 2. ZeroClaw (Lightweight Gateway)
```bash
# Uses Qwen OAuth token
archclaw zeroclaw start --qwen-oauth

# Or auto-detects saved token
archclaw zeroclaw start
```

### 3. OpenClaw (Full Gateway)
```bash
# Configure Qwen as provider
archclaw openclaw start --qwen

# Dashboard at localhost:18789
archclaw openclaw dashboard
```

### 4. Aider (Pair Programming)
```bash
# Aider with Qwen models via OAuth
archclaw aider --qwen

# Behind the scenes:
# OPENAI_API_KEY=<oauth-token>
# OPENAI_BASE_URL=https://dashscope.aliyuncs.com/compatible-mode/v1
```

---

## 🔍 OAuth Status & Management

```bash
# Check status
archclaw auth status

# Output:
# 🔐 Qwen OAuth Status:
#    Status: ✓ Active
#    Limit: 2,000 requests/day
#    Expires: 2026-04-09 14:30:00
#    Scopes: qwen-code-api
#    Token file: ~/.archclaw/qwen-oauth.json

# Logout (delete token)
archclaw auth logout

# Re-authenticate
archclaw auth qwen
```

---

## 🆚 Qwen OAuth vs DashScope API Key

| Feature | Qwen OAuth | DashScope API Key |
|---------|-----------|-------------------|
| **Cost** | **FREE** | Paid (pay per token) |
| **Credit Card** | **Not needed** | Required |
| **Daily Limit** | 2,000 requests | Depends on plan |
| **Setup** | Sign in at qwen.ai | Create account, add payment, generate key |
| **Best for** | Individual devs, students | Teams, production, high volume |
| **Models** | qwen3-coder-plus, qwen-max | All Qwen models |
| **CI/CD** | ❌ Needs browser | ✅ Works in headless |

**For personal coding on Android → Qwen OAuth is perfect.**

---

## 🚨 Troubleshooting

### OAuth won't open browser
```bash
# Manual steps:
# 1. Open browser manually to: https://qwen.ai
# 2. Sign in / create account
# 3. Go to: Settings → API Keys → Generate token
# 4. Copy the token
# 5. Save manually:

mkdir -p ~/.archclaw
cat > ~/.archclaw/qwen-oauth.json <<EOF
{
  "access_token": "PASTE_TOKEN_HERE",
  "refresh_token": "PASTE_TOKEN_HERE",
  "token_type": "Bearer",
  "expires_at": $(($(date +%s) + 3600)),
  "scope": "qwen-code-api"
}
EOF

# Now Qwen tools will work
```

### Token expired
```bash
# Just re-authenticate
archclaw auth qwen
# Same flow, takes 10 seconds
```

### "Not authenticated" error
```bash
# Check if token file exists
ls -la ~/.archclaw/qwen-oauth.json

# If missing, login again
archclaw auth qwen
```

### "401 Unauthorized" from Qwen API
```bash
# Token might be invalid
# Check status
archclaw auth status

# If expired or invalid:
archclaw auth qwen
```

---

## 📱 Mobile-Specific Notes

### Termux on Android
- Browser opens via `termux-open-url` or `am start`
- Callback server runs on localhost
- Works on any Android 10+ device
- No root needed

### ArchClaw APK
- Uses WebView instead of external browser
- OAuth happens inside the app
- More seamless experience
- Token saved to app's encrypted storage

---

## 🔐 Security

### Token Storage
- Saved in `~/.archclaw/qwen-oauth.json`
- Should be encrypted (TODO: implement AES-GCM)
- Only accessible to your user
- Deleted on logout

### Token Scope
- `openid` - Basic identity
- `profile` - Account info
- `qwen-code-api` - Access to Qwen Code API

### What OAuth Does NOT Access
- ❌ Your qwen.ai payment info (even if you have any)
- ❌ Other Alibaba Cloud services
- ❌ Personal data beyond profile
- ❌ Any other applications

---

## 💡 Tips

### Maximize Your 2,000 Requests/Day
1. **Be specific** in prompts - fewer iterations needed
2. **Use context** - point to specific files, not entire codebase
3. **Batch requests** - ask for multiple changes at once
4. **Review before accepting** - avoid wasting requests on wrong outputs

### Switch Between OAuth and API Key
```bash
# Use OAuth (default)
archclaw qwen

# Use API key instead
export DASHSCOPE_API_KEY="sk-..."
archclaw qwen

# OAuth takes priority if both configured
```

### Multiple Devices
```bash
# Just sign in on each device
# Each device gets its own 2,000 requests/day
# No limit on number of devices
```

---

## 📚 Resources

- **qwen.ai**: https://qwen.ai (create free account)
- **Qwen Code**: https://github.com/QwenLM/qwen-code
- **Qwen Blog**: https://qwen.ai/blog
- **Models**: qwen3-coder-plus, qwen-max, qwen-plus, qwen-turbo

---

## 🎓 Understanding the Free Tier

**2,000 requests/day** means:
- Each message you send to Qwen = 1 request
- Each code generation = 1 request
- Each file edit suggestion = 1 request
- Resets at midnight (UTC)

**That's enough for:**
- ~8 hours of active coding
- ~200-400 code changes
- ~50-100 file reviews

**For most developers, this is more than enough for daily work.**

---

**Qwen OAuth = Free coding AI on Android. Just sign in and code.** 🐉
