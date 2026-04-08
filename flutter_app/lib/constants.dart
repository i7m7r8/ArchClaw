class AppConstants {
  static const String appName = 'ArchClaw';
  static const String version = '1.8.7';
  static const String packageName = 'io.archclaw';

  /// Matches ANSI escape sequences (e.g. color codes in terminal output).
  static final ansiEscape = RegExp(r'\x1b\[[0-9;]*[a-zA-Z]');

  static const String authorName = 'Mithun Gowda B';
  static const String authorEmail = 'mithungowda.b7411@gmail.com';
  static const String githubUrl = 'https://github.com/mithun50/openclaw-termux';
  static const String license = 'MIT';

  static const String githubApiLatestRelease =
      'https://api.github.com/repos/mithun50/openclaw-termux/releases/latest';

  // NextGenX
  static const String orgName = 'NextGenX';
  static const String orgEmail = 'nxgextra@gmail.com';
  static const String instagramUrl = 'https://www.instagram.com/nexgenxplorer_nxg';
  static const String youtubeUrl = 'https://youtube.com/@nexgenxplorer?si=UG-wBC8UIyeT4bbw';
  static const String playStoreUrl = 'https://play.google.com/store/apps/dev?id=8262374975871504599';

  static const String gatewayHost = '127.0.0.1';
  static const int gatewayPort = 18789;
  static const String gatewayUrl = 'http://$gatewayHost:$gatewayPort';

  static const String archlinuxRootfsUrl =
      'https://archlinuxarm.org/os/';
  static const String rootfsArm64 = '${archlinuxRootfsUrl}ArchLinuxARM-aarch64-latest.tar.gz';
  static const String archlinuxRootfsArmhf = '${archlinuxRootfsUrl}ArchLinuxARM-armv7-latest.tar.gz';
  static const String archlinuxRootfsAmd64 = '${archlinuxRootfsUrl}ArchLinuxARM-x86_64-latest.tar.gz';

  // Node.js binary tarball — downloaded directly by Flutter, extracted by Java.
  // Bypasses curl/gpg/NodeSource which fail inside proot.
  static const String nodeVersion = '22.14.0';
  static const String nodeBaseUrl =
      'https://nodejs.org/dist/v$nodeVersion/node-v$nodeVersion-linux-';

  static String getNodeTarballUrl(String arch) {
    switch (arch) {
      case 'aarch64':
        return '${nodeBaseUrl}arm64.tar.xz';
      case 'arm':
        return '${nodeBaseUrl}armv7l.tar.xz';
      case 'x86_64':
        return '${nodeBaseUrl}x64.tar.xz';
      default:
        return '${nodeBaseUrl}arm64.tar.xz';
    }
  }

  static const int healthCheckIntervalMs = 5000;
  static const int maxAutoRestarts = 5;

  // Node constants
  static const int wsReconnectBaseMs = 350;
  static const double wsReconnectMultiplier = 1.7;
  static const int wsReconnectCapMs = 8000;
  static const String nodeRole = 'node';
  static const int pairingTimeoutMs = 300000;

  static const String channelName = 'io.archclaw/native';
  static const String eventChannelName = 'io.archclaw/gateway_logs';

  static String getRootfsUrl(String arch) {
    switch (arch) {
      case 'aarch64':
        return rootfsArm64;
      case 'arm':
        return archlinuxRootfsArmhf;
      case 'x86_64':
        return archlinuxRootfsAmd64;
      default:
        return rootfsArm64;
    }
  }
}
