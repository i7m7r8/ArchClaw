import 'package:flutter/foundation.dart';

enum DesktopEnvironment {
  xfce,
  kde,
  lxqt,
  gnome,
  none,
}

enum DesktopStatus {
  stopped,
  starting,
  running,
  stopping,
  error,
}

class DesktopProvider extends ChangeNotifier {
  DesktopStatus _status = DesktopStatus.stopped;
  DesktopEnvironment _environment = DesktopEnvironment.xfce;
  String _logOutput = '';

  DesktopStatus get status => _status;
  DesktopEnvironment get environment => _environment;
  String get logOutput => _logOutput;
  bool get isRunning => _status == DesktopStatus.running;

  Future<void> setEnvironment(DesktopEnvironment env) async {
    if (_status == DesktopStatus.running) {
      throw Exception('Cannot change environment while desktop is running');
    }
    _environment = env;
    notifyListeners();
  }

  Future<void> start() async {
    if (_status == DesktopStatus.running) return;

    _status = DesktopStatus.starting;
    notifyListeners();

    try {
      // TODO: Call native platform channel to start desktop
      // await platformBridge.startDesktop(_environment);
      
      _status = DesktopStatus.running;
      notifyListeners();
    } catch (e) {
      _status = DesktopStatus.error;
      _logOutput += '\nError starting desktop: $e';
      notifyListeners();
    }
  }

  Future<void> stop() async {
    if (_status != DesktopStatus.running) return;

    _status = DesktopStatus.stopping;
    notifyListeners();

    try {
      // TODO: Call native platform channel to stop desktop
      // await platformBridge.stopDesktop();
      
      _status = DesktopStatus.stopped;
      notifyListeners();
    } catch (e) {
      _status = DesktopStatus.error;
      _logOutput += '\nError stopping desktop: $e';
      notifyListeners();
    }
  }

  Future<void> restart() async {
    await stop();
    await start();
  }

  String getEnvironmentName() {
    switch (_environment) {
      case DesktopEnvironment.xfce:
        return 'XFCE';
      case DesktopEnvironment.kde:
        return 'KDE Plasma';
      case DesktopEnvironment.lxqt:
        return 'LXQt';
      case DesktopEnvironment.gnome:
        return 'GNOME';
      case DesktopEnvironment.none:
        return 'None (Terminal Only)';
    }
  }
}
