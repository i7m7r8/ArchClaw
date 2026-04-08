import 'package:flutter/foundation.dart';

enum GatewayStatus {
  stopped,
  starting,
  running,
  stopping,
  error,
}

class GatewayProvider extends ChangeNotifier {
  GatewayStatus _status = GatewayStatus.stopped;
  String _logOutput = '';
  int _port = 18789;
  DateTime? _startTime;

  GatewayStatus get status => _status;
  String get logOutput => _logOutput;
  int get port => _port;
  DateTime? get startTime => _startTime;
  bool get isRunning => _status == GatewayStatus.running;

  Future<void> start() async {
    if (_status == GatewayStatus.running) return;

    _status = GatewayStatus.starting;
    notifyListeners();

    try {
      // TODO: Call native platform channel to start gateway
      // await platformBridge.startGateway();
      
      _status = GatewayStatus.running;
      _startTime = DateTime.now();
      notifyListeners();
    } catch (e) {
      _status = GatewayStatus.error;
      _logOutput += '\nError starting gateway: $e';
      notifyListeners();
    }
  }

  Future<void> stop() async {
    if (_status != GatewayStatus.running) return;

    _status = GatewayStatus.stopping;
    notifyListeners();

    try {
      // TODO: Call native platform channel to stop gateway
      // await platformBridge.stopGateway();
      
      _status = GatewayStatus.stopped;
      _startTime = null;
      notifyListeners();
    } catch (e) {
      _status = GatewayStatus.error;
      _logOutput += '\nError stopping gateway: $e';
      notifyListeners();
    }
  }

  Future<void> restart() async {
    await stop();
    await start();
  }

  void appendLog(String line) {
    _logOutput += '\n$line';
    notifyListeners();
  }

  void clearLog() {
    _logOutput = '';
    notifyListeners();
  }

  String get uptime {
    if (_startTime == null) return 'Not running';
    final diff = DateTime.now().difference(_startTime!);
    final hours = diff.inHours.toString().padLeft(2, '0');
    final minutes = diff.inMinutes.remainder(60).toString().padLeft(2, '0');
    final seconds = diff.inSeconds.remainder(60).toString().padLeft(2, '0');
    return '$hours:$minutes:$seconds';
  }
}
