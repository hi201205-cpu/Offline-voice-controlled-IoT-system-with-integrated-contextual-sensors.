import 'dart:async';
import 'dart:convert';
import 'package:http/http.dart' as http;
import 'models.dart';

class FirebaseService {
  static const _baseUrl    =
      'https://keyword-spotting-a21f7-default-rtdb.asia-southeast1.firebasedatabase.app';
  static const _authToken  =
      'REMOVED';
  static const _deviceId   = 'esp32-voice';
  static const _devicePath = '/devices/$_deviceId';

  // ── Streams ──────────────────────────────────────────────
  final _stateCtrl = StreamController<DeviceState>.broadcast();
  final _logsCtrl  = StreamController<List<LogEntry>>.broadcast();

  Stream<DeviceState>    get deviceStateStream => _stateCtrl.stream;
  Stream<List<LogEntry>> get logsStream        => _logsCtrl.stream;

  DeviceState _state = const DeviceState();
  final Map<String, LogEntry> _logCache = {};
  bool _disposed = false;

  http.Client? _stateClient;
  http.Client? _logsClient;

  // ── Public API ───────────────────────────────────────────
  void startListening() {
    _streamDeviceState();
    _streamLogs();
  }

  Future<bool> sendCommand(String command) async {
    try {
      final uri = Uri.parse(
          '$_baseUrl$_devicePath/remote_cmd.json?auth=$_authToken');
      final res = await http
          .put(uri,
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode(command))
          .timeout(const Duration(seconds: 8));
      return res.statusCode >= 200 && res.statusCode < 300;
    } catch (_) {
      return false;
    }
  }

  void dispose() {
    _disposed = true;
    _stateClient?.close();
    _logsClient?.close();
    _stateCtrl.close();
    _logsCtrl.close();
  }

  // ── SSE device state ─────────────────────────────────────
  // Chỉ stream relay1, relay2, last_seen — không bao gồm logs
  void _streamDeviceState() async {
    while (!_disposed) {
      try {
        _stateClient = http.Client();
        final req = http.Request('GET',
            Uri.parse('$_baseUrl$_devicePath.json?auth=$_authToken'));
        req.headers['Accept'] = 'text/event-stream';
        req.headers['Cache-Control'] = 'no-cache';
        final res = await _stateClient!.send(req);

        await _parseSse(res.stream,
          onPut: (path, data) {
            if (path == '/') {
              if (data is Map) {
                _state = DeviceState.fromJson(data);
                _stateCtrl.add(_state);
              }
            } else {
              _applyField(path, data);
            }
          },
          onPatch: (path, data) {
            if (data is Map) {
              data.forEach((k, v) => _applyField('/$k', v));
              _stateCtrl.add(_state);
            }
          },
        );
      } catch (_) {
        if (!_disposed) await Future.delayed(const Duration(seconds: 4));
      } finally {
        _stateClient?.close();
      }
    }
  }

  // ── SSE logs ─────────────────────────────────────────────
  // Stream riêng cho logs — nhận PUT trực tiếp khi có entry mới
  void _streamLogs() async {
    while (!_disposed) {
      try {
        _logsClient = http.Client();
        final req = http.Request('GET',
            Uri.parse('$_baseUrl$_devicePath/logs.json'
                '?orderBy=%22%24key%22&limitToLast=20&auth=$_authToken'));
        req.headers['Accept'] = 'text/event-stream';
        req.headers['Cache-Control'] = 'no-cache';
        final res = await _logsClient!.send(req);

        await _parseSse(res.stream,
          onPut: (path, data) {
            if (path == '/') {
              if (data is Map) _rebuildLogs(data);
              else _logsCtrl.add([]);
            } else if (data is Map) {
              _upsertLog(path.replaceFirst('/', ''), data);
            }
          },
          onPatch: (path, data) {
            if (data is Map) {
              data.forEach((k, v) {
                if (v is Map) _upsertLog(k.toString(), v);
              });
              _emitLogs();
            }
          },
        );
      } catch (_) {
        if (!_disposed) await Future.delayed(const Duration(seconds: 5));
      } finally {
        _logsClient?.close();
      }
    }
  }

  // ── SSE parser ───────────────────────────────────────────
  Future<void> _parseSse(Stream<List<int>> stream, {
    required void Function(String, dynamic) onPut,
    required void Function(String, dynamic) onPatch,
  }) async {
    var buf = '';
    await for (final chunk in stream.transform(utf8.decoder)) {
      if (_disposed) break;
      buf += chunk;
      while (buf.contains('\n\n')) {
        final end = buf.indexOf('\n\n');
        final msg = buf.substring(0, end);
        buf = buf.substring(end + 2);
        String? event, data;
        for (final line in msg.split('\n')) {
          if (line.startsWith('event:')) event = line.substring(6).trim();
          if (line.startsWith('data:'))  data  = line.substring(5).trim();
        }
        if (event == null || data == null || data == 'null') continue;
        try {
          final decoded = jsonDecode(data) as Map<String, dynamic>;
          final path    = decoded['path']?.toString() ?? '/';
          final value   = decoded['data'];
          if (value == null) continue;
          if (event == 'put')   onPut(path, value);
          if (event == 'patch') onPatch(path, value);
        } catch (_) {}
      }
    }
  }

  // ── Helpers ──────────────────────────────────────────────
  void _applyField(String path, dynamic v) {
    switch (path.replaceFirst('/', '')) {
      case 'relay1':
        _state = _state.copyWith(relay1: DeviceState.parseBool(v)); break;
      case 'relay2':
        _state = _state.copyWith(relay2: DeviceState.parseBool(v)); break;
      case 'last_seen':
        final ms = v is int ? v : int.tryParse(v.toString()) ?? 0;
        _state = _state.copyWith(lastSeen: ms); break;
    }
    _stateCtrl.add(_state);
  }

  void _rebuildLogs(Map data) {
    _logCache.clear();
    for (final e in data.entries) {
      final key = e.key.toString();
      if (e.value is Map) {
        _logCache[key] = LogEntry.fromJson(key, e.value as Map);
      }
    }
    _emitLogs();
  }

  void _upsertLog(String key, Map data) {
    _logCache[key] = LogEntry.fromJson(key, data);
    _emitLogs();
  }

  void _emitLogs() {
    final sorted = _logCache.values.toList()
    // Sort theo Firebase server timestamp — chính xác dù ESP32 có reboot
      ..sort((a, b) => b.receivedAt.compareTo(a.receivedAt));
    _logsCtrl.add(sorted.take(20).toList());
  }
}