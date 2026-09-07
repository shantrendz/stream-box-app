package com.example.tuner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp

// Deliberate choice: a single monospace family everywhere, evoking teletext/EPG data
// readouts rather than a default Material sans. Falls back to the system monospace —
// bundle IBM Plex Mono as a font resource for a closer match if desired.
val TunerFontFamily = FontFamily.Monospace

val TunerTypography = Typography(
    displayLarge = TextStyle(fontFamily = TunerFontFamily, fontWeight = FontWeight.Bold, fontSize = 32.sp),
    headlineMedium = TextStyle(fontFamily = TunerFontFamily, fontWeight = FontWeight.Bold, fontSize = 22.sp),
    titleLarge = TextStyle(fontFamily = TunerFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 18.sp),
    titleMedium = TextStyle(fontFamily = TunerFontFamily, fontWeight = FontWeight.SemiBold, fontSize = 15.sp, letterSpacing = 0.5.sp),
    bodyLarge = TextStyle(fontFamily = TunerFontFamily, fontWeight = FontWeight.Normal, fontSize = 15.sp),
    bodyMedium = TextStyle(fontFamily = TunerFontFamily, fontWeight = FontWeight.Normal, fontSize = 13.sp),
    labelLarge = TextStyle(fontFamily = TunerFontFamily, fontWeight = FontWeight.Medium, fontSize = 13.sp, letterSpacing = 0.5.sp),
    labelMedium = TextStyle(fontFamily = TunerFontFamily, fontWeight = FontWeight.Medium, fontSize = 11.sp, letterSpacing = 0.5.sp),
    labelSmall = TextStyle(fontFamily = TunerFontFamily, fontWeight = FontWeight.Normal, fontSize = 10.sp, letterSpacing = 0.5.sp)
)
