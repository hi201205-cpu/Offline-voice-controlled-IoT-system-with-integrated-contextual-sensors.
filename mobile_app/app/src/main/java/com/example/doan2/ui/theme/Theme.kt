package com.example.doan2.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.compositionLocalOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue

enum class AppThemeMode {
    SYSTEM, LIGHT, DARK
}

class ThemeController(private val context: android.content.Context? = null) {
    private val prefs = context?.getSharedPreferences("app_theme_prefs", android.content.Context.MODE_PRIVATE)

    var themeMode by mutableStateOf(
        prefs?.getString("theme_mode", null)?.let {
            try { AppThemeMode.valueOf(it) } catch (e: Exception) { AppThemeMode.DARK }
        } ?: AppThemeMode.DARK
    )

    fun updateTheme(mode: AppThemeMode) {
        themeMode = mode
        prefs?.edit()?.putString("theme_mode", mode.name)?.apply()
    }
}

val LocalThemeController = compositionLocalOf { ThemeController() }

private val DarkColorScheme = darkColorScheme(
    primary = AuroraAccentStart,
    secondary = AuroraAccentEnd,
    tertiary = AuroraSpotPink,
    background = AuroraBgDark,
    surface = GlassFillDark,
    onBackground = TextPrimaryDark,
    onSurface = TextPrimaryDark,
    error = StateError
)

private val LightColorScheme = lightColorScheme(
    primary = AuroraAccentStart,
    secondary = AuroraAccentEnd,
    tertiary = AuroraSpotPink,
    background = AuroraBgLight,
    surface = GlassFillLight,
    onBackground = TextPrimaryLight,
    onSurface = TextPrimaryLight,
    error = StateError
)

@Composable
fun Doan2Theme(
    themeController: ThemeController = LocalThemeController.current,
    content: @Composable () -> Unit
) {
    val isDark = when (themeController.themeMode) {
        AppThemeMode.LIGHT -> false
        AppThemeMode.DARK -> true
        AppThemeMode.SYSTEM -> isSystemInDarkTheme()
    }

    val colorScheme = if (isDark) DarkColorScheme else LightColorScheme

    CompositionLocalProvider(LocalThemeController provides themeController) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = Typography,
            content = content
        )
    }
}
