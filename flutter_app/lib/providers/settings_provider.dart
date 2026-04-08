import 'package:flutter/foundation.dart';
import 'package:shared_preferences/shared_preferences.dart';

class SettingsProvider extends ChangeNotifier {
  bool _isSetupComplete = false;
  String? _aiProvider;
  String? _apiKeyEncrypted;
  bool _enableBatteryOptimizationWarning = true;
  bool _autoStartGateway = false;
  bool _autoStartDesktop = false;
  int _targetFps = 60;
  int _maxRamMB = 500;

  bool get isSetupComplete => _isSetupComplete;
  String? get aiProvider => _aiProvider;
  bool get hasApiKey => _apiKeyEncrypted != null && _apiKeyEncrypted!.isNotEmpty;
  bool get enableBatteryOptimizationWarning => _enableBatteryOptimizationWarning;
  bool get autoStartGateway => _autoStartGateway;
  bool get autoStartDesktop => _autoStartDesktop;
  int get targetFps => _targetFps;
  int get maxRamMB => _maxRamMB;

  SettingsProvider() {
    _loadSettings();
  }

  Future<void> _loadSettings() async {
    try {
      final prefs = await SharedPreferences.getInstance();
      _isSetupComplete = prefs.getBool('setup_complete') ?? false;
      _aiProvider = prefs.getString('ai_provider');
      _apiKeyEncrypted = prefs.getString('api_key_encrypted');
      _enableBatteryOptimizationWarning = 
          prefs.getBool('battery_warning') ?? true;
      _autoStartGateway = prefs.getBool('auto_start_gateway') ?? false;
      _autoStartDesktop = prefs.getBool('auto_start_desktop') ?? false;
      _targetFPS = prefs.getInt('target_fps') ?? 60;
      _maxRamMB = prefs.getInt('max_ram_mb') ?? 500;
      notifyListeners();
    } catch (e) {
      debugPrint('Error loading settings: $e');
    }
  }

  Future<void> completeSetup() async {
    _isSetupComplete = true;
    await _saveBool('setup_complete', true);
    notifyListeners();
  }

  Future<void> setAIProvider(String provider) async {
    _aiProvider = provider;
    await _saveString('ai_provider', provider);
    notifyListeners();
  }

  Future<void> setAPIKey(String apiKey) async {
    // TODO: Encrypt API key before storing
    _apiKeyEncrypted = apiKey; // Placeholder - implement encryption
    await _saveString('api_key_encrypted', apiKey);
    notifyListeners();
  }

  Future<void> setBatteryOptimizationWarning(bool enabled) async {
    _enableBatteryOptimizationWarning = enabled;
    await _saveBool('battery_warning', enabled);
    notifyListeners();
  }

  Future<void> setAutoStartGateway(bool enabled) async {
    _autoStartGateway = enabled;
    await _saveBool('auto_start_gateway', enabled);
    notifyListeners();
  }

  Future<void> setAutoStartDesktop(bool enabled) async {
    _autoStartDesktop = enabled;
    await _saveBool('auto_start_desktop', enabled);
    notifyListeners();
  }

  Future<void> setTargetFPS(int fps) async {
    _targetFps = fps;
    await _saveInt('target_fps', fps);
    notifyListeners();
  }

  Future<void> setMaxRamMB(int mb) async {
    _maxRamMB = mb;
    await _saveInt('max_ram_mb', mb);
    notifyListeners();
  }

  Future<void> _saveBool(String key, bool value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setBool(key, value);
  }

  Future<void> _saveString(String key, String value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setString(key, value);
  }

  Future<void> _saveInt(String key, int value) async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.setInt(key, value);
  }

  Future<void> resetAll() async {
    final prefs = await SharedPreferences.getInstance();
    await prefs.clear();
    _isSetupComplete = false;
    _aiProvider = null;
    _apiKeyEncrypted = null;
    _enableBatteryOptimizationWarning = true;
    _autoStartGateway = false;
    _autoStartDesktop = false;
    _targetFps = 60;
    _maxRamMB = 500;
    notifyListeners();
  }
}
