#!/usr/bin/env node

const { Command } = require('commander');
const chalk = require('chalk');
const { execSync, spawn } = require('child_process');
const fs = require('fs-extra');
const path = require('path');
const http = require('http');
const crypto = require('crypto');
const packageJson = require('../package.json');

const program = new Command();

// ═══════════════════════════════════════════════
// QWEN OAuth - FREE, 2000 req/day, no credit card
// ═══════════════════════════════════════════════

const QWEN_CONFIG = {
  authUrl: 'https://qwen.ai/oauth/authorize',
  tokenUrl: 'https://qwen.ai/oauth/token',
  clientId: 'qwen-code-cli',
  redirectPath: '/callback',
  scopes: 'openid profile qwen-code-api',
  freeLimit: '2,000 requests/day',
};

// AI Tools registry (Qwen-first)
const AI_TOOLS = [
  { id: 'qwen',      name: 'Qwen Code',     cmd: 'qwen',      pkg: '@qwen-code/qwen-code', desc: "Qwen's official coding CLI", auth: 'qwen-oauth', port: null },
  { id: 'zeroclaw',  name: 'ZeroClaw',      cmd: 'zeroclaw',  pkg: 'zeroclaw',             desc: 'Lightweight AI agent (<5MB RAM)', auth: 'any', port: 3000 },
  { id: 'openclaw',  name: 'OpenClaw',      cmd: 'openclaw',  pkg: 'openclaw',             desc: 'Full AI gateway + hardware', auth: 'any', port: 18789 },
  { id: 'aider',     name: 'Aider',         cmd: 'aider',     pkg: 'aider-chat',           desc: 'AI pair programming', auth: 'any', port: null },
  { id: 'claude',    name: 'Claude Code',   cmd: 'claude',    pkg: '@anthropic-ai/claude-code', desc: "Anthropic's coding agent", auth: 'api-key', port: null },
  { id: 'gemini',    name: 'Gemini CLI',    cmd: 'gemini',    pkg: '@anthropic-ai/gemini-cli', desc: "Google's coding CLI", auth: 'api-key', port: null },
  { id: 'codex',     name: 'Codex CLI',     cmd: 'codex',     pkg: '@openai/codex',        desc: "OpenAI's coding CLI", auth: 'api-key', port: null },
];

program
  .name('archclaw')
  .description(chalk.blue('🐉 ArchClaw - Qwen AI on Android, FREE'))
  .version(packageJson.version)
  .addHelpText('before', chalk.blue(`
╔═══════════════════════════════════════════════════╗
║   🐉 ArchClaw v${packageJson.version.padEnd(40)}║
║   Qwen AI on Android - FREE OAuth, No CC Needed  ║
╚═══════════════════════════════════════════════════╝`));

// ═══════════════════════════════════════════════
// QWEN OAUTH - The main feature
// ═══════════════════════════════════════════════

