import 'dart:async';
import 'dart:math';
import 'package:flutter/material.dart';
import 'package:flutter/services.dart';
import 'package:intl/intl.dart';
import 'models.dart';
import 'firebase_service.dart';

class HomeScreen extends StatefulWidget {
  final VoidCallback onSignOut;
  const HomeScreen({super.key, required this.onSignOut});

  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _service = FirebaseService();
  int _currentTabIndex = 0;

  DeviceState _state = const DeviceState();
  SensorData _sensor = SensorData(updatedAt: DateTime.now());
  List<LogEntry> _logs = [];
  List<UserProfile> _members = [];
  String? _busyDevice;
  bool _previousKeypadLocked = false;

  // Stream subscriptions
  late StreamSubscription<DeviceState> _stateSub;
  late StreamSubscription<SensorData> _sensorSub;
  late StreamSubscription<List<LogEntry>> _logsSub;
  late StreamSubscription<UserProfile?> _profileSub;

  // Profile controllers
  final _nameCtrl = TextEditingController();
  final _phoneCtrl = TextEditingController();
  final _dobCtrl = TextEditingController();
  final _addressCtrl = TextEditingController();
  String _selectedGender = 'Nam';
  bool _isSavingProfile = false;

  // Change password controllers
  final _oldPassCtrl = TextEditingController();
  final _newPassCtrl = TextEditingController();
  final _confirmNewPassCtrl = TextEditingController();
  bool _obscureOld = true;
  bool _obscureNew = true;
  bool _obscureConfirm = true;
  bool _isChangingPassword = false;

  // Online indicator: lastSeen < 45 giây trước
  bool get _isOnline {
    if (_state.lastSeen == 0) return false;
    final diff = DateTime.now().millisecondsSinceEpoch - _state.lastSeen;
    return diff < 45000;
  }

  @override
  void initState() {
    super.initState();
    _initProfileControllers();

    _stateSub = _service.deviceStateStream.listen((s) {
      if (!mounted) return;
      // Cảnh báo khẩn cấp khi phát hiện trạng thái vừa bị khóa an ninh do nhập sai 4 lần
      if (s.keypadLocked && !_previousKeypadLocked) {
        _showSecurityLockoutDialog();
      }
      _previousKeypadLocked = s.keypadLocked;
      setState(() => _state = s);
    });

    _sensorSub = _service.sensorDataStream.listen((s) {
      if (mounted) setState(() => _sensor = s);
    });

    _logsSub = _service.logsStream.listen((l) {
      if (mounted) setState(() => _logs = l);
    });

    _profileSub = _service.profileStream.listen((u) {
      if (mounted) {
        _initProfileControllers();
        setState(() {});
      }
    });

    _service.startListening();
    _loadMembers();
  }

  void _initProfileControllers() {
    final user = _service.currentUser;
    if (user != null) {
      _nameCtrl.text = user.displayName;
      _phoneCtrl.text = user.phone;
      _dobCtrl.text = user.dob;
      _addressCtrl.text = user.address;
      if (user.gender.isNotEmpty) {
        _selectedGender = user.gender;
      }
    }
  }

  Future<void> _loadMembers() async {
    if (_service.currentUser?.isMaster == true) {
      final list = await _service.getMembers();
      if (mounted) setState(() => _members = list);
    }
  }

  @override
  void dispose() {
    _stateSub.cancel();
    _sensorSub.cancel();
    _logsSub.cancel();
    _profileSub.cancel();
    _nameCtrl.dispose();
    _phoneCtrl.dispose();
    _dobCtrl.dispose();
    _addressCtrl.dispose();
    _oldPassCtrl.dispose();
    _newPassCtrl.dispose();
    _confirmNewPassCtrl.dispose();
    super.dispose();
  }

  // ── KIỂM TRA PHÂN QUYỀN ──────────────────────────────────
  bool _canControl(String deviceKey) {
    return _service.currentUser?.hasPermission(deviceKey) ?? false;
  }

