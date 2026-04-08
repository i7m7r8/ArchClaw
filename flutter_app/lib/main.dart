import 'package:flutter/material.dart';
import 'package:flutter/services.dart';

void main() {
  runApp(const ArchClawApp());
}

class ArchClawApp extends StatelessWidget {
  const ArchClawApp({super.key});

  @override
  Widget build(BuildContext context) {
    return MaterialApp(
      title: 'ArchClaw',
      debugShowCheckedModeBanner: false,
      theme: ThemeData(
        useMaterial3: true,
        colorScheme: ColorScheme.fromSeed(
          seedColor: const Color(0xFF6366F1), // Qwen purple
          brightness: Brightness.dark,
        ),
        cardTheme: CardTheme(
          elevation: 0,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(16),
            side: BorderSide(color: Colors.white.withOpacity(0.1)),
          ),
        ),
      ),
      home: const HomeScreen(),
    );
  }
}

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  bool _qwenAuthActive = false;
  bool _isQwenLoggedIn = false;
  String _qwenStatus = 'Not authenticated';
  
  final List<AITool> _qwenTools = [
    AITool(
      id: 'qwen',
      name: 'Qwen Code',
      description: "Qwen's official coding CLI",
      icon: Icons.code,
      color: const Color(0xFF6366F1), // Qwen purple
      auth: 'FREE OAuth',
      isFree: true,
    ),
    AITool(
      id: 'zeroclaw',
      name: 'ZeroClaw',
      description: 'Lightweight AI agent (<5MB RAM)',
      icon: Icons.bolt,
      color: Colors.green,
      auth: 'Uses Qwen OAuth',
      isFree: true,
    ),
    AITool(
      id: 'openclaw',
      name: 'OpenClaw',
      description: 'Full AI gateway + hardware',
      icon: Icons.dns,
      color: Colors.blue,
      port: 18789,
      auth: 'Uses Qwen OAuth',
      isFree: true,
    ),
    AITool(
      id: 'aider',
      name: 'Aider',
      description: 'AI pair programming (Qwen models)',
      icon: Icons.people,
      color: Colors.purple,
      auth: 'Uses Qwen OAuth',
      isFree: true,
    ),
  ];

  final List<AITool> _otherTools = [
    AITool(
      id: 'claude',
      name: 'Claude Code',
      description: "Anthropic's coding agent",
      icon: Icons.smart_toy,
      color: const Color(0xFFD97706),
      auth: 'Needs API key',
      isFree: false,
    ),
    AITool(
      id: 'gemini',
      name: 'Gemini CLI',
      description: "Google's coding CLI",
      icon: Icons.auto_awesome,
      color: Colors.cyan,
      auth: 'Needs API key',
      isFree: false,
    ),
    AITool(
      id: 'codex',
      name: 'Codex CLI',
      description: "OpenAI's coding CLI",
      icon: Icons.terminal,
      color: const Color(0xFF10B981),
      auth: 'Needs API key',
      isFree: false,
    ),
  ];

  @override
  Widget build(BuildContext context) {
    return Scaffold(
      body: SafeArea(
        child: ListView(
          padding: const EdgeInsets.all(16),
          children: [
            // Header
            Row(
              children: [
                const Text('🐉', style: TextStyle(fontSize: 32)),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(
                        'ArchClaw',
                        style: Theme.of(context).textTheme.headlineSmall?.copyWith(
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      Text(
                        'Qwen AI on Android - FREE',
                        style: TextStyle(
                          color: Colors.grey[400],
                          fontSize: 14,
                        ),
                      ),
                    ],
                  ),
                ),
                IconButton(
                  icon: const Icon(Icons.settings),
                  onPressed: () => _showSettings(),
                ),
              ],
            ),
            
            const SizedBox(height: 24),
            
            // Qwen OAuth Card (PRIMARY ACTION)
            _buildQwenAuthCard(),
            
            const SizedBox(height: 24),
            
            // Qwen Tools Header
            Row(
              children: [
                const Text(
                  'Qwen Tools',
                  style: TextStyle(
                    fontSize: 20,
                    fontWeight: FontWeight.bold,
                  ),
                ),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
                  decoration: BoxDecoration(
                    color: Colors.green.withOpacity(0.2),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Text(
                    'FREE',
                    style: TextStyle(color: Colors.green, fontSize: 11, fontWeight: FontWeight.bold),
                  ),
                ),
              ],
            ),
            
            const SizedBox(height: 12),
            
            // Qwen Tools Grid
            GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                crossAxisSpacing: 12,
                mainAxisSpacing: 12,
                childAspectRatio: 1.2,
              ),
              itemCount: _qwenTools.length,
              itemBuilder: (context, index) {
                return _buildToolCard(_qwenTools[index], canUse: _isQwenLoggedIn);
              },
            ),
            
            const SizedBox(height: 24),
            
            // Other Tools
            Text(
              'Other Tools (API key needed)',
              style: Theme.of(context).textTheme.titleLarge?.copyWith(
                fontWeight: FontWeight.bold,
              ),
            ),
            
            const SizedBox(height: 12),
            
            GridView.builder(
              shrinkWrap: true,
              physics: const NeverScrollableScrollPhysics(),
              gridDelegate: const SliverGridDelegateWithFixedCrossAxisCount(
                crossAxisCount: 2,
                crossAxisSpacing: 12,
                mainAxisSpacing: 12,
                childAspectRatio: 1.2,
              ),
              itemCount: _otherTools.length,
              itemBuilder: (context, index) {
                return _buildToolCard(_otherTools[index], canUse: true);
              },
            ),
            
            const SizedBox(height: 24),
            
            // Terminal Button
            SizedBox(
              width: double.infinity,
              child: OutlinedButton.icon(
                onPressed: _openTerminal,
                icon: const Icon(Icons.terminal),
                label: const Text('Open Terminal'),
                style: OutlinedButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 16),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildQwenAuthCard() {
    return Card(
      child: Padding(
        padding: const EdgeInsets.all(20),
        child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(
              children: [
                Container(
                  padding: const EdgeInsets.all(12),
                  decoration: BoxDecoration(
                    color: const Color(0xFF6366F1).withOpacity(0.2),
                    borderRadius: BorderRadius.circular(12),
                  ),
                  child: const Icon(
                    Icons.login,
                    color: Color(0xFF6366F1),
                    size: 28,
                  ),
                ),
                const SizedBox(width: 16),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'Qwen OAuth',
                        style: TextStyle(
                          fontSize: 18,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                      Text(
                        _qwenStatus,
                        style: TextStyle(
                          color: _isQwenLoggedIn ? Colors.green : Colors.grey[400],
                          fontSize: 13,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
            
            const SizedBox(height: 16),
            
            // Benefits
            Row(
              children: [
                _buildBenefitChip('✓ FREE'),
                _buildBenefitChip('✓ 2,000 req/day'),
                _buildBenefitChip('✓ No credit card'),
              ],
            ),
            
            const SizedBox(height: 16),
            
            // Login Button
            SizedBox(
              width: double.infinity,
              child: FilledButton.icon(
                onPressed: _qwenAuthActive ? null : _startQwenOAuth,
                icon: _qwenAuthActive
                    ? const SizedBox(
                        width: 16,
                        height: 16,
                        child: CircularProgressIndicator(strokeWidth: 2),
                      )
                    : const Icon(Icons.login),
                label: Text(_qwenAuthActive ? 'Waiting for login...' : 'Login with Qwen'),
                style: FilledButton.styleFrom(
                  padding: const EdgeInsets.symmetric(vertical: 14),
                  backgroundColor: const Color(0xFF6366F1),
                ),
              ),
            ),
          ],
        ),
      ),
    );
  }

  Widget _buildBenefitChip(String text) {
    return Container(
      margin: const EdgeInsets.only(right: 8),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 6),
      decoration: BoxDecoration(
        color: Colors.green.withOpacity(0.15),
        borderRadius: BorderRadius.circular(20),
      ),
      child: Text(
        text,
        style: const TextStyle(
          color: Colors.green,
          fontSize: 11,
          fontWeight: FontWeight.bold,
        ),
      ),
    );
  }

  Widget _buildToolCard(AITool tool, {bool canUse = true}) {
    return Card(
      clipBehavior: Clip.antiAlias,
      child: InkWell(
        onTap: canUse ? () => _launchTool(tool) : null,
        child: Padding(
          padding: const EdgeInsets.all(12),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Container(
                padding: const EdgeInsets.all(10),
                decoration: BoxDecoration(
                  color: tool.color.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(
                  tool.icon,
                  color: tool.color,
                  size: 28,
                ),
              ),
              const Spacer(),
              Row(
                children: [
                  Expanded(
                    child: Text(
                      tool.name,
                      style: const TextStyle(
                        fontWeight: FontWeight.bold,
                        fontSize: 14,
                      ),
                      maxLines: 1,
                      overflow: TextOverflow.ellipsis,
                    ),
                  ),
                  if (tool.isFree)
                    Container(
                      padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                      decoration: BoxDecoration(
                        color: Colors.green.withOpacity(0.2),
                        borderRadius: BorderRadius.circular(8),
                      ),
                      child: const Text(
                        'FREE',
                        style: TextStyle(
                          color: Colors.green,
                          fontSize: 9,
                          fontWeight: FontWeight.bold,
                        ),
                      ),
                    ),
                ],
              ),
              const SizedBox(height: 2),
              Text(
                tool.auth,
                style: TextStyle(
                  color: tool.isFree ? Colors.green : Colors.grey[400],
                  fontSize: 10,
                ),
              ),
            ],
          ),
        ),
      ),
    );
  }

  void _startQwenOAuth() async {
    setState(() => _qwenAuthActive = true);
    
    // TODO: Call native platform channel to start OAuth
    // This will:
    // 1. Start local HTTP server
    // 2. Open browser to qwen.ai/oauth
    // 3. Wait for callback with token
    // 4. Save encrypted token
    // 5. Return success
    
    // Simulate for now
    await Future.delayed(const Duration(seconds: 2));
    
    setState(() {
      _qwenAuthActive = false;
      _isQwenLoggedIn = true;
      _qwenStatus = 'Active • 2,000 requests/day';
    });
    
    if (mounted) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(
          content: Text('✓ Qwen OAuth authenticated! All Qwen tools unlocked.'),
          backgroundColor: Colors.green,
          duration: Duration(seconds: 3),
        ),
      );
    }
  }

  void _launchTool(AITool tool) {
    if (!tool.isFree && !_isQwenLoggedIn) {
      ScaffoldMessenger.of(context).showSnackBar(
        const SnackBar(content: Text('Login with Qwen OAuth first for free access')),
      );
      return;
    }
    
    showModalBottomSheet(
      context: context,
      builder: (context) => _ToolLaunchSheet(tool: tool),
    );
  }

  void _openTerminal() {
    // TODO: Open terminal with Arch Linux shell
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Opening terminal...')),
    );
  }

  void _showSettings() {
    ScaffoldMessenger.of(context).showSnackBar(
      const SnackBar(content: Text('Settings coming soon')),
    );
  }
}

