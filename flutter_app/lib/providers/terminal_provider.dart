import 'package:flutter/foundation.dart';

class TerminalProvider extends ChangeNotifier {
  List<TerminalSession> _sessions = [];
  int _activeSessionIndex = 0;

  List<TerminalSession> get sessions => _sessions;
  int get activeSessionIndex => _activeSessionIndex;
  TerminalSession? get activeSession => 
      _sessions.isNotEmpty ? _sessions[_activeSessionIndex] : null;

  Future<void> createSession({
    String? name,
    Map<String, String>? environment,
  }) async {
    final session = TerminalSession(
      id: _sessions.length,
      name: name ?? 'Session ${_sessions.length + 1}',
      environment: environment,
    );
    
    // TODO: Initialize PTY and connect to terminal widget
    _sessions.add(session);
    _activeSessionIndex = _sessions.length - 1;
    notifyListeners();
  }

  void closeSession(int index) {
    if (index >= 0 && index < _sessions.length) {
      _sessions.removeAt(index);
      if (_activeSessionIndex >= _sessions.length) {
        _activeSessionIndex = _sessions.length - 1;
      }
      notifyListeners();
    }
  }

  void setActiveSession(int index) {
    if (index >= 0 && index < _sessions.length) {
      _activeSessionIndex = index;
      notifyListeners();
    }
  }

  void sendInput(String input) {
    activeSession?._addOutput(input);
    notifyListeners();
  }
}

class TerminalSession {
  final int id;
  final String name;
  final Map<String, String>? environment;
  final StringBuffer _output = StringBuffer();
  final Function(String)? _onOutput;

  TerminalSession({
    required this.id,
    required this.name,
    this.environment,
    Function(String)? onOutput,
  }) : _onOutput = onOutput;

  String get output => _output.toString();

  void _addOutput(String text) {
    _output.write(text);
    _onOutput?.call(text);
  }

  void clear() {
    _output.clear();
  }
}