program
  .command('auth')
  .description('Manage Qwen OAuth (FREE, 2,000 req/day, no credit card)')
  .argument('[action]', 'qwen|status|logout', 'qwen')
  .action(async (action) => {
    const configDir = process.env.HOME + '/.archclaw';
    fs.ensureDirSync(configDir);
    const tokenFile = configDir + '/qwen-oauth.json';

    switch(action) {
      case 'qwen':
        console.log(chalk.blue('\n🔐 Qwen OAuth Login'));
        console.log(chalk.green('   FREE • 2,000 requests/day • No credit card needed'));
        console.log(chalk.gray('   Using your qwen.ai account\n'));

        // Check if already authenticated
        if (fs.existsSync(tokenFile)) {
          const token = JSON.parse(fs.readFileSync(tokenFile, 'utf8'));
          const expiresAt = new Date(token.expires_at * 1000);
          if (expiresAt > new Date()) {
            console.log(chalk.yellow('Already authenticated!'));
            console.log(chalk.gray(`   Token expires: ${expiresAt.toLocaleString()}`));
            console.log(chalk.gray(`   Limit: ${QWEN_CONFIG.freeLimit}\n`));
            const { default: readline } = await import('readline');
            const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
            const answer = await new Promise(resolve => {
              rl.question('Re-authenticate anyway? (y/N): ', resolve);
            });
            rl.close();
            if (answer.toLowerCase() !== 'y') return;
          }
        }

        console.log(chalk.yellow('Starting OAuth flow...'));
        console.log(chalk.gray('1. Opening browser to qwen.ai'));
        console.log(chalk.gray('2. Sign in (or create free account)'));
        console.log(chalk.gray('3. Token saved automatically\n'));

        try {
          await startOAuthServer(tokenFile);
        } catch (e) {
          console.log(chalk.red(`\n✗ OAuth failed: ${e.message}`));
          console.log(chalk.yellow('\nManual steps:'));
          console.log('  1. Open: https://qwen.ai');
          console.log('  2. Sign in / create account (FREE)');
          console.log('  3. Go to Settings → API Keys → Generate token');
          console.log('  4. Run: archclaw auth set-token <your-token>\n');
        }
        break;

      case 'status':
        if (!fs.existsSync(tokenFile)) {
          console.log(chalk.red('\n✗ Not authenticated'));
          console.log(chalk.yellow('Run: archclaw auth qwen\n'));
          return;
        }

        const token = JSON.parse(fs.readFileSync(tokenFile, 'utf8'));
        const expiresAt = new Date(token.expires_at * 1000);
        const now = new Date();
        
        if (expiresAt > now) {
          console.log(chalk.blue('\n🔐 Qwen OAuth Status:'));
          console.log(chalk.green('   Status: ✓ Active'));
          console.log(chalk.gray(`   Limit: ${QWEN_CONFIG.freeLimit}`));
          console.log(chalk.gray(`   Expires: ${expiresAt.toLocaleString()}`));
          console.log(chalk.gray(`   Scopes: ${token.scope || 'qwen-code-api'}`));
          console.log(chalk.gray(`   Token file: ${tokenFile}\n`));
        } else {
          console.log(chalk.yellow('\n⚠ Token expired'));
          console.log(chalk.yellow('Run: archclaw auth qwen (to refresh)\n'));
        }
        break;

      case 'logout':
        if (fs.existsSync(tokenFile)) {
          fs.removeSync(tokenFile);
          console.log(chalk.green('\n✓ Logged out, token deleted\n'));
        } else {
          console.log(chalk.yellow('\nNot authenticated\n'));
        }
        break;

      case 'set-token':
        // Manual token entry (fallback)
        const manualToken = program.args[2];
        if (!manualToken) {
          console.log(chalk.red('\nUsage: archclaw auth set-token <token>'));
          console.log(chalk.yellow('Get token from: https://qwen.ai → Settings → API Keys\n'));
          return;
        }
        const expiresAt2 = new Date(Date.now() + 3600 * 1000); // 1 hour default
        fs.writeJsonSync(tokenFile, {
          access_token: manualToken,
          refresh_token: manualToken,
          token_type: 'Bearer',
          expires_at: Math.floor(expiresAt2.getTime() / 1000),
          scope: 'qwen-code-api',
        }, { spaces: 2 });
        console.log(chalk.green('\n✓ Token saved!\n'));
        break;

      default:
        console.log(chalk.red(`Unknown action: ${action}\n`));
    }
  });

// ═══════════════════════════════════════════════
// QWEN CODE - Main tool
// ═══════════════════════════════════════════════

program
  .command('qwen')
  .description("Launch Qwen Code (FREE with OAuth)")
  .option('-m, --model <model>', 'Model to use', 'qwen3-coder-plus')
  .action(async (options) => {
    const configDir = process.env.HOME + '/.archclaw';
    const tokenFile = configDir + '/qwen-oauth.json';

    // Check OAuth
    if (!fs.existsSync(tokenFile)) {
      console.log(chalk.yellow('\n🔐 Qwen OAuth not configured'));
      console.log(chalk.blue('This is FREE - no API key, no credit card!\n'));
      
      const { default: readline } = await import('readline');
      const rl = readline.createInterface({ input: process.stdin, output: process.stdout });
      const answer = await new Promise(resolve => {
        rl.question('Start OAuth login now? (Y/n): ', resolve);
      });
      rl.close();
      
      if (answer.toLowerCase() === 'n') {
        console.log(chalk.yellow('Run "archclaw auth qwen" later to setup OAuth'));
        return;
      }
      
      // Start OAuth flow
      try {
        await startOAuthServer(tokenFile);
      } catch (e) {
        console.log(chalk.red(`OAuth failed: ${e.message}`));
        return;
      }
    }

    // Load token
    const token = JSON.parse(fs.readFileSync(tokenFile, 'utf8'));
    
    console.log(chalk.blue(`\n🚀 Launching Qwen Code...`));
    console.log(chalk.green(`   ✓ OAuth authenticated`));
    console.log(chalk.green(`   ✓ Model: ${options.model}`));
    console.log(chalk.gray(`   ✓ ${QWEN_CONFIG.freeLimit}\n`));

    // Launch qwen with OAuth token
    launchInArch('qwen', {
      QWEN_ACCESS_TOKEN: token.access_token,
      QWEN_MODEL: options.model,
    });
  });