  // ── CẢNH BÁO KHÓA CỨNG AN NINH KHI NHẬP SAI 4 LẦN ─────────
  void _showSecurityLockoutDialog() {
    showDialog(
      context: context,
      barrierDismissible: false,
      builder: (ctx) => AlertDialog(
        icon: const Icon(Icons.gpp_bad_rounded, color: Colors.red, size: 54),
        title: const Text('CẢNH BÁO BẢO VỆ AN NINH', style: TextStyle(fontWeight: FontWeight.bold, color: Colors.red)),
        content: const Text(
          'Đã phát hiện nhập sai mật khẩu 4 lần liên tiếp tại cửa chính!\n\n'
          'Bàn phím ngoài đã bị vô hiệu hóa an ninh để ngăn chặn kẻ gian dò mã.\n'
          'Nút mở cửa đã tạm khóa an toàn.',
          style: TextStyle(fontSize: 14, height: 1.4),
        ),
        actions: [
          OutlinedButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Đã hiểu'),
          ),
          FilledButton.icon(
            style: FilledButton.styleFrom(backgroundColor: Colors.red),
            icon: const Icon(Icons.lock_open_rounded),
            label: const Text('Mở khóa an ninh ngay'),
            onPressed: () async {
              Navigator.pop(ctx);
              await _handleUnlockKeypad();
            },
          ),
        ],
      ),
    );
  }

  Future<void> _handleUnlockKeypad() async {
    final ok = await _service.unlockKeypad();
    if (mounted) {
      if (ok) {
        _showSnack('Đã mở khóa an ninh bàn phím thành công!');
      } else {
        _showSnack('Không thể mở khóa, vui lòng thử lại', isError: true);
      }
    }
  }

  // ── ĐIỀU KHIỂN THIẾT BỊ ──────────────────────────────────
  Future<void> _toggleDevice(String key, bool currentVal) async {
    if (!_canControl(key)) {
      _showSnack('Bạn không có quyền điều khiển thiết bị này!', isError: true);
      return;
    }
    if (_busyDevice != null) return;

    setState(() => _busyDevice = key);
    final ok = await _service.setDevice(key, !currentVal);
    if (mounted) {
      setState(() => _busyDevice = null);
      if (!ok) _showSnack('Không gửi được lệnh, vui lòng thử lại', isError: true);
    }
  }

  Future<void> _toggleCurtain() async {
    if (!_canControl('curtain')) {
      _showSnack('Bạn không có quyền điều khiển rèm cửa!', isError: true);
      return;
    }
    if (_busyDevice != null) return;

    final nextAction = _state.isCurtainOpen ? 'close' : 'open';
    setState(() => _busyDevice = 'curtain');
    final ok = await _service.setCurtain(nextAction);
    if (mounted) {
      setState(() => _busyDevice = null);
      if (!ok) _showSnack('Không điều khiển được rèm cửa', isError: true);
    }
  }

  Future<void> _openDoor() async {
    if (!_canControl('door')) {
      _showSnack('Bạn không có quyền mở cửa!', isError: true);
      return;
    }
    // Nếu bàn phím đang bị khóa an ninh do nhập sai 4 lần -> Khóa không cho bấm
    if (_state.keypadLocked) {
      _showSnack('Cửa đang bị khóa an ninh do nhập sai 4 lần liên tiếp! Vui lòng mở khóa an ninh trước.', isError: true);
      return;
    }
    if (_busyDevice != null) return;

    setState(() => _busyDevice = 'door');
    final ok = await _service.openDoor();
    if (mounted) {
      setState(() => _busyDevice = null);
      if (ok) {
        _showSnack('Đã gửi lệnh mở cửa (cửa sẽ tự đóng sau 5 giây)');
      } else {
        _showSnack('Không gửi được lệnh mở cửa', isError: true);
      }
    }
  }

  Future<void> _allOn() async {
    setState(() => _busyDevice = 'all');
    final ok = await _service.allOn();
    if (mounted) {
      setState(() => _busyDevice = null);
      if (!ok) _showSnack('Không gửi được lệnh bật tất cả', isError: true);
    }
  }

  Future<void> _allOff() async {
    setState(() => _busyDevice = 'all');
    final ok = await _service.allOff();
    if (mounted) {
      setState(() => _busyDevice = null);
      if (!ok) _showSnack('Không gửi được lệnh tắt tất cả', isError: true);
    }
  }

  // ── LƯU HỒ SƠ CÁ NHÂN ─────────────────────────────────────
  Future<void> _handleSaveProfile() async {
    final name = _nameCtrl.text.trim();
    if (name.isEmpty) {
      _showSnack('Họ và tên không được để trống!', isError: true);
      return;
    }

    setState(() => _isSavingProfile = true);
    try {
      await _service.updateProfile(
        displayName: name,
        phone: _phoneCtrl.text.trim(),
        dob: _dobCtrl.text.trim(),
        gender: _selectedGender,
        address: _addressCtrl.text.trim(),
      );
      if (mounted) _showSnack('Đã cập nhật thông tin hồ sơ thành công!');
    } catch (e) {
      if (mounted) _showSnack(e.toString(), isError: true);
    } finally {
      if (mounted) setState(() => _isSavingProfile = false);
    }
  }

  // ── ĐỔI MẬT KHẨU ─────────────────────────────────────────
  Future<void> _handleChangePassword() async {
    final oldPass = _oldPassCtrl.text;
    final newPass = _newPassCtrl.text;
    final confirm = _confirmNewPassCtrl.text;

    if (oldPass.isEmpty || newPass.isEmpty || confirm.isEmpty) {
      _showSnack('Vui lòng điền đầy đủ cả 3 ô mật khẩu!', isError: true);
      return;
    }
    if (newPass.length < 6) {
      _showSnack('Mật khẩu mới phải có ít nhất 6 ký tự!', isError: true);
      return;
    }
    if (newPass != confirm) {
      _showSnack('Xác nhận mật khẩu mới không khớp!', isError: true);
      return;
    }
    if (oldPass == newPass) {
      _showSnack('Mật khẩu mới phải khác mật khẩu hiện tại!', isError: true);
      return;
    }

    setState(() => _isChangingPassword = true);
    try {
      await _service.changePassword(oldPassword: oldPass, newPassword: newPass);
      _oldPassCtrl.clear();
      _newPassCtrl.clear();
      _confirmNewPassCtrl.clear();

      if (mounted) {
        showDialog(
          context: context,
          builder: (ctx) => AlertDialog(
            icon: const Icon(Icons.check_circle_rounded, color: Colors.green, size: 48),
            title: const Text('Đổi mật khẩu thành công!'),
            content: const Text('Mật khẩu mới của bạn đã có hiệu lực ngay bây giờ.'),
            actions: [
              FilledButton(onPressed: () => Navigator.pop(ctx), child: const Text('Hoàn tất')),
            ],
          ),
        );
      }
    } catch (e) {
      if (mounted) _showSnack(e.toString(), isError: true);
    } finally {
      if (mounted) setState(() => _isChangingPassword = false);
    }
  }

  // ── MASTER TẠO MÃ MỜI ────────────────────────────────────
  Future<void> _handleCreateInviteCode() async {
    try {
      final code = await _service.createInviteCode();
      if (mounted) {
        showDialog(
          context: context,
          builder: (ctx) => AlertDialog(
            title: const Text('Mã mời thành viên mới'),
            content: Column(
              mainAxisSize: MainAxisSize.min,
              children: [
                const Text('Mã mời 1 lần vừa được tạo thành công:'),
                const SizedBox(height: 12),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 20, vertical: 12),
                  decoration: BoxDecoration(
                    color: Colors.blue.withAlpha(30),
                    borderRadius: BorderRadius.circular(12),
                    border: Border.all(color: Colors.blue),
                  ),
                  child: SelectableText(
                    code,
                    style: const TextStyle(fontSize: 26, fontWeight: FontWeight.bold, letterSpacing: 4, color: Colors.blue),
                  ),
                ),
                const SizedBox(height: 12),
                const Text(
                  'Gửi mã này cho thành viên gia đình để liên kết tài khoản.',
                  style: TextStyle(fontSize: 12.5, color: Colors.grey),
                  textAlign: TextAlign.center,
                ),
              ],
            ),
            actions: [
              TextButton.icon(
                icon: const Icon(Icons.copy_rounded),
                label: const Text('Sao chép'),
                onPressed: () {
                  Clipboard.setData(ClipboardData(text: code));
                  Navigator.pop(ctx);
                  _showSnack('Đã sao chép mã mời: $code');
                },
              ),
              FilledButton(onPressed: () => Navigator.pop(ctx), child: const Text('Đóng')),
            ],
          ),
        );
      }
    } catch (e) {
      if (mounted) _showSnack(e.toString(), isError: true);
    }
  }

  void _showSnack(String msg, {bool isError = false}) {
    ScaffoldMessenger.of(context).showSnackBar(
      SnackBar(
        content: Text(msg),
        backgroundColor: isError ? Colors.red[700] : const Color(0xFF00B4D8),
        behavior: SnackBarBehavior.floating,
      ),
    );
  }

  // ── MODAL BIỂU ĐỒ LỊCH SỬ NHIỆT ĐỘ & ĐỘ ẨM CHUẨN WEB ────────
  void _openSensorHistoryModal() {
    showModalBottomSheet(
      context: context,
      isScrollControlled: true,
      backgroundColor: Colors.transparent,
      builder: (ctx) => _SensorHistorySheet(service: _service, currentSensor: _sensor),
    );
  }

  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    final user = _service.currentUser;

    return Scaffold(
      backgroundColor: scheme.surfaceContainerLowest,
      appBar: AppBar(
        backgroundColor: scheme.surfaceContainerLowest,
        title: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            const Text('Smart Home IoT', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 18)),
            Text(
              user?.email ?? 'Chưa đăng nhập',
              style: TextStyle(fontSize: 12, color: scheme.outline),
            ),
          ],
        ),
        actions: [
          _buildOnlineBadge(),
          const SizedBox(width: 8),
          _buildRoleBadge(user),
          const SizedBox(width: 14),
        ],
      ),
      body: IndexedStack(
        index: _currentTabIndex,
        children: [
          _buildControlsTab(scheme),
          _buildSensorsTab(scheme),
          _buildHistoryTab(scheme),
          _buildSettingsTab(scheme, user),
        ],
      ),
      bottomNavigationBar: NavigationBar(
        selectedIndex: _currentTabIndex,
        onDestinationSelected: (idx) {
          setState(() => _currentTabIndex = idx);
          if (idx == 3) _loadMembers();
        },
        destinations: const [
          NavigationDestination(
            icon: Icon(Icons.dashboard_outlined),
            selectedIcon: Icon(Icons.dashboard_rounded),
            label: 'Điều khiển',
          ),
          NavigationDestination(
            icon: Icon(Icons.sensors_outlined),
            selectedIcon: Icon(Icons.sensors_rounded),
            label: 'Cảm biến',
          ),
          NavigationDestination(
            icon: Icon(Icons.history_outlined),
            selectedIcon: Icon(Icons.history_rounded),
            label: 'Lịch sử',
          ),
          NavigationDestination(
            icon: Icon(Icons.settings_outlined),
            selectedIcon: Icon(Icons.settings_rounded),
            label: 'Cài đặt',
          ),
        ],
      ),
    );
  }

  // ── BADGES (KHÔNG EMOJI) ──────────────────────────────────
  Widget _buildOnlineBadge() {
    final color = _isOnline ? Colors.green : Colors.red;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withAlpha(25),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color, width: 1),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Container(width: 6, height: 6, decoration: BoxDecoration(shape: BoxShape.circle, color: color)),
          const SizedBox(width: 5),
          Text(_isOnline ? 'Online' : 'Offline', style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: color)),
        ],
      ),
    );
  }

  Widget _buildRoleBadge(UserProfile? user) {
    final isMaster = user?.isMaster ?? false;
    final color = isMaster ? Colors.amber[800]! : const Color(0xFF00B4D8);
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withAlpha(25),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color, width: 1),
      ),
      child: Row(
        mainAxisSize: MainAxisSize.min,
        children: [
          Icon(isMaster ? Icons.shield_rounded : Icons.person_rounded, size: 12, color: color),
          const SizedBox(width: 4),
          Text(
            isMaster ? 'Master' : 'Thành viên',
            style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: color),
          ),
        ],
      ),
    );
  }

  // ── TAB 0: ĐIỀU KHIỂN (CONTROLS) ─────────────────────────
  Widget _buildControlsTab(ColorScheme scheme) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // CẢNH BÁO KHÓA CỨNG AN NINH KHI NHẬP SAI 4 LẦN
        if (_state.keypadLocked) ...[
          Container(
            padding: const EdgeInsets.all(14),
            margin: const EdgeInsets.only(bottom: 16),
            decoration: BoxDecoration(
              color: Colors.red.withAlpha(25),
              borderRadius: BorderRadius.circular(14),
              border: Border.all(color: Colors.red, width: 1.5),
            ),
            child: Row(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Icon(Icons.warning_amber_rounded, color: Colors.red, size: 28),
                const SizedBox(width: 12),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text(
                        'BÀN PHÍM NGOÀI BỊ KHÓA AN NINH',
                        style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13.5, color: Colors.red),
                      ),
                      const SizedBox(height: 3),
                      const Text(
                        'Đã nhập sai mật khẩu 4 lần liên tiếp. Bàn phím bên ngoài đã bị vô hiệu hóa an toàn.',
                        style: TextStyle(fontSize: 12, color: Colors.black87),
                      ),
                      const SizedBox(height: 10),
                      ElevatedButton.icon(
                        style: ElevatedButton.styleFrom(
                          backgroundColor: Colors.red,
                          foregroundColor: Colors.white,
                          padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 8),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(10)),
                        ),
                        onPressed: _handleUnlockKeypad,
                        icon: const Icon(Icons.lock_open_rounded, size: 16),
                        label: const Text('Mở khóa bàn phím', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12)),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ],

        // Quick Action Bar
        Row(
          children: [
            Expanded(
              child: FilledButton.tonalIcon(
                onPressed: _busyDevice != null ? null : _allOn,
                icon: const Icon(Icons.power_rounded, color: Colors.green),
                label: const Text('Bật tất cả', style: TextStyle(fontWeight: FontWeight.bold)),
              ),
            ),
            const SizedBox(width: 12),
            Expanded(
              child: FilledButton.tonalIcon(
                onPressed: _busyDevice != null ? null : _allOff,
                icon: const Icon(Icons.power_off_rounded, color: Colors.red),
                label: const Text('Tắt tất cả', style: TextStyle(fontWeight: FontWeight.bold)),
              ),
            ),
          ],
        ),
        const SizedBox(height: 18),

        const Text('THIẾT BỊ TRONG NHÀ', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, letterSpacing: 1.1, color: Colors.grey)),
        const SizedBox(height: 10),

        // Grid thiết bị
        GridView.count(
          crossAxisCount: 2,
          shrinkWrap: true,
          physics: const NeverScrollableScrollPhysics(),
          mainAxisSpacing: 12,
          crossAxisSpacing: 12,
          childAspectRatio: 1.15,
          children: [
            // 1. Đèn phòng
            _buildDeviceCard(
              title: 'Đèn phòng',
              isOn: _state.light,
              icon: Icons.lightbulb_rounded,
              activeColor: Colors.amber,
              permitted: _canControl('light'),
              isBusy: _busyDevice == 'light',
              onTap: () => _toggleDevice('light', _state.light),
            ),

            // 2. Quạt làm mát
            _buildDeviceCard(
              title: 'Quạt làm mát',
              isOn: _state.fan,
              icon: Icons.wind_power_rounded,
              activeColor: const Color(0xFF00B4D8),
              permitted: _canControl('fan'),
              isBusy: _busyDevice == 'fan',
              onTap: () => _toggleDevice('fan', _state.fan),
            ),

            // 3. Rèm cửa
            _buildDeviceCard(
              title: 'Rèm cửa',
              isOn: _state.isCurtainOpen,
              stateText: _state.isCurtainOpen ? 'Đang mở' : 'Đang đóng',
              icon: Icons.curtains_rounded,
              activeColor: Colors.indigo,
              permitted: _canControl('curtain'),
              isBusy: _busyDevice == 'curtain',
              onTap: _toggleCurtain,
            ),

            // 4. Cửa thông minh (Khóa an ninh khi 4 lần sai)
            _buildDeviceCard(
              title: 'Cửa thông minh',
              isOn: _state.door,
              stateText: _state.keypadLocked
                  ? 'Khóa an ninh (4 lần sai)'
                  : (_state.door ? 'Đang mở' : 'Đang đóng'),
              icon: _state.keypadLocked ? Icons.lock_clock_rounded : Icons.door_front_door_rounded,
              activeColor: _state.keypadLocked ? Colors.red : Colors.teal,
              permitted: _canControl('door') && !_state.keypadLocked,
              isBusy: _busyDevice == 'door',
              onTap: _openDoor,
            ),

            // 5. Đèn sân vườn
            _buildDeviceCard(
              title: 'Đèn sân vườn',
              isOn: _state.yard,
              icon: Icons.park_rounded,
              activeColor: Colors.lightGreen,
              permitted: _canControl('yard'),
              isBusy: _busyDevice == 'yard',
              onTap: () => _toggleDevice('yard', _state.yard),
            ),

            // 6. Mic giọng nói AI
            _buildDeviceCard(
              title: 'Giọng nói AI',
              isOn: _state.voice,
              stateText: _state.voice ? 'Sẵn sàng' : 'Tạm dừng',
              icon: Icons.mic_rounded,
              activeColor: Colors.purple,
              permitted: false,
              isBusy: false,
              onTap: () {},
            ),
          ],
        ),
      ],
    );
  }

  Widget _buildDeviceCard({
    required String title,
    required bool isOn,
    String? stateText,
    required IconData icon,
    required Color activeColor,
    required bool permitted,
    required bool isBusy,
    required VoidCallback onTap,
  }) {
    final scheme = Theme.of(context).colorScheme;
    final displayState = stateText ?? (isOn ? 'Đang bật' : 'Đang tắt');

    return Card(
      elevation: isOn ? 3 : 1,
      color: isOn ? activeColor.withAlpha(25) : scheme.surfaceContainerLow,
      shape: RoundedRectangleBorder(
        borderRadius: BorderRadius.circular(16),
        side: BorderSide(
          color: isOn ? activeColor.withAlpha(120) : Colors.transparent,
          width: 1.5,
        ),
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: (!permitted || isBusy) ? null : onTap,
        child: Padding(
          padding: const EdgeInsets.all(14),
          child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            mainAxisAlignment: MainAxisAlignment.spaceBetween,
            children: [
              Row(
                mainAxisAlignment: MainAxisAlignment.spaceBetween,
                children: [
                  Container(
                    padding: const EdgeInsets.all(8),
                    decoration: BoxDecoration(
                      color: isOn ? activeColor : scheme.surfaceContainerHighest,
                      borderRadius: BorderRadius.circular(12),
                    ),
                    child: Icon(icon, color: isOn ? Colors.white : scheme.onSurfaceVariant, size: 24),
                  ),
                  if (isBusy)
                    const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2))
                  else if (!permitted)
                    const Icon(Icons.lock_outline, size: 16, color: Colors.grey)
                  else
                    Switch(
                      value: isOn,
                      activeColor: activeColor,
                      onChanged: permitted ? (_) => onTap() : null,
                    ),
                ],
              ),
              Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  Text(title, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
                  const SizedBox(height: 2),
                  Text(
                    permitted ? displayState : 'Chưa cấp quyền',
                    style: TextStyle(
                      fontSize: 12,
                      color: permitted ? (isOn ? activeColor : Colors.grey) : Colors.red[400],
                      fontWeight: FontWeight.w500,
                    ),
                  ),
                ],
              ),
            ],
          ),
        ),
      ),
    );
  }

  // ── TAB 1: CẢM BIẾN (SENSORS) ─────────────────────────────
  Widget _buildSensorsTab(ColorScheme scheme) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        const Text('MÔI TRƯỜNG & KHÔNG GIAN', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold, letterSpacing: 1.1, color: Colors.grey)),
        const SizedBox(height: 12),

        // Nhiệt độ & Độ ẩm Card (Bấm vào để mở Biểu đồ lịch sử chuẩn Web)
        Card(
          elevation: 1,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          child: InkWell(
            borderRadius: BorderRadius.circular(16),
            onTap: _openSensorHistoryModal,
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                children: [
                  Row(
                    children: [
                      Expanded(
                        child: Row(
                          children: [
                            Container(
                              padding: const EdgeInsets.all(12),
                              decoration: BoxDecoration(color: const Color(0xFFFF6B6B).withAlpha(30), shape: BoxShape.circle),
                              child: const Icon(Icons.thermostat_rounded, color: Color(0xFFFF6B6B), size: 30),
                            ),
                            const SizedBox(width: 12),
                            Column(
                              crossAxisAlignment: CrossAxisAlignment.start,
                              children: [
                                const Text('Nhiệt độ', style: TextStyle(fontSize: 12.5, color: Colors.grey)),
                                Text(
                                  '${_sensor.temp.toStringAsFixed(1)}°C',
                                  style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
                                ),
                              ],
                            ),
                          ],
                        ),
                      ),
                      Container(width: 1, height: 40, color: scheme.outlineVariant),
                      Expanded(
                        child: Padding(
                          padding: const EdgeInsets.only(left: 12),
                          child: Row(
                            children: [
                              Container(
                                padding: const EdgeInsets.all(12),
                                decoration: BoxDecoration(color: const Color(0xFF00B4D8).withAlpha(30), shape: BoxShape.circle),
                                child: const Icon(Icons.water_drop_rounded, color: Color(0xFF00B4D8), size: 30),
                              ),
                              const SizedBox(width: 12),
                              Column(
                                crossAxisAlignment: CrossAxisAlignment.start,
                                children: [
                                  const Text('Độ ẩm', style: TextStyle(fontSize: 12.5, color: Colors.grey)),
                                  Text(
                                    '${_sensor.hum.toStringAsFixed(1)}%',
                                    style: const TextStyle(fontSize: 22, fontWeight: FontWeight.bold),
                                  ),
                                ],
                              ),
                            ],
                          ),
                        ),
                      ),
                    ],
                  ),
                  const SizedBox(height: 12),
                  Row(
                    mainAxisAlignment: MainAxisAlignment.center,
                    children: [
                      Icon(Icons.show_chart_rounded, size: 16, color: scheme.primary),
                      const SizedBox(width: 6),
                      Text(
                        'Chạm để xem biểu đồ lịch sử 24 giờ & 7 ngày',
                        style: TextStyle(fontSize: 12, color: scheme.primary, fontWeight: FontWeight.w600),
                      ),
                    ],
                  ),
                ],
              ),
            ),
          ),
        ),
        const SizedBox(height: 12),

        // mmWave Radar & PIR
        Card(
          elevation: 1,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          child: ListTile(
            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            leading: Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(
                color: _sensor.person ? Colors.green.withAlpha(30) : Colors.grey.withAlpha(30),
                shape: BoxShape.circle,
              ),
              child: Icon(Icons.radar_rounded, color: _sensor.person ? Colors.green : Colors.grey, size: 28),
            ),
            title: const Text('Hiện diện con người (mmWave Radar)', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
            subtitle: Text(
              _sensor.person
                  ? 'Phát hiện có người trong phòng (${_sensor.mmwaveDist.toStringAsFixed(1)}m)'
                  : 'Không phát hiện có người',
              style: TextStyle(color: _sensor.person ? Colors.green[700] : Colors.grey, fontSize: 13),
            ),
            trailing: Container(
              padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
              decoration: BoxDecoration(
                color: _sensor.person ? Colors.green.withAlpha(25) : Colors.grey.withAlpha(25),
                borderRadius: BorderRadius.circular(8),
              ),
              child: Text(
                _sensor.person ? 'CÓ NGƯỜI' : 'VẮNG',
                style: TextStyle(fontWeight: FontWeight.bold, fontSize: 11, color: _sensor.person ? Colors.green : Colors.grey),
              ),
            ),
          ),
        ),
        const SizedBox(height: 12),

        // Cảm biến ánh sáng LDR
        Card(
          elevation: 1,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          child: ListTile(
            contentPadding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
            leading: Container(
              padding: const EdgeInsets.all(10),
              decoration: BoxDecoration(color: Colors.amber.withAlpha(30), shape: BoxShape.circle),
              child: const Icon(Icons.wb_sunny_rounded, color: Colors.amber, size: 28),
            ),
            title: const Text('Cảm biến ánh sáng (LDR)', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
            subtitle: Text('Độ sáng môi trường: ${_sensor.ldr}'),
            trailing: TextButton(
              onPressed: () => _service.setGardenMode('auto'),
              child: const Text('Auto Sân vườn'),
            ),
          ),
        ),
      ],
    );
  }

  // ── TAB 2: LỊCH SỬ HOẠT ĐỘNG (HISTORY CHUẨN WEB) ───────────
  Widget _buildHistoryTab(ColorScheme scheme) {
    if (_logs.isEmpty) {
      return const Center(
        child: Column(
          mainAxisAlignment: MainAxisAlignment.center,
          children: [
            Icon(Icons.history_toggle_off_rounded, size: 64, color: Colors.grey),
            SizedBox(height: 12),
            Text('Chưa có lịch sử hoạt động', style: TextStyle(color: Colors.grey, fontSize: 16)),
          ],
        ),
      );
    }

    return ListView.separated(
      padding: const EdgeInsets.all(16),
      itemCount: _logs.length,
      separatorBuilder: (_, __) => const SizedBox(height: 8),
      itemBuilder: (ctx, idx) {
        final entry = _logs[idx];
        final timeStr = DateFormat('dd/MM/yyyy HH:mm:ss').format(entry.timestamp);
        final isSecurityEvent = entry.action.toLowerCase() == 'denied' || entry.action.toLowerCase() == 'locked';

        return Card(
          elevation: 1,
          color: isSecurityEvent ? Colors.red.withAlpha(12) : scheme.surfaceContainerLow,
          shape: RoundedRectangleBorder(
            borderRadius: BorderRadius.circular(12),
            side: BorderSide(
              color: isSecurityEvent ? Colors.red.withAlpha(90) : scheme.outlineVariant.withAlpha(70),
              width: 1,
            ),
          ),
          child: ListTile(
            leading: Container(
              width: 42,
              height: 42,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: isSecurityEvent ? Colors.red.withAlpha(30) : scheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Icon(entry.iconData, color: isSecurityEvent ? Colors.red : scheme.primary, size: 22),
            ),
            title: Row(
              children: [
                Expanded(
                  child: Text(
                    entry.actionLabel,
                    style: TextStyle(
                      fontWeight: FontWeight.bold,
                      fontSize: 14,
                      color: isSecurityEvent ? Colors.red[800] : scheme.onSurface,
                    ),
                  ),
                ),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: (isSecurityEvent ? Colors.red : const Color(0xFF00B4D8)).withAlpha(25),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(
                    '[${entry.deviceLabel}]',
                    style: TextStyle(
                      fontSize: 10.5,
                      fontWeight: FontWeight.bold,
                      color: isSecurityEvent ? Colors.red : const Color(0xFF00B4D8),
                    ),
                  ),
                ),
              ],
            ),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 3),
                Text(
                  'Nguồn: ${entry.sourceLabel} · ${entry.user}',
                  style: const TextStyle(fontSize: 12, color: Colors.grey),
                ),
                const SizedBox(height: 2),
                Text(timeStr, style: TextStyle(fontSize: 11, color: scheme.outline)),
              ],
            ),
          ),
        );
      },
    );
  }

  // ── TAB 3: CÀI ĐẶT (GỌN GÀNG DẠNG ACCORDION NHƯ WEB) ────────
  Widget _buildSettingsTab(ColorScheme scheme, UserProfile? user) {
    final isMaster = user?.isMaster ?? false;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // THẺ 1: THÔNG TIN TÀI KHOẢN & HỒ SƠ CÁ NHÂN
        Card(
          elevation: 1,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          child: ExpansionTile(
            initiallyExpanded: true,
            leading: CircleAvatar(
              backgroundColor: const Color(0xFF00B4D8).withAlpha(30),
              child: Text(
                (user?.displayName.isNotEmpty == true ? user!.displayName[0] : 'U').toUpperCase(),
                style: const TextStyle(fontWeight: FontWeight.bold, color: Color(0xFF00B4D8)),
              ),
            ),
            title: Text(user?.displayName ?? 'Người dùng', style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
            subtitle: Text(user?.email ?? '', style: TextStyle(fontSize: 12, color: scheme.outline)),
            trailing: _buildRoleBadge(user),
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                child: Column(
                  crossAxisAlignment: CrossAxisAlignment.start,
                  children: [
                    const Divider(),
                    const SizedBox(height: 8),
                    const Text('Cập nhật hồ sơ cá nhân', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 13.5)),
                    const SizedBox(height: 12),
                    TextField(
                      controller: _nameCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Họ và tên',
                        prefixIcon: Icon(Icons.person_outline_rounded),
                        border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(12))),
                      ),
                    ),
                    const SizedBox(height: 10),
                    TextField(
                      controller: _phoneCtrl,
                      keyboardType: TextInputType.phone,
                      decoration: const InputDecoration(
                        labelText: 'Số điện thoại',
                        prefixIcon: Icon(Icons.phone_outlined),
                        border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(12))),
                      ),
                    ),
                    const SizedBox(height: 10),
                    TextField(
                      controller: _dobCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Ngày sinh (DD/MM/YYYY)',
                        prefixIcon: Icon(Icons.calendar_today_outlined),
                        border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(12))),
                      ),
                    ),
                    const SizedBox(height: 10),
                    Row(
                      children: [
                        const Text('Giới tính:', style: TextStyle(fontSize: 13, color: Colors.grey)),
                        const SizedBox(width: 12),
                        Wrap(
                          spacing: 8,
                          children: ['Nam', 'Nữ', 'Khác'].map((g) {
                            final selected = _selectedGender == g;
                            return ChoiceChip(
                              label: Text(g),
                              selected: selected,
                              selectedColor: const Color(0xFF00B4D8).withAlpha(40),
                              onSelected: (_) => setState(() => _selectedGender = g),
                            );
                          }).toList(),
                        ),
                      ],
                    ),
                    const SizedBox(height: 10),
                    TextField(
                      controller: _addressCtrl,
                      decoration: const InputDecoration(
                        labelText: 'Địa chỉ',
                        prefixIcon: Icon(Icons.home_outlined),
                        border: OutlineInputBorder(borderRadius: BorderRadius.all(Radius.circular(12))),
                      ),
                    ),
                    const SizedBox(height: 14),
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton.icon(
                        style: FilledButton.styleFrom(
                          backgroundColor: const Color(0xFF00B4D8),
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                        onPressed: _isSavingProfile ? null : _handleSaveProfile,
                        icon: _isSavingProfile
                            ? const SizedBox(width: 16, height: 16, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                            : const Icon(Icons.save_rounded),
                        label: const Text('Lưu thông tin hồ sơ', style: TextStyle(fontWeight: FontWeight.bold)),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),

        // THẺ 2: BẢO MẬT & ĐỔI MẬT KHẨU
        Card(
          elevation: 1,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          child: ExpansionTile(
            leading: const Icon(Icons.lock_reset_rounded, color: Colors.blue),
            title: const Text('Bảo mật & Đổi mật khẩu', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
            subtitle: const Text('Quản lý mật khẩu đăng nhập', style: TextStyle(fontSize: 12, color: Colors.grey)),
            children: [
              Padding(
                padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                child: Column(
                  children: [
                    const Divider(),
                    const SizedBox(height: 8),
                    TextField(
                      controller: _oldPassCtrl,
                      obscureText: _obscureOld,
                      decoration: InputDecoration(
                        labelText: 'Mật khẩu hiện tại',
                        prefixIcon: const Icon(Icons.lock_outline),
                        suffixIcon: IconButton(
                          icon: Icon(_obscureOld ? Icons.visibility_off : Icons.visibility),
                          onPressed: () => setState(() => _obscureOld = !_obscureOld),
                        ),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                    ),
                    const SizedBox(height: 10),
                    TextField(
                      controller: _newPassCtrl,
                      obscureText: _obscureNew,
                      decoration: InputDecoration(
                        labelText: 'Mật khẩu mới (tối thiểu 6 ký tự)',
                        prefixIcon: const Icon(Icons.vpn_key_outlined),
                        suffixIcon: IconButton(
                          icon: Icon(_obscureNew ? Icons.visibility_off : Icons.visibility),
                          onPressed: () => setState(() => _obscureNew = !_obscureNew),
                        ),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                    ),
                    const SizedBox(height: 10),
                    TextField(
                      controller: _confirmNewPassCtrl,
                      obscureText: _obscureConfirm,
                      decoration: InputDecoration(
                        labelText: 'Xác nhận lại mật khẩu mới',
                        prefixIcon: const Icon(Icons.vpn_key_outlined),
                        suffixIcon: IconButton(
                          icon: Icon(_obscureConfirm ? Icons.visibility_off : Icons.visibility),
                          onPressed: () => setState(() => _obscureConfirm = !_obscureConfirm),
                        ),
                        border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
                      ),
                    ),
                    const SizedBox(height: 14),
                    SizedBox(
                      width: double.infinity,
                      child: FilledButton(
                        onPressed: _isChangingPassword ? null : _handleChangePassword,
                        style: FilledButton.styleFrom(
                          padding: const EdgeInsets.symmetric(vertical: 12),
                          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                        ),
                        child: _isChangingPassword
                            ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
                            : const Text('Cập nhật mật khẩu mới', style: TextStyle(fontWeight: FontWeight.bold)),
                      ),
                    ),
                  ],
                ),
              ),
            ],
          ),
        ),
        const SizedBox(height: 12),

        // THẺ 3: PHÂN QUYỀN & QUẢN LÝ THÀNH VIÊN (CHỈ MASTER)
        if (isMaster) ...[
          Card(
            elevation: 1,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            child: ExpansionTile(
              leading: const Icon(Icons.people_outline_rounded, color: Colors.amber),
              title: const Text('Phân quyền & Quản lý thành viên', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 15)),
              subtitle: Text('${_members.length} thành viên đã liên kết', style: const TextStyle(fontSize: 12, color: Colors.grey)),
              children: [
                Padding(
                  padding: const EdgeInsets.fromLTRB(16, 0, 16, 16),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Divider(),
                      const SizedBox(height: 6),
                      SizedBox(
                        width: double.infinity,
                        child: FilledButton.tonalIcon(
                          onPressed: _handleCreateInviteCode,
                          icon: const Icon(Icons.add_rounded),
                          label: const Text('Tạo mã mời 1 lần mới'),
                        ),
                      ),
                      const SizedBox(height: 14),
                      if (_members.isEmpty)
                        const Padding(
                          padding: EdgeInsets.symmetric(vertical: 12),
                          child: Text('Chưa có thành viên nào liên kết qua mã mời.', style: TextStyle(color: Colors.grey, fontSize: 12.5)),
                        )
                      else
                        ..._members.map((m) => _buildMemberAccordionItem(m)),
                    ],
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 12),
        ],

        // Nút Đăng xuất
        OutlinedButton.icon(
          onPressed: () {
            _service.signOut();
            widget.onSignOut();
          },
          icon: const Icon(Icons.logout_rounded, color: Colors.red),
          label: const Text('Đăng xuất tài khoản', style: TextStyle(color: Colors.red, fontWeight: FontWeight.bold)),
          style: OutlinedButton.styleFrom(
            padding: const EdgeInsets.symmetric(vertical: 14),
            side: const BorderSide(color: Colors.red),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        const SizedBox(height: 24),
      ],
    );
  }

  // ── MỖI THÀNH VIÊN LÀ 1 ACCORDION THU GỌN VỚI CHECKBOX MÀU XANH ──
  Widget _buildMemberAccordionItem(UserProfile m) {
    return Container(
      margin: const EdgeInsets.only(bottom: 10),
      decoration: BoxDecoration(
        border: Border.all(color: Colors.grey.withAlpha(70)),
        borderRadius: BorderRadius.circular(14),
      ),
      child: ExpansionTile(
        tilePadding: const EdgeInsets.symmetric(horizontal: 12, vertical: 2),
        leading: CircleAvatar(
          radius: 18,
          backgroundColor: const Color(0xFF00B4D8).withAlpha(25),
          child: Text(
            (m.displayName.isNotEmpty ? m.displayName[0] : 'U').toUpperCase(),
            style: const TextStyle(fontWeight: FontWeight.bold, color: Color(0xFF00B4D8)),
          ),
        ),
        title: Text(m.displayName, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
        subtitle: Text(m.email, style: const TextStyle(fontSize: 11.5, color: Colors.grey)),
        trailing: TextButton(
          style: TextButton.styleFrom(
            foregroundColor: Colors.red,
            padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
          ),
          onPressed: () async {
            final confirm = await showDialog<bool>(
              context: context,
              builder: (ctx) => AlertDialog(
                title: const Text('Hủy liên kết thành viên?'),
                content: Text('Xác nhận ngắt quyền truy cập ngôi nhà của ${m.displayName}?'),
                actions: [
                  TextButton(onPressed: () => Navigator.pop(ctx, false), child: const Text('Hủy')),
                  FilledButton(style: FilledButton.styleFrom(backgroundColor: Colors.red), onPressed: () => Navigator.pop(ctx, true), child: const Text('Ngắt kết nối')),
                ],
              ),
            );
            if (confirm == true) {
              await _service.deleteMember(m.uid);
              _loadMembers();
              _showSnack('Đã hủy liên kết thành viên.');
            }
          },
          child: const Text('Hủy liên kết', style: TextStyle(fontSize: 12, fontWeight: FontWeight.bold)),
        ),
        children: [
          Padding(
            padding: const EdgeInsets.fromLTRB(14, 0, 14, 14),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Divider(),
                // Thông tin cá nhân
                Container(
                  padding: const EdgeInsets.all(10),
                  decoration: BoxDecoration(
                    color: Colors.grey.withAlpha(20),
                    borderRadius: BorderRadius.circular(10),
                  ),
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      const Text('Thông tin cá nhân:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 11.5, color: Color(0xFF00B4D8))),
                      const SizedBox(height: 6),
                      Text('SĐT: ${m.phone.isNotEmpty ? m.phone : "Chưa cập nhật"}', style: const TextStyle(fontSize: 12)),
                      Text('Ngày sinh: ${m.dob.isNotEmpty ? m.dob : "Chưa cập nhật"}', style: const TextStyle(fontSize: 12)),
                      Text('Giới tính: ${m.gender.isNotEmpty ? m.gender : "Chưa cập nhật"}', style: const TextStyle(fontSize: 12)),
                      Text('Địa chỉ: ${m.address.isNotEmpty ? m.address : "Chưa cập nhật"}', style: const TextStyle(fontSize: 12)),
                    ],
                  ),
                ),
                const SizedBox(height: 10),

                // Phân quyền thiết bị
                const Text('Quyền thiết bị:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12, color: Colors.grey)),
                const SizedBox(height: 4),
                Wrap(
                  spacing: 4,
                  runSpacing: 2,
                  children: [
                    _buildPermCheckbox(m, 'light', 'Đèn'),
                    _buildPermCheckbox(m, 'fan', 'Quạt'),
                    _buildPermCheckbox(m, 'curtain', 'Rèm'),
                    _buildPermCheckbox(m, 'door', 'Cửa chính'),
                    _buildPermCheckbox(m, 'yard', 'Đèn sân'),
                  ],
                ),
                const SizedBox(height: 8),

                // Phân quyền nâng cao
                const Text('Quyền nâng cao:', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 12, color: Colors.grey)),
                const SizedBox(height: 4),
                Wrap(
                  spacing: 4,
                  runSpacing: 2,
                  children: [
                    _buildPermCheckbox(m, 'auto', 'Cấu hình tự động hóa'),
                    _buildPermCheckbox(m, 'passcodeAdd', 'Thêm mật khẩu Keypad'),
                    _buildPermCheckbox(m, 'passcodeDel', 'Xóa mật khẩu Keypad'),
                  ],
                ),
              ],
            ),
          ),
        ],
      ),
    );
  }

  /// Checkbox phân quyền với màu xanh cyan nổi bật, khắc phục lỗi tích ra màu trắng
  Widget _buildPermCheckbox(UserProfile m, String devKey, String label) {
    final isChecked = m.hasPermission(devKey);
    return Row(
      mainAxisSize: MainAxisSize.min,
      children: [
        Checkbox(
          value: isChecked,
          activeColor: const Color(0xFF00B4D8), // Màu xanh cyan chuẩn theo yêu cầu
          checkColor: Colors.white,
          side: BorderSide(
            color: isChecked ? const Color(0xFF00B4D8) : Colors.grey.withAlpha(120),
            width: 1.5,
          ),
          onChanged: (val) async {
            if (val == null) return;
            await _service.updateMemberPermission(m.uid, devKey, val);
            _loadMembers();
          },
        ),
        Text(label, style: const TextStyle(fontSize: 12)),
      ],
    );
  }
}

// ── SHEET BIỂU ĐỒ LỊCH SỬ NHIỆT ĐỘ & ĐỘ ẨM CHUẨN WEB ──────────
class _SensorHistorySheet extends StatefulWidget {
  final FirebaseService service;
  final SensorData currentSensor;

  const _SensorHistorySheet({required this.service, required this.currentSensor});

  @override
  State<_SensorHistorySheet> createState() => _SensorHistorySheetState();
}

class _SensorHistorySheetState extends State<_SensorHistorySheet> {
  String _range = '24h';
  bool _isLoading = true;
  List<SensorPoint> _points = [];

  @override
  void initState() {
    super.initState();
    _loadData();
  }

  Future<void> _loadData() async {
    setState(() => _isLoading = true);
    final pts = await widget.service.fetchSensorHistory(range: _range);
    if (!mounted) return;

    // Nếu rỗng, thêm điểm thời gian thực hiện tại để biểu đồ không bị trống
    if (pts.isEmpty) {
      final now = DateTime.now();
      pts.add(SensorPoint(
        timestamp: now.subtract(const Duration(minutes: 30)),
        temp: widget.currentSensor.temp > 0 ? widget.currentSensor.temp - 0.5 : 28.0,
        hum: widget.currentSensor.hum > 0 ? widget.currentSensor.hum - 2 : 60.0,
      ));
      pts.add(SensorPoint(
        timestamp: now,
        temp: widget.currentSensor.temp > 0 ? widget.currentSensor.temp : 28.5,
        hum: widget.currentSensor.hum > 0 ? widget.currentSensor.hum : 62.0,
      ));
    }

    setState(() {
      _points = pts;
      _isLoading = false;
    });
  }

  @override
  Widget build(BuildContext context) {
    double minTemp = 999, maxTemp = -999, sumTemp = 0;
    double minHum = 999, maxHum = -999, sumHum = 0;

    for (final p in _points) {
      if (p.temp < minTemp) minTemp = p.temp;
      if (p.temp > maxTemp) maxTemp = p.temp;
      sumTemp += p.temp;

      if (p.hum < minHum) minHum = p.hum;
      if (p.hum > maxHum) maxHum = p.hum;
      sumHum += p.hum;
    }

    final avgTemp = _points.isNotEmpty ? sumTemp / _points.length : 0.0;
    final avgHum = _points.isNotEmpty ? sumHum / _points.length : 0.0;

    return Container(
      decoration: const BoxDecoration(
        color: Color(0xFF0F1D32),
        borderRadius: BorderRadius.vertical(top: Radius.circular(24)),
      ),
      padding: const EdgeInsets.fromLTRB(20, 16, 20, 24),
      child: Column(
        mainAxisSize: MainAxisSize.min,
        children: [
          // Drag handle
          Container(width: 40, height: 4, decoration: BoxDecoration(color: Colors.white24, borderRadius: BorderRadius.circular(2))),
          const SizedBox(height: 14),

          // Title
          const Text('Lịch sử Nhiệt độ & Độ ẩm', style: TextStyle(color: Colors.white, fontSize: 17, fontWeight: FontWeight.bold)),
          const SizedBox(height: 14),

          // Tab switch 24 giờ / 7 ngày
          Container(
            padding: const EdgeInsets.all(3),
            decoration: BoxDecoration(color: const Color(0xFF162A47), borderRadius: BorderRadius.circular(12)),
            child: Row(
              children: [
                Expanded(
                  child: GestureDetector(
                    onTap: () {
                      if (_range != '24h') {
                        setState(() => _range = '24h');
                        _loadData();
                      }
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      decoration: BoxDecoration(
                        color: _range == '24h' ? const Color(0xFF00B4D8) : Colors.transparent,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      alignment: Alignment.center,
                      child: Text(
                        '24 giờ',
                        style: TextStyle(
                          color: _range == '24h' ? Colors.white : Colors.white60,
                          fontWeight: FontWeight.bold,
                          fontSize: 13,
                        ),
                      ),
                    ),
                  ),
                ),
                Expanded(
                  child: GestureDetector(
                    onTap: () {
                      if (_range != '7d') {
                        setState(() => _range = '7d');
                        _loadData();
                      }
                    },
                    child: Container(
                      padding: const EdgeInsets.symmetric(vertical: 8),
                      decoration: BoxDecoration(
                        color: _range == '7d' ? const Color(0xFF00B4D8) : Colors.transparent,
                        borderRadius: BorderRadius.circular(10),
                      ),
                      alignment: Alignment.center,
                      child: Text(
                        '7 ngày',
                        style: TextStyle(
                          color: _range == '7d' ? Colors.white : Colors.white60,
                          fontWeight: FontWeight.bold,
                          fontSize: 13,
                        ),
                      ),
                    ),
                  ),
                ),
              ],
            ),
          ),
          const SizedBox(height: 14),

          // Legend
          Row(
            mainAxisAlignment: MainAxisAlignment.center,
            children: [
              Container(width: 10, height: 10, decoration: const BoxDecoration(color: Color(0xFFFF6B6B), shape: BoxShape.circle)),
              const SizedBox(width: 6),
              const Text('Nhiệt độ (°C)', style: TextStyle(color: Colors.white70, fontSize: 12)),
              const SizedBox(width: 20),
              Container(width: 10, height: 10, decoration: const BoxDecoration(color: Color(0xFF00B4D8), shape: BoxShape.circle)),
              const SizedBox(width: 6),
              const Text('Độ ẩm (%)', style: TextStyle(color: Colors.white70, fontSize: 12)),
            ],
          ),
          const SizedBox(height: 16),

          // Biểu đồ Canvas mượt mà chuẩn Web
          SizedBox(
            height: 180,
            width: double.infinity,
            child: _isLoading
                ? const Center(child: CircularProgressIndicator(color: Color(0xFF00B4D8)))
                : CustomPaint(
                    painter: _SplineChartPainter(points: _points, minTemp: minTemp, maxTemp: maxTemp, minHum: minHum, maxHum: maxHum),
                  ),
          ),
          const SizedBox(height: 18),

          // 3 Khung thống kê Cao nhất / Thấp nhất / Trung bình chuẩn Web
          Row(
            children: [
              Expanded(child: _buildStatBox('Cao nhất', '${maxTemp.toStringAsFixed(1)}°C', '${maxHum.toStringAsFixed(0)}%')),
              const SizedBox(width: 8),
              Expanded(child: _buildStatBox('Thấp nhất', '${minTemp.toStringAsFixed(1)}°C', '${minHum.toStringAsFixed(0)}%')),
              const SizedBox(width: 8),
              Expanded(child: _buildStatBox('Trung bình', '${avgTemp.toStringAsFixed(1)}°C', '${avgHum.toStringAsFixed(0)}%')),
            ],
          ),
          const SizedBox(height: 16),

          // Nút Đóng
          SizedBox(
            width: double.infinity,
            child: FilledButton(
              style: FilledButton.styleFrom(
                backgroundColor: const Color(0xFF00B4D8),
                shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
                padding: const EdgeInsets.symmetric(vertical: 12),
              ),
              onPressed: () => Navigator.pop(context),
              child: const Text('Đóng', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
            ),
          ),
        ],
      ),
    );
  }

  Widget _buildStatBox(String title, String tempStr, String humStr) {
    return Container(
      padding: const EdgeInsets.symmetric(vertical: 10, horizontal: 8),
      decoration: BoxDecoration(
        color: const Color(0xFF162A47),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: Colors.white12),
      ),
      child: Column(
        children: [
          Text(title, style: const TextStyle(color: Colors.white60, fontSize: 11)),
          const SizedBox(height: 4),
          Text('$tempStr · $humStr', style: const TextStyle(color: Colors.white, fontWeight: FontWeight.bold, fontSize: 12.5)),
        ],
      ),
    );
  }
}

// ── VẼ BIỂU ĐỒ NỘI SUY SPLINE MƯỢT MÀ VỚI GRADIENT CHUẨN WEB ──
class _SplineChartPainter extends CustomPainter {
  final List<SensorPoint> points;
  final double minTemp;
  final double maxTemp;
  final double minHum;
  final double maxHum;

  _SplineChartPainter({
    required this.points,
    required this.minTemp,
    required this.maxTemp,
    required this.minHum,
    required this.maxHum,
  });

  @override
  void paint(Canvas canvas, Size size) {
    final gridPaint = Paint()
      ..color = Colors.white.withAlpha(25)
      ..strokeWidth = 1;

    // Vẽ lưới ngang
    for (int i = 0; i <= 3; i++) {
      final y = size.height * (i / 3);
      canvas.drawLine(Offset(0, y), Offset(size.width, y), gridPaint);
    }

    if (points.length < 2) return;

    final tempRange = max(1.0, maxTemp - minTemp);
    final humRange = max(1.0, maxHum - minHum);

    final tempPath = Path();
    final tempFillPath = Path();
    final humPath = Path();

    final tempCoords = <Offset>[];
    final humCoords = <Offset>[];

    for (int i = 0; i < points.length; i++) {
      final x = (i / (points.length - 1)) * size.width;
      final ty = size.height - ((points[i].temp - minTemp) / tempRange) * (size.height - 30) - 15;
      final hy = size.height - ((points[i].hum - minHum) / humRange) * (size.height - 30) - 15;
      tempCoords.add(Offset(x, ty));
      humCoords.add(Offset(x, hy));
    }

    // Vẽ đường cong mượt mà cho Nhiệt độ (Màu đỏ/coral)
    tempPath.moveTo(tempCoords[0].dx, tempCoords[0].dy);
    tempFillPath.moveTo(tempCoords[0].dx, size.height);
    tempFillPath.lineTo(tempCoords[0].dx, tempCoords[0].dy);

    for (int i = 0; i < tempCoords.length - 1; i++) {
      final p0 = tempCoords[i];
      final p1 = tempCoords[i + 1];
      final cpX = (p0.dx + p1.dx) / 2;
      tempPath.cubicTo(cpX, p0.dy, cpX, p1.dy, p1.dx, p1.dy);
      tempFillPath.cubicTo(cpX, p0.dy, cpX, p1.dy, p1.dx, p1.dy);
    }
    tempFillPath.lineTo(size.width, size.height);
    tempFillPath.close();

    // Gradient phủ dưới đường nhiệt độ
    final tempFillPaint = Paint()
      ..shader = const LinearGradient(
        begin: Alignment.topCenter,
        end: Alignment.bottomCenter,
        colors: [Color(0x55FF6B6B), Color(0x00FF6B6B)],
      ).createShader(Rect.fromLTWH(0, 0, size.width, size.height));
    canvas.drawPath(tempFillPath, tempFillPaint);

    final tempStrokePaint = Paint()
      ..color = const Color(0xFFFF6B6B)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.5
      ..strokeCap = StrokeCap.round;
    canvas.drawPath(tempPath, tempStrokePaint);

    // Vẽ đường cong mượt mà cho Độ ẩm (Màu xanh cyan)
    humPath.moveTo(humCoords[0].dx, humCoords[0].dy);
    for (int i = 0; i < humCoords.length - 1; i++) {
      final p0 = humCoords[i];
      final p1 = humCoords[i + 1];
      final cpX = (p0.dx + p1.dx) / 2;
      humPath.cubicTo(cpX, p0.dy, cpX, p1.dy, p1.dx, p1.dy);
    }

    final humStrokePaint = Paint()
      ..color = const Color(0xFF00B4D8)
      ..style = PaintingStyle.stroke
      ..strokeWidth = 2.2
      ..strokeCap = StrokeCap.round;
    canvas.drawPath(humPath, humStrokePaint);

    // Điểm nút đầu và cuối
    final dotPaint = Paint()..color = Colors.white;
    canvas.drawCircle(tempCoords.last, 3.5, dotPaint);
    canvas.drawCircle(humCoords.last, 3.5, dotPaint);
  }

  @override
  bool shouldRepaint(covariant _SplineChartPainter oldDelegate) => true;
}
