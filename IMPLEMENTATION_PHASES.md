# 🏗️ ArchClaw Implementation Phases

> **Current state: All files created, all logic scaffolded. Nothing actually runs yet.**

---

## 📊 Reality Check

| Component | Files | Status | Works? |
|-----------|-------|--------|--------|
| CLI (archclaw.js) | 696 lines | ✅ Scaffolded | ❌ Not tested |
| Rust OAuth | 433 lines | ✅ Scaffolded | ❌ Needs real endpoints |
| Flutter UI | 614 lines | ✅ Scaffolded | ❌ No platform channels |
| Install script | 224 lines | ✅ Written | ❌ Not tested |
| Bootstrap script | 221 lines | ✅ Written | ❌ Not tested |
| Documentation | 5 files | ✅ Complete | ✅ Done |

**Total code**: ~2,200 lines
**Working code**: ~0 lines
**Next step**: Actually make it run

---

## Phase 1: Make CLI Work (1-2 days)

### What's needed
```bash
# Goal: This should actually work
archclaw auth qwen
archclaw qwen
archclaw status
```

### Tasks

#### 1.1 Fix Qwen OAuth endpoints
**File**: `cli/bin/archclaw.js` (lines ~300-400)

**Current**: Assumed endpoints that may not be real
```javascript
// These need verification:
authUrl: 'https://qwen.ai/oauth/authorize',
tokenUrl: 'https://qwen.ai/oauth/token',
```

**What to do**:
- [ ] Test if `qwen.ai/oauth/authorize` exists
- [ ] Find actual OAuth endpoint from Qwen Code source
- [ ] Verify client_id (`qwen-code-cli`)
- [ ] Test callback flow manually

**How to test**:
```bash
# Install Qwen Code first
npm i -g @qwen-code/qwen-code

# Run it and see what OAuth URL it uses
qwen
> /auth
# Watch what URL opens in browser

# Or check source code:
cat $(which qwen) | grep -i oauth
```

#### 1.2 Fix OAuth server callback
**File**: `cli/bin/archclaw.js` - `startOAuthServer()`

**Current**: Simplified - just saves code as token
**What to do**:
- [ ] Implement actual code→token exchange
- [ ] Handle token refresh
- [ ] Add proper error handling
- [ ] Test with real qwen.ai account

#### 1.3 Fix `proot-distro` integration
**File**: `cli/bin/archclaw.js` - `runInArch()`, `launchInArch()`

**Current**: Assumes Arch Linux is installed
**What to do**:
- [ ] Test `proot-distro login archlinux` on actual device
- [ ] Handle "Arch not installed" gracefully
- [ ] Add auto-install prompt
- [ ] Test command execution works

#### 1.4 Add error handling everywhere
**Current**: Basic try/catch
**What to do**:
- [ ] Meaningful error messages
- [ ] Recovery suggestions
- [ ] Network timeout handling

**Time**: 1-2 days (just testing + fixing endpoints)

---

## Phase 2: Make Qwen OAuth Actually Work (2-3 days)

### What's needed
```
User runs: archclaw auth qwen
→ Browser opens to REAL qwen.ai OAuth
→ User signs in
→ Token saved
→ archclaw qwen WORKS
```

### Tasks

#### 2.1 Discover real OAuth flow
```bash
# Method 1: Check Qwen Code source
git clone https://github.com/QwenLM/qwen-code.git
grep -r "oauth\|auth\|login" qwen-code/src/

# Method 2: Network sniff
# Run qwen, do /auth, watch network traffic

# Method 3: Check settings.json format
cat ~/.qwen/settings.json  # After running qwen /auth
```

#### 2.2 Implement correct flow
Based on what we find, update:
- [ ] OAuth URLs
- [ ] Client ID
- [ ] Scopes needed
- [ ] Token exchange format
- [ ] Token storage location

#### 2.3 Handle Termux browser opening
**Current**: Uses `termux-open-url` or `am start`
**What to do**:
- [ ] Test `termux-open-url` actually works
- [ ] Test `am start` fallback
- [ ] Handle "no browser" case
- [ ] Test callback redirect works on Android

#### 2.4 Test end-to-end
```bash
# Full flow test:
archclaw auth qwen
# → Browser opens
# → Sign in to qwen.ai
# → Returns to terminal
# → Token saved

archclaw auth status
# → Shows active, 2000/day remaining

archclaw qwen
# → Qwen Code launches
# → Can make requests
# → No "unauthorized" errors
```

**Time**: 2-3 days (depends on OAuth discovery)

---

## Phase 3: Make Tools Install & Launch (1-2 days)

### What's needed
```bash
archclaw tools install --all
# Actually installs everything

archclaw qwen
# Actually launches Qwen Code
```

### Tasks

#### 3.1 Fix npm package names
**Current**: May have wrong package names
```javascript
{ id: 'qwen',     pkg: '@qwen-code/qwen-code' },    // Verify
{ id: 'zeroclaw', pkg: 'zeroclaw' },                // Verify
{ id: 'openclaw', pkg: 'openclaw' },                // Verify
```

**What to do**:
- [ ] Verify each package exists on npm
- [ ] Test install command works
- [ ] Verify command after install