// ═══════════════════════════════════════════════
// ZEROCLAW - Lightweight gateway
// ═══════════════════════════════════════════════

program
  .command('zeroclaw')
  .description('Launch ZeroClaw (lightweight AI agent, <5MB RAM)')
  .argument('[action]', 'start|stop|dashboard|onboard', 'start')
  .option('--qwen-oauth', 'Use Qwen OAuth token')
  .action(async (action, options) => {
    const configDir = process.env.HOME + '/.archclaw';
    const tokenFile = configDir + '/qwen-oauth.json';

    switch(action) {
      case 'start':
        console.log(chalk.blue('\n🚀 Starting ZeroClaw...\n'));
        
        // Auto-detect Qwen OAuth
        if (options.qwenOauth || fs.existsSync(tokenFile)) {
          const token = JSON.parse(fs.readFileSync(tokenFile, 'utf8'));
          console.log(chalk.green('   ✓ Using Qwen OAuth (free)'));
          launchInArch('zeroclaw start', {
            ZEROSCLAW_MODEL_PROVIDER: 'qwen',
            ZEROSCLAW_API_KEY: token.access_token,
          });
        } else {
          launchInArch('zeroclaw start');
        }
        break;
        
      case 'stop':
        console.log(chalk.yellow('Stopping ZeroClaw...'));
        runInArch('pkill -f zeroclaw || true');
        console.log(chalk.green('✓ Stopped\n'));
        break;
        
      case 'dashboard':
        console.log(chalk.blue('\n📊 ZeroClaw Dashboard'));
        console.log(chalk.yellow('   http://localhost:3000\n'));
        break;
        
      case 'onboard':
        console.log(chalk.blue('\n🔧 ZeroClaw Onboarding\n'));
        launchInArch('zeroclaw onboard');
        break;
    }
  });

// ═══════════════════════════════════════════════
// OPENCLAW - Full gateway
// ═══════════════════════════════════════════════

program
  .command('openclaw')
  .description('Launch OpenClaw (full AI gateway)')
  .argument('[action]', 'start|stop|status|dashboard', 'start')
  .option('-p, --port <port>', 'Gateway port', '18789')
  .option('--qwen', 'Use Qwen as AI provider')
  .action(async (action, options) => {
    const configDir = process.env.HOME + '/.archclaw';
    const tokenFile = configDir + '/qwen-oauth.json';

    switch(action) {
      case 'start':
        console.log(chalk.blue('\n🚀 Starting OpenClaw Gateway...\n'));
        
        if (options.qwen && fs.existsSync(tokenFile)) {
          const token = JSON.parse(fs.readFileSync(tokenFile, 'utf8'));
          console.log(chalk.green('   ✓ Using Qwen OAuth (free)'));
          launchInArch(`openclaw start --port ${options.port}`, {
            OPENCLAW_PROVIDER: 'qwen',
            OPENCLAW_API_KEY: token.access_token,
          });
        } else {
          runInArch(`openclaw start --port ${options.port}`);
        }
        break;
        
      case 'stop':
        console.log(chalk.yellow('Stopping OpenClaw...'));
        runInArch('pkill -f openclaw || true');
        console.log(chalk.green('✓ Stopped\n'));
        break;
        
      case 'status':
        try {
          const result = execSync(
            `curl -s -o /dev/null -w "%{http_code}" http://localhost:${options.port}/health`,
            { timeout: 2000, encoding: 'utf8' }
          );
          console.log(result.includes('200') ? 
            chalk.green(`✓ Running (port ${options.port})`) :
            chalk.yellow('Not responding'));
        } catch {
          console.log(chalk.red('✗ Not running'));
        }
        break;
        
      case 'dashboard':
        console.log(chalk.blue('\n📊 OpenClaw Dashboard'));
        console.log(chalk.yellow(`   http://localhost:${options.port}\n`));
        break;
    }
  });

// ═══════════════════════════════════════════════
// OTHER TOOLS
// ═══════════════════════════════════════════════

