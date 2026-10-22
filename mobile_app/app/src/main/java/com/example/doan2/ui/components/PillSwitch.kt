package com.example.doan2.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.shadow
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.example.doan2.ui.theme.AppThemeMode
import com.example.doan2.ui.theme.LocalThemeController

@Composable
fun PillSwitch(
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true
) {
    val thumbPosition by animateFloatAsState(
        targetValue = if (checked) 1f else 0f,
        animationSpec = tween(250),
        label = "thumbPosition"
    )

    val themeController = LocalThemeController.current
    val isDark = when (themeController.themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> androidx.compose.foundation.isSystemInDarkTheme()
    }

    val offColor = if (isDark) Color(0xFF526174) else Color(0xFF9EACBA)
    val disabledColor = if (isDark) Color.White.copy(alpha = 0.08f) else Color(0xFFE2E8F0)
    val trackBrush = if (!enabled) {
        Brush.horizontalGradient(listOf(disabledColor, disabledColor))
    } else if (checked) {
        Brush.horizontalGradient(listOf(Color(0xFF38BDF8), Color(0xFF6366F1)))
    } else {
        Brush.horizontalGradient(listOf(offColor, offColor))
    }

    val interactionSource = remember { MutableInteractionSource() }

    Box(
        modifier = modifier
            .width(48.dp)
            .height(26.dp)
            .clip(RoundedCornerShape(13.dp))
            .background(trackBrush)
            .border(
                width = 1.dp,
                color = if (checked && enabled) Color.White.copy(alpha = 0.35f) else if (isDark) Color.White.copy(alpha = 0.15f) else Color(0xFF366795).copy(alpha = 0.25f),
                shape = RoundedCornerShape(13.dp)
            )
            .clickable(
                enabled = enabled,
                interactionSource = interactionSource,
                indication = null
            ) {
                onCheckedChange(!checked)
            }
            .padding(2.dp),
        contentAlignment = Alignment.CenterStart
    ) {
        Box(
            modifier = Modifier
                .size(22.dp)
                .offset(x = (thumbPosition * 22).dp)
                .shadow(elevation = if (checked) 4.dp else 1.dp, shape = CircleShape)
                .clip(CircleShape)
                .background(if (enabled) Color.White else Color.Gray)
        )
    }
}
