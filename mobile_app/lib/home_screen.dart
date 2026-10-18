import 'dart:async';
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
  String? _busyDevice;

  // Stream subscriptions
  late StreamSubscription<DeviceState> _stateSub;
  late StreamSubscription<SensorData> _sensorSub;
  late StreamSubscription<List<LogEntry>> _logsSub;

  // Form controllers cho Đổi mật khẩu trong Cài đặt
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
    _stateSub = _service.deviceStateStream.listen((s) {
      if (mounted) setState(() => _state = s);
    });
    _sensorSub = _service.sensorDataStream.listen((s) {
      if (mounted) setState(() => _sensor = s);
    });
    _logsSub = _service.logsStream.listen((l) {
      if (mounted) setState(() => _logs = l);
    });

    _service.startListening();
  }

  @override
  void dispose() {
    _stateSub.cancel();
    _sensorSub.cancel();
    _logsSub.cancel();
    _oldPassCtrl.dispose();
    _newPassCtrl.dispose();
    _confirmNewPassCtrl.dispose();
    super.dispose();
  }

  // ── KIỂM TRA PHÂN QUYỀN ──────────────────────────────────
  bool _canControl(String deviceKey) {
    return _service.currentUser?.hasPermission(deviceKey) ?? false;
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

  // ── ĐỔI MẬT KHẨU TRONG CÀI ĐẶT ────────────────────────────
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
              FilledButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Hoàn tất'),
              ),
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
                  'Gửi mã này cho thành viên gia đình để đăng ký tài khoản trên App hoặc Web.',
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
              FilledButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Đóng'),
              ),
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
        backgroundColor: isError ? Colors.red[700] : Colors.green[700],
        behavior: SnackBarBehavior.floating,
      ),
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
        onDestinationSelected: (idx) => setState(() => _currentTabIndex = idx),
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

  // ── BADGES ────────────────────────────────────────────────
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
    final color = isMaster ? Colors.amber[800]! : Colors.blue;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 4),
      decoration: BoxDecoration(
        color: color.withAlpha(25),
        borderRadius: BorderRadius.circular(12),
        border: Border.all(color: color, width: 1),
      ),
      child: Text(
        isMaster ? '👑 Master' : '👤 Thành viên',
        style: TextStyle(fontSize: 11, fontWeight: FontWeight.bold, color: color),
      ),
    );
  }

  // ── TAB 0: ĐIỀU KHIỂN (CONTROLS) ─────────────────────────
  Widget _buildControlsTab(ColorScheme scheme) {
    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
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
              activeColor: Colors.cyan,
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

            // 4. Cửa thông minh
            _buildDeviceCard(
              title: 'Cửa thông minh',
              isOn: _state.door,
              stateText: _state.door ? 'Đang mở' : 'Đang khóa',
              icon: Icons.door_front_door_rounded,
              activeColor: Colors.teal,
              permitted: _canControl('door'),
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
              permitted: false, // ESP32 tự quản lý
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
      color: isOn ? activeColor.withAlpha(30) : scheme.surfaceContainerLow,
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
                    child: Icon(
                      icon,
                      color: isOn ? Colors.white : scheme.onSurfaceVariant,
                      size: 24,
                    ),
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

        // Nhiệt độ & Độ ẩm Card
        Card(
          elevation: 1,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                Expanded(
                  child: Row(
                    children: [
                      Container(
                        padding: const EdgeInsets.all(12),
                        decoration: BoxDecoration(color: Colors.red.withAlpha(30), shape: BoxShape.circle),
                        child: const Icon(Icons.thermostat_rounded, color: Colors.red, size: 30),
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
                          decoration: BoxDecoration(color: Colors.blue.withAlpha(30), shape: BoxShape.circle),
                          child: const Icon(Icons.water_drop_rounded, color: Colors.blue, size: 30),
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
              child: Icon(
                Icons.radar_rounded,
                color: _sensor.person ? Colors.green : Colors.grey,
                size: 28,
              ),
            ),
            title: const Text('Hiện diện con người (mmWave LD2410S)', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 14)),
            subtitle: Text(
              _sensor.person
                  ? 'Phát hiện có người trong phòng (khoảng cách ${_sensor.mmwaveDist.toStringAsFixed(1)}m)'
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
                style: TextStyle(
                  fontWeight: FontWeight.bold,
                  fontSize: 11,
                  color: _sensor.person ? Colors.green : Colors.grey,
                ),
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
            subtitle: Text('Giá trị độ sáng: ${_sensor.ldr}'),
            trailing: TextButton(
              onPressed: () => _service.setGardenMode('auto'),
              child: const Text('Auto Sân vườn'),
            ),
          ),
        ),
      ],
    );
  }

  // ── TAB 2: LỊCH SỬ HOẠT ĐỘNG (HISTORY) ─────────────────────
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
        final timeStr = DateFormat('HH:mm:ss dd/MM').format(entry.timestamp);

        return Card(
          elevation: 1,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          child: ListTile(
            leading: Container(
              width: 42,
              height: 42,
              alignment: Alignment.center,
              decoration: BoxDecoration(
                color: scheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(10),
              ),
              child: Text(entry.deviceIcon, style: const TextStyle(fontSize: 20)),
            ),
            title: Row(
              children: [
                Text(entry.deviceLabel, style: const TextStyle(fontWeight: FontWeight.bold, fontSize: 14.5)),
                const SizedBox(width: 8),
                Container(
                  padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 2),
                  decoration: BoxDecoration(
                    color: Colors.blue.withAlpha(25),
                    borderRadius: BorderRadius.circular(6),
                  ),
                  child: Text(entry.actionLabel, style: const TextStyle(fontSize: 11, color: Colors.blue, fontWeight: FontWeight.bold)),
                ),
              ],
            ),
            subtitle: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const SizedBox(height: 4),
                Row(
                  children: [
                    const Icon(Icons.account_circle_outlined, size: 14, color: Colors.grey),
                    const SizedBox(width: 4),
                    Expanded(
                      child: Text(
                        entry.user,
                        style: const TextStyle(fontSize: 12, fontWeight: FontWeight.w600, color: Colors.black87),
                        overflow: TextOverflow.ellipsis,
                      ),
                    ),
                  ],
                ),
                Text(timeStr, style: const TextStyle(fontSize: 11, color: Colors.grey)),
              ],
            ),
            trailing: Container(
              padding: const EdgeInsets.symmetric(horizontal: 6, vertical: 3),
              decoration: BoxDecoration(
                color: scheme.surfaceContainerHighest,
                borderRadius: BorderRadius.circular(6),
              ),
              child: Text(
                entry.source.toUpperCase(),
                style: const TextStyle(fontSize: 10, fontWeight: FontWeight.bold, color: Colors.grey),
              ),
            ),
          ),
        );
      },
    );
  }

  // ── TAB 3: CÀI ĐẶT (SETTINGS) ─────────────────────────────
  Widget _buildSettingsTab(ColorScheme scheme, UserProfile? user) {
    final isMaster = user?.isMaster ?? false;

    return ListView(
      padding: const EdgeInsets.all(16),
      children: [
        // Card thông tin cá nhân
        Card(
          elevation: 1,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Row(
              children: [
                CircleAvatar(
                  radius: 28,
                  backgroundColor: scheme.primary.withAlpha(30),
                  child: Icon(Icons.person, size: 32, color: scheme.primary),
                ),
                const SizedBox(width: 14),
                Expanded(
                  child: Column(
                    crossAxisAlignment: CrossAxisAlignment.start,
                    children: [
                      Text(user?.displayName ?? 'Người dùng', style: const TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
                      Text(user?.email ?? '', style: TextStyle(fontSize: 13, color: scheme.outline)),
                      const SizedBox(height: 4),
                      Text(
                        isMaster ? '👑 Quyền: Master Quản trị' : '👤 Quyền: Thành viên gia đình',
                        style: TextStyle(
                          fontSize: 12,
                          fontWeight: FontWeight.bold,
                          color: isMaster ? Colors.amber[800] : Colors.blue,
                        ),
                      ),
                    ],
                  ),
                ),
              ],
            ),
          ),
        ),
        const SizedBox(height: 18),

        // SECTION: ĐỔI MẬT KHẨU TÀI KHOẢN
        Card(
          elevation: 1,
          shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
          child: Padding(
            padding: const EdgeInsets.all(16),
            child: Column(
              crossAxisAlignment: CrossAxisAlignment.start,
              children: [
                const Row(
                  children: [
                    Icon(Icons.lock_reset_rounded, color: Colors.blue),
                    SizedBox(width: 8),
                    Text('Đổi mật khẩu tài khoản', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                  ],
                ),
                const SizedBox(height: 6),
                const Text(
                  'Yêu cầu xác nhận mật khẩu hiện tại trước khi tạo mật khẩu mới.',
                  style: TextStyle(fontSize: 12.5, color: Colors.grey),
                ),
                const SizedBox(height: 14),

                // 1. Mật khẩu hiện tại
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
                const SizedBox(height: 12),

                // 2. Mật khẩu mới
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
                const SizedBox(height: 12),

                // 3. Xác nhận mật khẩu mới
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
        ),
        const SizedBox(height: 18),

        // MASTER PANEL: QUẢN LÝ MÃ MỜI
        if (isMaster) ...[
          Card(
            elevation: 1,
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(16)),
            child: Padding(
              padding: const EdgeInsets.all(16),
              child: Column(
                crossAxisAlignment: CrossAxisAlignment.start,
                children: [
                  const Row(
                    children: [
                      Icon(Icons.vpn_key_rounded, color: Colors.amber),
                      SizedBox(width: 8),
                      Text('Quản lý mã mời thành viên', style: TextStyle(fontWeight: FontWeight.bold, fontSize: 16)),
                    ],
                  ),
                  const SizedBox(height: 6),
                  const Text(
                    'Tạo mã mời 1 lần để cấp quyền cho thành viên gia đình tạo tài khoản.',
                    style: TextStyle(fontSize: 12.5, color: Colors.grey),
                  ),
                  const SizedBox(height: 12),
                  FilledButton.tonalIcon(
                    onPressed: _handleCreateInviteCode,
                    icon: const Icon(Icons.add_rounded),
                    label: const Text('Tạo mã mời mới'),
                  ),
                ],
              ),
            ),
          ),
          const SizedBox(height: 18),
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
}