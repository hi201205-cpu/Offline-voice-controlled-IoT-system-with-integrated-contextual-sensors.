package com.example.doan2.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.interaction.collectIsPressedAsState
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Surface
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Shape
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.doan2.ui.theme.*
import dev.chrisbanes.haze.HazeState
import dev.chrisbanes.haze.hazeChild

@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    shape: Shape = RoundedCornerShape(24.dp),
    borderWidth: Dp = 1.dp,
    fillColor: Color? = null,
    borderColor: Color? = null,
    hazeState: HazeState? = null,
    onClick: (() -> Unit)? = null,
    enabled: Boolean = true,
    content: @Composable () -> Unit
) {
    val interactionSource = remember { MutableInteractionSource() }
    val isPressed by interactionSource.collectIsPressedAsState()

    val scale by animateFloatAsState(
        targetValue = if (isPressed && enabled && onClick != null) 0.97f else 1f,
        animationSpec = tween(150),
        label = "pressScale"
    )

    val themeController = LocalThemeController.current
    val isDark = when (themeController.themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val defaultFill = fillColor ?: if (isDark) {
        GlassFillDark
    } else {
        GlassFillLight
    }

    val borderBrush = if (borderColor != null) {
        Brush.linearGradient(listOf(borderColor, borderColor))
    } else {
        Brush.verticalGradient(
            colors = if (isDark) {
                listOf(GlassBorderStartDark, GlassBorderEndDark)
            } else {
                listOf(GlassBorderStartLight, GlassBorderEndLight)
            }
        )
    }

    val hazeModifier = if (hazeState != null) {
        Modifier.hazeChild(state = hazeState)
    } else {
        Modifier
    }

    Surface(
        shape = shape,
        color = defaultFill,
        border = BorderStroke(borderWidth, borderBrush),
        tonalElevation = 0.dp,
        shadowElevation = if (isDark) 0.dp else 2.dp,
        modifier = modifier
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
            }
            .then(hazeModifier)
            .then(
                if (onClick != null) {
                    Modifier.clickable(
                        enabled = enabled,
                        interactionSource = interactionSource,
                        indication = null,
                        onClick = onClick
                    )
                } else Modifier
            )
    ) {
        content()
    }
}
