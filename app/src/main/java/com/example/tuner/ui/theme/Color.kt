package com.example.tuner.ui.theme

import androidx.compose.runtime.Composable
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.compose.ui.graphics.Color

/**
 * Live-broadcast palette modeled on the design-3 reference (ui-reference/design-3-01.png):
 * a deep navy-to-violet ground, purple/blue accents, and a red "LIVE" badge color instead
 * of the earlier flat amber/cyan teletext scheme. Custom components (channel rows, group
 * headers, the static overlay, etc.) read these directly rather than via
 * MaterialTheme.colorScheme, so the palette is threaded through [LocalTunerPalette] and
 * swapped by [TunerTheme] based on the active [ThemeMode].
 *
 * Field names (amber/cyan/red) are kept from the prior scheme to avoid a repo-wide rename —
 * amber is now the violet primary accent, cyan is the secondary blue accent, red stays red.
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
    val green: Color,
    val amberTint: Color,
    val redTint: Color,
    val gradientTop: Color,
    val gradientBottom: Color
)

val DarkTunerPalette = TunerPalette(
    background = Color(0xFF0B0E1A),
    surface = Color(0xFF171B2E),
    outline = Color(0xFF2C3150),
    textPrimary = Color(0xFFF2F1F8),
    textSecondary = Color(0xFF8D91AC),
    amber = Color(0xFF7C6CF6),
    cyan = Color(0xFF4E8DF7),
    red = Color(0xFFFF4B5C),
    green = Color(0xFF3DDC84),
    amberTint = Color(0x267C6CF6),
    redTint = Color(0x26FF4B5C),
    gradientTop = Color(0xFF1B1740),
    gradientBottom = Color(0xFF0B0E1A)
)

// Light variant: same accent hues, darkened slightly for contrast against a light ground;
// off-white background with near-black text.
val LightTunerPalette = TunerPalette(
    background = Color(0xFFF3F2FA),
    surface = Color(0xFFE7E5F5),
    outline = Color(0xFFCBC8E6),
    textPrimary = Color(0xFF181A2A),
    textSecondary = Color(0xFF5B5E78),
    amber = Color(0xFF5B45E0),
    cyan = Color(0xFF2464C4),
    red = Color(0xFFC62839),
    green = Color(0xFF1E8E4E),
    amberTint = Color(0x265B45E0),
    redTint = Color(0x26C62839),
    gradientTop = Color(0xFFDFDBF5),
    gradientBottom = Color(0xFFF3F2FA)
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
val TunerGreen: Color @Composable get() = LocalTunerPalette.current.green
val TunerAmberTint: Color @Composable get() = LocalTunerPalette.current.amberTint
val TunerRedTint: Color @Composable get() = LocalTunerPalette.current.redTint
val TunerGradientTop: Color @Composable get() = LocalTunerPalette.current.gradientTop
val TunerGradientBottom: Color @Composable get() = LocalTunerPalette.current.gradientBottom
