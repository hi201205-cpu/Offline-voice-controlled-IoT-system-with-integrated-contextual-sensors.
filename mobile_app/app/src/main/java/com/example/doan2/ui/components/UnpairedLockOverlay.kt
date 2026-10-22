package com.example.doan2.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardCapitalization
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
    modifier: Modifier = Modifier
) {
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
                    .padding(horizontal = 24.dp, vertical = 36.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Web Matching Glowing Icon Box
                Surface(
                    shape = RoundedCornerShape(22.dp),
                    color = AuroraAccentStart.copy(alpha = 0.18f),
                    border = androidx.compose.foundation.BorderStroke(1.dp, AuroraAccentStart.copy(alpha = 0.4f)),
                    modifier = Modifier.size(72.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = null,
                            tint = AuroraAccentStart,
                            modifier = Modifier.size(36.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(20.dp))

                Text(
                    text = "Tài khoản chưa liên kết ngôi nhà",
                    fontSize = 20.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(12.dp))

                Text(
                    text = "Chào mừng bạn đến với hệ thống Smart Home! Bạn chưa được kết nối với thiết bị ESP32 nào.\n\nVui lòng nhập Mã mời 1 lần do Chủ nhà (Master) cung cấp để bắt đầu giám sát và điều khiển.",
                    fontSize = 13.5.sp,
                    color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f),
                    lineHeight = 20.sp,
                    textAlign = androidx.compose.ui.text.style.TextAlign.Center
                )

                Spacer(modifier = Modifier.height(26.dp))

                Button(
                    onClick = onOpenDialog,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = AuroraAccentStart,
                        contentColor = Color.White
                    ),
                    shape = RoundedCornerShape(14.dp),
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(50.dp)
                ) {
                    Text(
                        text = "＋ Thêm thiết bị mới (Nhập mã)",
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        maxLines = 1
                    )
                }
            }
        }
    }

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
