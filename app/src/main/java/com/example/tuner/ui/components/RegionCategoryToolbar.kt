package com.example.tuner.ui.components

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tuner.data.repository.TopMode
import com.example.tuner.domain.PlaylistSource
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerOutline
import com.example.tuner.ui.theme.TunerSurface
import com.example.tuner.ui.theme.TunerTextSecondary

private val ChipShape = RoundedCornerShape(8.dp)

/**
 * Region / Category / Language are combinable filters, applied together — each highlights
 * amber whenever it isn't set to "All". Custom Sources / Favorites / History are separate,
 * mutually-exclusive top-level tabs.
 */
@Composable
fun RegionCategoryToolbar(
    topMode: TopMode,
    region: PlaylistSource.Region,
    category: PlaylistSource.Category,
    language: PlaylistSource.Language,
    onSelectRegion: (PlaylistSource.Region) -> Unit,
    onSelectCategory: (PlaylistSource.Category) -> Unit,
    onSelectLanguage: (PlaylistSource.Language) -> Unit,
    onSelectCustomSources: () -> Unit,
    onSelectFavorites: () -> Unit,
    onSelectHistory: () -> Unit,
    modifier: Modifier = Modifier
) {
    var regionMenuExpanded by remember { mutableStateOf(false) }
    var categoryMenuExpanded by remember { mutableStateOf(false) }
    var languageMenuExpanded by remember { mutableStateOf(false) }

    Row(
        modifier = modifier
            .clip(ChipShape)
            .background(TunerSurface)
            .border(width = 1.dp, color = TunerOutline, shape = ChipShape)
            .horizontalScroll(rememberScrollState())
            .padding(horizontal = 8.dp, vertical = 6.dp),
    ) {
        Box {
            ModeChip(
                label = region.displayName,
                isActive = region.code != PlaylistSource.Region.ALL_CODE,
                onClick = { regionMenuExpanded = true }
            )
            DropdownMenu(expanded = regionMenuExpanded, onDismissRequest = { regionMenuExpanded = false }) {
                PlaylistSource.REGIONS.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            regionMenuExpanded = false
                            onSelectRegion(option)
                        }
                    )
                }
            }
        }

        Box {
            ModeChip(
                label = category.displayName,
                isActive = category.slug != PlaylistSource.Category.ALL_SLUG,
                onClick = { categoryMenuExpanded = true }
            )
            DropdownMenu(expanded = categoryMenuExpanded, onDismissRequest = { categoryMenuExpanded = false }) {
                PlaylistSource.CATEGORIES.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            categoryMenuExpanded = false
                            onSelectCategory(option)
                        }
                    )
                }
            }
        }

        Box {
            ModeChip(
                label = language.displayName,
                isActive = language.code != PlaylistSource.Language.ALL_CODE,
                onClick = { languageMenuExpanded = true }
            )
            DropdownMenu(expanded = languageMenuExpanded, onDismissRequest = { languageMenuExpanded = false }) {
                PlaylistSource.LANGUAGES.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option.displayName) },
                        onClick = {
                            languageMenuExpanded = false
                            onSelectLanguage(option)
                        }
                    )
                }
            }
        }

        ModeChip(
            label = "Custom sources",
            isActive = topMode == TopMode.CUSTOM,
            onClick = onSelectCustomSources
        )

        ModeChip(
            label = "★ Favorites",
            isActive = topMode == TopMode.FAVORITES,
            onClick = onSelectFavorites
        )

        ModeChip(
            label = "History",
            isActive = topMode == TopMode.HISTORY,
            onClick = onSelectHistory
        )
    }
}

@Composable
private fun ModeChip(label: String, isActive: Boolean, onClick: () -> Unit) {
    Box(
        modifier = Modifier
            .padding(end = 8.dp)
            .clip(ChipShape)
            .background(if (isActive) TunerAmber.copy(alpha = 0.14f) else TunerSurface)
            .border(width = if (isActive) 1.5.dp else 1.dp, color = if (isActive) TunerAmber else TunerOutline, shape = ChipShape)
            .clickable(onClick = onClick)
            .padding(horizontal = 12.dp, vertical = 7.dp)
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            fontWeight = if (isActive) FontWeight.SemiBold else FontWeight.Normal,
            color = if (isActive) TunerAmber else TunerTextSecondary
        )
    }
}
