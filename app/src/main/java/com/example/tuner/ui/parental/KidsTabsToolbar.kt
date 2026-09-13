package com.example.tuner.ui.parental

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.example.tuner.ui.channels.KidsTab
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerAmberTint
import com.example.tuner.ui.theme.TunerTextPrimary

/** Replaces the Region/Category/Language toolbar while Kids Mode is on. */
@Composable
fun KidsTabsToolbar(selected: KidsTab, onSelect: (KidsTab) -> Unit, modifier: Modifier = Modifier) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        KidsTab.entries.forEach { tab ->
            FilterChip(
                selected = tab == selected,
                onClick = { onSelect(tab) },
                label = { Text(if (tab == KidsTab.CHANNELS) "Kids channels" else "Favorites") },
                colors = FilterChipDefaults.filterChipColors(
                    selectedContainerColor = TunerAmberTint,
                    selectedLabelColor = TunerAmber,
                    labelColor = TunerTextPrimary
                )
            )
        }
    }
}
