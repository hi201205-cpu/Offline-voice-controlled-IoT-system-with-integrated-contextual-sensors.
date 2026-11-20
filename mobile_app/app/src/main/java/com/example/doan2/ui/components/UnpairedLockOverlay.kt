package com.example.doan2.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doan2.ui.theme.*

@Composable
fun UnpairedLockOverlay(
    showDialog: Boolean,
    codeInput: String,
    isLoading: Boolean,
    onOpenDialog: () -> Unit,
    onCloseDialog: () -> Unit,
    onCodeChange: (String) -> Unit,
    onSubmitCode: () -> Unit,
    currentUserEmail: String = "",
    currentUid: String = "",
    onProvisionSuccess: (homeId: String) -> Unit = {},
    modifier: Modifier = Modifier
) {
    var showBleDialog by remember { mutableStateOf(false) }

    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val dividerColor = if (isDark) Color(0xFF334155) else Color(0xFFE2E8F0)

    Box(
        modifier = modifier
            .fillMaxSize()
            .padding(16.dp),
        contentAlignment = Alignment.Center
    ) {
        GlassCard(
            shape = RoundedCornerShape(28.dp),
            modifier = Modifier
                .fillMaxWidth()
                .padding(8.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 22.dp, vertical = 28.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Web Matching Glowing Icon Box
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = AuroraAccentStart.copy(alpha = 0.18f),
                    border = BorderStroke(1.dp, AuroraAccentStart.copy(alpha = 0.4f)),
                    modifier = Modifier.size(68.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Sensors,
                            contentDescription = null,
                            tint = AuroraAccentStart,
                            modifier = Modifier.size(34.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Chưa liên kết ngôi nhà",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(8.dp))

                Text(
                    text = "Chào mừng bạn đến với hệ thống Smart Home! Bạn chưa được kết nối với thiết bị ESP32 nào.\n\nVui lòng chọn phương thức kết nối bên dưới để bắt đầu:",
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    lineHeight = 19.sp,
                    textAlign = TextAlign.Center
                )

                Spacer(modifier = Modifier.height(22.dp))

                // OPTION 1 (HERO / PRIMARY): Bluetooth Device Scan & Setup
                Button(
                    onClick = { showBleDialog = true },
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuroraAccentStart,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.BluetoothSearching,
                        contentDescription = null,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Quét & Cài đặt thiết bị (Bluetooth)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "★ Ưu tiên cho Chủ nhà: Quét BLE và nạp Wi-Fi cho ESP32 mới hoặc sau khi Reset thiết bị.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )

                Spacer(modifier = Modifier.height(14.dp))

                // Divider with "HOẶC"
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 4.dp)
                ) {
                    HorizontalDivider(modifier = Modifier.weight(1f), color = dividerColor)
                    Text(
                        text = "HOẶC",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.45f),
                        modifier = Modifier.padding(horizontal = 10.dp)
                    )
                    HorizontalDivider(modifier = Modifier.weight(1f), color = dividerColor)
                }

                Spacer(modifier = Modifier.height(14.dp))

                // OPTION 2 (SECONDARY): Invite Code Entry for Family Members
                OutlinedButton(
                    onClick = onOpenDialog,
                    shape = RoundedCornerShape(14.dp),
                    border = BorderStroke(1.2.dp, AuroraAccentStart.copy(alpha = 0.5f)),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(48.dp)
                ) {
                    Icon(
                        imageVector = Icons.Default.Key,
                        contentDescription = null,
                        tint = AuroraAccentStart,
                        modifier = Modifier.size(18.dp)
                    )
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(
                        text = "Nhập mã mời liên kết (Thành viên)",
                        fontSize = 13.5.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = AuroraAccentStart,
                        maxLines = 1
                    )
                }

                Spacer(modifier = Modifier.height(6.dp))

                Text(
                    text = "Dành cho người thân, thành viên trong nhà được Chủ nhà cấp mã mời HM-XXXX.",
                    fontSize = 11.5.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                    textAlign = TextAlign.Center,
                    lineHeight = 16.sp
                )
            }
        }
    }

    // BLE Provisioning Dialog (Quét Bluetooth trực tiếp từ màn hình chính)
    if (showBleDialog) {
        BleProvisioningDialog(
            currentUserEmail = currentUserEmail,
            currentUid = currentUid,
            onDismiss = { showBleDialog = false },
            onSuccess = { homeId ->
                onProvisionSuccess(homeId)
            }
        )
    }

    // Invite Code Dialog (Nhập mã mời 1 lần)
    if (showDialog) {
        AlertDialog(
            onDismissRequest = onCloseDialog,
            title = {
                Text(
                    text = "Nhập mã liên kết thành viên",
                    fontSize = 18.sp,
                    fontWeight = FontWeight.Bold
                )
            },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text(
                        text = "Nhập mã HM-XXXX do Chủ nhà cấp:",
                        fontSize = 14.sp,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = codeInput,
                        onValueChange = { if (it.length <= 10) onCodeChange(it) },
                        label = { Text("Mã liên kết (HM-XXXX)") },
                        singleLine = true,
                        keyboardOptions = KeyboardOptions(
                            capitalization = KeyboardCapitalization.Characters,
                            imeAction = ImeAction.Done
                        ),
                        modifier = Modifier.fillMaxWidth()
                    )

                    if (isLoading) {
                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = onSubmitCode,
                    enabled = codeInput.isNotBlank() && !isLoading
                ) {
                    Text("Xác nhận")
                }
            },
            dismissButton = {
                TextButton(onClick = onCloseDialog) {
                    Text("Hủy")
                }
            }
        )
    }
}