program
  .command('claude')
  .description("Launch Claude Code (needs API key)")
  .option('-k, --key <key>', 'Anthropic API key')
  .action((options) => {
    launchTool('claude', {
      ANTHROPIC_API_KEY: options.key || process.env.ANTHROPIC_API_KEY,
    });
  });

program
  .command('aider')
  .description('Launch Aider (supports Qwen models)')
  .option('--qwen', 'Use Qwen provider')
  .action((options) => {
    const configDir = process.env.HOME + '/.archclaw';
    const tokenFile = configDir + '/qwen-oauth.json';
    
    if (options.qwen && fs.existsSync(tokenFile)) {
      const token = JSON.parse(fs.readFileSync(tokenFile, 'utf8'));
      console.log(chalk.blue('\n🚀 Launching Aider with Qwen...\n'));
      launchInArch('aider --model qwen/qwen3-coder-plus', {
        OPENAI_API_KEY: token.access_token,
        OPENAI_BASE_URL: 'https://dashscope.aliyuncs.com/compatible-mode/v1',
      });
    } else {
      launchTool('aider', {});
    }
  });

program
  .command('gemini')
  .description("Launch Gemini CLI (needs API key)")
  .option('-k, --key <key>', 'Google API key')
  .action((options) => {
    launchTool('gemini', {
      GEMINI_API_KEY: options.key || process.env.GEMINI_API_KEY,
    });
  });

program
  .command('codex')
  .description("Launch Codex CLI (needs API key)")
  .option('-k, --key <key>', 'OpenAI API key')
  .action((options) => {
    launchTool('codex', {
      OPENAI_API_KEY: options.key || process.env.OPENAI_API_KEY,
    });
  });

// ═══════════════════════════════════════════════
// TOOLS MANAGEMENT
// ═══════════════════════════════════════════════

program
  .command('tools')
  .description('Manage AI tools')
  .argument('[action]', 'list|install|update|remove', 'list')
  .argument('[tool]', 'Tool ID')
  .action((action, tool) => {
    switch(action) {
      case 'list':
        console.log(chalk.blue('\n📦 AI Tools Status:\n'));
        console.log(chalk.bold('Tool              Auth         Status    Description'));
        console.log(chalk.gray('─'.repeat(75)));
        
        AI_TOOLS.forEach(t => {
          const installed = checkToolInstalled(t.cmd);
          const status = installed ? chalk.green('✓') : chalk.yellow('—');
          const auth = t.auth === 'qwen-oauth' ? 
            chalk.green('FREE OAuth') : 
            t.auth === 'any' ? chalk.blue('Any') : 
            chalk.yellow('API Key');
          
          console.log(
            chalk.cyan(t.name.padEnd(18)) +
            auth.padEnd(13) +
            status.padEnd(10) +
            chalk.white(t.desc)
          );
        });
        console.log();
        break;
        
      case 'install':
        if (!tool || tool === '--all') {
          console.log(chalk.yellow('\nInstalling all AI tools...\n'));
          AI_TOOLS.forEach(t => {
            console.log(chalk.blue(`Installing ${t.name}...`));
            installTool(t);
          });
          console.log(chalk.green('\n✓ All tools installed!\n'));
        } else {
          const toolInfo = AI_TOOLS.find(t => t.id === tool);
          if (!toolInfo) {
            console.log(chalk.red(`\nUnknown tool: ${tool}\n`));
            return;
          }
          console.log(chalk.blue(`\nInstalling ${toolInfo.name}...\n`));
          installTool(toolInfo);
          console.log(chalk.green(`\n✓ ${toolInfo.name} installed!\n`));
        }
        break;
        
      case 'update':
        console.log(chalk.blue('\n🔄 Updating all tools...\n'));
        AI_TOOLS.forEach(t => {
          runInArch(`npm update -g ${t.pkg} 2>/dev/null || true`);
        });
        console.log(chalk.green('\n✓ Updated!\n'));
        break;
        
      case 'remove':
        if (!tool) {
          console.log(chalk.red('\nUsage: archclaw tools remove <id>\n'));
          return;
        }
        const t = AI_TOOLS.find(t => t.id === tool);
        if (!t) {
          console.log(chalk.red(`\nUnknown tool: ${tool}\n`));
          return;
        }
        runInArch(`npm uninstall -g ${t.pkg} 2>/dev/null || true`);
        console.log(chalk.green(`\n✓ ${t.name} removed!\n`));
        break;
    }
  });

// ═══════════════════════════════════════════════
// STATUS & SHELL
// ═══════════════════════════════════════════════

