package com.example.doan2.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import com.example.doan2.ui.theme.*

@Composable
fun AuroraBackground(
    modifier: Modifier = Modifier,
    content: @Composable () -> Unit
) {
    val themeController = LocalThemeController.current
    val isDark = when (themeController.themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val bgColor = if (isDark) AuroraBgDark else AuroraBgLight
    val spotPurple = if (isDark) AuroraSpotPurple.copy(alpha = 0.35f) else AuroraSpotPurple.copy(alpha = 0.12f)
    val spotCyan = if (isDark) AuroraSpotCyan.copy(alpha = 0.30f) else AuroraSpotCyan.copy(alpha = 0.10f)
    val spotPink = if (isDark) AuroraSpotPink.copy(alpha = 0.25f) else AuroraSpotPink.copy(alpha = 0.08f)

    Box(modifier = modifier.fillMaxSize()) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val width = size.width
            val height = size.height

            // Base Background
            drawRect(color = bgColor)

            // Top-Left Purple Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(spotPurple, Color.Transparent),
                    center = Offset(width * 0.15f, height * 0.15f),
                    radius = width * 0.8f
                ),
                center = Offset(width * 0.15f, height * 0.15f),
                radius = width * 0.8f
            )

            // Bottom-Right Cyan Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(spotCyan, Color.Transparent),
                    center = Offset(width * 0.85f, height * 0.85f),
                    radius = width * 0.85f
                ),
                center = Offset(width * 0.85f, height * 0.85f),
                radius = width * 0.85f
            )

            // Center Pink Glow
            drawCircle(
                brush = Brush.radialGradient(
                    colors = listOf(spotPink, Color.Transparent),
                    center = Offset(width * 0.5f, height * 0.5f),
                    radius = width * 0.65f
                ),
                center = Offset(width * 0.5f, height * 0.5f),
                radius = width * 0.65f
            )
        }

        content()
    }
}
