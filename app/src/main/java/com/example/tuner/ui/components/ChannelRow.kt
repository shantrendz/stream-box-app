package com.example.tuner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tuner.data.model.Channel
import com.example.tuner.livecheck.LiveStatus
import com.example.tuner.parental.KidsShieldState
import com.example.tuner.ui.parental.KidsShieldButton
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerAmberTint
import com.example.tuner.ui.theme.TunerOutline
import com.example.tuner.ui.theme.TunerTextPrimary
import com.example.tuner.ui.theme.TunerTextSecondary

/**
 * A single teletext-row style channel entry: numbered index, status dot, name, favorite
 * star, and — when channels are merged from multiple custom sources — a small Source pill
 * so channels stay distinguishable even if two sources share a name.
 *
 * Flat row, hairline divider, no card/rounded-corner/shadow chrome. Selected state reads as
 * an amber left-border accent + tinted background rather than a generic Material ripple.
 */
@Composable
fun ChannelRow(
    index: Int,
    channel: Channel,
    isSelected: Boolean,
    isFavorite: Boolean,
    showSourceTag: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    liveStatus: LiveStatus = LiveStatus.UNCHECKED,
    shieldState: KidsShieldState? = null,
    onShieldClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    val rowShape = RoundedCornerShape(12.dp)
    Row(
        modifier = modifier
            .fillMaxWidth()
            .padding(horizontal = 8.dp, vertical = 3.dp)
            .clip(rowShape)
            .background(if (isSelected) TunerAmberTint else MaterialTheme.colorScheme.surface)
            .border(width = if (isSelected) 1.5.dp else 1.dp, color = if (isSelected) TunerAmber else TunerOutline, shape = rowShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .width(3.dp)
                .height(20.dp)
                .background(if (isSelected) TunerAmber else Color.Transparent)
        )

        Spacer(Modifier.width(9.dp))

        Text(
            text = index.toString().padStart(3, '0'),
            style = MaterialTheme.typography.labelMedium,
            color = TunerTextSecondary
        )

        Spacer(Modifier.width(10.dp))

        LiveStatusDot(status = liveStatus)

        Spacer(Modifier.width(10.dp))

        if (!channel.logoUrl.isNullOrBlank()) {
            AsyncImage(
                model = channel.logoUrl,
                contentDescription = null,
                contentScale = ContentScale.Fit,
                modifier = Modifier.size(24.dp)
            )
            Spacer(Modifier.width(10.dp))
        }

        Text(
            text = channel.name,
            style = MaterialTheme.typography.bodyLarge,
            color = if (isSelected) TunerAmber else TunerTextPrimary,
            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
            modifier = Modifier.weight(1f)
        )

        if (showSourceTag) {
            Spacer(Modifier.width(4.dp))
            SourceTag(channel.source)
        }

        if (shieldState != null) {
            KidsShieldButton(state = shieldState, onClick = onShieldClick, modifier = Modifier.size(32.dp))
        }

        IconButton(onClick = onToggleFavorite, modifier = Modifier.size(32.dp)) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) TunerAmber else TunerTextSecondary
            )
        }
    }
}

@Composable
fun SourceTag(label: String, modifier: Modifier = Modifier) {
    Box(
        modifier = modifier
            .border(1.dp, TunerOutline)
            .padding(horizontal = 6.dp, vertical = 2.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelSmall,
            color = TunerTextSecondary
        )
    }
}
