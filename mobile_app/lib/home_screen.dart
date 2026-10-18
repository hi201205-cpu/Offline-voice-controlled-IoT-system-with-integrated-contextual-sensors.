import 'dart:async';
import 'package:flutter/material.dart';
import 'package:intl/intl.dart';
import 'models.dart';
import 'firebase_service.dart';

class HomeScreen extends StatefulWidget {
  const HomeScreen({super.key});
  @override
  State<HomeScreen> createState() => _HomeScreenState();
}

class _HomeScreenState extends State<HomeScreen> {
  final _service = FirebaseService();

  DeviceState    _state  = const DeviceState();
  List<LogEntry> _logs   = [];
  String?        _sendingDevice;

  // Optimistic UI
  bool? _optR1;
  bool? _optR2;
  bool get _r1 => _optR1 ?? _state.relay1;
  bool get _r2 => _optR2 ?? _state.relay2;

  Timer? _uiTimer;

  // Online: dựa vào last_seen từ Firebase server timestamp
  // Nếu last_seen < 50s trước → Online (heartbeat 15s × 3 + buffer)
  bool get _isOnline {
    if (_state.lastSeen == 0) return false;
    final elapsed =
        DateTime.now().millisecondsSinceEpoch - _state.lastSeen;
    return elapsed < 40000;
  }

  late StreamSubscription<DeviceState>    _stateSub;
  late StreamSubscription<List<LogEntry>> _logsSub;

  @override
  void initState() {
    super.initState();
    _stateSub = _service.deviceStateStream.listen((s) {
      setState(() {
        _state = s;
        if (_optR1 != null && _optR1 == s.relay1) _optR1 = null;
        if (_optR2 != null && _optR2 == s.relay2) _optR2 = null;
      });
    });
    _logsSub = _service.logsStream.listen(
          (l) => setState(() => _logs = l),
    );
    _service.startListening();

    _uiTimer = Timer.periodic(
      const Duration(seconds: 10),
          (_) { if (mounted) setState(() {}); },
    );
  }

  @override
  void dispose() {
    _stateSub.cancel();
    _logsSub.cancel();
    _uiTimer?.cancel();

    _service.dispose();
    super.dispose();
  }

  // ── Gửi lệnh ─────────────────────────────────────────────
  Future<void> _sendCommand(String command, String deviceKey) async {
    if (_sendingDevice != null) return;
    setState(() => _sendingDevice = deviceKey);
    final ok = await _service.sendCommand(command);
    if (mounted) {
      setState(() => _sendingDevice = null);
      if (!ok) {
        setState(() { _optR1 = null; _optR2 = null; });
        ScaffoldMessenger.of(context).showSnackBar(const SnackBar(
          content: Text('Không gửi được lệnh. Kiểm tra kết nối mạng.'),
          backgroundColor: Colors.red,
          duration: Duration(seconds: 3),
        ));
      }
    }
  }

  void _toggleRelay1() {
    if (_sendingDevice != null) return;
    final newR1 = !_r1;
    setState(() => _optR1 = newR1);
    _sendCommand(newR1 ? 'bat_den' : 'tat_den', 'relay1');
  }

  void _toggleRelay2() {
    if (_sendingDevice != null) return;
    final newR2 = !_r2;
    setState(() => _optR2 = newR2);
    _sendCommand(newR2 ? 'bat_quat' : 'tat_quat', 'relay2');
  }

  void _allOn()  {
    if (_sendingDevice != null) return;
    setState(() { _optR1 = true;  _optR2 = true;  });
    _sendCommand('bat_het', 'all_on');
  }

  void _allOff() {
    if (_sendingDevice != null) return;
    setState(() { _optR1 = false; _optR2 = false; });
    _sendCommand('tat_het', 'all_off');
  }

