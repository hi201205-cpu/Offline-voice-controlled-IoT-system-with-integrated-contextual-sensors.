import 'package:flutter/material.dart';
import 'firebase_service.dart';

class AuthScreen extends StatefulWidget {
  final VoidCallback onLoginSuccess;
  const AuthScreen({super.key, required this.onLoginSuccess});

  @override
  State<AuthScreen> createState() => _AuthScreenState();
}

class _AuthScreenState extends State<AuthScreen> with SingleTickerProviderStateMixin {
  final _service = FirebaseService();
  late TabController _tabController;

  // Controllers Đăng nhập
  final _loginEmailCtrl = TextEditingController();
  final _loginPassCtrl  = TextEditingController();
  bool _loginPassObscure = true;
  bool _isLoadingLogin   = false;

  // Controllers Đăng ký
  final _regNameCtrl       = TextEditingController();
  final _regEmailCtrl      = TextEditingController();
  final _regPassCtrl       = TextEditingController();
  final _regConfirmPassCtrl= TextEditingController();
  final _regInviteCtrl     = TextEditingController();
  bool _regPassObscure     = true;
  bool _regConfirmObscure  = true;
  bool _isLoadingReg       = false;

  @override
  void initState() {
    super.initState();
    _tabController = TabController(length: 2, vsync: this);
  }

  @override
  void dispose() {
    _tabController.dispose();
    _loginEmailCtrl.dispose();
    _loginPassCtrl.dispose();
    _regNameCtrl.dispose();
    _regEmailCtrl.dispose();
    _regPassCtrl.dispose();
    _regConfirmPassCtrl.dispose();
    _regInviteCtrl.dispose();
    super.dispose();
  }

  // ── XỬ LÝ ĐĂNG NHẬP ──────────────────────────────────────
  Future<void> _handleLogin() async {
    final email = _loginEmailCtrl.text.trim();
    final pass  = _loginPassCtrl.text;

    if (email.isEmpty || pass.isEmpty) {
      _showSnackBar('Vui lòng nhập đầy đủ Email và Mật khẩu', isError: true);
      return;
    }

    setState(() => _isLoadingLogin = true);
    try {
      await _service.signInWithEmail(email, pass);
      if (mounted) {
        widget.onLoginSuccess();
      }
    } catch (e) {
      if (mounted) _showSnackBar(e.toString(), isError: true);
    } finally {
      if (mounted) setState(() => _isLoadingLogin = false);
    }
  }

  // ── XỬ LÝ QUÊN MẬT KHẨU ──────────────────────────────────
  Future<void> _handleForgotPassword() async {
    final email = _loginEmailCtrl.text.trim();
    if (email.isEmpty) {
      _showSnackBar('Vui lòng nhập Email của bạn vào ô trên trước khi bấm Quên mật khẩu!', isError: true);
      return;
    }

    final confirmed = await showDialog<bool>(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Text('Khôi phục mật khẩu'),
        content: Text('Hệ thống sẽ gửi một liên kết đặt lại mật khẩu an toàn tới email:\n\n$email\n\nBạn có muốn tiếp tục?'),
        actions: [
          TextButton(
            onPressed: () => Navigator.pop(ctx, false),
            child: const Text('Hủy'),
          ),
          FilledButton(
            onPressed: () => Navigator.pop(ctx, true),
            child: const Text('Gửi email'),
          ),
        ],
      ),
    );

    if (confirmed != true) return;

