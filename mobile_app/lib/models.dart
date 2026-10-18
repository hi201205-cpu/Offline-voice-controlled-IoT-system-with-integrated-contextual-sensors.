import 'package:flutter/foundation.dart';

/// Trạng thái thiết bị phần cứng từ ESP32 và Realtime Database
class DeviceState {
  final bool light;       // Relay 1 - Đèn
  final bool fan;         // Relay 2 - Quạt
  final String curtain;   // 'open' | 'close'
  final bool door;        // true: Mở, false: Đóng
  final bool yard;        // Đèn sân vườn
  final bool voice;       // Nhận diện giọng nói offline đang bật/tắt
  final int lastSeen;     // Epoch ms từ Firebase server timestamp

  const DeviceState({
    this.light = false,
    this.fan = false,
    this.curtain = 'close',
    this.door = false,
    this.yard = false,
    this.voice = true,
    this.lastSeen = 0,
  });

  bool get isCurtainOpen => curtain == 'open';

  factory DeviceState.fromJson(Map<dynamic, dynamic> json) {
    return DeviceState(
      light: parseBool(json['light'] ?? json['relay1']),
      fan: parseBool(json['fan'] ?? json['relay2']),
      curtain: (json['curtain'] ?? json['curtain_state'] ?? 'close').toString(),
      door: parseBool(json['door'] ?? (json['door_state'] == 'open')),
      yard: parseBool(json['yard'] ?? json['garden'] ?? json['relay_garden']),
      voice: parseBool(json['voice'] ?? true),
      lastSeen: parseInt(json['last_seen'] ?? json['ts']),
    );
  }

  DeviceState copyWith({
    bool? light,
    bool? fan,
    String? curtain,
    bool? door,
    bool? yard,
    bool? voice,
    int? lastSeen,
  }) {
    return DeviceState(
      light: light ?? this.light,
      fan: fan ?? this.fan,
      curtain: curtain ?? this.curtain,
      door: door ?? this.door,
      yard: yard ?? this.yard,
      voice: voice ?? this.voice,
      lastSeen: lastSeen ?? this.lastSeen,
    );
  }

  static bool parseBool(dynamic val) {
    if (val == null) return false;
    if (val is bool) return val;
    if (val is int) return val != 0;
    if (val is String) {
      final s = val.toLowerCase().trim();
      return s == '1' || s == 'true' || s == 'on' || s == 'open';
    }
    return false;
  }

  static int parseInt(dynamic val) {
    if (val == null) return 0;
    if (val is int) return val;
    if (val is double) return val.toInt();
    return int.tryParse(val.toString()) ?? 0;
  }

  static double parseDouble(dynamic val) {
    if (val == null) return 0.0;
    if (val is double) return val;
    if (val is int) return val.toDouble();
    return double.tryParse(val.toString()) ?? 0.0;
  }
}

/// Dữ liệu cảm biến môi trường (DHT22, mmWave Radar, PIR, LDR)
class SensorData {
  final double temp;
  final double hum;
  final int ldr;
  final bool person;
  final double mmwaveDist;
  final DateTime updatedAt;

  const SensorData({
    this.temp = 0.0,
    this.hum = 0.0,
    this.ldr = 0,
    this.person = false,
    this.mmwaveDist = 0.0,
    required this.updatedAt,
  });

  factory SensorData.fromJson(Map<dynamic, dynamic> json) {
    return SensorData(
      temp: DeviceState.parseDouble(json['temp'] ?? json['temperature']),
      hum: DeviceState.parseDouble(json['hum'] ?? json['humidity']),
      ldr: DeviceState.parseInt(json['ldr'] ?? json['ldr_raw']),
      person: DeviceState.parseBool(json['person'] ?? json['presence'] ?? json['mmwave_detected']),
      mmwaveDist: DeviceState.parseDouble(json['mmwave_dist'] ?? json['mmwave_distance']),
      updatedAt: DateTime.now(),
    );
  }
}

/// Lịch sử thao tác & sự kiện trong hệ thống
class LogEntry {
  final String key;
  final String device;
  final String action;
  final String source;    // 'app' | 'web' | 'voice' | 'door' | 'auto'
  final String user;      // Email tài khoản thao tác
  final DateTime timestamp;

  const LogEntry({
    required this.key,
    required this.device,
    required this.action,
    required this.source,
    required this.user,
    required this.timestamp,
  });

  factory LogEntry.fromJson(String key, Map<dynamic, dynamic> json) {
    DateTime time = DateTime.now();
    final tsRaw = json['ts'] ?? json['timestamp'] ?? json['time'];
    if (tsRaw != null) {
      final ms = DeviceState.parseInt(tsRaw);
      if (ms > 0) {
        time = DateTime.fromMillisecondsSinceEpoch(ms > 1000000000000 ? ms : ms * 1000);
      }
    } else {
      final keyNum = int.tryParse(key);
      if (keyNum != null && keyNum > 0) {
        time = DateTime.fromMillisecondsSinceEpoch(keyNum > 1000000000000 ? keyNum : keyNum * 1000);
      }
    }

    final rawDevice = json['device']?.toString() ?? json['command']?.toString() ?? '';
    final rawAction = json['action']?.toString() ?? json['state']?.toString() ?? '';
    final rawUser = json['user']?.toString() ?? json['email']?.toString() ?? (json['source'] == 'voice' ? 'ESP32 Mic' : 'Hệ thống');

    return LogEntry(
      key: key,
      device: rawDevice,
      action: rawAction,
      source: json['src']?.toString() ?? json['source']?.toString() ?? 'app',
      user: rawUser,
      timestamp: time,
    );
  }

