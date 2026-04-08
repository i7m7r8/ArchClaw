import 'package:flutter/foundation.dart';

enum HardwareCapability {
  camera,
  flash,
  location,
  screenRecord,
  accelerometer,
  gyroscope,
  magnetometer,
  haptic,
  canvas,
}

class HardwareProvider extends ChangeNotifier {
  final Map<HardwareCapability, bool> _capabilities = {
    HardwareCapability.camera: false,
    HardwareCapability.flash: false,
    HardwareCapability.location: false,
    HardwareCapability.screenRecord: false,
    HardwareCapability.accelerometer: false,
    HardwareCapability.gyroscope: false,
    HardwareCapability.magnetometer: false,
    HardwareCapability.haptic: false,
    HardwareCapability.canvas: false,
  };

  Map<HardwareCapability, bool> get capabilities => _capabilities;
  
  bool isEnabled(HardwareCapability capability) {
    return _capabilities[capability] ?? false;
  }

  Future<void> toggleCapability(HardwareCapability capability, bool enabled) async {
    // TODO: Request Android permissions if needed
    // if (enabled) {
    //   await _requestPermission(capability);
    // }
    
    _capabilities[capability] = enabled;
    notifyListeners();
  }

  Future<void> enableAll() async {
    for (var capability in HardwareCapability.values) {
      _capabilities[capability] = true;
    }
    notifyListeners();
  }

  Future<void> disableAll() async {
    for (var capability in HardwareCapability.values) {
      _capabilities[capability] = false;
    }
    notifyListeners();
  }

  List<HardwareCapability> get enabledCapabilities {
    return _capabilities.entries
        .where((e) => e.value)
        .map((e) => e.key)
        .toList();
  }

  String getCapabilityName(HardwareCapability capability) {
    switch (capability) {
      case HardwareCapability.camera:
        return 'Camera';
      case HardwareCapability.flash:
        return 'Flash/Torch';
      case HardwareCapability.location:
        return 'Location';
      case HardwareCapability.screenRecord:
        return 'Screen Recording';
      case HardwareCapability.accelerometer:
        return 'Accelerometer';
      case HardwareCapability.gyroscope:
        return 'Gyroscope';
      case HardwareCapability.magnetometer:
        return 'Magnetometer';
      case HardwareCapability.haptic:
        return 'Haptic Feedback';
      case HardwareCapability.canvas:
        return 'Canvas Drawing';
    }
  }

  String getCapabilityDescription(HardwareCapability capability) {
    switch (capability) {
      case HardwareCapability.camera:
        return 'Access device camera for AI vision capabilities';
      case HardwareCapability.flash:
        return 'Control flashlight/torch';
      case HardwareCapability.location:
        return 'Provide GPS/network location data';
      case HardwareCapability.screenRecord:
        return 'Record screen content for AI analysis';
      case HardwareCapability.accelerometer:
        return 'Access motion detection data';
      case HardwareCapability.gyroscope:
        return 'Access rotation and orientation data';
      case HardwareCapability.magnetometer:
        return 'Access compass/magnetic field data';
      case HardwareCapability.haptic:
        return 'Provide tactile feedback';
      case HardwareCapability.canvas:
        return 'Draw overlays on screen';
    }
  }
}