class AITool {
  final String id;
  final String name;
  final String description;
  final IconData icon;
  final Color color;
  final int? port;
  final String auth;
  final bool isFree;

  const AITool({
    required this.id,
    required this.name,
    required this.description,
    required this.icon,
    required this.color,
    this.port,
    required this.auth,
    required this.isFree,
  });
}

class _ToolLaunchSheet extends StatelessWidget {
  final AITool tool;
  
  const _ToolLaunchSheet({required this.tool});

  @override
  Widget build(BuildContext context) {
    return Container(
      padding: const EdgeInsets.all(24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        crossAxisAlignment: CrossAxisAlignment.start,
        children: [
          Row(
            children: [
              Container(
                padding: const EdgeInsets.all(12),
                decoration: BoxDecoration(
                  color: tool.color.withOpacity(0.2),
                  borderRadius: BorderRadius.circular(12),
                ),
                child: Icon(tool.icon, color: tool.color, size: 32),
              ),
              const SizedBox(width: 16),
              Expanded(
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    Text(
                      tool.name,
                      style: const TextStyle(
                        fontSize: 20,
                        fontWeight: FontWeight.bold,
                      ),
                    ),
                    Text(
                      tool.description,
                      style: TextStyle(color: Colors.grey[400]),
                    ),
                  ],
                ),
              ),
            ],
          ),
          const SizedBox(height: 24),
          Container(
            padding: const EdgeInsets.all(12),
            decoration: BoxDecoration(
              color: tool.isFree ? Colors.green.withOpacity(0.1) : Colors.yellow.withOpacity(0.1),
              borderRadius: BorderRadius.circular(12),
            ),
            child: Row(
              children: [
                Icon(
                  tool.isFree ? Icons.check_circle : Icons.warning,
                  color: tool.isFree ? Colors.green : Colors.yellow,
                  size: 20,
                ),
                const SizedBox(width: 12),
                Expanded(
                  child: Text(
                    tool.isFree 
                        ? 'FREE with Qwen OAuth (2,000 requests/day, no credit card)'
                        : 'Requires API key from ${tool.name.split(' ')[0]}',
                    style: TextStyle(
                      color: tool.isFree ? Colors.green : Colors.yellow,
                    ),
                  ),
                ),
              ],
            ),
          ),
          if (tool.port != null) ...[
            const SizedBox(height: 12),
            Text(
              'Dashboard: http://localhost:${tool.port}',
              style: TextStyle(color: Colors.grey[400], fontFamily: 'monospace'),
            ),
          ],
          const SizedBox(height: 24),
          Row(
            children: [
              Expanded(
                child: FilledButton.icon(
                  onPressed: () {
                    Navigator.pop(context);
                    ScaffoldMessenger.of(context).showSnackBar(
                      SnackBar(content: Text('Starting ${tool.name}...')),
                    );
                  },
                  icon: const Icon(Icons.play_arrow),
                  label: const Text('Launch'),
                  style: FilledButton.styleFrom(
                    backgroundColor: tool.color,
                  ),
                ),
              ),
              const SizedBox(width: 12),
              OutlinedButton(
                onPressed: () => Navigator.pop(context),
                child: const Text('Cancel'),
              ),
            ],
          ),
        ],
      ),
    );
  }
}
