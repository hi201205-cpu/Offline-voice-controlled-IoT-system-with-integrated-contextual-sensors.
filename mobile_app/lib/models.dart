import 'package:flutter/material.dart';

/// Trạng thái thiết bị phần cứng từ ESP32 và Realtime Database
class DeviceState {
  final bool light;          // Relay 1 - Đèn phòng
  final bool fan;            // Relay 2 - Quạt làm mát
  final String curtain;      // 'open' | 'close'
  final bool door;           // true: Mở, false: Đóng
  final bool yard;           // Đèn sân vườn
  final bool voice;          // Nhận diện giọng nói offline đang bật/tắt
  final int lastSeen;        // Epoch ms từ Firebase server timestamp
  final bool keypadLocked;   // Khóa an ninh bàn phím ngoài khi nhập sai 4 lần

  const DeviceState({
    this.light = false,
    this.fan = false,
    this.curtain = 'close',
    this.door = false,
    this.yard = false,
    this.voice = true,
    this.lastSeen = 0,
    this.keypadLocked = false,
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
      keypadLocked: parseBool(json['keypad_locked'] ?? json['is_locked_out']),
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
    bool? keypadLocked,
  }) {
    return DeviceState(
      light: light ?? this.light,
      fan: fan ?? this.fan,
      curtain: curtain ?? this.curtain,
      door: door ?? this.door,
      yard: yard ?? this.yard,
      voice: voice ?? this.voice,
      lastSeen: lastSeen ?? this.lastSeen,
      keypadLocked: keypadLocked ?? this.keypadLocked,
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

/// Điểm dữ liệu lịch sử cảm biến dùng để vẽ biểu đồ
class SensorPoint {
  final DateTime timestamp;
  final double temp;
  final double hum;

  const SensorPoint({
    required this.timestamp,
    required this.temp,
    required this.hum,
  });
}

/// Lịch sử thao tác & sự kiện trong hệ thống (Không dùng emoji)
class LogEntry {
  final String key;
  final String device;
  final String action;
  final String source;    // 'app' | 'web' | 'voice' | 'door' | 'auto'
  final String user;      // Email hoặc nguồn thực hiện
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
    final rawSource = json['src']?.toString() ?? json['source']?.toString() ?? 'app';
    final rawUser = json['user']?.toString() ?? json['email']?.toString() ?? 
        (rawSource == 'voice' ? 'ESP32 Mic' : (rawSource == 'door' ? 'Bàn phím ngoài' : 'Hệ thống'));

    return LogEntry(
      key: key,
      device: rawDevice,
      action: rawAction,
      source: rawSource,
      user: rawUser,
      timestamp: time,
    );
  }

  String get deviceLabel {
    final d = device.toLowerCase();
    final s = source.toLowerCase();
    if (s == 'door' || s == 'keypad' || d.contains('door') || d.contains('cua')) return 'Cửa chính / Keypad';
    if (d.contains('den') || d.contains('light')) return 'Đèn phòng';
    if (d.contains('quat') || d.contains('fan')) return 'Quạt làm mát';
    if (d.contains('rem') || d.contains('curtain')) return 'Rèm cửa';
    if (d.contains('vuon') || d.contains('yard') || d.contains('garden')) return 'Đèn sân vườn';
    if (s == 'voice') return 'Lệnh giọng nói AI';
    if (s == 'auto') return 'Tự động hóa thông minh';
    if (d.contains('all') || d.contains('het')) return 'Tất cả thiết bị';
    return device.isNotEmpty ? device : 'Thiết bị';
  }

  String get actionLabel {
    final a = action.toLowerCase();
    final s = source.toLowerCase();
    final d = device.toLowerCase();
    if (a == 'denied') return 'Từ chối mở cửa (Sai mật khẩu)';
    if (a == 'locked') return 'Khóa an ninh bàn phím (4 lần sai)';
    if (a == 'unlock') return 'Mở khóa an ninh bàn phím';
    if (a == 'on' || a == 'bat' || a == 'bat_den' || a == 'bat_quat') return 'Bật';
    if (a == 'off' || a == 'tat' || a == 'tat_den' || a == 'tat_quat') return 'Tắt';
    if (a == 'open' || a == 'mo' || a == 'mo_rem' || a == 'mo_cua') {
      if (d.contains('cua') || s.contains('door')) return 'Mở cửa thành công';
      return 'Mở';
    }
    if (a == 'close' || a == 'dong' || a == 'dong_rem') return 'Đóng';
    if (a.contains('passcode_add')) return 'Thêm mật khẩu Keypad';
    if (a.contains('passcode_del')) return 'Xóa mật khẩu Keypad';
    return action.isNotEmpty ? action : 'Thao tác';
  }

  String get sourceLabel {
    final s = source.toLowerCase();
    if (s == 'door' || s == 'keypad' || s == 'bàn phím') return 'Bàn phím ngoài';
    if (s == 'voice') return 'Giọng nói AI';
    if (s == 'auto') return 'Tự động hóa';
    if (s == 'web') return 'Web Dashboard';
    if (s == 'app') return 'Ứng dụng';
    return source.isNotEmpty ? source : 'Hệ thống';
  }

  IconData get iconData {
    final d = device.toLowerCase();
    final s = source.toLowerCase();
    final a = action.toLowerCase();
    if (a == 'locked' || a == 'denied') return Icons.lock_clock_rounded;
    if (s == 'door' || s == 'keypad' || d.contains('door') || d.contains('cua')) return Icons.door_front_door_rounded;
    if (d.contains('den') || d.contains('light')) return Icons.lightbulb_rounded;
    if (d.contains('quat') || d.contains('fan')) return Icons.wind_power_rounded;
    if (d.contains('rem') || d.contains('curtain')) return Icons.curtains_rounded;
    if (d.contains('vuon') || d.contains('yard') || d.contains('garden')) return Icons.park_rounded;
    if (s == 'voice') return Icons.mic_rounded;
    if (s == 'auto') return Icons.auto_awesome_rounded;
    return Icons.settings_remote_rounded;
  }
}

/// Thông tin tài khoản người dùng và phân quyền đầy đủ (Đồng bộ với Web)
class UserProfile {
  final String uid;
  final String email;
  final String displayName;
  final String role;         // 'master' | 'member'
  final Map<String, bool> permissions;
  final bool pairedHome;
  final String phone;
  final String dob;
  final String gender;
  final String address;
  final String? updatedAt;

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
      'auto': true,
      'passcodeAdd': false,
      'passcodeDel': false,
    },
    this.pairedHome = true,
    this.phone = '',
    this.dob = '',
    this.gender = '',
    this.address = '',
    this.updatedAt,
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
      'auto': true,
      'passcodeAdd': false,
      'passcodeDel': false,
    };

