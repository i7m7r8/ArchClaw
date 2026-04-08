import 'dart:io';
import 'dart:ui' as ui;
import 'package:flutter/material.dart';
import 'package:flutter/rendering.dart';
import 'package:path_provider/path_provider.dart';

/// Captures a widget wrapped in [RepaintBoundary] as a PNG image.
class ScreenshotService {
  /// Capture the widget behind [key] and save as PNG.
  /// Returns the saved file path, or null on failure.
  static Future<String?> capture(
    GlobalKey key, {
    String prefix = 'terminal',
    double pixelRatio = 3.0,
  }) async {
    try {
      final boundary =
          key.currentContext?.findRenderObject() as RenderRepaintBoundary?;
      if (boundary == null) return null;

      final image = await boundary.toImage(pixelRatio: pixelRatio);
      final byteData =
          await image.toByteData(format: ui.ImageByteFormat.png);
      if (byteData == null) return null;

      final bytes = byteData.buffer.asUint8List();
      final timestamp = DateTime.now().millisecondsSinceEpoch;
      final filename = '${prefix}_$timestamp.png';

      // Prefer app-specific external storage (visible in file managers)
      Directory? saveDir;
      try {
        saveDir = await getExternalStorageDirectory();
      } catch (_) {}
      saveDir ??= await getTemporaryDirectory();

      final file = File('${saveDir.path}/$filename');
      await file.writeAsBytes(bytes);
      return file.path;
    } catch (_) {
      return null;
    }
  }
}
