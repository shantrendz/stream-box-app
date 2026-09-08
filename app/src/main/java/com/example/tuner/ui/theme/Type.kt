package com.example.tuner.ui.theme

import androidx.compose.material3.Typography
import androidx.compose.ui.text.ExperimentalTextApi
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.Font
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontVariation
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.sp
import com.example.tuner.R

// Bundled Inter variable font (res/font/inter.ttf). Each entry pins the variable
// font's weight axis so Compose can pick the right instance per TextStyle weight.
@OptIn(ExperimentalTextApi::class)
val TunerFontFamily = FontFamily(
    Font(R.font.inter, FontWeight.Normal, variationSettings = FontVariation.Settings(FontVariation.weight(400))),
    Font(R.font.inter, FontWeight.Medium, variationSettings = FontVariation.Settings(FontVariation.weight(500))),
    Font(R.font.inter, FontWeight.SemiBold, variationSettings = FontVariation.Settings(FontVariation.weight(600))),
    Font(R.font.inter, FontWeight.Bold, variationSettings = FontVariation.Settings(FontVariation.weight(700)))
)

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