  String get deviceLabel {
    final d = device.toLowerCase();
    if (d.contains('den') || d.contains('light')) return 'Đèn phòng';
    if (d.contains('quat') || d.contains('fan')) return 'Quạt';
    if (d.contains('rem') || d.contains('curtain')) return 'Rèm cửa';
    if (d.contains('cua') || d.contains('door')) return 'Cửa thông minh';
    if (d.contains('vuon') || d.contains('yard') || d.contains('garden')) return 'Đèn sân vườn';
    if (d.contains('all') || d.contains('het')) return 'Tất cả thiết bị';
    return device.isNotEmpty ? device : 'Thiết bị';
  }

  String get actionLabel {
    final a = action.toLowerCase();
    if (a == 'on' || a == 'bat' || a == 'bat_den' || a == 'bat_quat') return 'Bật';
    if (a == 'off' || a == 'tat' || a == 'tat_den' || a == 'tat_quat') return 'Tắt';
    if (a == 'open' || a == 'mo' || a == 'mo_rem' || a == 'mo_cua') return 'Mở';
    if (a == 'close' || a == 'dong' || a == 'dong_rem') return 'Đóng';
    return action.isNotEmpty ? action : 'Thao tác';
  }

  String get deviceIcon {
    final d = device.toLowerCase();
    if (d.contains('den') || d.contains('light')) return '💡';
    if (d.contains('quat') || d.contains('fan')) return '🌀';
    if (d.contains('rem') || d.contains('curtain')) return '🪟';
    if (d.contains('cua') || d.contains('door')) return '🚪';
    if (d.contains('vuon') || d.contains('yard') || d.contains('garden')) return '🌳';
    if (d.contains('voice')) return '🎙️';
    return '⚡';
  }
}

/// Thông tin tài khoản người dùng và phân quyền
class UserProfile {
  final String uid;
  final String email;
  final String displayName;
  final String role;         // 'master' | 'member'
  final Map<String, bool> permissions;
  final bool pairedHome;

  const UserProfile({
    required this.uid,
    required this.email,
    required this.displayName,
    required this.role,
    this.permissions = const {
      'light': true,
      'fan': true,
      'curtain': true,
      'door': true,
      'yard': true,
    },
    this.pairedHome = true,
  });

  bool get isMaster => role == 'master' || email.toLowerCase() == 'xhai2005@gmail.com';

  bool hasPermission(String deviceKey) {
    if (isMaster) return true;
    return permissions[deviceKey] ?? false;
  }

  factory UserProfile.fromJson(String uid, Map<dynamic, dynamic> json) {
    final email = json['email']?.toString() ?? '';
    final isMasterUser = email.toLowerCase() == 'xhai2005@gmail.com' || json['role'] == 'master';

    Map<String, bool> perms = {
      'light': true,
      'fan': true,
      'curtain': true,
      'door': true,
      'yard': true,
    };

    if (json['permissions'] is Map) {
      final pMap = json['permissions'] as Map;
      perms = {
        'light': DeviceState.parseBool(pMap['light'] ?? true),
        'fan': DeviceState.parseBool(pMap['fan'] ?? true),
        'curtain': DeviceState.parseBool(pMap['curtain'] ?? true),
        'door': DeviceState.parseBool(pMap['door'] ?? true),
        'yard': DeviceState.parseBool(pMap['yard'] ?? true),
      };
    }

    return UserProfile(
      uid: uid,
      email: email,
      displayName: json['displayName']?.toString() ?? (email.isNotEmpty ? email.split('@').first : 'Người dùng'),
      role: isMasterUser ? 'master' : (json['role']?.toString() ?? 'member'),
      permissions: perms,
      pairedHome: DeviceState.parseBool(json['pairedHome'] ?? true),
    );
  }
}

/// Mã mời 1 lần cho thành viên mới
class InviteCode {
  final String code;
  final String status;     // 'unused' | 'used'
  final String createdBy;
  final int createdAt;
  final String? usedBy;

  const InviteCode({
    required this.code,
    required this.status,
    required this.createdBy,
    required this.createdAt,
    this.usedBy,
  });

  bool get isUnused => status == 'unused';

  factory InviteCode.fromJson(String code, Map<dynamic, dynamic> json) {
    return InviteCode(
      code: code,
      status: json['status']?.toString() ?? 'unused',
      createdBy: json['createdBy']?.toString() ?? '',
      createdAt: DeviceState.parseInt(json['createdAt']),
      usedBy: json['usedBy']?.toString(),
    );
  }
}