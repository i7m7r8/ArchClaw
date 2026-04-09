import 'dart:io';
import '../models/optional_package.dart';
import 'native_bridge.dart';

/// Manages SSH server via a native foreground service.
/// sshd runs in a persistent proot process (not single-shot runInProot).
class SshService {
  static String? _rootfsDir;

  static Future<String> _getRootfsDir() async {
    if (_rootfsDir != null) return _rootfsDir!;
    final filesDir = await NativeBridge.getFilesDir();
    _rootfsDir = '$filesDir/rootfs/archlinux';
    return _rootfsDir!;
  }

  /// Check if OpenSSH is installed.
  static Future<bool> isInstalled() async {
    final rootfs = await _getRootfsDir();
    return File('$rootfs/${OptionalPackage.sshPackage.checkPath}').existsSync();
  }

  /// Check if sshd foreground service is running.
  static Future<bool> isSshdRunning() async {
    try {
      return await NativeBridge.isSshdRunning();
    } catch (_) {
      return false;
    }
  }

  /// Start sshd in a persistent proot process via foreground service.
  static Future<void> startSshd({int port = 8022}) async {
    await NativeBridge.startSshd(port: port);
  }

  /// Stop the sshd foreground service.
  static Future<void> stopSshd() async {
    await NativeBridge.stopSshd();
  }

  /// Set the root password inside proot.
  static Future<void> setPassword(String password) async {
    await NativeBridge.setRootPassword(password);
  }

  /// Get device IP addresses from Android NetworkInterface (not proot).
  static Future<List<String>> getIpAddresses() async {
    try {
      return await NativeBridge.getDeviceIps();
    } catch (_) {
      return [];
    }
  }

  /// Get the port sshd is running on.
  static Future<int> getPort() async {
    try {
      return await NativeBridge.getSshdPort();
    } catch (_) {
      return 8022;
    }
  }
}