    if (json['permissions'] is Map) {
      final pMap = json['permissions'] as Map;
      perms = {
        'light': DeviceState.parseBool(pMap['light'] ?? true),
        'fan': DeviceState.parseBool(pMap['fan'] ?? true),
        'curtain': DeviceState.parseBool(pMap['curtain'] ?? true),
        'door': DeviceState.parseBool(pMap['door'] ?? true),
        'yard': DeviceState.parseBool(pMap['yard'] ?? true),
        'auto': DeviceState.parseBool(pMap['auto'] ?? true),
        'passcodeAdd': DeviceState.parseBool(pMap['passcodeAdd'] ?? false),
        'passcodeDel': DeviceState.parseBool(pMap['passcodeDel'] ?? false),
      };
    }

    final prof = (json['profile'] is Map) ? json['profile'] as Map : {};

    return UserProfile(
      uid: uid,
      email: email,
      displayName: json['displayName']?.toString() ?? (email.isNotEmpty ? email.split('@').first : 'Người dùng'),
      role: isMasterUser ? 'master' : (json['role']?.toString() ?? 'member'),
      permissions: perms,
      pairedHome: DeviceState.parseBool(json['pairedHome'] ?? true),
      phone: prof['phone']?.toString() ?? json['phone']?.toString() ?? '',
      dob: prof['dob']?.toString() ?? json['dob']?.toString() ?? '',
      gender: prof['gender']?.toString() ?? json['gender']?.toString() ?? '',
      address: prof['address']?.toString() ?? json['address']?.toString() ?? '',
      updatedAt: prof['updatedAt']?.toString() ?? json['updatedAt']?.toString(),
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
