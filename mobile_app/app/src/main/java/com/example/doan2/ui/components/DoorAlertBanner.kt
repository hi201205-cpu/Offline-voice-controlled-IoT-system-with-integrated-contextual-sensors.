package com.example.doan2.ui.components

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Error
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doan2.R
import com.example.doan2.data.model.DoorAlert
import com.example.doan2.ui.theme.*
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

@Composable
fun DoorAlertBanner(
    alert: DoorAlert?,
    keypadLocked: Boolean = false,
    onAck: () -> Unit = {},
    onUnlockKeypad: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    var showConfirmUnlockDialog by remember { mutableStateOf(false) }

    if (showConfirmUnlockDialog) {
        AlertDialog(
            onDismissRequest = { showConfirmUnlockDialog = false },
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
                        showConfirmUnlockDialog = false
                        onUnlockKeypad?.invoke()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = StateError)
                ) {
                    Text("Xác nhận mở khóa", fontWeight = FontWeight.Bold)
                }
            },
            dismissButton = {
                OutlinedButton(
                    onClick = { showConfirmUnlockDialog = false }
                ) {
                    Text("Hủy")
                }
            }
        )
    }

    if (keypadLocked) {
        GlassCard(
            shape = RoundedCornerShape(16.dp),
            fillColor = DoorAlertRedBg,
            borderColor = StateError.copy(alpha = 0.7f),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Lock,
                    contentDescription = "Bàn phím bị khóa",
                    tint = StateError,
                    modifier = Modifier.size(24.dp)
                )
                Spacer(modifier = Modifier.width(10.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = "CẢNH BÁO AN NINH BÀN PHÍM",
                        color = StateError,
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Khóa an ninh do nhập sai mật khẩu 4 lần. Cửa tạm khóa chống đột nhập.",
                        color = Color.White.copy(alpha = 0.9f),
                        fontSize = 12.sp,
                        lineHeight = 16.sp
                    )
                }
                if (onUnlockKeypad != null) {
                    Spacer(modifier = Modifier.width(8.dp))
                    Button(
                        onClick = { showConfirmUnlockDialog = true },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = StateError,
                            contentColor = Color.White
                        ),
                        contentPadding = PaddingValues(horizontal = 10.dp, vertical = 4.dp),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.height(34.dp)
                    ) {
                        Icon(imageVector = Icons.Default.LockOpen, contentDescription = null, modifier = Modifier.size(14.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(
                            text = "Mở khóa",
                            fontSize = 12.sp,
                            fontWeight = FontWeight.Bold
                        )
                    }
                }
            }
        }
    } else if (alert != null && alert.ack == false) {
        val count = alert.count ?: 1
        val timeStr = alert.ts?.let {
            val alertMs = if (it < 10_000_000_000L) it * 1000L else it
            SimpleDateFormat("HH:mm", Locale.getDefault()).format(Date(alertMs))
        } ?: ""

        GlassCard(
            shape = RoundedCornerShape(16.dp),
            fillColor = DoorAlertRedBg,
            borderColor = StateError.copy(alpha = 0.5f),
            modifier = modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 4.dp)
        ) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 14.dp, vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Icon(
                    imageVector = Icons.Default.Error,
                    contentDescription = stringResource(R.string.alert_door_wrong_pass, count, timeStr),
                    tint = StateError,
                    modifier = Modifier.size(22.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text(
                    text = stringResource(R.string.alert_door_wrong_pass, count, timeStr),
                    color = StateError,
                    fontSize = 13.sp,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Button(
                    onClick = onAck,
                    colors = ButtonDefaults.buttonColors(
                        containerColor = StateError,
                        contentColor = Color.White
                    ),
                    contentPadding = PaddingValues(horizontal = 12.dp, vertical = 4.dp),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.height(32.dp)
                ) {
                    Text(
                        text = stringResource(R.string.btn_ack),
                        fontSize = 12.sp,
                        fontWeight = FontWeight.Bold
                    )
                }
            }
        }
    }
}