  // ── Build ─────────────────────────────────────────────────
  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return Scaffold(
      backgroundColor: scheme.surfaceContainerLowest,
      appBar: AppBar(
        backgroundColor: scheme.surfaceContainerLowest,
        title: const Text('Điều Khiển Thông Minh',
            style: TextStyle(fontWeight: FontWeight.w600)),
        actions: [
          _OnlineBadge(isOnline: _isOnline),
          const SizedBox(width: 12),
        ],
      ),
      body: RefreshIndicator(
        onRefresh: () async =>
        await Future.delayed(const Duration(milliseconds: 500)),
        child: ListView(
          padding: const EdgeInsets.symmetric(horizontal: 16, vertical: 8),
          children: [
            _SectionLabel(label: 'Thiết bị'),
            const SizedBox(height: 8),
            Row(children: [
              Expanded(child: _DeviceCard(
                icon: Icons.lightbulb_outline_rounded,
                activeIcon: Icons.lightbulb_rounded,
                label: 'Đèn', isOn: _r1,
                isLoading: _sendingDevice == 'relay1',
                enabled: _isOnline,
                activeColor: const Color(0xFFFFA000),
                onToggle: _toggleRelay1,
              )),
              const SizedBox(width: 12),
              Expanded(child: _DeviceCard(
                icon: Icons.wind_power_outlined,
                activeIcon: Icons.wind_power_rounded,
                label: 'Quạt', isOn: _r2,
                isLoading: _sendingDevice == 'relay2',
                enabled: _isOnline,
                activeColor: const Color(0xFF0288D1),
                onToggle: _toggleRelay2,
              )),
            ]),
            const SizedBox(height: 14),
            Row(children: [
              Expanded(child: _ActionButton(
                label: 'Bật tất cả', icon: Icons.power_rounded,
                color: Colors.green, enabled: _isOnline,
                isLoading: _sendingDevice == 'all_on', onPressed: _allOn,
              )),
              const SizedBox(width: 10),
              Expanded(child: _ActionButton(
                label: 'Tắt tất cả', icon: Icons.power_off_rounded,
                color: Colors.red, enabled: _isOnline,
                isLoading: _sendingDevice == 'all_off', onPressed: _allOff,
              )),
            ]),
            const SizedBox(height: 24),
            _SectionLabel(label: 'Lịch sử hoạt động'),
            const SizedBox(height: 8),
            if (_logs.isEmpty) _EmptyHistory()
            else ...(_logs.map((e) => _LogTile(entry: e))),
            const SizedBox(height: 24),
          ],
        ),
      ),
    );
  }
}

// ── Widgets ───────────────────────────────────────────────────

class _OnlineBadge extends StatelessWidget {
  final bool isOnline;
  const _OnlineBadge({required this.isOnline});
  @override
  Widget build(BuildContext context) {
    final color = isOnline ? Colors.green : Colors.red;
    return AnimatedContainer(
      duration: const Duration(milliseconds: 300),
      padding: const EdgeInsets.symmetric(horizontal: 10, vertical: 4),
      decoration: BoxDecoration(
        color: color.withAlpha(30),
        borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color, width: 1),
      ),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Container(width: 7, height: 7,
            decoration: BoxDecoration(shape: BoxShape.circle, color: color)),
        const SizedBox(width: 5),
        Text(isOnline ? 'Online' : 'Offline',
            style: TextStyle(fontSize: 12, fontWeight: FontWeight.w600,
                color: isOnline ? Colors.green[700] : Colors.red[700])),
      ]),
    );
  }
}

class _SectionLabel extends StatelessWidget {
  final String label;
  const _SectionLabel({required this.label});
  @override
  Widget build(BuildContext context) => Text(label.toUpperCase(),
      style: Theme.of(context).textTheme.labelMedium?.copyWith(
          letterSpacing: 1.2, fontWeight: FontWeight.w700,
          color: Theme.of(context).colorScheme.primary));
}

class _DeviceCard extends StatelessWidget {
  final IconData icon, activeIcon;
  final String label;
  final bool isOn, isLoading;
  final bool enabled;
  final Color activeColor;
  final VoidCallback onToggle;
  const _DeviceCard({required this.icon, required this.activeIcon,
    required this.label, required this.isOn, required this.isLoading,
    this.enabled = true, required this.activeColor, required this.onToggle});
  @override
  Widget build(BuildContext context) {
    final scheme = Theme.of(context).colorScheme;
    return AnimatedContainer(
      duration: const Duration(milliseconds: 200),
      decoration: BoxDecoration(
        color: isOn ? activeColor.withAlpha(25) : scheme.surfaceContainerLow,
        borderRadius: BorderRadius.circular(16),
        border: Border.all(
            color: isOn ? activeColor.withAlpha(128) : Colors.transparent,
            width: 1.5),
        boxShadow: isOn ? [BoxShadow(color: activeColor.withAlpha(40),
            blurRadius: 12, offset: const Offset(0, 4))] : [],
      ),
      child: InkWell(
        borderRadius: BorderRadius.circular(16),
        onTap: (isLoading || !enabled) ? null : onToggle,
        child: Padding(padding: const EdgeInsets.all(16), child: Column(
          crossAxisAlignment: CrossAxisAlignment.start,
          children: [
            Row(mainAxisAlignment: MainAxisAlignment.spaceBetween, children: [
              Icon(isOn ? activeIcon : icon, size: 32,
                  color: isOn ? activeColor : scheme.onSurfaceVariant),
              isLoading
                  ? SizedBox(width: 20, height: 20,
                  child: CircularProgressIndicator(strokeWidth: 2, color: activeColor))
                  : Switch.adaptive(value: isOn, onChanged: (enabled && !isLoading) ? (_) => onToggle() : null,
                  activeColor: activeColor,
                  materialTapTargetSize: MaterialTapTargetSize.shrinkWrap),
            ]),
            const SizedBox(height: 8),
            Text(label, style: Theme.of(context).textTheme.titleMedium?.copyWith(
                fontWeight: FontWeight.w600,
                color: isOn ? activeColor : scheme.onSurface)),
            const SizedBox(height: 2),
            Text(isOn ? 'Đang bật' : 'Đang tắt',
                style: Theme.of(context).textTheme.bodySmall?.copyWith(
                    color: isOn ? activeColor.withAlpha(204) : scheme.onSurfaceVariant)),
          ],
        )),
      ),
    );
  }
}

