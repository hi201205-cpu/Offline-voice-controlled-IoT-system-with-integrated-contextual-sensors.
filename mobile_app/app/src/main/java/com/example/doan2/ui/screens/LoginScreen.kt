package com.example.doan2.ui.screens

import android.app.Activity
import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doan2.R
import com.example.doan2.ui.components.AuroraBackground
import com.example.doan2.ui.components.GlassCard
import com.example.doan2.ui.theme.*
import com.example.doan2.viewmodel.AuthViewModel
import com.google.android.gms.auth.api.signin.GoogleSignIn
import com.google.android.gms.auth.api.signin.GoogleSignInOptions
import com.google.android.gms.common.api.ApiException

@Composable
fun LoginScreen(
    authViewModel: AuthViewModel,
    onLoginSuccess: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by authViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var passwordVisible by remember { mutableStateOf(false) }

    val gso = remember {
        GoogleSignInOptions.Builder(GoogleSignInOptions.DEFAULT_SIGN_IN)
            .requestIdToken("828571400201-c5c12h0hcbcuku4avqqgjf6hlsaml0fu.apps.googleusercontent.com")
            .requestEmail()
            .build()
    }
    val googleSignInClient = remember { GoogleSignIn.getClient(context, gso) }

    val googleSignInLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        val task = GoogleSignIn.getSignedInAccountFromIntent(result.data)
        try {
            val account = task.getResult(ApiException::class.java)
            val idToken = account?.idToken
            if (!idToken.isNullOrEmpty()) {
                authViewModel.loginWithGoogle(idToken, onLoginSuccess)
            } else {
                Toast.makeText(context, "Không nhận được ID Token từ Google", Toast.LENGTH_SHORT).show()
            }
        } catch (e: ApiException) {
            if (e.statusCode == 12501) {
                // User cancelled sign-in dialog
                return@rememberLauncherForActivityResult
            }
            val errorMsg = when (e.statusCode) {
                12500 -> "Lỗi Google (12500): Vui lòng đăng nhập Google Play trên máy và thêm SHA-1 vào Firebase Console."
                10 -> "Lỗi cấu hình (10: DEVELOPER_ERROR): Chưa thêm mã chứng chỉ SHA-1 của máy vào Firebase Console."
                7 -> "Lỗi kết nối mạng khi xác thực Google (Code 7)."
                else -> "Lỗi đăng nhập Google (${e.statusCode}): ${e.localizedMessage ?: "Thất bại"}"
            }
            Toast.makeText(context, errorMsg, Toast.LENGTH_LONG).show()
        }
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            authViewModel.clearToastMessage()
        }
    }

    val themeController = LocalThemeController.current
    val isDark = when (themeController.themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    AuroraBackground(modifier = modifier) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(24.dp),
            contentAlignment = Alignment.Center
        ) {
            GlassCard(
                shape = RoundedCornerShape(28.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .fillMaxHeight(0.92f)
            ) {
                val scrollState = rememberScrollState()
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(24.dp)
                        .verticalScroll(scrollState),
                    horizontalAlignment = Alignment.CenterHorizontally
                ) {
                    Surface(
                        shape = RoundedCornerShape(18.dp),
                        color = AuroraAccentStart.copy(alpha = 0.25f),
                        modifier = Modifier.size(64.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = Icons.Default.Home,
                                contentDescription = stringResource(R.string.login_title),
                                tint = AuroraAccentEnd,
                                modifier = Modifier.size(36.dp)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    Text(
                        text = stringResource(R.string.app_name),
                        fontSize = 24.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground
                    )

                    Text(
                        text = stringResource(R.string.login_subtitle),
                        fontSize = 13.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                    )

                    Spacer(modifier = Modifier.height(20.dp))

                    // Mode Switch Tabs (Sign In / Sign Up)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(44.dp)
                            .background(
                                if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFF152C45).copy(alpha = 0.06f),
                                RoundedCornerShape(12.dp)
                            )
                            .padding(4.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    if (!uiState.isSignUpMode) AuroraAccentStart else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { if (uiState.isSignUpMode) authViewModel.toggleAuthMode() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Đăng nhập",
                                fontSize = 14.sp,
                                fontWeight = if (!uiState.isSignUpMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (!uiState.isSignUpMode) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                            )
                        }

                        Box(
                            modifier = Modifier
                                .weight(1f)
                                .fillMaxHeight()
                                .background(
                                    if (uiState.isSignUpMode) AuroraAccentStart else Color.Transparent,
                                    RoundedCornerShape(10.dp)
                                )
                                .clickable { if (!uiState.isSignUpMode) authViewModel.toggleAuthMode() },
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = "Đăng ký mới",
                                fontSize = 14.sp,
                                fontWeight = if (uiState.isSignUpMode) FontWeight.Bold else FontWeight.Medium,
                                color = if (uiState.isSignUpMode) Color.White else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Field 1: Họ và tên chủ nhà (Only in Sign Up Mode)
                    if (uiState.isSignUpMode) {
                        OutlinedTextField(
                            value = uiState.fullName,
                            onValueChange = { authViewModel.onFullNameChanged(it) },
                            label = { Text("Họ và tên chủ nhà") },
                            placeholder = { Text("Nguyễn Văn A") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.AccountCircle,
                                    contentDescription = "Họ và tên"
                                )
                            },
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Text,
                                imeAction = ImeAction.Next
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                        Spacer(modifier = Modifier.height(12.dp))
                    }

                    // Field 2: Email Input
                    OutlinedTextField(
                        value = uiState.email,
                        onValueChange = { authViewModel.onEmailChanged(it) },
                        label = { Text(if (uiState.isSignUpMode) "Email đăng ký" else "Tài khoản Email") },
                        placeholder = { Text("example@email.com") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Email,
                                contentDescription = stringResource(R.string.email)
                            )
                        },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Next
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    Spacer(modifier = Modifier.height(12.dp))

                    // Field 3: Password Input
                    OutlinedTextField(
                        value = uiState.pass,
                        onValueChange = { authViewModel.onPassChanged(it) },
                        label = { Text(if (uiState.isSignUpMode) "Mật khẩu (tối thiểu 6 ký tự)" else "Mật khẩu") },
                        placeholder = { Text(if (uiState.isSignUpMode) "Tạo mật khẩu an toàn" else "Nhập mật khẩu") },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Default.Lock,
                                contentDescription = stringResource(R.string.password)
                            )
                        },
                        trailingIcon = {
                            IconButton(onClick = { passwordVisible = !passwordVisible }) {
                                Icon(
                                    imageVector = if (passwordVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                    contentDescription = if (passwordVisible) "Ẩn mật khẩu" else "Hiện mật khẩu"
                                )
                            }
                        },
                        visualTransformation = if (passwordVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Password,
                            imeAction = if (uiState.isSignUpMode) ImeAction.Next else ImeAction.Done
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier.fillMaxWidth()
                    )

                    // Field 4: Confirm Password (Only in Sign Up Mode)
                    if (uiState.isSignUpMode) {
                        var confirmPassVisible by remember { mutableStateOf(false) }
                        Spacer(modifier = Modifier.height(12.dp))
                        OutlinedTextField(
                            value = uiState.confirmPass,
                            onValueChange = { authViewModel.onConfirmPassChanged(it) },
                            label = { Text("Xác nhận mật khẩu") },
                            placeholder = { Text("Nhập lại mật khẩu") },
                            leadingIcon = {
                                Icon(
                                    imageVector = Icons.Default.Lock,
                                    contentDescription = "Xác nhận mật khẩu"
                                )
                            },
                            trailingIcon = {
                                IconButton(onClick = { confirmPassVisible = !confirmPassVisible }) {
                                    Icon(
                                        imageVector = if (confirmPassVisible) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                                        contentDescription = if (confirmPassVisible) "Ẩn" else "Hiện"
                                    )
                                }
                            },
                            visualTransformation = if (confirmPassVisible) VisualTransformation.None else PasswordVisualTransformation(),
                            singleLine = true,
                            keyboardOptions = KeyboardOptions(
                                keyboardType = KeyboardType.Password,
                                imeAction = ImeAction.Done
                            ),
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Forgot Password Link Button (Only in Login Mode)
                    if (!uiState.isSignUpMode) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            Text(
                                text = "Quên mật khẩu?",
                                fontSize = 13.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = AuroraAccentEnd,
                                modifier = Modifier
                                    .clickable { authViewModel.onForgotPasswordClicked() }
                                    .padding(vertical = 4.dp, horizontal = 2.dp)
                            )
                        }
                    }

                    // Error message
                    if (uiState.errorMessage != null) {
                        Spacer(modifier = Modifier.height(10.dp))
                        Text(
                            text = uiState.errorMessage ?: "",
                            color = StateError,
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium
                        )
                    }

                    Spacer(modifier = Modifier.height(20.dp))

                    // Submit Button
                    Button(
                        onClick = { authViewModel.loginOrRegister(onLoginSuccess) },
                        enabled = !uiState.isLoading,
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AuroraAccentStart,
                            contentColor = Color.White
                        ),
                        shape = RoundedCornerShape(14.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        if (uiState.isLoading) {
                            CircularProgressIndicator(
                                color = Color.White,
                                modifier = Modifier.size(22.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text(
                                    text = if (uiState.isSignUpMode) "Tạo tài khoản mới" else "Đăng nhập hệ thống",
                                    fontSize = 16.sp,
                                    fontWeight = FontWeight.Bold
                                )
                                Spacer(modifier = Modifier.width(8.dp))
                                Text(text = "→", fontSize = 16.sp, fontWeight = FontWeight.Bold)
                            }
                        }
                    }

                    // Divider & Google Sign-In Button (shown in both login and register)
                    Spacer(modifier = Modifier.height(16.dp))

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDark) Color.White.copy(alpha = 0.2f) else Color(0xFF366795).copy(alpha = 0.2f))
                        Text(
                            text = " hoặc tiếp tục với ",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                            modifier = Modifier.padding(horizontal = 8.dp)
                        )
                        HorizontalDivider(modifier = Modifier.weight(1f), color = if (isDark) Color.White.copy(alpha = 0.2f) else Color(0xFF366795).copy(alpha = 0.2f))
                    }

                    Spacer(modifier = Modifier.height(16.dp))

                    OutlinedButton(
                        onClick = {
                            googleSignInClient.signOut().addOnCompleteListener {
                                googleSignInLauncher.launch(googleSignInClient.signInIntent)
                            }
                        },
                        enabled = !uiState.isLoading,
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, if (isDark) Color.White.copy(alpha = 0.35f) else Color(0xFF366795).copy(alpha = 0.25f)),
                        colors = ButtonDefaults.outlinedButtonColors(
                            containerColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color.White.copy(alpha = 0.9f),
                            contentColor = MaterialTheme.colorScheme.onBackground
                        ),
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(50.dp)
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.Center
                        ) {
                            Icon(
                                imageVector = Icons.Default.AccountCircle,
                                contentDescription = "Google",
                                tint = AuroraAccentEnd,
                                modifier = Modifier.size(24.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = "Đăng nhập với Google",
                                fontSize = 15.sp,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(14.dp))
                    Text(
                        text = "Tài khoản tự động lưu & xác thực bảo mật qua Firebase Auth.",
                        fontSize = 11.sp,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                        textAlign = androidx.compose.ui.text.style.TextAlign.Center
                    )
                }
            }
        }
    }

    // Forgot Password Dialog
    if (uiState.showForgotPasswordDialog) {
        var emailInput by remember { mutableStateOf(uiState.email) }
        AlertDialog(
            onDismissRequest = { authViewModel.closeForgotPasswordDialog() },
            title = {
                Text(
                    text = "Khôi phục mật khẩu",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column {
                    Text(
                        text = "Nhập địa chỉ email của bạn. Hệ thống sẽ gửi email chứa đường link để bạn đặt lại mật khẩu mới.",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(14.dp))
                    OutlinedTextField(
                        value = emailInput,
                        onValueChange = {
                            emailInput = it
                            authViewModel.onEmailChanged(it)
                        },
                        label = { Text("Email nhận link khôi phục") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Email,
                            imeAction = ImeAction.Done
                        ),
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        authViewModel.onEmailChanged(emailInput)
                        authViewModel.confirmSendPasswordReset()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = AuroraAccentStart)
                ) {
                    Text("Gửi mail khôi phục")
                }
            },
            dismissButton = {
                TextButton(
                    onClick = { authViewModel.closeForgotPasswordDialog() }
                ) {
                    Text("Hủy")
                }
            }
        )
    }
}
