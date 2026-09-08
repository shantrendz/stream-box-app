package com.example.tuner.ui.components

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.KeyboardArrowDown
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.tuner.ui.theme.TunerCyan
import com.example.tuner.ui.theme.TunerSurface

/**
 * "Animation;Kids" -> "Animation · Kids", "NEWS" -> "News" — capitalizes each word and swaps
 * the raw playlist ";" separator for a " · " divider, which reads far better in a header.
 */
fun String.toInitCaps(): String =
    replace(Regex("\\s*;\\s*"), " · ")
        .split(Regex("(?<=[ /,])|(?=[ /,])"))
        .joinToString("") { part ->
            if (part.isNotEmpty() && part[0].isLetter()) {
                part.replaceFirstChar { it.uppercase() }.let { it[0] + it.substring(1).lowercase() }
            } else {
                part
            }
        }

/**
 * EPG-style category header — bold and cyan to stand out from channel rows, in init caps
 * (not shouty all-caps) to match the rest of the app's labels. Doubles as an accordion
 * toggle: tapping it collapses/expands the channels under it, with a chevron that rotates
 * to reflect the current state. Long category names are truncated (never wrap to a second
 * line) so the channel-count badge and chevron always stay visible on the right.
 */
@Composable
fun GroupHeader(
    title: String,
    channelCount: Int,
    isExpanded: Boolean,
    onToggle: () -> Unit,
    modifier: Modifier = Modifier
) {
    val rotation by animateFloatAsState(targetValue = if (isExpanded) 0f else -90f, label = "chevronRotation")
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(TunerSurface)
            .clickable(onClick = onToggle)
            .padding(horizontal = 12.dp, vertical = 6.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(
            text = title.toInitCaps(),
            style = MaterialTheme.typography.titleMedium,
            color = TunerCyan,
            fontWeight = FontWeight.Bold,
            maxLines = 1,
            overflow = TextOverflow.Ellipsis,
            modifier = Modifier.weight(1f)
        )
        Spacer(Modifier.width(8.dp))
        Box(
            modifier = Modifier
                .clip(CircleShape)
                .background(TunerCyan.copy(alpha = 0.18f))
                .padding(horizontal = 8.dp, vertical = 2.dp),
            contentAlignment = Alignment.Center
        ) {
            Text(
                text = channelCount.toString(),
                style = MaterialTheme.typography.labelSmall,
                color = TunerCyan,
                fontWeight = FontWeight.Bold
            )
        }
        Spacer(Modifier.width(6.dp))
        Icon(
            imageVector = Icons.Filled.KeyboardArrowDown,
            contentDescription = if (isExpanded) "Collapse" else "Expand",
            tint = TunerCyan,
            modifier = Modifier.rotate(rotation)
        )
    }
}
