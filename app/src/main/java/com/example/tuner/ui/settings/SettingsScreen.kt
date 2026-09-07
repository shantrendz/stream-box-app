package com.example.tuner.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.RadioButton
import androidx.compose.material3.RadioButtonDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.tuner.ui.channels.ChannelListViewModel
import com.example.tuner.ui.components.AppIcon
import com.example.tuner.ui.theme.ThemeMode
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerBackground
import com.example.tuner.ui.theme.TunerCyan
import com.example.tuner.ui.theme.TunerOutline
import com.example.tuner.ui.theme.TunerRed
import com.example.tuner.ui.theme.TunerTextPrimary
import com.example.tuner.ui.theme.TunerTextSecondary

/** App-wide preferences: theme (Dark/Light/System) and watch-history controls. */
@Composable
fun SettingsScreen(
    viewModel: ChannelListViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    val uriHandler = LocalUriHandler.current

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TunerBackground)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TunerTextPrimary)
            }
            Text(
                text = "SETTINGS",
                style = MaterialTheme.typography.titleLarge,
                color = TunerAmber,
                fontWeight = FontWeight.Bold
            )
        }

        SectionHeader("THEME")
        Column(Modifier.selectableGroup()) {
            ThemeMode.entries.forEach { mode ->
                ThemeOptionRow(
                    label = when (mode) {
                        ThemeMode.DARK -> "Dark"
                        ThemeMode.LIGHT -> "Light"
                        ThemeMode.SYSTEM -> "System default"
                    },
                    selected = uiState.themeMode == mode,
                    onSelect = { viewModel.setThemeMode(mode) }
                )
            }
        }

        SectionHeader("WATCH HISTORY")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Save watch history", style = MaterialTheme.typography.bodyLarge, color = TunerTextPrimary)
                Text(
                    "When off, playing a channel won't be added to History.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TunerTextSecondary
                )
            }
            Switch(
                checked = uiState.historyEnabled,
                onCheckedChange = { viewModel.setHistoryEnabled(it) },
                colors = SwitchDefaults.colors(checkedThumbColor = TunerAmber, checkedTrackColor = TunerAmber.copy(alpha = 0.4f))
            )
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 6.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1f)) {
                Text("Clear watch history", style = MaterialTheme.typography.bodyLarge, color = TunerTextPrimary)
                Text(
                    "Removes everything already saved. Doesn't affect Favorites.",
                    style = MaterialTheme.typography.labelSmall,
                    color = TunerTextSecondary
                )
            }
            Button(
                onClick = { viewModel.clearHistory() },
                colors = ButtonDefaults.buttonColors(containerColor = TunerRed, contentColor = Color.Black)
            ) {
                Text("CLEAR")
            }
        }

        SectionHeader("ABOUT")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(size = 40.dp)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "StreamBox",
                    style = MaterialTheme.typography.titleMedium,
                    color = TunerAmber,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "Developed by Shanavas Abdul Samad",
                    style = MaterialTheme.typography.bodyLarge,
                    color = TunerTextPrimary
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "shanavas.dev",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TunerCyan,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { uriHandler.openUri("https://shanavas.dev") }
                )
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String) {
    Text(
        text = title,
        style = MaterialTheme.typography.titleMedium,
        color = TunerCyan,
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

@Composable
private fun ThemeOptionRow(label: String, selected: Boolean, onSelect: () -> Unit) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = TunerOutline)
            .selectable(selected = selected, onClick = onSelect)
            .padding(horizontal = 16.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        RadioButton(
            selected = selected,
            onClick = null,
            colors = RadioButtonDefaults.colors(selectedColor = TunerAmber, unselectedColor = TunerTextSecondary)
        )
        Spacer(Modifier.width(4.dp))
        Text(
            text = label,
            style = MaterialTheme.typography.bodyLarge,
            color = if (selected) TunerAmber else TunerTextPrimary
        )
    }
}