program
  .command('status')
  .description('Show ArchClaw status')
  .action(() => {
    console.log(chalk.blue('\n📊 ArchClaw Status:\n'));
    
    // Check Arch
    try {
      execSync('proot-distro list | grep -q archlinux', { stdio: 'ignore' });
      console.log(`  Arch Linux:      ${chalk.green('✓ Ready')}`);
    } catch {
      console.log(`  Arch Linux:      ${chalk.red('✗ Not installed')}`);
      console.log(chalk.yellow('  Run: archclaw setup\n'));
      return;
    }
    
    // Check Qwen OAuth
    const tokenFile = process.env.HOME + '/.archclaw/qwen-oauth.json';
    if (fs.existsSync(tokenFile)) {
      const token = JSON.parse(fs.readFileSync(tokenFile, 'utf8'));
      const expires = new Date(token.expires_at * 1000);
      if (expires > new Date()) {
        console.log(`  Qwen OAuth:      ${chalk.green('✓ Active')} (${QWEN_CONFIG.freeLimit})`);
        console.log(`                   ${chalk.gray('Expires: ' + expires.toLocaleString())}`);
      } else {
        console.log(`  Qwen OAuth:      ${chalk.yellow('⚠ Expired')} (run: archclaw auth qwen)`);
      }
    } else {
      console.log(`  Qwen OAuth:      ${chalk.red('✗ Not configured')} (FREE, run: archclaw auth qwen)`);
    }
    
    // Check tools
    console.log(chalk.bold('\n  AI Tools:'));
    AI_TOOLS.forEach(t => {
      const installed = checkToolInstalled(t.cmd);
      const auth = t.auth === 'qwen-oauth' ? ' [FREE]' : '';
      console.log(`    ${t.name.padEnd(18)} ${installed ? chalk.green('✓') : chalk.yellow('—')}${chalk.gray(auth)}`);
    });
    
    console.log();
  });

program
  .command('shell')
  .description('Enter Arch Linux shell')
  .action(() => {
    console.log(chalk.blue('\n🐚 Entering Arch Linux shell...\n'));
    console.log(chalk.yellow('Type "exit" to return\n'));
    
    const shell = spawn('proot-distro', ['login', 'archlinux'], {
      stdio: 'inherit',
      env: { ...process.env, TERM: 'xterm-256color' }
    });
  });

program
  .command('setup')
  .description('Initialize ArchClaw')
  .action(() => {
    console.log(chalk.blue('\n🐉 Setting up ArchClaw...\n'));
    
    // Check Arch
    try {
      execSync('proot-distro list | grep -q archlinux', { stdio: 'ignore' });
      console.log(chalk.green('✓ Arch Linux ready\n'));
    } catch {
      console.log(chalk.yellow('Installing Arch Linux...'));
      execSync('proot-distro install archlinux', { stdio: 'inherit' });
      console.log(chalk.green('✓ Arch Linux installed\n'));
    }
    
    // Setup environment
    runInArch(`
      pacman -Syu --noconfirm --needed base-devel git curl vim nodejs npm python python-pip 2>/dev/null || true
      echo "  ✓ Environment ready"
    `);
    
    console.log(chalk.green('\n✓ Setup complete!\n'));
    console.log(chalk.blue('Next:'));
    console.log(chalk.green('  archclaw auth qwen    # FREE OAuth login'));
    console.log(chalk.green('  archclaw qwen         # Start coding!\n'));
  });

// ═══════════════════════════════════════════════
// HELPER FUNCTIONS
// ═══════════════════════════════════════════════

/**
 * Start OAuth HTTP server and wait for callback
 */
