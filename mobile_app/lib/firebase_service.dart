import 'dart:async';
import 'dart:convert';
import 'dart:math';
import 'package:http/http.dart' as http;
import 'models.dart';

class FirebaseService {
  static final FirebaseService _instance = FirebaseService._internal();
  factory FirebaseService() => _instance;
  FirebaseService._internal();

  static const String apiKey = 'AIzaSyBmNwu9LFCdL8Y72SGTuvz9-v9v3GM0jfU';
  static const String baseUrl = 'https://do-an-2-f791a-default-rtdb.asia-southeast1.firebasedatabase.app';
  static const String masterEmail = 'xhai2005@gmail.com';

  // ── Session ──────────────────────────────────────────────
  String? _idToken;
  String? _refreshToken;
  UserProfile? _currentUser;

  UserProfile? get currentUser => _currentUser;
  bool get isLoggedIn => _currentUser != null;

  // ── Streams ──────────────────────────────────────────────
  final _stateCtrl   = StreamController<DeviceState>.broadcast();
  final _sensorCtrl  = StreamController<SensorData>.broadcast();
  final _logsCtrl    = StreamController<List<LogEntry>>.broadcast();
  final _profileCtrl = StreamController<UserProfile?>.broadcast();

  Stream<DeviceState>    get deviceStateStream => _stateCtrl.stream;
  Stream<SensorData>     get sensorDataStream  => _sensorCtrl.stream;
  Stream<List<LogEntry>> get logsStream        => _logsCtrl.stream;
  Stream<UserProfile?>   get profileStream     => _profileCtrl.stream;

  DeviceState _lastState = const DeviceState();
  SensorData  _lastSensor = SensorData(updatedAt: DateTime.now());
  Timer? _pollingTimer;
  bool _disposed = false;

  // ── Khởi động lắng nghe Realtime Database ─────────────────
  void startListening() {
    _pollRealtimeData();
    _pollingTimer?.cancel();
    _pollingTimer = Timer.periodic(const Duration(seconds: 3), (_) {
      if (!_disposed) _pollRealtimeData();
    });
  }

  void stopListening() {
    _pollingTimer?.cancel();
  }

  void dispose() {
    _disposed = true;
    _pollingTimer?.cancel();
    _stateCtrl.close();
    _sensorCtrl.close();
    _logsCtrl.close();
    _profileCtrl.close();
  }

  // ── FIREBASE AUTHENTICATION (REST API) ────────────────────

