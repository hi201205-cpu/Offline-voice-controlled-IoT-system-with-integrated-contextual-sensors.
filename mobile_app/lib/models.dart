/// Trạng thái thiết bị ESP32
class DeviceState {
  final bool relay1;
  final bool relay2;
  final int  lastSeen; // epoch ms từ Firebase server timestamp

  const DeviceState({
    this.relay1   = false,
    this.relay2   = false,
    this.lastSeen = 0,
  });

  factory DeviceState.fromJson(Map<dynamic, dynamic> json) {
    return DeviceState(
      relay1:   parseBool(json['relay1']),
      relay2:   parseBool(json['relay2']),
      lastSeen: _parseInt(json['last_seen']),
    );
  }

  DeviceState copyWith({bool? relay1, bool? relay2, int? lastSeen}) {
    return DeviceState(
      relay1:   relay1   ?? this.relay1,
      relay2:   relay2   ?? this.relay2,
      lastSeen: lastSeen ?? this.lastSeen,
    );
  }

  static int _parseInt(dynamic val) {
    if (val == null) return 0;
    if (val is int) return val;
    if (val is double) return val.toInt();
    return int.tryParse(val.toString()) ?? 0;
  }

  static bool parseBool(dynamic val) {
    if (val == null) return false;
    if (val is bool) return val;
    if (val is int) return val != 0;
    if (val is String) return val == '1' || val == 'true';
    return false;
  }
}

/// Lịch sử — ESP32 ghi lên Firebase, Flutter chỉ đọc
class LogEntry {
  final String   key;
  final String   command;
  final String   source;     // 'voice' | 'remote'
  final bool     relay1;
  final bool     relay2;
  final DateTime receivedAt;

  const LogEntry({
    required this.key,
    required this.command,
    required this.source,
    required this.relay1,
    required this.relay2,
    required this.receivedAt,
  });

  factory LogEntry.fromJson(String key, Map<dynamic, dynamic> json,
      {DateTime? receivedAt}) {
    // Ưu tiên 1: field timestamp/ts trong data (ESP32 ghi lên)
    // Ưu tiên 2: parse key dạng số (Unix ms hoặc s)
    // Ưu tiên 3: receivedAt truyền vào (fallback cuối cùng)
    DateTime time = receivedAt ?? DateTime.now();
    final tsRaw = json['timestamp'] ?? json['ts'] ?? json['time'];
    if (tsRaw != null) {
      final ms = tsRaw is int
          ? tsRaw
          : double.tryParse(tsRaw.toString())?.toInt();
      if (ms != null && ms > 0) {
        // Phân biệt milliseconds (> 1e12) vs seconds
        time = DateTime.fromMillisecondsSinceEpoch(
            ms > 1000000000000 ? ms : ms * 1000);
      }
    } else {
      final keyNum = int.tryParse(key);
      if (keyNum != null && keyNum > 0) {
        time = DateTime.fromMillisecondsSinceEpoch(
            keyNum > 1000000000000 ? keyNum : keyNum * 1000);
      }
    }
    return LogEntry(
      key:        key,
      command:    json['command']?.toString() ?? '',
      source:     json['source']?.toString() ?? 'remote',
      relay1:     DeviceState.parseBool(json['relay1']),
      relay2:     DeviceState.parseBool(json['relay2']),
      receivedAt: time,
    );
  }

  String get commandLabel {
    const labels = {
      'bat_den':  'Bật đèn',    'tat_den':  'Tắt đèn',
      'bat_quat': 'Bật quạt',   'tat_quat': 'Tắt quạt',
      'bat_het':  'Bật tất cả', 'tat_het':  'Tắt tất cả',
    };
    return labels[command] ?? command;
  }

  String get commandIcon {
    if (command.contains('den'))  return '💡';
    if (command.contains('quat')) return '🌀';
    if (command == 'bat_het')     return '⚡';
    if (command == 'tat_het')     return '🔌';
    return '📟';
  }

  bool get isVoice => source == 'voice';
}