#### 3.2 Fix `checkToolInstalled()`
**Current**: May give false positives/negatives
```javascript
function checkToolInstalled(cmd) {
  // This is slow and might hang
  execSync(`proot-distro login archlinux -- bash -c "which ${cmd}"`)
}
```

**What to do**:
- [ ] Faster check (just `which` in current shell)
- [ ] Add timeout
- [ ] Cache results

#### 3.3 Fix `launchTool()`
**Current**: Spawns process but may not connect stdio properly
**What to do**:
- [ ] Test interactive mode works
- [ ] Test Ctrl+C handling
- [ ] Test process cleanup on exit

**Time**: 1-2 days

---

## Phase 4: Make Install Script Work (1 day)

### What's needed
```bash
curl -fsSL https://archclaw.dev/install.sh | bash
# Should just work from zero
```

### Tasks

#### 4.1 Test dependencies
```bash
# Script needs:
pkg install proot proot-distro nodejs

# Does it detect missing deps?
# Does it install them correctly?
```

#### 4.2 Test Arch Linux install
```bash
proot-distro install archlinux
# How long does this take?
# Does it succeed on first try?
# How much storage does it use?
```

#### 4.3 Fix any script bugs
- [ ] Test on fresh Termux install
- [ ] Handle interruptions gracefully
- [ ] Add progress indicators

**Time**: 1 day

---

## Phase 5: Flutter APK Build (3-5 days)

### What's needed
```
Install APK → Tap "Login with Qwen" → WebView → Done
```

### Tasks

#### 5.1 Set up Android project properly
**Current**: Minimal AndroidManifest + MainActivity
**What to do**:
- [ ] Create full Flutter project structure
- [ ] Add all dependencies in pubspec.yaml
- [ ] Configure Android build.gradle

#### 5.2 Implement platform channel
**Current**: Just a stub
```dart
// Need to implement:
const platform = MethodChannel('io.archclaw/native');

// Methods to implement:
await platform.invokeMethod('startOAuth');
await platform.invokeMethod('launchTool', {'tool': 'qwen'});
```

#### 5.3 Implement Kotlin native bridge
**File**: `android/app/src/main/kotlin/io/archclaw/MainActivity.kt`
**What to do**:
- [ ] Handle `startOAuth` → opens WebView
- [ ] Handle `launchTool` → calls Rust/CLI
- [ ] Handle `checkStatus` → returns tool states
- [ ] Foreground service for background tools

#### 5.4 Build actual APK
```bash
flutter build apk --release
# Does it compile?
# Does it install?
# Does it launch?
```

**Time**: 3-5 days (Flutter + Android is complex)

---

## Phase 6: Test Everything (2-3 days)

### What's needed
```bash
# Full test on actual Android device
```

### Tasks

#### 6.1 Test on real device
- [ ] Fresh Termux install
- [ ] Run install script
- [ ] Test each tool
- [ ] Test OAuth flow
- [ ] Test error cases

#### 6.2 Fix device-specific issues
- Android version differences
- Storage permissions
- Battery optimization killing processes
- Network issues

#### 6.3 Performance testing
- [ ] How much storage does Arch Linux use?
- [ ] How much RAM does Qwen Code use?
- [ ] How fast does OAuth work?
- [ ] How fast do tools launch?

**Time**: 2-3 days

---

## ⚡ Fastest Path to "It Works"

If you want to use this ASAP, here's the order:

### Day 1: Get Qwen Code working in Termux manually
```bash
# Just verify the goal is achievable
pkg install nodejs proot proot-distro
proot-distro install archlinux
proot-distro login archlinux
pacman -Syu --noconfirm base-devel nodejs npm
npm i -g @qwen-code/qwen-code
qwen
> /auth
# Sign in → Verify it works
```

### Day 2: Make CLI wrapper work
```bash
# Fix archclaw.js to actually:
# 1. Launch Qwen Code
# 2. Handle OAuth
# 3. Show status
cd archclaw/cli
node bin/archclaw.js auth qwen
node bin/archclaw.js qwen
```

### Day 3: Make install script work
```bash
# Test from zero
./scripts/install.sh
# Should end with working archclaw command
```

### Days 4-7: Polish, APK, docs
- Fix bugs
- Build APK
- Write docs

---

## 🎯 What to Start With RIGHT NOW

```bash
# Step 1: Verify Qwen Code works on your device
pkg install nodejs
npm i -g @qwen-code/qwen-code
qwen
# Try to use it. If it works → we know the foundation is solid

# Step 2: If it works, fix ArchClaw CLI to match
# Step 3: If it doesn't work, fix the foundation first
```

---

## ❓ Questions to Answer First

1. **Does `@qwen-code/qwen-code` work on your device?**
   - If yes → we build on top of it
   - If no → we need to fix that first

2. **What's the actual OAuth URL?**
   - Run `qwen` → `/auth` → watch what URL opens
   - Update `QWEN_CONFIG` with real URLs

3. **How does Qwen Code store OAuth token?**
   - Check `~/.qwen/settings.json` after login
   - Match that format in our implementation

---

**Want me to start implementing any of these phases now?**
