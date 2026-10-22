package com.example.doan2.ui.screens

import android.widget.Toast
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
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
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.example.doan2.R
import com.example.doan2.ui.components.ConnectionBanner
import com.example.doan2.ui.components.DoorAlertBanner
import com.example.doan2.ui.components.GlassCard
import com.example.doan2.ui.theme.*
import com.example.doan2.viewmodel.DoorViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DoorScreen(
    doorViewModel: DoorViewModel,
    modifier: Modifier = Modifier
) {
    val uiState by doorViewModel.uiState.collectAsState()
    val context = LocalContext.current
    var showFallbackConfirmDialog by remember { mutableStateOf(false) }
    var showKeypadUnlockConfirmDialog by remember { mutableStateOf(false) }

    if (showKeypadUnlockConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showKeypadUnlockConfirmDialog = false },
            title = {
                Text(
                    text = "Xác nhận mở khóa bàn phím",
                    fontWeight = FontWeight.Bold,
                    fontSize = 17.sp
                )
            },
            text = {
                Text(
                    text = "Bàn phím ngoài đang bị khóa an ninh do nhập sai mật khẩu 4 lần. Bạn có chắc chắn muốn mở khóa bàn phím ngay bây giờ không?",
                    fontSize = 14.sp
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showKeypadUnlockConfirmDialog = false
                        doorViewModel.unlockKeypad()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StateError)
                ) {
                    Text("Xác nhận mở khóa", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showKeypadUnlockConfirmDialog = false }
                ) {
                    Text("Hủy")
                }
            }
        )
    }

    LaunchedEffect(uiState.toastMessage) {
        uiState.toastMessage?.let {
            Toast.makeText(context, it, Toast.LENGTH_SHORT).show()
            doorViewModel.clearToastMessage()
        }
    }

    val isDoorOpen = uiState.deviceState.door == "open"
    val isOnline = doorViewModel.isOnline

    // Biometric Launcher
    val triggerBiometricOrFallback = {
        val activity = context as? FragmentActivity
        if (activity != null) {
            val biometricManager = BiometricManager.from(activity)
            val authenticators = BiometricManager.Authenticators.BIOMETRIC_STRONG or BiometricManager.Authenticators.DEVICE_CREDENTIAL
            val canAuth = biometricManager.canAuthenticate(authenticators)

            if (canAuth == BiometricManager.BIOMETRIC_SUCCESS) {
                val executor = ContextCompat.getMainExecutor(activity)
                val biometricPrompt = BiometricPrompt(
                    activity,
                    executor,
                    object : BiometricPrompt.AuthenticationCallback() {
                        override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                            super.onAuthenticationSucceeded(result)
                            doorViewModel.openDoorConfirmed()
                        }

                        override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                            super.onAuthenticationError(errorCode, errString)
                            if (errorCode != BiometricPrompt.ERROR_USER_CANCELED && errorCode != BiometricPrompt.ERROR_NEGATIVE_BUTTON) {
                                Toast.makeText(activity, "Lỗi xác thực: $errString", Toast.LENGTH_SHORT).show()
                            }
                        }
                    }
                )

                val promptInfo = BiometricPrompt.PromptInfo.Builder()
                    .setTitle(activity.getString(R.string.biometric_prompt_title))
                    .setSubtitle(activity.getString(R.string.biometric_prompt_subtitle))
                    .setAllowedAuthenticators(authenticators)
                    .build()

                biometricPrompt.authenticate(promptInfo)
            } else {
                showFallbackConfirmDialog = true
            }
        } else {
            showFallbackConfirmDialog = true
        }
    }

    Column(
        modifier = modifier.fillMaxSize()
    ) {
        ConnectionBanner(
            isDisconnected = !isOnline,
            lastSeen = uiState.deviceState.esp32LastSeen
        )
        DoorAlertBanner(
            alert = null,
            keypadLocked = uiState.deviceState.keypadLocked,
            onUnlockKeypad = { doorViewModel.unlockKeypad() }
        )

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // Door Status Header Card
            item {
                GlassCard(
                    shape = RoundedCornerShape(24.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Surface(
                            shape = RoundedCornerShape(22.dp),
                            color = if (isDoorOpen) Color(0xFFEA580C).copy(alpha = 0.25f) else StateOk.copy(alpha = 0.2f),
                            modifier = Modifier.size(80.dp)
                        ) {
                            Box(contentAlignment = Alignment.Center) {
                                Icon(
                                    imageVector = if (isDoorOpen) Icons.Default.LockOpen else Icons.Default.Lock,
                                    contentDescription = if (isDoorOpen) "Cửa đang mở" else "Cửa đang đóng",
                                    tint = if (isDoorOpen) Color(0xFFFB923C) else StateOk,
                                    modifier = Modifier.size(44.dp)
                                )
                            }
                        }

                        Spacer(modifier = Modifier.height(16.dp))

                        Text(
                            text = if (isDoorOpen) "Cửa đang mở" else "Cửa đang đóng",
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Bold,
                            color = if (isDoorOpen) Color(0xFFFB923C) else StateOk
                        )

                        Spacer(modifier = Modifier.height(4.dp))

                        Text(
                            text = if (isDoorOpen) "Cửa sẽ tự động đóng và khóa sau 5 giây" else "Cửa chính hiện đang được đóng & khóa an toàn",
                            fontSize = 13.sp,
                            color = TextSecondaryDark
                        )

                        Spacer(modifier = Modifier.height(24.dp))

                        val isKeypadLocked = uiState.deviceState.keypadLocked
                        // Single Open Door Button
                        Button(
                            onClick = { 
                                if (!isKeypadLocked) {
                                    triggerBiometricOrFallback() 
                                }
                            },
                            enabled = isOnline && !isDoorOpen && !isKeypadLocked,
                            colors = ButtonDefaults.buttonColors(
                                containerColor = when {
                                    isKeypadLocked -> StateError.copy(alpha = 0.5f)
                                    isDoorOpen -> Color.Gray
                                    else -> AuroraAccentStart
                                },
                                contentColor = Color.White
                            ),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(52.dp)
                        ) {
                            Icon(
                                imageVector = if (isKeypadLocked) Icons.Default.Lock else if (isDoorOpen) Icons.Default.LockOpen else Icons.Default.Fingerprint,
                                contentDescription = "Mở cửa",
                                modifier = Modifier.size(22.dp)
                            )
                            Spacer(modifier = Modifier.width(10.dp))
                            Text(
                                text = when {
                                    isKeypadLocked -> "Bàn phím bị khóa an ninh (4 lần sai)"
                                    isDoorOpen -> "Cửa đang mở..."
                                    else -> "Mở cửa chính"
                                },
                                fontSize = 15.sp,
                                fontWeight = FontWeight.Bold
                            )
                        }

                        if (isKeypadLocked) {
                            Spacer(modifier = Modifier.height(10.dp))
                            OutlinedButton(
                                onClick = { showKeypadUnlockConfirmDialog = true },
                                colors = ButtonDefaults.outlinedButtonColors(contentColor = StateError),
                                border = androidx.compose.foundation.BorderStroke(1.dp, StateError),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth().height(44.dp)
                            ) {
                                Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(8.dp))
                                Text("Mở khóa an ninh bàn phím", fontWeight = FontWeight.Bold, fontSize = 14.sp)
                            }
                        }
                    }
                }
            }

            // Keypad Passcodes Header + Add Button
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = stringResource(R.string.passcode_section_title),
                        fontSize = 17.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        modifier = Modifier.weight(1f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.width(8.dp))

                    Button(
                        onClick = { doorViewModel.openAddDialog() },
                        shape = RoundedCornerShape(12.dp),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = AuroraAccentStart,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 6.dp)
                    ) {
                        Icon(
                            imageVector = Icons.Default.Add,
                            contentDescription = stringResource(R.string.btn_add_passcode),
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = stringResource(R.string.btn_add_passcode),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.SemiBold,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }

            // Pending Action Loading Bar
            if (uiState.isPendingAction) {
                item {
                    GlassCard(
                        shape = RoundedCornerShape(14.dp),
                        fillColor = AuroraAccentStart.copy(alpha = 0.2f),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(14.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(20.dp),
                                strokeWidth = 2.dp,
                                color = AuroraAccentEnd
                            )
                            Spacer(modifier = Modifier.width(12.dp))
                            Text(
                                text = uiState.pendingMessage ?: stringResource(R.string.processing),
                                fontSize = 13.sp,
                                color = MaterialTheme.colorScheme.onBackground,
                                fontWeight = FontWeight.Medium
                            )
                        }
                    }
                }
            }

            // Passcode List Items
            if (uiState.passcodes.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(24.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            text = "Chưa có mật khẩu nào",
                            fontSize = 14.sp,
                            color = TextSecondaryDark
                        )
                    }
                }
            } else {
                items(uiState.passcodes, key = { it.id }) { item ->
                    val createdMs = if (item.createdTs < 10_000_000_000L) item.createdTs * 1000L else item.createdTs
                    val formattedDate = if (item.createdTs > 0) {
                        SimpleDateFormat("dd/MM/yyyy HH:mm", Locale.getDefault()).format(Date(createdMs))
                    } else ""

                    GlassCard(
                        shape = RoundedCornerShape(16.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                modifier = Modifier.weight(1f),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Surface(
                                    shape = RoundedCornerShape(10.dp),
                                    color = AuroraAccentStart.copy(alpha = 0.2f),
                                    modifier = Modifier.size(38.dp)
                                ) {
                                    Box(contentAlignment = Alignment.Center) {
                                        Icon(
                                            imageVector = Icons.Default.Key,
                                            contentDescription = item.name,
                                            tint = AuroraAccentEnd,
                                            modifier = Modifier.size(20.dp)
                                        )
                                    }
                                }

                                Spacer(modifier = Modifier.width(12.dp))

                                Column {
                                    Text(
                                        text = item.name,
                                        fontSize = 15.sp,
                                        fontWeight = FontWeight.Bold,
                                        color = MaterialTheme.colorScheme.onBackground
                                    )
                                    if (formattedDate.isNotEmpty()) {
                                        Spacer(modifier = Modifier.height(2.dp))
                                        Text(
                                            text = stringResource(R.string.created_at_format, formattedDate),
                                            fontSize = 12.sp,
                                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f)
                                        )
                                    }
                                }
                            }

                            Spacer(modifier = Modifier.width(8.dp))

                            // Glass Pill Button "Xóa"
                            Button(
                                onClick = { doorViewModel.confirmDeletePasscode(item) },
                                colors = ButtonDefaults.buttonColors(
                                    containerColor = Color.White.copy(alpha = 0.12f),
                                    contentColor = Color.White
                                ),
                                shape = RoundedCornerShape(12.dp),
                                contentPadding = PaddingValues(horizontal = 14.dp, vertical = 4.dp),
                                modifier = Modifier.height(32.dp)
                            ) {
                                Text(
                                    text = stringResource(R.string.btn_delete),
                                    fontSize = 12.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }

            // Security Notice Card
            item {
                GlassCard(
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(14.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Default.Info,
                            contentDescription = "Ghi chú an toàn",
                            tint = TextSecondaryDark,
                            modifier = Modifier.size(20.dp)
                        )
                        Spacer(modifier = Modifier.width(10.dp))
                        Text(
                            text = stringResource(R.string.passcode_notice),
                            fontSize = 12.sp,
                            color = TextSecondaryDark,
                            lineHeight = 16.sp
                        )
                    }
                }
            }
        }
    }

    // Add Passcode Dialog
    if (uiState.showAddDialog) {
        var name by remember { mutableStateOf("") }
        var code by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { doorViewModel.dismissAddDialog() },
            title = {
                Text(
                    text = stringResource(R.string.add_passcode_dialog_title),
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text(stringResource(R.string.passcode_name_label)) },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Next),
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = code,
                        onValueChange = { if (it.length <= 8 && it.all { char -> char.isDigit() }) code = it },
                        label = { Text(stringResource(R.string.passcode_code_label)) },
                        singleLine = true,
                        visualTransformation = PasswordVisualTransformation(),
                        keyboardOptions = KeyboardOptions(
                            keyboardType = KeyboardType.Number,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = { doorViewModel.addPasscode(name, code) }
                ) {
                    Text(stringResource(R.string.dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { doorViewModel.dismissAddDialog() }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    // Delete Passcode Confirmation Dialog
    uiState.passcodeToDelete?.let { item ->
        AlertDialog(
            onDismissRequest = { doorViewModel.dismissDeleteDialog() },
            title = {
                Text(
                    text = stringResource(R.string.dialog_delete_passcode_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(stringResource(R.string.dialog_delete_passcode_msg, item.name))
            },
            confirmButton = {
                Button(
                    onClick = { doorViewModel.executeDeletePasscode() },
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error)
                ) {
                    Text(stringResource(R.string.btn_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = { doorViewModel.dismissDeleteDialog() }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }

    // Fallback Open Door Confirmation Dialog
    if (showFallbackConfirmDialog) {
        AlertDialog(
            onDismissRequest = { showFallbackConfirmDialog = false },
            title = {
                Text(
                    text = stringResource(R.string.dialog_confirm_open_title),
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Text(stringResource(R.string.dialog_confirm_open_msg))
            },
            confirmButton = {
                Button(
                    onClick = {
                        showFallbackConfirmDialog = false
                        doorViewModel.openDoorConfirmed()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StateError)
                ) {
                    Text(stringResource(R.string.dialog_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { showFallbackConfirmDialog = false }) {
                    Text(stringResource(R.string.dialog_cancel))
                }
            }
        )
    }
}
