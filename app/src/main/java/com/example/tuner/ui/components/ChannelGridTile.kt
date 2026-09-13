package com.example.tuner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.tuner.data.model.Channel
import com.example.tuner.livecheck.LiveStatus
import com.example.tuner.parental.KidsShieldState
import com.example.tuner.ui.parental.KidsShieldButton
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerAmberTint
import com.example.tuner.ui.theme.TunerOutline
import com.example.tuner.ui.theme.TunerSurface
import com.example.tuner.ui.theme.TunerTextPrimary
import com.example.tuner.ui.theme.TunerTextSecondary

private val TileShape = RoundedCornerShape(10.dp)

/** Icon-mode channel tile: logo (or a monospace initial placeholder) + name, favorite star, no rows/numbers. */
@Composable
fun ChannelGridTile(
    channel: Channel,
    isSelected: Boolean,
    isFavorite: Boolean,
    onClick: () -> Unit,
    onToggleFavorite: () -> Unit,
    liveStatus: LiveStatus = LiveStatus.UNCHECKED,
    shieldState: KidsShieldState? = null,
    onShieldClick: () -> Unit = {},
    modifier: Modifier = Modifier
) {
    Box(modifier = modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .clip(TileShape)
                .background(if (isSelected) TunerAmberTint else TunerSurface)
                .border(width = if (isSelected) 1.5.dp else 1.dp, color = if (isSelected) TunerAmber else TunerOutline, shape = TileShape)
                .clickable(onClick = onClick)
                .semantics { if (liveStatus != LiveStatus.UNCHECKED) stateDescription = liveStatusDescription(liveStatus) }
                .padding(10.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
                contentAlignment = Alignment.Center
            ) {
                if (!channel.logoUrl.isNullOrBlank()) {
                    AsyncImage(
                        model = channel.logoUrl,
                        contentDescription = null,
                        contentScale = ContentScale.Fit,
                        modifier = Modifier.fillMaxWidth()
                    )
                } else {
                    Text(
                        text = channel.name.take(1).uppercase(),
                        style = MaterialTheme.typography.displayLarge,
                        color = TunerAmber
                    )
                }
            }

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.padding(top = 6.dp)
            ) {
                LiveStatusDot(status = liveStatus, size = 7.dp)
                Spacer(Modifier.width(4.dp))
                Text(
                    text = channel.name,
                    style = MaterialTheme.typography.labelMedium,
                    color = if (isSelected) TunerAmber else TunerTextPrimary,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                    textAlign = TextAlign.Center,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )
            }
        }

        // Anchored to the tile's actual top-right corner, not just the logo area within it.
        IconButton(
            onClick = onToggleFavorite,
            modifier = Modifier
                .align(Alignment.TopEnd)
                .size(28.dp)
        ) {
            Icon(
                imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                tint = if (isFavorite) TunerAmber else TunerTextSecondary,
                modifier = Modifier.size(18.dp)
            )
        }

        if (shieldState != null) {
            KidsShieldButton(
                state = shieldState,
                onClick = onShieldClick,
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .size(28.dp),
                iconSize = 18.dp
            )
        }
    }
}