class _ActionButton extends StatelessWidget {
  final String label;
  final IconData icon;
  final Color color;
  final bool isLoading;
  final bool enabled;
  final VoidCallback onPressed;
  const _ActionButton({required this.label, required this.icon,
    required this.color, required this.isLoading,
    this.enabled = true, required this.onPressed});
  @override
  Widget build(BuildContext context) {
    final active = enabled && !isLoading;
    return OutlinedButton.icon(
      style: OutlinedButton.styleFrom(
        backgroundColor: active ? color.withAlpha(20) : Colors.transparent,
        foregroundColor: active ? color : Colors.grey,
        side: BorderSide(color: active ? color.withAlpha(180) : Colors.grey.withAlpha(80)),
        shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
        padding: const EdgeInsets.symmetric(vertical: 12),
      ),
      onPressed: active ? onPressed : null,
      icon: isLoading
          ? SizedBox(width: 16, height: 16,
          child: CircularProgressIndicator(strokeWidth: 2, color: color))
          : Icon(icon, size: 18),
      label: Text(label, style: const TextStyle(fontWeight: FontWeight.w600)),
    );
  }
}

class _EmptyHistory extends StatelessWidget {
  @override
  Widget build(BuildContext context) => Container(
    padding: const EdgeInsets.symmetric(vertical: 32),
    alignment: Alignment.center,
    child: Column(children: [
      Icon(Icons.history_rounded, size: 40,
          color: Theme.of(context).colorScheme.onSurfaceVariant.withAlpha(100)),
      const SizedBox(height: 8),
      Text('Chưa có hoạt động nào',
          style: Theme.of(context).textTheme.bodyMedium?.copyWith(
              color: Theme.of(context).colorScheme.onSurfaceVariant.withAlpha(160))),
    ]),
  );
}

class _LogTile extends StatelessWidget {
  final LogEntry entry;
  const _LogTile({required this.entry});
  @override
  Widget build(BuildContext context) {
    final scheme  = Theme.of(context).colorScheme;
    final timeStr = DateFormat('HH:mm  dd/MM/yyyy').format(entry.receivedAt);
    return Padding(
      padding: const EdgeInsets.only(bottom: 6),
      child: Container(
        padding: const EdgeInsets.symmetric(horizontal: 14, vertical: 10),
        decoration: BoxDecoration(
          color: scheme.surfaceContainerLow,
          borderRadius: BorderRadius.circular(12),
          border: Border.all(color: scheme.outlineVariant.withAlpha(128)),
        ),
        child: Row(children: [
          Container(width: 36, height: 36,
              decoration: BoxDecoration(color: scheme.primaryContainer,
                  shape: BoxShape.circle),
              alignment: Alignment.center,
              child: Text(entry.commandIcon, style: const TextStyle(fontSize: 16))),
          const SizedBox(width: 12),
          Expanded(child: Column(
            crossAxisAlignment: CrossAxisAlignment.start,
            children: [
              Text(entry.commandLabel,
                  style: Theme.of(context).textTheme.bodyMedium?.copyWith(
                      fontWeight: FontWeight.w600, color: scheme.onSurface)),
              const SizedBox(height: 2),
              Text(timeStr,
                  style: Theme.of(context).textTheme.labelSmall?.copyWith(
                      color: scheme.onSurfaceVariant)),
            ],
          )),
          _SourceBadge(isVoice: entry.isVoice),
        ]),
      ),
    );
  }
}

class _SourceBadge extends StatelessWidget {
  final bool isVoice;
  const _SourceBadge({required this.isVoice});
  @override
  Widget build(BuildContext context) {
    final color = isVoice ? Colors.deepPurple : Colors.teal;
    return Container(
      padding: const EdgeInsets.symmetric(horizontal: 8, vertical: 3),
      decoration: BoxDecoration(
        color: color.withAlpha(30), borderRadius: BorderRadius.circular(20),
        border: Border.all(color: color.withAlpha(100)),
      ),
      child: Row(mainAxisSize: MainAxisSize.min, children: [
        Icon(isVoice ? Icons.mic_rounded : Icons.smartphone_rounded,
            size: 11, color: color),
        const SizedBox(width: 3),
        Text(isVoice ? 'Giọng nói' : 'Ứng dụng',
            style: TextStyle(fontSize: 10, fontWeight: FontWeight.w600,
                color: color)),
      ]),
    );
  }
}