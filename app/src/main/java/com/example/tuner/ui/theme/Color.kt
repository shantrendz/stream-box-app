package com.example.tuner.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * The bespoke teletext/CRT tuner palette. Custom components (channel rows, group headers,
 * the static overlay, etc.) read these directly rather than via MaterialTheme.colorScheme,
 * so the palette is threaded through [LocalTunerPalette] and swapped by [TunerTheme] based
 * on the active [ThemeMode].
 */
data class TunerPalette(
    val background: Color,
    val surface: Color,
    val outline: Color,
    val textPrimary: Color,
    val textSecondary: Color,
    val amber: Color,
    val cyan: Color,
    val red: Color,
    val amberTint: Color,
    val redTint: Color
)

val DarkTunerPalette = TunerPalette(
    background = Color(0xFF0A0A0C),
    surface = Color(0xFF111114),
    outline = Color(0xFF2A2A2F),
    textPrimary = Color(0xFFE8E6E1),
    textSecondary = Color(0xFF7A7A80),
    amber = Color(0xFFFFB000),
    cyan = Color(0xFF4FD8E0),
    red = Color(0xFFFF4D3D),
    amberTint = Color(0x1FFFB000),
    redTint = Color(0x1FFF4D3D)
)

// Light variant: same accent hues, darkened slightly for contrast against a light ground;
// off-white/paper background with near-black text. Still deliberately flat and hairline-
// divided, not a default Material light scheme.
val LightTunerPalette = TunerPalette(
    background = Color(0xFFF4F3EF),
    surface = Color(0xFFE7E5DE),
    outline = Color(0xFFC8C6BE),
    textPrimary = Color(0xFF1A1A1C),
    textSecondary = Color(0xFF5B5A55),
    amber = Color(0xFFB35F00),
    cyan = Color(0xFF00838F),
    red = Color(0xFFC62828),
    amberTint = Color(0x26B35F00),
    redTint = Color(0x26C62828)
)

val LocalTunerPalette = staticCompositionLocalOf { DarkTunerPalette }

// Composable accessors keep the existing call-site names used throughout the UI — only
// Theme.kt needs to know about TunerPalette/LocalTunerPalette directly.
val TunerBackground: Color @Composable get() = LocalTunerPalette.current.background
val TunerSurface: Color @Composable get() = LocalTunerPalette.current.surface
val TunerOutline: Color @Composable get() = LocalTunerPalette.current.outline
val TunerTextPrimary: Color @Composable get() = LocalTunerPalette.current.textPrimary
val TunerTextSecondary: Color @Composable get() = LocalTunerPalette.current.textSecondary
val TunerAmber: Color @Composable get() = LocalTunerPalette.current.amber
val TunerCyan: Color @Composable get() = LocalTunerPalette.current.cyan
val TunerRed: Color @Composable get() = LocalTunerPalette.current.red
val TunerAmberTint: Color @Composable get() = LocalTunerPalette.current.amberTint
val TunerRedTint: Color @Composable get() = LocalTunerPalette.current.redTint
