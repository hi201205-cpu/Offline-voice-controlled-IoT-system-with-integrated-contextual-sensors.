package com.example.doan2.ui.components

import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.scale
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doan2.R
import com.example.doan2.ui.theme.*

@Composable
fun ConnectionBanner(
    isDisconnected: Boolean,
    lastSeen: Long = 0L,
    modifier: Modifier = Modifier
) {
    val nowMs = System.currentTimeMillis()
    val ageSec = if (lastSeen == 0L) 0L else if (lastSeen > 2_000_000_000L) (nowMs - lastSeen) / 1000L else (nowMs / 1000L) - lastSeen

    // Slow pulse animation on online status dot
    val infiniteTransition = rememberInfiniteTransition(label = "pulse")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 1f,
        targetValue = 1.35f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Column(modifier = modifier.fillMaxWidth()) {
        // Red Banner on Top only when Offline
        if (isDisconnected) {
            GlassCard(
                shape = RoundedCornerShape(16.dp),
                fillColor = DoorAlertRedBg,
                borderColor = StateError.copy(alpha = 0.5f),
                modifier = Modifier
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
                        imageVector = Icons.Default.Warning,
                        contentDescription = stringResource(R.string.banner_device_disconnected),
                        tint = StateError,
                        modifier = Modifier.size(20.dp)
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = stringResource(R.string.banner_device_disconnected),
                        color = StateError,
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
        }

        // Online / Offline Status Badge
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 2.dp),
            horizontalArrangement = Arrangement.End,
            verticalAlignment = Alignment.CenterVertically
        ) {
            GlassCard(
                shape = RoundedCornerShape(12.dp),
                fillColor = if (isDisconnected) StateError.copy(alpha = 0.2f) else StateOk.copy(alpha = 0.2f),
                borderColor = if (isDisconnected) StateError.copy(alpha = 0.4f) else StateOk.copy(alpha = 0.4f)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Pulsing Dot for Online
                    Box(
                        modifier = Modifier
                            .size(8.dp)
                            .scale(if (!isDisconnected) pulseScale else 1f)
                            .background(
                                color = if (isDisconnected) StateError else StateOk,
                                shape = CircleShape
                            )
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(
                        text = if (isDisconnected) "ESP32: Offline" else "ESP32: Online (${if (ageSec >= 0) ageSec else 0} s)",
                        fontSize = 11.sp,
                        fontWeight = FontWeight.Bold,
                        color = if (isDisconnected) StateError else StateOk
                    )
                }
            }
        }
    }
}