  /// 1. Đăng nhập bằng Email & Mật khẩu
  Future<UserProfile> signInWithEmail(String email, String password) async {
    final url = Uri.parse('https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey');
    final response = await http.post(
      url,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'email': email.trim(),
        'password': password,
        'returnSecureToken': true,
      }),
    ).timeout(const Duration(seconds: 10));

    final resData = jsonDecode(response.body);
    if (response.statusCode != 200) {
      throw _parseAuthError(resData['error']?['message']);
    }

    _idToken = resData['idToken'];
    _refreshToken = resData['refreshToken'];
    final uid = resData['localId'];
    final userEmail = resData['email'] ?? email;

    _currentUser = await _fetchOrCreateProfile(uid, userEmail, resData['displayName'] ?? '');
    _profileCtrl.add(_currentUser);
    return _currentUser!;
  }

  /// 2. Đăng ký tài khoản (kèm kiểm tra mã mời 1 lần cho Member)
  Future<UserProfile> signUpWithEmail({
    required String email,
    required String password,
    required String displayName,
    String? inviteCode,
  }) async {
    final cleanEmail = email.trim();
    final isMaster = cleanEmail.toLowerCase() == masterEmail;

    if (!isMaster) {
      if (inviteCode == null || inviteCode.trim().isEmpty) {
        throw 'Cần có Mã mời thành viên từ Master để đăng ký!';
      }
      final codeValid = await _validateInviteCode(inviteCode.trim());
      if (!codeValid) {
        throw 'Mã mời không tồn tại hoặc đã được sử dụng!';
      }
    }

    final url = Uri.parse('https://identitytoolkit.googleapis.com/v1/accounts:signUp?key=$apiKey');
    final response = await http.post(
      url,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'email': cleanEmail,
        'password': password,
        'returnSecureToken': true,
      }),
    ).timeout(const Duration(seconds: 10));

    final resData = jsonDecode(response.body);
    if (response.statusCode != 200) {
      throw _parseAuthError(resData['error']?['message']);
    }

    _idToken = resData['idToken'];
    _refreshToken = resData['refreshToken'];
    final uid = resData['localId'];

    if (displayName.trim().isNotEmpty) {
      await http.post(
        Uri.parse('https://identitytoolkit.googleapis.com/v1/accounts:update?key=$apiKey'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'idToken': _idToken,
          'displayName': displayName.trim(),
          'returnSecureToken': true,
        }),
      );
    }

    final profile = UserProfile(
      uid: uid,
      email: cleanEmail,
      displayName: displayName.trim().isNotEmpty ? displayName.trim() : cleanEmail.split('@').first,
      role: isMaster ? 'master' : 'member',
      permissions: {
        'light': true,
        'fan': true,
        'curtain': true,
        'door': true,
        'yard': true,
        'auto': true,
        'passcodeAdd': false,
        'passcodeDel': false,
      },
      pairedHome: true,
    );

    await http.put(
      Uri.parse('$baseUrl/users/$uid.json?auth=$_idToken'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'email': profile.email,
        'displayName': profile.displayName,
        'role': profile.role,
        'permissions': profile.permissions,
        'pairedHome': true,
        'registeredAt': DateTime.now().millisecondsSinceEpoch,
      }),
    );

    if (!isMaster && inviteCode != null) {
      await http.patch(
        Uri.parse('$baseUrl/invites/${inviteCode.trim()}.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'status': 'used',
          'usedBy': uid,
          'usedAt': DateTime.now().millisecondsSinceEpoch,
        }),
      );
    }

    _currentUser = profile;
    _profileCtrl.add(_currentUser);
    return _currentUser!;
  }

  /// 3. Quên mật khẩu
  Future<void> sendPasswordResetEmail(String email) async {
    final cleanEmail = email.trim();
    if (cleanEmail.isEmpty) throw 'Vui lòng nhập địa chỉ email của bạn!';

    final url = Uri.parse('https://identitytoolkit.googleapis.com/v1/accounts:sendOobCode?key=$apiKey');
    final response = await http.post(
      url,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'requestType': 'PASSWORD_RESET',
        'email': cleanEmail,
      }),
    ).timeout(const Duration(seconds: 10));

    final resData = jsonDecode(response.body);
    if (response.statusCode != 200) {
      throw _parseAuthError(resData['error']?['message']);
    }
  }

  /// 4. Đổi mật khẩu trong Cài đặt
  Future<void> changePassword({
    required String oldPassword,
    required String newPassword,
  }) async {
    if (_currentUser == null) throw 'Bạn chưa đăng nhập!';
    if (newPassword.length < 6) throw 'Mật khẩu mới phải có tối thiểu 6 ký tự!';
    if (oldPassword == newPassword) throw 'Mật khẩu mới phải khác mật khẩu hiện tại!';

    final verifyUrl = Uri.parse('https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey');
    final verifyRes = await http.post(
      verifyUrl,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'email': _currentUser!.email,
        'password': oldPassword,
        'returnSecureToken': true,
      }),
    );

    final verifyData = jsonDecode(verifyRes.body);
    if (verifyRes.statusCode != 200) {
      throw 'Mật khẩu hiện tại không chính xác!';
    }

    final freshToken = verifyData['idToken'] ?? _idToken;

    final updateUrl = Uri.parse('https://identitytoolkit.googleapis.com/v1/accounts:update?key=$apiKey');
    final updateRes = await http.post(
      updateUrl,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'idToken': freshToken,
        'password': newPassword,
        'returnSecureToken': true,
      }),
    );

    final updateData = jsonDecode(updateRes.body);
    if (updateRes.statusCode != 200) {
      throw _parseAuthError(updateData['error']?['message']);
    }

    _idToken = updateData['idToken'];
  }

  /// 5. Cập nhật thông tin cá nhân (Profile)
  Future<void> updateProfile({
    required String displayName,
    required String phone,
    required String dob,
    required String gender,
    required String address,
  }) async {
    if (_currentUser == null) throw 'Bạn chưa đăng nhập!';
    final uid = _currentUser!.uid;
    final nowIso = DateTime.now().toIso8601String();

    await http.patch(
      Uri.parse('$baseUrl/users/$uid.json?auth=$_idToken'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'displayName': displayName.trim(),
        'profile': {
          'phone': phone.trim(),
          'dob': dob.trim(),
          'gender': gender.trim(),
          'address': address.trim(),
          'updatedAt': nowIso,
        },
      }),
    );

    _currentUser = await _fetchOrCreateProfile(uid, _currentUser!.email, displayName);
    _profileCtrl.add(_currentUser);
  }

  /// 6. Đăng xuất
  void signOut() {
    _idToken = null;
    _refreshToken = null;
    _currentUser = null;
    _profileCtrl.add(null);
  }

  // ── ĐIỀU KHIỂN THIẾT BỊ & GỬI LỆNH ─────────────────────────

  Future<bool> setDevice(String deviceKey, bool value) async {
    final userEmail = _currentUser?.email ?? 'Mobile App';
    final now = DateTime.now().millisecondsSinceEpoch;

    try {
      final cmdTarget = (deviceKey == 'yard' || deviceKey == 'garden') ? 'yard' : deviceKey;
      await http.put(
        Uri.parse('$baseUrl/commands/$cmdTarget.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'value': value,
          'ts': now,
          'src': 'app',
          'user': userEmail,
        }),
      );

      await http.patch(
        Uri.parse('$baseUrl/devices/state.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          deviceKey: value,
        }),
      );

      await _writeOperationLog(deviceKey, value ? 'on' : 'off', userEmail, now);
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<bool> setCurtain(String action) async {
    final userEmail = _currentUser?.email ?? 'Mobile App';
    final now = DateTime.now().millisecondsSinceEpoch;

    try {
      await http.put(
        Uri.parse('$baseUrl/commands/curtain.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'value': action,
          'ts': now,
          'src': 'app',
          'user': userEmail,
        }),
      );

      await http.patch(
        Uri.parse('$baseUrl/devices/state.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'curtain': action,
        }),
      );

      await _writeOperationLog('curtain', action, userEmail, now);
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<bool> openDoor() async {
    final userEmail = _currentUser?.email ?? 'Mobile App';
    final now = DateTime.now().millisecondsSinceEpoch;

    try {
      await http.put(
        Uri.parse('$baseUrl/commands/door.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'action': 'open',
          'ts': now,
          'src': 'app',
          'user': userEmail,
        }),
      );

      await http.patch(
        Uri.parse('$baseUrl/devices/state.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'door': true,
        }),
      );

      await _writeOperationLog('door', 'open', userEmail, now);
      return true;
    } catch (_) {
      return false;
    }
  }

  /// Mở khóa an ninh bàn phím ngoài (khi bị khóa do nhập sai 4 lần)
  Future<bool> unlockKeypad() async {
    final userEmail = _currentUser?.email ?? 'Mobile App';
    final now = DateTime.now().millisecondsSinceEpoch;

    try {
      await http.patch(
        Uri.parse('$baseUrl/devices/state.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'keypad_locked': false}),
      );

      await http.put(
        Uri.parse('$baseUrl/door/keypad_locked.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(false),
      );

      await _writeOperationLog('door', 'unlock', userEmail, now);
      _lastState = _lastState.copyWith(keypadLocked: false);
      _stateCtrl.add(_lastState);
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<bool> allOn() async {
    final userEmail = _currentUser?.email ?? 'Mobile App';
    final now = DateTime.now().millisecondsSinceEpoch;

    try {
      final payload = {'value': true, 'ts': now, 'src': 'app', 'user': userEmail};
      await http.put(Uri.parse('$baseUrl/commands/light.json?auth=$_idToken'), body: jsonEncode(payload));
      await http.put(Uri.parse('$baseUrl/commands/fan.json?auth=$_idToken'), body: jsonEncode(payload));
      await http.put(Uri.parse('$baseUrl/commands/yard.json?auth=$_idToken'), body: jsonEncode(payload));

      await http.patch(
        Uri.parse('$baseUrl/devices/state.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'light': true, 'fan': true, 'yard': true}),
      );

      await _writeOperationLog('all', 'on', userEmail, now);
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<bool> allOff() async {
    final userEmail = _currentUser?.email ?? 'Mobile App';
    final now = DateTime.now().millisecondsSinceEpoch;

    try {
      final payload = {'value': false, 'ts': now, 'src': 'app', 'user': userEmail};
      await http.put(Uri.parse('$baseUrl/commands/light.json?auth=$_idToken'), body: jsonEncode(payload));
      await http.put(Uri.parse('$baseUrl/commands/fan.json?auth=$_idToken'), body: jsonEncode(payload));
      await http.put(Uri.parse('$baseUrl/commands/yard.json?auth=$_idToken'), body: jsonEncode(payload));

      await http.patch(
        Uri.parse('$baseUrl/devices/state.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'light': false, 'fan': false, 'yard': false}),
      );

      await _writeOperationLog('all', 'off', userEmail, now);
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<bool> setAutomation(bool enabled) async {
    try {
      await http.put(
        Uri.parse('$baseUrl/config/auto/enabled.json?auth=$_idToken'),
        body: jsonEncode(enabled),
      );
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<bool> setGardenMode(String mode) async {
    try {
      await http.put(
        Uri.parse('$baseUrl/config/garden/mode.json?auth=$_idToken'),
        body: jsonEncode(mode),
      );
      return true;
    } catch (_) {
      return false;
    }
  }

  // ── BIỂU ĐỒ LỊCH SỬ CẢM BIẾN (REALTIME DATABASE) ─────────────

  /// Tải các điểm dữ liệu lịch sử cảm biến từ sensors/history
  Future<List<SensorPoint>> fetchSensorHistory({String range = '24h'}) async {
    try {
      final res = await http.get(Uri.parse('$baseUrl/sensors/history.json?auth=$_idToken'));
      if (res.statusCode == 200 && res.body != 'null') {
        final data = jsonDecode(res.body) as Map;
        final points = <SensorPoint>[];
        final now = DateTime.now();
        final maxAgeHours = range == '7d' ? 24 * 7 : 24;

        data.forEach((k, v) {
          if (v is Map) {
            final t = DeviceState.parseDouble(v['temp'] ?? v['t']);
            final h = DeviceState.parseDouble(v['hum'] ?? v['h']);
            final tsRaw = v['ts'] ?? v['time'] ?? int.tryParse(k.toString());
            DateTime time = now;
            if (tsRaw != null) {
              final ms = DeviceState.parseInt(tsRaw);
              time = DateTime.fromMillisecondsSinceEpoch(ms > 1000000000000 ? ms : ms * 1000);
            }
            if (now.difference(time).inHours <= maxAgeHours) {
              points.add(SensorPoint(timestamp: time, temp: t, hum: h));
            }
          }
        });

        points.sort((a, b) => a.timestamp.compareTo(b.timestamp));
        return points;
      }
    } catch (_) {}
    return [];
  }

  // ── MASTER: QUẢN LÝ MÃ MỜI & THÀNH VIÊN ────────────────────

  Future<String> createInviteCode() async {
    if (_currentUser?.isMaster != true) throw 'Chỉ Master mới có quyền tạo mã mời!';

    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    final random = Random();
    final code = List.generate(6, (_) => chars[random.nextInt(chars.length)]).join();

    final now = DateTime.now().millisecondsSinceEpoch;
    await http.put(
      Uri.parse('$baseUrl/invites/$code.json?auth=$_idToken'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'code': code,
        'createdAt': now,
        'createdBy': _currentUser!.email,
        'status': 'unused',
      }),
    );
    return code;
  }

  Future<List<InviteCode>> getInvites() async {
    try {
      final res = await http.get(Uri.parse('$baseUrl/invites.json?auth=$_idToken'));
      if (res.statusCode == 200 && res.body != 'null') {
        final data = jsonDecode(res.body) as Map;
        return data.entries.map((e) => InviteCode.fromJson(e.key.toString(), e.value as Map)).toList()
          ..sort((a, b) => b.createdAt.compareTo(a.createdAt));
      }
    } catch (_) {}
    return [];
  }

  Future<List<UserProfile>> getMembers() async {
    try {
      final res = await http.get(Uri.parse('$baseUrl/users.json?auth=$_idToken'));
      if (res.statusCode == 200 && res.body != 'null') {
        final data = jsonDecode(res.body) as Map;
        return data.entries
            .map((e) => UserProfile.fromJson(e.key.toString(), e.value as Map))
            .where((u) => u.pairedHome && u.email.toLowerCase() != masterEmail.toLowerCase())
            .toList();
      }
    } catch (_) {}
    return [];
  }

  Future<void> updateMemberPermission(String uid, String deviceKey, bool allowed) async {
    if (_currentUser?.isMaster != true) throw 'Chỉ Master mới có quyền đổi phân quyền!';
    await http.patch(
      Uri.parse('$baseUrl/users/$uid/permissions.json?auth=$_idToken'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        deviceKey: allowed,
      }),
    );
  }

  Future<void> deleteMember(String uid) async {
    if (_currentUser?.isMaster != true) throw 'Chỉ Master mới có quyền xóa thành viên!';
    await http.patch(
      Uri.parse('$baseUrl/users/$uid.json?auth=$_idToken'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'pairedHome': false,
      }),
    );
  }

  // ── PRIVATE HELPERS ───────────────────────────────────────

  Future<void> _writeOperationLog(String device, String action, String user, int ts) async {
    try {
      final dateStr = DateTime.fromMillisecondsSinceEpoch(ts).toString().substring(11, 19);
      final logNode = (device == 'door') ? 'door' : 'web';
      await http.post(
        Uri.parse('$baseUrl/logs/$logNode.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'device': device,
          'action': action,
          'user': user,
          'src': 'app',
          'ts': ts,
          'time': dateStr,
        }),
      );
    } catch (_) {}
  }

  Future<bool> _validateInviteCode(String code) async {
    try {
      final res = await http.get(Uri.parse('$baseUrl/invites/$code.json'));
      if (res.statusCode == 200 && res.body != 'null') {
        final data = jsonDecode(res.body) as Map;
        return data['status'] == 'unused';
      }
    } catch (_) {}
    return false;
  }

  Future<UserProfile> _fetchOrCreateProfile(String uid, String email, String displayName) async {
    final cleanEmail = email.trim();
    final isMaster = cleanEmail.toLowerCase() == masterEmail;

    try {
      final res = await http.get(Uri.parse('$baseUrl/users/$uid.json?auth=$_idToken'));
      if (res.statusCode == 200 && res.body != 'null') {
        final data = jsonDecode(res.body) as Map;
        return UserProfile.fromJson(uid, data);
      }
    } catch (_) {}

    final profile = UserProfile(
      uid: uid,
      email: cleanEmail,
      displayName: displayName.isNotEmpty ? displayName : cleanEmail.split('@').first,
      role: isMaster ? 'master' : 'member',
    );

    try {
      await http.put(
        Uri.parse('$baseUrl/users/$uid.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'email': cleanEmail,
          'displayName': profile.displayName,
          'role': profile.role,
          'permissions': profile.permissions,
          'pairedHome': true,
          'registeredAt': DateTime.now().millisecondsSinceEpoch,
        }),
      );
    } catch (_) {}

    return profile;
  }

  void _pollRealtimeData() async {
    // 1. Trạng thái thiết bị & Khóa an ninh bàn phím
    try {
      final res = await http.get(Uri.parse('$baseUrl/devices/state.json?auth=$_idToken'));
      final doorLockRes = await http.get(Uri.parse('$baseUrl/door/keypad_locked.json?auth=$_idToken'));
      
      bool isLocked = false;
      if (doorLockRes.statusCode == 200 && doorLockRes.body == 'true') {
        isLocked = true;
      }

      if (res.statusCode == 200 && res.body != 'null') {
        final data = jsonDecode(res.body) as Map;
        if (data['keypad_locked'] == true) isLocked = true;
        _lastState = DeviceState.fromJson(data).copyWith(keypadLocked: isLocked);
        _stateCtrl.add(_lastState);
      } else if (isLocked != _lastState.keypadLocked) {
        _lastState = _lastState.copyWith(keypadLocked: isLocked);
        _stateCtrl.add(_lastState);
      }
    } catch (_) {}

    // 2. Dữ liệu cảm biến mới nhất
    try {
      final res = await http.get(Uri.parse('$baseUrl/sensors/latest.json?auth=$_idToken'));
      if (res.statusCode == 200 && res.body != 'null') {
        final data = jsonDecode(res.body) as Map;
        _lastSensor = SensorData.fromJson(data);
        _sensorCtrl.add(_lastSensor);
      }
    } catch (_) {}

    // 3. Lịch sử thao tác tổng hợp (web, door/keypad, voice, auto)
    try {
      final allLogs = <LogEntry>[];
      final nodes = ['logs/web', 'logs/door', 'logs/voice', 'logs/auto'];

      for (final node in nodes) {
        try {
          final res = await http.get(Uri.parse('$baseUrl/$node.json?orderBy=%22%24key%22&limitToLast=20&auth=$_idToken'));
          if (res.statusCode == 200 && res.body != 'null') {
            final data = jsonDecode(res.body) as Map;
            data.forEach((k, v) {
              if (v is Map) {
                allLogs.add(LogEntry.fromJson(k.toString(), v));
              }
            });
          }
        } catch (_) {}
      }

      allLogs.sort((a, b) => b.timestamp.compareTo(a.timestamp));
      final finalLogs = allLogs.take(50).toList();
      _logsCtrl.add(finalLogs);
    } catch (_) {}
  }

  String _parseAuthError(String? code) {
    switch (code) {
      case 'EMAIL_EXISTS':
        return 'Email này đã được đăng ký tài khoản!';
      case 'OPERATION_NOT_ALLOWED':
        return 'Phương thức đăng nhập này chưa được kích hoạt.';
      case 'TOO_MANY_ATTEMPTS_TRY_LATER':
        return 'Bạn đã thử quá nhiều lần. Vui lòng thử lại sau ít phút.';
      case 'EMAIL_NOT_FOUND':
        return 'Email không tồn tại trong hệ thống!';
      case 'INVALID_PASSWORD':
      case 'INVALID_LOGIN_CREDENTIALS':
        return 'Mật khẩu hoặc email không chính xác!';
      case 'USER_DISABLED':
        return 'Tài khoản này đã bị khóa.';
      case 'WEAK_PASSWORD : Password should be at least 6 characters':
        return 'Mật khẩu phải có tối thiểu 6 ký tự!';
      default:
        return 'Đã xảy ra lỗi: ${code ?? 'Vui lòng kiểm tra kết nối mạng'}';
    }
  }
}
