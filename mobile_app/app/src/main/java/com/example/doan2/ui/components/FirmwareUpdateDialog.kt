package com.example.doan2.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.DeveloperBoard
import androidx.compose.material.icons.filled.Error
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.example.doan2.data.FirmwareInfo
import com.example.doan2.ui.theme.AuroraAccentStart
import com.example.doan2.ui.theme.StateError
import com.example.doan2.ui.theme.StateOk

@Composable
fun FirmwareUpdateDialog(
    firmwareInfo: FirmwareInfo,
    currentFirmwareVersion: String,
    otaProgress: Int,
    otaStatus: String,
    otaError: String?,
    onConfirmUpdate: () -> Unit,
    onDismiss: () -> Unit
) {
    val isDark = MaterialTheme.colorScheme.background.red < 0.5f
    val cardBg = if (isDark) Color(0xFF1E293B) else Color.White
    val subCardBg = if (isDark) Color(0xFF334155).copy(alpha = 0.5f) else Color(0xFFF8FAFC)
    val subCardBorder = if (isDark) Color(0xFF475569) else Color(0xFFE2E8F0)

    val isFlashing = otaStatus == "starting" || otaStatus == "downloading" || otaStatus == "flashing"

    Dialog(
        onDismissRequest = {
            if (!isFlashing) onDismiss()
        },
        properties = DialogProperties(
            dismissOnBackPress = !isFlashing,
            dismissOnClickOutside = !isFlashing,
            usePlatformDefaultWidth = false
        )
    ) {
        Surface(
            shape = RoundedCornerShape(26.dp),
            color = cardBg,
            tonalElevation = 6.dp,
            modifier = Modifier
                .fillMaxWidth(0.9f)
                .wrapContentHeight()
                .padding(vertical = 16.dp)
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(24.dp),
                horizontalAlignment = Alignment.CenterHorizontally
            ) {
                // Header Icon
                Surface(
                    shape = CircleShape,
                    color = AuroraAccentStart.copy(alpha = 0.15f),
                    border = BorderStroke(1.dp, AuroraAccentStart.copy(alpha = 0.35f)),
                    modifier = Modifier.size(64.dp)
                ) {
                    Box(contentAlignment = Alignment.Center) {
                        Icon(
                            imageVector = Icons.Default.DeveloperBoard,
                            contentDescription = null,
                            tint = AuroraAccentStart,
                            modifier = Modifier.size(32.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(16.dp))

                Text(
                    text = "Cập nhật Firmware thiết bị",
                    fontSize = 19.sp,
                    fontWeight = FontWeight.Bold,
                    color = MaterialTheme.colorScheme.onBackground
                )

                Spacer(modifier = Modifier.height(8.dp))

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = AuroraAccentStart.copy(alpha = 0.15f)
                    ) {
                        Text(
                            text = "Bản mới: v${firmwareInfo.version}",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold,
                            color = AuroraAccentStart,
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }

                    Surface(
                        shape = RoundedCornerShape(8.dp),
                        color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.08f)
                    ) {
                        Text(
                            text = "Hiện tại: v$currentFirmwareVersion",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Medium,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.6f),
                            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.height(18.dp))

                // Release notes
                if (firmwareInfo.releaseNotes.isNotBlank()) {
                    Surface(
                        shape = RoundedCornerShape(16.dp),
                        color = subCardBg,
                        border = BorderStroke(1.dp, subCardBorder),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 160.dp)
                    ) {
                        Column(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(14.dp)
                                .verticalScroll(rememberScrollState())
                        ) {
                            Text(
                                text = "Nội dung nâng cấp Firmware:",
                                fontSize = 12.5.sp,
                                fontWeight = FontWeight.Bold,
                                color = MaterialTheme.colorScheme.onBackground
                            )
                            Spacer(modifier = Modifier.height(6.dp))
                            Text(
                                text = firmwareInfo.releaseNotes,
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.8f),
                                lineHeight = 18.sp
                            )
                        }
                    }

                    Spacer(modifier = Modifier.height(18.dp))
                }

                // Flashing / Progress Status
                if (isFlashing) {
                    val progressVal = otaProgress / 100f
                    Column(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalAlignment = Alignment.CenterHorizontally
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(
                                text = if (otaStatus == "downloading") "Đang tải Firmware..." else "Đang nạp vào bộ nhớ Flash...",
                                fontSize = 12.sp,
                                color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.7f)
                            )
                            Text(
                                text = "$otaProgress%",
                                fontSize = 12.sp,
                                fontWeight = FontWeight.Bold,
                                color = AuroraAccentStart
                            )
                        }
                        Spacer(modifier = Modifier.height(8.dp))
                        LinearProgressIndicator(
                            progress = { progressVal },
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(8.dp),
                            color = AuroraAccentStart,
                            trackColor = AuroraAccentStart.copy(alpha = 0.2f)
                        )
                        Spacer(modifier = Modifier.height(8.dp))
                        Text(
                            text = "Hệ thống đang nạp an toàn vào phân vùng dự phòng. Vui lòng không rút nguồn ESP32.",
                            fontSize = 11.sp,
                            color = MaterialTheme.colorScheme.onBackground.copy(alpha = 0.55f),
                            lineHeight = 15.sp
                        )
                    }
                } else if (otaStatus == "success") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.CheckCircle, contentDescription = null, tint = StateOk, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nạp Firmware thành công! Thiết bị đang khởi động lại...", fontSize = 12.5.sp, color = StateOk, fontWeight = FontWeight.Medium)
                    }
                } else if (otaStatus == "failed") {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(vertical = 8.dp)
                    ) {
                        Icon(Icons.Default.Error, contentDescription = null, tint = StateError, modifier = Modifier.size(20.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Nạp thất bại: ${otaError ?: "Lỗi kết nối"}", fontSize = 12.5.sp, color = StateError)
                    }
                }

                Spacer(modifier = Modifier.height(14.dp))

                // Actions
                if (!isFlashing) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        OutlinedButton(
                            onClick = onDismiss,
                            shape = RoundedCornerShape(14.dp),
                            modifier = Modifier
                                .weight(1f)
                                .height(48.dp)
                        ) {
                            Text(if (otaStatus == "success") "Đóng" else "Hủy")
                        }

                        if (otaStatus != "success") {
                            Button(
                                onClick = onConfirmUpdate,
                                colors = ButtonDefaults.buttonColors(containerColor = AuroraAccentStart),
                                shape = RoundedCornerShape(14.dp),
                                modifier = Modifier
                                    .weight(1.4f)
                                    .height(48.dp)
                            ) {
                                Text(
                                    text = if (otaStatus == "failed") "Thử lại" else "Nạp Firmware ngay",
                                    fontSize = 13.sp,
                                    fontWeight = FontWeight.Bold
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}
