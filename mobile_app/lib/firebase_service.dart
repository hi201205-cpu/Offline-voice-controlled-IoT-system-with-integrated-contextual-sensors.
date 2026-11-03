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

  // -- Session ------------------------------------------------
  String? _idToken;
  String? _refreshToken;
  UserProfile? _currentUser;

  UserProfile? get currentUser => _currentUser;
  bool get isLoggedIn => _currentUser != null;

  // -- Streams ------------------------------------------------
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

  // -- Khởi động lắng nghe Realtime Database -------------------
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

  // -- FIREBASE AUTHENTICATION (REST API) ----------------------

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
      final codeValid = await _validateInviteCode(inviteCode.trim().toUpperCase());
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

    final now = DateTime.now().millisecondsSinceEpoch;
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
      },
      pairedHome: true,
    );

    // Lưu vào RTDB /users/$uid đồng bộ chuẩn Web & Security Rules
    await http.put(
      Uri.parse('$baseUrl/users/$uid.json?auth=$_idToken'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'email': profile.email,
        'displayName': profile.displayName,
        'name': profile.displayName,
        'role': profile.role,
        'permissions': profile.permissions,
        'pairedHome': 'HOME-2026-ESP32',
        'pairedAt': now,
        'registeredAt': now,
        'inviteCode': inviteCode?.trim().toUpperCase(),
      }),
    );

    if (!isMaster && inviteCode != null) {
      final code = inviteCode.trim().toUpperCase();
      await http.patch(
        Uri.parse('$baseUrl/invites/$code.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'status': 'used',
          'usedBy': uid,
          'usedByEmail': cleanEmail,
          'usedAt': now,
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

  /// 4. Đổi mật khẩu
  Future<void> changePassword({
    required String oldPassword,
    required String newPassword,
  }) async {
    if (_currentUser == null) throw 'Bạn chưa đăng nhập!';

    final verifyUrl = Uri.parse('https://identitytoolkit.googleapis.com/v1/accounts:signInWithPassword?key=$apiKey');
    final verifyRes = await http.post(
      verifyUrl,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'email': _currentUser!.email,
        'password': oldPassword,
        'returnSecureToken': true,
      }),
    ).timeout(const Duration(seconds: 10));

    final verifyData = jsonDecode(verifyRes.body);
    if (verifyRes.statusCode != 200) {
      throw 'Mật khẩu hiện tại không đúng!';
    }

    final newIdToken = verifyData['idToken'];

    final updateUrl = Uri.parse('https://identitytoolkit.googleapis.com/v1/accounts:update?key=$apiKey');
    final updateRes = await http.post(
      updateUrl,
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'idToken': newIdToken,
        'password': newPassword,
        'returnSecureToken': true,
      }),
    ).timeout(const Duration(seconds: 10));

    final updateData = jsonDecode(updateRes.body);
    if (updateRes.statusCode != 200) {
      throw _parseAuthError(updateData['error']?['message']);
    }

    _idToken = updateData['idToken'];
    _refreshToken = updateData['refreshToken'];
  }

  /// 5. Cập nhật hồ sơ cá nhân
  Future<void> updateProfile({
    required String name,
    required String phone,
    required String dob,
    required String gender,
    required String address,
  }) async {
    if (_currentUser == null || _idToken == null) throw 'Bạn chưa đăng nhập!';

    final cleanName = name.trim();
    if (cleanName.isNotEmpty) {
      try {
        await http.post(
          Uri.parse('https://identitytoolkit.googleapis.com/v1/accounts:update?key=$apiKey'),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode({
            'idToken': _idToken,
            'displayName': cleanName,
            'returnSecureToken': true,
          }),
        );
      } catch (_) {}
    }

    final nowStr = DateTime.now().toIso8601String();
    final profilePayload = {
      'phone': phone.trim(),
      'dob': dob.trim(),
      'gender': gender.trim(),
      'address': address.trim(),
      'updatedAt': nowStr,
    };

    await http.patch(
      Uri.parse('$baseUrl/users/${_currentUser!.uid}/profile.json?auth=$_idToken'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode(profilePayload),
    );

    await http.patch(
      Uri.parse('$baseUrl/users/${_currentUser!.uid}.json?auth=$_idToken'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'displayName': cleanName,
        'name': cleanName,
      }),
    );

    await refreshCurrentUser();
  }

  /// 6. Đăng xuất
  void signOut() {
    stopListening();
    _idToken = null;
    _refreshToken = null;
    _currentUser = null;
    _profileCtrl.add(null);
  }

  /// 7. Làm mới thông tin người dùng từ Realtime Database
  Future<void> refreshCurrentUser() async {
    if (_currentUser == null || _idToken == null) return;
    try {
      final res = await http.get(Uri.parse('$baseUrl/users/${_currentUser!.uid}.json?auth=$_idToken'));
      if (res.statusCode == 200 && res.body != 'null') {
        final data = jsonDecode(res.body) as Map;
        _currentUser = UserProfile.fromJson(_currentUser!.uid, data);
        _profileCtrl.add(_currentUser);
      }
    } catch (_) {}
  }

  // -- ĐIỀU KHIỂN THIẾT BỊ (REST API + REALTIME LOGS) ----------

  Future<bool> setDevice(String deviceKey, bool value) async {
    final userEmail = _currentUser?.email ?? 'Mobile App';
    final now = DateTime.now().millisecondsSinceEpoch;

    try {
      final cmdRes = await http.put(
        Uri.parse('$baseUrl/commands/$deviceKey.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'value': value, 'ts': now, 'src': 'app', 'user': userEmail}),
      );
      if (cmdRes.statusCode != 200) return false;

      final stateRes = await http.patch(
        Uri.parse('$baseUrl/devices/state.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({deviceKey: value}),
      );
      if (stateRes.statusCode != 200) return false;

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
      final cmdRes = await http.put(
        Uri.parse('$baseUrl/commands/curtain.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'action': action, 'ts': now, 'src': 'app', 'user': userEmail}),
      );
      if (cmdRes.statusCode != 200) return false;

      final stateRes = await http.patch(
        Uri.parse('$baseUrl/devices/state.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'curtain': action}),
      );
      if (stateRes.statusCode != 200) return false;

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
      final cmdRes = await http.put(
        Uri.parse('$baseUrl/commands/door.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'action': 'open', 'ts': now, 'src': 'app', 'user': userEmail}),
      );
      if (cmdRes.statusCode != 200) return false;

      await http.patch(
        Uri.parse('$baseUrl/devices/state.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'door': true}),
      );

      await _writeOperationLog('door', 'open', userEmail, now);
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<bool> unlockKeypad() async {
    final userEmail = _currentUser?.email ?? 'Mobile App';
    final now = DateTime.now().millisecondsSinceEpoch;

    try {
      await http.put(
        Uri.parse('$baseUrl/door/keypad_locked.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: 'false',
      );

      await http.patch(
        Uri.parse('$baseUrl/devices/state.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({'keypad_locked': false}),
      );

      await _writeOperationLog('keypad', 'unlock', userEmail, now);
      return true;
    } catch (_) {
      return false;
    }
  }

  Future<bool> allOn() async {
    final userEmail = _currentUser?.email ?? 'Mobile App';
    final now = DateTime.now().millisecondsSinceEpoch;
    final isMaster = _currentUser?.isMaster ?? false;

    try {
      final payload = {'value': true, 'ts': now, 'src': 'app', 'user': userEmail};
      final patchState = <String, dynamic>{};

      if (isMaster || _currentUser?.hasPermission('light') == true) {
        await http.put(Uri.parse('$baseUrl/commands/light.json?auth=$_idToken'), body: jsonEncode(payload));
        patchState['light'] = true;
      }
      if (isMaster || _currentUser?.hasPermission('fan') == true) {
        await http.put(Uri.parse('$baseUrl/commands/fan.json?auth=$_idToken'), body: jsonEncode(payload));
        patchState['fan'] = true;
      }
      if (isMaster || _currentUser?.hasPermission('yard') == true) {
        await http.put(Uri.parse('$baseUrl/commands/yard.json?auth=$_idToken'), body: jsonEncode(payload));
        patchState['yard'] = true;
      }

      if (patchState.isNotEmpty) {
        await http.patch(
          Uri.parse('$baseUrl/devices/state.json?auth=$_idToken'),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode(patchState),
        );
        await _writeOperationLog('all', 'on', userEmail, now);
        return true;
      }
      return false;
    } catch (_) {
      return false;
    }
  }

  Future<bool> allOff() async {
    final userEmail = _currentUser?.email ?? 'Mobile App';
    final now = DateTime.now().millisecondsSinceEpoch;
    final isMaster = _currentUser?.isMaster ?? false;

    try {
      final payload = {'value': false, 'ts': now, 'src': 'app', 'user': userEmail};
      final patchState = <String, dynamic>{};

      if (isMaster || _currentUser?.hasPermission('light') == true) {
        await http.put(Uri.parse('$baseUrl/commands/light.json?auth=$_idToken'), body: jsonEncode(payload));
        patchState['light'] = false;
      }
      if (isMaster || _currentUser?.hasPermission('fan') == true) {
        await http.put(Uri.parse('$baseUrl/commands/fan.json?auth=$_idToken'), body: jsonEncode(payload));
        patchState['fan'] = false;
      }
      if (isMaster || _currentUser?.hasPermission('yard') == true) {
        await http.put(Uri.parse('$baseUrl/commands/yard.json?auth=$_idToken'), body: jsonEncode(payload));
        patchState['yard'] = false;
      }

      if (patchState.isNotEmpty) {
        await http.patch(
          Uri.parse('$baseUrl/devices/state.json?auth=$_idToken'),
          headers: {'Content-Type': 'application/json'},
          body: jsonEncode(patchState),
        );
        await _writeOperationLog('all', 'off', userEmail, now);
        return true;
      }
      return false;
    } catch (_) {
      return false;
    }
  }

  Future<bool> setAutomation(bool enabled) async {
    try {
      final res = await http.put(
        Uri.parse('$baseUrl/automation/enabled.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(enabled),
      );
      return res.statusCode == 200;
    } catch (_) {
      return false;
    }
  }

  Future<bool> setGardenMode(String mode) async {
    try {
      final res = await http.put(
        Uri.parse('$baseUrl/devices/garden/mode.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode(mode),
      );
      return res.statusCode == 200;
    } catch (_) {
      return false;
    }
  }

  Future<List<SensorPoint>> fetchSensorHistory({String range = '24h'}) async {
    try {
      final res = await http.get(Uri.parse('$baseUrl/sensors/history.json?orderBy=%22%24key%22&limitToLast=100&auth=$_idToken'));
      if (res.statusCode == 200 && res.body != 'null') {
        final data = jsonDecode(res.body) as Map;
        final points = <SensorPoint>[];

        data.forEach((key, val) {
          if (val is Map) {
            final t = DeviceState.parseDouble(val['temp'] ?? val['temperature']);
            final h = DeviceState.parseDouble(val['hum'] ?? val['humidity']);
            DateTime time = DateTime.now();

            final ts = val['ts'] ?? val['timestamp'];
            if (ts != null) {
              final ms = DeviceState.parseInt(ts);
              time = DateTime.fromMillisecondsSinceEpoch(ms > 1000000000000 ? ms : ms * 1000);
            } else {
              final keyNum = int.tryParse(key);
              if (keyNum != null) {
                time = DateTime.fromMillisecondsSinceEpoch(keyNum > 1000000000000 ? keyNum : keyNum * 1000);
              }
            }

            if (t > 0 || h > 0) {
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

  // -- MASTER: QUẢN LÝ MÃ MỜI & THÀNH VIÊN --------------------

  Future<String> createInviteCode() async {
    if (_currentUser?.isMaster != true) throw 'Chỉ Master mới có quyền tạo mã mời!';

    const chars = 'ABCDEFGHJKLMNPQRSTUVWXYZ23456789';
    final random = Random();
    final code = List.generate(6, (_) => chars[random.nextInt(chars.length)]).join();

    final now = DateTime.now().millisecondsSinceEpoch;
    final expiresAt = now + 24 * 3600 * 1000; // 24 giờ
    await http.put(
      Uri.parse('$baseUrl/invites/$code.json?auth=$_idToken'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'code': code,
        'createdAt': now,
        'expiresAt': expiresAt,
        'createdBy': _currentUser!.uid,
        'creatorEmail': _currentUser!.email,
        'status': 'active', // Đồng bộ chuẩn Web & Security Rules
        'homeId': 'HOME-2026-ESP32',
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
    if (_currentUser?.isMaster != true) throw 'Chỉ Master mới có quyền phân quyền!';
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

  // -- THÀNH VIÊN: TỰ HỦY LIÊN KẾT & LIÊN KẾT BẰNG MÃ MỜI -----

  Future<void> unlinkSelf() async {
    if (_currentUser == null || _idToken == null) return;
    if (_currentUser!.isMaster) throw 'Chủ nhà (Master) không thể hủy liên kết!';

    await http.patch(
      Uri.parse('$baseUrl/users/${_currentUser!.uid}.json?auth=$_idToken'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'pairedHome': false,
      }),
    );

    await refreshCurrentUser();
  }

  Future<bool> pairWithInviteCode(String inputCode) async {
    if (_currentUser == null || _idToken == null) throw 'Bạn chưa đăng nhập!';
    final code = inputCode.trim().toUpperCase();
    if (code.isEmpty) throw 'Vui lòng nhập mã mời!';

    final inviteRes = await http.get(Uri.parse('$baseUrl/invites/$code.json?auth=$_idToken'));
    if (inviteRes.statusCode != 200 || inviteRes.body == 'null') {
      throw 'Mã mời không tồn tại hoặc đã hết hạn!';
    }
    final invData = jsonDecode(inviteRes.body) as Map;
    if (invData['status'] != 'active') {
      throw 'Mã mời này đã được sử dụng trước đó!';
    }

    final now = DateTime.now().millisecondsSinceEpoch;
    await http.patch(
      Uri.parse('$baseUrl/invites/$code.json?auth=$_idToken'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'status': 'used',
        'usedBy': _currentUser!.uid,
        'usedByEmail': _currentUser!.email,
        'usedAt': now,
      }),
    );

    await http.patch(
      Uri.parse('$baseUrl/users/${_currentUser!.uid}.json?auth=$_idToken'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode({
        'pairedHome': 'HOME-2026-ESP32',
        'inviteCode': code,
        'pairedAt': now,
      }),
    );

    await refreshCurrentUser();
    return true;
  }

  // -- MẬT KHẨU KEYPAD CỬA CHÍNH (ĐỒNG BỘ WEB) ---------------

  Future<List<PasscodeItem>> getPasscodes() async {
    try {
      final passRes = await http.get(Uri.parse('$baseUrl/door/passcodes.json?auth=$_idToken'));
      final ownRes = await http.get(Uri.parse('$baseUrl/door/passcodeOwners.json?auth=$_idToken'));

      Map passcodes = {};
      Map owners = {};
      if (passRes.statusCode == 200 && passRes.body != 'null') passcodes = jsonDecode(passRes.body) as Map;
      if (ownRes.statusCode == 200 && ownRes.body != 'null') owners = jsonDecode(ownRes.body) as Map;

      final list = <PasscodeItem>[];
      passcodes.forEach((k, v) {
        if (v is Map) {
          final owner = owners[k] is Map ? owners[k] as Map : null;
          list.add(PasscodeItem.fromJson(k.toString(), v, owner));
        }
      });
      return list;
    } catch (_) {
      return [];
    }
  }

  Future<bool> addPasscode(String code, String name) async {
    if (_currentUser == null) return false;
    final isMaster = _currentUser!.isMaster;
    if (!isMaster && _currentUser!.hasPermission('passcodeAdd') != true) {
      throw 'Chủ nhà chưa cấp quyền thêm mật khẩu Keypad cho bạn!';
    }
    final now = DateTime.now().millisecondsSinceEpoch;
    final opId = 'op_$now';

    final creatorName = _currentUser!.displayName.isNotEmpty
        ? _currentUser!.displayName
        : (isMaster ? 'Chủ nhà' : (_currentUser!.email.split('@').first));

    final payload = {
      'op': 'add',
      'code': code.trim(),
      'name': name.trim(),
      'owner': {
        'uid': _currentUser!.uid,
        'email': _currentUser!.email,
        'name': creatorName,
        'ts': now,
      }
    };

    final res = await http.put(
      Uri.parse('$baseUrl/door/pending/$opId.json?auth=$_idToken'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode(payload),
    );
    return res.statusCode == 200;
  }

  Future<bool> deletePasscode(PasscodeItem item) async {
    if (_currentUser == null) return false;
    final isMaster = _currentUser!.isMaster;
    final isOwner = item.isCreatedBy(_currentUser!.uid, _currentUser!.email);
    if (!isMaster && !isOwner) {
      throw 'Bạn chỉ có quyền xóa mật khẩu do chính mình tạo ra!';
    }

    final now = DateTime.now().millisecondsSinceEpoch;
    final opId = 'del_$now';
    final payload = {
      'op': 'delete',
      'target': item.id,
    };

    final res = await http.put(
      Uri.parse('$baseUrl/door/pending/$opId.json?auth=$_idToken'),
      headers: {'Content-Type': 'application/json'},
      body: jsonEncode(payload),
    );
    return res.statusCode == 200;
  }

  // -- PRIVATE HELPERS ---------------------------------------

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
        return data['status'] == 'active' || data['status'] == 'unused';
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

    final now = DateTime.now().millisecondsSinceEpoch;
    final profile = UserProfile(
      uid: uid,
      email: cleanEmail,
      displayName: displayName.isNotEmpty ? displayName : cleanEmail.split('@').first,
      role: isMaster ? 'master' : 'member',
      pairedHome: isMaster,
    );

    try {
      await http.put(
        Uri.parse('$baseUrl/users/$uid.json?auth=$_idToken'),
        headers: {'Content-Type': 'application/json'},
        body: jsonEncode({
          'email': cleanEmail,
          'displayName': profile.displayName,
          'name': profile.displayName,
          'role': profile.role,
          'permissions': profile.permissions,
          'pairedHome': isMaster ? 'HOME-2026-ESP32' : false,
          'registeredAt': now,
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

    // 4. Đồng bộ quyền và trạng thái liên kết người dùng hiện tại theo Realtime
    if (_currentUser != null && _idToken != null) {
      try {
        final uRes = await http.get(Uri.parse('$baseUrl/users/${_currentUser!.uid}.json?auth=$_idToken'));
        if (uRes.statusCode == 200 && uRes.body != 'null') {
          final uData = jsonDecode(uRes.body) as Map;
          final updated = UserProfile.fromJson(_currentUser!.uid, uData);
          if (updated.pairedHome != _currentUser!.pairedHome ||
              updated.permissions.toString() != _currentUser!.permissions.toString() ||
              updated.role != _currentUser!.role) {
            _currentUser = updated;
            _profileCtrl.add(_currentUser);
          }
        }
      } catch (_) {}
    }
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