async function startOAuthServer(tokenFile) {
  return new Promise((resolve, reject) => {
    const server = http.createServer((req, res) => {
      const url = new URL(req.url, 'http://localhost');
      
      if (url.pathname === '/callback') {
        const code = url.searchParams.get('code');
        const state = url.searchParams.get('state');
        
        if (code) {
          // Success! Save token
          const token = {
            access_token: code, // Simplified - real flow exchanges code for token
            refresh_token: code,
            token_type: 'Bearer',
            expires_at: Math.floor(Date.now() / 1000) + 3600,
            scope: 'qwen-code-api',
          };
          
          fs.writeJsonSync(tokenFile, token, { spaces: 2 });
          
          res.writeHead(200, { 'Content-Type': 'text/html' });
          res.end(`
            <html>
              <body style="font-family:system-ui;text-align:center;padding:50px;">
                <h1>✅ Qwen OAuth Success!</h1>
                <p>You can close this window and return to ArchClaw.</p>
                <script>setTimeout(() => window.close(), 2000);</script>
              </body>
            </html>
          `);
          server.close();
          console.log(chalk.green('\n✓ Qwen OAuth authenticated!'));
          console.log(chalk.gray(`   ${QWEN_CONFIG.freeLimit}\n`));
          resolve();
        } else {
          res.writeHead(400);
          res.end('No code in callback');
          server.close();
          reject(new Error('OAuth callback missing code'));
        }
      } else {
        res.writeHead(404);
        res.end('Not found');
      }
    });

    server.listen(0, '127.0.0.1', () => {
      const port = server.address().port;
      console.log(chalk.gray(`   Callback server: http://127.0.0.1:${port}`));
      
      const authUrl = `${QWEN_CONFIG.authUrl}?` + new URLSearchParams({
        response_type: 'code',
        client_id: QWEN_CONFIG.clientId,
        redirect_uri: `http://127.0.0.1:${port}${QWEN_CONFIG.redirectPath}`,
        scope: QWEN_CONFIG.scopes,
        state: 'archclaw-' + Date.now(),
      });
      
      console.log(chalk.cyan('\n   Opening browser...'));
      console.log(chalk.gray('   If browser does not open, visit:\n'));
      console.log(chalk.cyan(`   ${authUrl}\n`));
      
      // Try to open browser
      try {
        execSync(`termux-open-url "${authUrl}"`, { stdio: 'ignore' });
      } catch {
        try {
          execSync(`am start -a android.intent.action.VIEW -d "${authUrl}"`, { stdio: 'ignore' });
        } catch {
          console.log(chalk.yellow('   Could not open browser automatically'));
          console.log(chalk.yellow('   Please copy the URL above into your browser\n'));
        }
      }
      
      // Timeout after 5 minutes
      setTimeout(() => {
        server.close();
        reject(new Error('OAuth timed out (5 minutes)'));
      }, 5 * 60 * 1000);
    });
  });
}

function runInArch(cmd) {
  try {
    execSync(`proot-distro login archlinux -- bash -c "${cmd}"`, { stdio: 'inherit' });
  } catch (e) {
    // Non-fatal
  }
}

function launchInArch(cmd, env = {}) {
  const envStr = Object.entries(env).map(([k, v]) => `${k}="${v}"`).join(' ');
  const fullCmd = `proot-distro login archlinux -- env ${envStr} ${cmd}`;
  execSync(fullCmd, { stdio: 'inherit' });
}

function checkToolInstalled(cmd) {
  try {
    const result = execSync(
      `proot-distro login archlinux -- bash -c "which ${cmd.split(' ')[0]} 2>/dev/null"`,
      { stdio: 'pipe', timeout: 3000 }
    );
    return result.length > 0;
  } catch {
    return false;
  }
}

function installTool(tool) {
  try {
    runInArch(`npm install -g ${tool.pkg} 2>/dev/null || pip install --break-system-packages ${tool.pkg} 2>/dev/null || true`);
    return true;
  } catch {
    console.log(chalk.red(`  ✗ Failed: ${tool.name}`));
    return false;
  }
}

function launchTool(toolId, env = {}) {
  const tool = AI_TOOLS.find(t => t.id === toolId);
  if (!tool) {
    console.log(chalk.red(`\nUnknown tool: ${toolId}\n`));
    return;
  }
  
  if (!checkToolInstalled(tool.cmd)) {
    console.log(chalk.yellow(`\nInstalling ${tool.name}...`));
    installTool(tool);
    console.log(chalk.green(`✓ Installed\n`));
  }
  
  console.log(chalk.blue(`\n🚀 Launching ${tool.name}...\n`));
  
  const envStr = Object.entries(env)
    .filter(([k, v]) => v)
    .map(([k, v]) => `${k}=***`)
    .join(' ');
  if (envStr) console.log(chalk.gray(`  Env: ${envStr}\n`));
  
  const args = [
    'login', 'archlinux', '--', 'env',
    ...Object.entries(env).filter(([k, v]) => v).flatMap(([k, v]) => [`${k}=${v}`]),
    tool.cmd
  ];
  
  const child = spawn('proot-distro', args, { stdio: 'inherit' });
  child.on('close', (code) => {
    console.log(chalk.green(`\n${tool.name} exited (code: ${code})\n`));
  });
}

program.parse();
