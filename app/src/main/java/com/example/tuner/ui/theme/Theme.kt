package com.example.tuner.ui.theme

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.CompositionLocalProvider

/** User-selectable theme preference. SYSTEM follows the device's day/night setting. */
enum class ThemeMode { DARK, LIGHT, SYSTEM }

@Composable
fun TunerTheme(themeMode: ThemeMode = ThemeMode.SYSTEM, content: @Composable () -> Unit) {
    val isDark = when (themeMode) {
        ThemeMode.DARK -> true
        ThemeMode.LIGHT -> false
        ThemeMode.SYSTEM -> isSystemInDarkTheme()
    }
    val palette = if (isDark) DarkTunerPalette else LightTunerPalette

    val colorScheme = if (isDark) {
        darkColorScheme(
            primary = palette.amber,
            onPrimary = palette.background,
            secondary = palette.cyan,
            onSecondary = palette.background,
            error = palette.red,
            onError = palette.background,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.outline,
            outlineVariant = palette.outline
        )
    } else {
        lightColorScheme(
            primary = palette.amber,
            onPrimary = palette.background,
            secondary = palette.cyan,
            onSecondary = palette.background,
            error = palette.red,
            onError = palette.background,
            background = palette.background,
            onBackground = palette.textPrimary,
            surface = palette.surface,
            onSurface = palette.textPrimary,
            surfaceVariant = palette.surface,
            onSurfaceVariant = palette.textSecondary,
            outline = palette.outline,
            outlineVariant = palette.outline
        )
    }

    CompositionLocalProvider(LocalTunerPalette provides palette) {
        MaterialTheme(
            colorScheme = colorScheme,
            typography = TunerTypography,
            content = content
        )
    }
}