    try {
      await _service.sendPasswordResetEmail(email);
      if (mounted) {
        showDialog(
          context: context,
          builder: (ctx) => AlertDialog(
            icon: const Icon(Icons.mark_email_read_rounded, color: Colors.green, size: 48),
            title: const Text('Đã gửi email khôi phục!'),
            content: Text('Vui lòng kiểm tra hộp thư đến (hoặc hòm thư Spam) của $email và nhấn vào liên kết bên trong để tạo mật khẩu mới.'),
            actions: [
              FilledButton(
                onPressed: () => Navigator.pop(ctx),
                child: const Text('Đã hiểu'),
              ),
            ],
          ),
        );
      }
    } catch (e) {
      if (mounted) _showSnackBar(e.toString(), isError: true);
    }
  }

  // ── XỬ LÝ ĐĂNG KÝ ────────────────────────────────────────
  Future<void> _handleRegister() async {
    final name    = _regNameCtrl.text.trim();
    final email   = _regEmailCtrl.text.trim();
    final pass    = _regPassCtrl.text;
    final confirm = _regConfirmPassCtrl.text;
    final invite  = _regInviteCtrl.text.trim();

    if (email.isEmpty || pass.isEmpty) {
      _showSnackBar('Vui lòng nhập Email và Mật khẩu!', isError: true);
      return;
    }

    if (pass.length < 6) {
      _showSnackBar('Mật khẩu mới phải có tối thiểu 6 ký tự!', isError: true);
      return;
    }

    if (pass != confirm) {
      _showSnackBar('Xác nhận mật khẩu không khớp!', isError: true);
      return;
    }

    final isMaster = email.toLowerCase() == FirebaseService.masterEmail;
    if (!isMaster && invite.isEmpty) {
      _showSnackBar('Vui lòng nhập Mã mời do Master cấp để đăng ký!', isError: true);
      return;
    }

    setState(() => _isLoadingReg = true);
    try {
      await _service.signUpWithEmail(
        email: email,
        password: pass,
        displayName: name,
        inviteCode: isMaster ? null : invite,
      );
      if (mounted) {
        _showSnackBar('Đăng ký tài khoản thành công! Đang đăng nhập...');
        widget.onLoginSuccess();
      }
    } catch (e) {
      if (mounted) _showSnackBar(e.toString(), isError: true);
    } finally {
      if (mounted) setState(() => _isLoadingReg = false);
    }
  }

  // ── XỬ LÝ ĐĂNG NHẬP GOOGLE ───────────────────────────────
  void _handleGoogleSignIn() {
    showDialog(
      context: context,
      builder: (ctx) => AlertDialog(
        title: const Row(
          children: [
            Icon(Icons.g_mobiledata, color: Colors.blue, size: 32),
            SizedBox(width: 8),
            Text('Đăng nhập Google'),
          ],
        ),
        content: const Text(
          'Dự án hỗ trợ Đăng nhập nhanh bằng Google qua Firebase Authentication.\n\n'
          'Trên thiết bị di động, bạn có thể đăng nhập trực tiếp bằng tài khoản Email/Password của mình, hoặc liên kết tài khoản Google trên bảng điều khiển Web: https://do-an-2-f791a.web.app',
        ),
        actions: [
          FilledButton(
            onPressed: () => Navigator.pop(ctx),
            child: const Text('Đóng'),
          ),
        ],
      ),
    );
  }

  void _showSnackBar(String msg, {bool isError = false}) {
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

    return Scaffold(
      backgroundColor: scheme.surfaceContainerLowest,
      body: SafeArea(
        child: Center(
          child: SingleChildScrollView(
            padding: const EdgeInsets.symmetric(horizontal: 24, vertical: 20),
            child: ConstrainedBox(
              constraints: const BoxConstraints(maxWidth: 440),
              child: Column(
                mainAxisAlignment: MainAxisAlignment.center,
                children: [
                  // Logo & Header
                  Container(
                    width: 72,
                    height: 72,
                    decoration: BoxDecoration(
                      color: scheme.primary.withAlpha(25),
                      shape: BoxShape.circle,
                    ),
                    child: Icon(Icons.home_work_rounded, size: 38, color: scheme.primary),
                  ),
                  const SizedBox(height: 14),
                  const Text(
                    'Smart Home IoT',
                    style: TextStyle(fontSize: 24, fontWeight: FontWeight.bold),
                  ),
                  const SizedBox(height: 4),
                  Text(
                    'Hệ thống điều khiển & nhận diện giọng nói',
                    style: TextStyle(color: scheme.outline, fontSize: 13.5),
                  ),
                  const SizedBox(height: 24),

                  // Segmented Tabs
                  Container(
                    decoration: BoxDecoration(
                      color: scheme.surfaceContainerHigh,
                      borderRadius: BorderRadius.circular(14),
                    ),
                    child: TabBar(
                      controller: _tabController,
                      indicatorSize: TabBarIndicatorSize.tab,
                      indicator: BoxDecoration(
                        color: scheme.primary,
                        borderRadius: BorderRadius.circular(12),
                      ),
                      labelColor: scheme.onPrimary,
                      unselectedLabelColor: scheme.onSurfaceVariant,
                      dividerColor: Colors.transparent,
                      tabs: const [
                        Tab(text: 'Đăng nhập'),
                        Tab(text: 'Đăng ký'),
                      ],
                    ),
                  ),
                  const SizedBox(height: 20),

                  // Tab views inside sized container
                  SizedBox(
                    height: 420,
                    child: TabBarView(
                      controller: _tabController,
                      children: [
                        _buildLoginForm(scheme),
                        _buildRegisterForm(scheme),
                      ],
                    ),
                  ),
                ],
              ),
            ),
          ),
        ),
      ),
    );
  }

  // ── FORM ĐĂNG NHẬP ───────────────────────────────────────
  Widget _buildLoginForm(ColorScheme scheme) {
    return ListView(
      physics: const ClampingScrollPhysics(),
      children: [
        TextField(
          controller: _loginEmailCtrl,
          keyboardType: TextInputType.emailAddress,
          decoration: InputDecoration(
            labelText: 'Email tài khoản',
            prefixIcon: const Icon(Icons.email_outlined),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        const SizedBox(height: 14),
        TextField(
          controller: _loginPassCtrl,
          obscureText: _loginPassObscure,
          decoration: InputDecoration(
            labelText: 'Mật khẩu',
            prefixIcon: const Icon(Icons.lock_outline),
            suffixIcon: IconButton(
              icon: Icon(_loginPassObscure ? Icons.visibility_off : Icons.visibility),
              onPressed: () => setState(() => _loginPassObscure = !_loginPassObscure),
            ),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        Align(
          alignment: Alignment.centerRight,
          child: TextButton(
            onPressed: _handleForgotPassword,
            child: const Text('Quên mật khẩu?', style: TextStyle(fontWeight: FontWeight.w600)),
          ),
        ),
        const SizedBox(height: 6),
        FilledButton(
          onPressed: _isLoadingLogin ? null : _handleLogin,
          style: FilledButton.styleFrom(
            padding: const EdgeInsets.symmetric(vertical: 14),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
          child: _isLoadingLogin
              ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
              : const Text('Đăng nhập', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
        ),
        const SizedBox(height: 16),
        Row(
          children: [
            Expanded(child: Divider(color: scheme.outlineVariant)),
            Padding(
              padding: const EdgeInsets.symmetric(horizontal: 12),
              child: Text('hoặc', style: TextStyle(color: scheme.outline, fontSize: 13)),
            ),
            Expanded(child: Divider(color: scheme.outlineVariant)),
          ],
        ),
        const SizedBox(height: 16),
        OutlinedButton.icon(
          onPressed: _handleGoogleSignIn,
          icon: const Icon(Icons.g_mobiledata, size: 26, color: Colors.red),
          label: const Text('Đăng nhập với Google', style: TextStyle(fontWeight: FontWeight.w600)),
          style: OutlinedButton.styleFrom(
            padding: const EdgeInsets.symmetric(vertical: 12),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
      ],
    );
  }

  // ── FORM ĐĂNG KÝ ─────────────────────────────────────────
  Widget _buildRegisterForm(ColorScheme scheme) {
    return ListView(
      physics: const ClampingScrollPhysics(),
      children: [
        TextField(
          controller: _regNameCtrl,
          decoration: InputDecoration(
            labelText: 'Họ và tên',
            prefixIcon: const Icon(Icons.person_outline),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _regEmailCtrl,
          keyboardType: TextInputType.emailAddress,
          decoration: InputDecoration(
            labelText: 'Email',
            prefixIcon: const Icon(Icons.email_outlined),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _regPassCtrl,
          obscureText: _regPassObscure,
          decoration: InputDecoration(
            labelText: 'Mật khẩu (tối thiểu 6 ký tự)',
            prefixIcon: const Icon(Icons.lock_outline),
            suffixIcon: IconButton(
              icon: Icon(_regPassObscure ? Icons.visibility_off : Icons.visibility),
              onPressed: () => setState(() => _regPassObscure = !_regPassObscure),
            ),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _regConfirmPassCtrl,
          obscureText: _regConfirmObscure,
          decoration: InputDecoration(
            labelText: 'Nhập lại mật khẩu',
            prefixIcon: const Icon(Icons.lock_outline),
            suffixIcon: IconButton(
              icon: Icon(_regConfirmObscure ? Icons.visibility_off : Icons.visibility),
              onPressed: () => setState(() => _regConfirmObscure = !_regConfirmObscure),
            ),
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        const SizedBox(height: 12),
        TextField(
          controller: _regInviteCtrl,
          textCapitalization: TextCapitalization.characters,
          decoration: InputDecoration(
            labelText: 'Mã mời thành viên (Master không cần)',
            prefixIcon: const Icon(Icons.vpn_key_outlined),
            helperText: 'Thành viên cần mã mời do Master cấp',
            border: OutlineInputBorder(borderRadius: BorderRadius.circular(12)),
          ),
        ),
        const SizedBox(height: 14),
        FilledButton(
          onPressed: _isLoadingReg ? null : _handleRegister,
          style: FilledButton.styleFrom(
            padding: const EdgeInsets.symmetric(vertical: 14),
            shape: RoundedRectangleBorder(borderRadius: BorderRadius.circular(12)),
          ),
          child: _isLoadingReg
              ? const SizedBox(width: 20, height: 20, child: CircularProgressIndicator(strokeWidth: 2, color: Colors.white))
              : const Text('Đăng ký tài khoản', style: TextStyle(fontSize: 16, fontWeight: FontWeight.bold)),
        ),
      ],
    );
  }
}
