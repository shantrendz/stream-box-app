package com.example.tuner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tuner.ui.theme.TunerCyan
import com.example.tuner.ui.theme.TunerSurface

/** "Animation;Kids" -> "Animation;Kids", "NEWS" -> "News" — capitalizes each word, leaves separators as-is. */
private fun String.toInitCaps(): String =
    split(Regex("(?<=[ ;/,])|(?=[ ;/,])"))
        .joinToString("") { part ->
            if (part.isNotEmpty() && part[0].isLetter()) {
                part.replaceFirstChar { it.uppercase() }.let { it[0] + it.substring(1).lowercase() }
            } else {
                part
            }
        }

/**
 * EPG-style category header — bold and cyan to stand out from channel rows, in init caps
 * (not shouty all-caps) to match the rest of the app's labels.
 */
@Composable
fun GroupHeader(title: String, modifier: Modifier = Modifier) {
    Text(
        text = title.toInitCaps(),
        style = MaterialTheme.typography.titleMedium,
        color = TunerCyan,
        fontWeight = FontWeight.Bold,
        modifier = modifier
            .fillMaxWidth()
            .background(TunerSurface)
            .padding(horizontal = 12.dp, vertical = 6.dp)
    )
}
