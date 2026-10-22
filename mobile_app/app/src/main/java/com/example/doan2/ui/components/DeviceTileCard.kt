package com.example.doan2.ui.components

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.example.doan2.ui.theme.*

@Composable
fun DeviceTileCard(
    title: String,
    statusText: String,
    icon: ImageVector,
    isOn: Boolean,
    subtitleText: String? = null,
    isProcessing: Boolean = false,
    isErrorText: Boolean = false,
    enabled: Boolean = true,
    showSwitch: Boolean = true,
    gardenMode: String? = null,
    onGardenModeChange: ((String) -> Unit)? = null,
    onClick: () -> Unit,
    onLongClick: (() -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val themeController = LocalThemeController.current
    val isDark = when (themeController.themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    val cardFillColor by animateColorAsState(
        targetValue = when {
            !enabled -> if (isDark) Color(0xFF0F172A).copy(alpha = 0.5f) else Color(0xFFE2E8F0).copy(alpha = 0.6f)
            isOn -> if (isDark) Color(0xFF162A45).copy(alpha = 0.9f) else Color(0xFFD4EAFC).copy(alpha = 0.95f)
            else -> if (isDark) GlassFillDark else GlassFillLight
        },
        animationSpec = tween(250),
        label = "cardFill"
    )

    val iconTint by animateColorAsState(
        targetValue = when {
            !enabled -> Color.Gray
            isOn -> AuroraAccentStart
            else -> if (isDark) Color.White.copy(alpha = 0.65f) else Color(0xFF152C45).copy(alpha = 0.7f)
        },
        animationSpec = tween(250),
        label = "iconTint"
    )

    val displayText = when {
        isProcessing -> "Đang chờ thiết bị..."
        isErrorText -> "Thiết bị chưa phản hồi"
        else -> statusText
    }

    GlassCard(
        shape = RoundedCornerShape(20.dp),
        fillColor = cardFillColor,
        borderColor = if (isOn && enabled) AuroraAccentStart.copy(alpha = 0.75f) else null,
        onClick = onClick,
        enabled = enabled,
        modifier = modifier
            .fillMaxWidth()
            .alpha(if (enabled) 1f else 0.55f)
    ) {
        Box(
            modifier = Modifier
                .fillMaxWidth()
                .padding(14.dp)
        ) {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Icon Container
                    Surface(
                        shape = RoundedCornerShape(12.dp),
                        color = if (isOn && enabled) {
                            AuroraAccentStart.copy(alpha = if (isDark) 0.35f else 0.22f)
                        } else {
                            if (isDark) Color.White.copy(alpha = 0.1f) else Color(0xFF152C45).copy(alpha = 0.08f)
                        },
                        modifier = Modifier.size(40.dp)
                    ) {
                        Box(contentAlignment = Alignment.Center) {
                            Icon(
                                imageVector = icon,
                                contentDescription = title,
                                tint = iconTint,
                                modifier = Modifier.size(22.dp)
                            )
                        }
                    }

                    if (isProcessing) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(20.dp),
                            strokeWidth = 2.dp,
                            color = AuroraAccentEnd
                        )
                    } else if (showSwitch) {
                        PillSwitch(
                            checked = isOn,
                            onCheckedChange = { if (enabled) onClick() },
                            enabled = enabled
                        )
                    }
                }

                Spacer(modifier = Modifier.height(10.dp))

                Column {
                    Text(
                        text = title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Bold,
                        color = MaterialTheme.colorScheme.onBackground,
                        minLines = 2,
                        maxLines = 2,
                        lineHeight = 18.sp,
                        overflow = TextOverflow.Ellipsis
                    )
                    Spacer(modifier = Modifier.height(2.dp))
                    Text(
                        text = displayText,
                        fontSize = 13.sp,
                        fontWeight = if (isErrorText) FontWeight.Bold else FontWeight.Medium,
                        color = if (isErrorText) StateError else MaterialTheme.colorScheme.onBackground.copy(alpha = 0.65f),
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    // Optional Garden Segmented Glass Control in Đèn sân tile
                    if (gardenMode != null && onGardenModeChange != null) {
                        Spacer(modifier = Modifier.height(8.dp))
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(28.dp)
                                .clip(RoundedCornerShape(14.dp))
                                .background(Color.White.copy(alpha = 0.1f))
                                .border(1.dp, Color.White.copy(alpha = 0.15f), RoundedCornerShape(14.dp))
                                .padding(2.dp),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            val isManual = gardenMode == "manual"
                            val isAuto = gardenMode == "auto"

                            val manualBg = if (isManual) AuroraAccentStart else Color.Transparent
                            val autoBg = if (isAuto) AuroraAccentStart else Color.Transparent

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(manualBg)
                                    .clickable { onGardenModeChange("manual") },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Thủ công",
                                    fontSize = 11.sp,
                                    fontWeight = if (isManual) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isManual) Color.White else TextSecondaryDark
                                )
                            }

                            Box(
                                modifier = Modifier
                                    .weight(1f)
                                    .fillMaxHeight()
                                    .clip(RoundedCornerShape(12.dp))
                                    .background(autoBg)
                                    .clickable { onGardenModeChange("auto") },
                                contentAlignment = Alignment.Center
                            ) {
                                Text(
                                    text = "Tự động",
                                    fontSize = 11.sp,
                                    fontWeight = if (isAuto) FontWeight.Bold else FontWeight.Medium,
                                    color = if (isAuto) Color.White else TextSecondaryDark
                                )
                            }
                        }
                    } else if (!subtitleText.isNullOrEmpty() && !isProcessing && !isErrorText) {
                        Spacer(modifier = Modifier.height(2.dp))
                        Text(
                            text = subtitleText,
                            fontSize = 11.sp,
                            fontWeight = FontWeight.Normal,
                            color = TextTertiaryDark,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                }
            }
        }
    }
}
