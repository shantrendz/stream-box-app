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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.selection.selectable
import androidx.compose.foundation.selection.selectableGroup
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.ui.draw.clip
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Copyright
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
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalUriHandler
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.unit.dp
import com.example.tuner.ui.channels.ChannelListViewModel
import com.example.tuner.ui.components.AppIcon
import com.example.tuner.ui.components.NowPlayingBanner
import androidx.compose.ui.graphics.Brush
import com.example.tuner.ui.theme.ThemeMode
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerBackground
import com.example.tuner.ui.theme.TunerCyan
import com.example.tuner.ui.theme.TunerGradientBottom
import com.example.tuner.ui.theme.TunerGradientTop
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
    val context = LocalContext.current
    val appVersionName = remember {
        runCatching { context.packageManager.getPackageInfo(context.packageName, 0).versionName }
            .getOrNull() ?: "1.0"
    }
    val currentYear = remember { java.util.Calendar.getInstance().get(java.util.Calendar.YEAR) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TunerBackground)
            .verticalScroll(rememberScrollState())
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .background(Brush.verticalGradient(listOf(TunerGradientTop, TunerGradientBottom)))
                .padding(horizontal = 8.dp, vertical = 8.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back", tint = TunerTextPrimary)
            }
            Text(
                text = "Settings",
                style = MaterialTheme.typography.titleLarge,
                color = TunerAmber,
                fontWeight = FontWeight.Bold
            )
        }

        uiState.selectedChannel?.let { channel ->
            NowPlayingBanner(
                channelName = channel.name,
                onClick = onBack,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        SectionHeader("Theme")
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

        Spacer(Modifier.height(28.dp))
        SectionHeader("Watch History")
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

        Spacer(Modifier.height(28.dp))
        SectionHeader("About")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp, vertical = 10.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            AppIcon(size = 56.dp)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = "StreamBox",
                    style = MaterialTheme.typography.titleMedium,
                    color = TunerAmber,
                    fontWeight = FontWeight.Bold
                )
                Spacer(Modifier.height(4.dp))
                Text(
                    text = "shanavas.dev",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TunerCyan,
                    textDecoration = TextDecoration.Underline,
                    modifier = Modifier.clickable { uriHandler.openUri("https://shanavas.dev") }
                )
                Spacer(Modifier.height(6.dp))
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(
                        imageVector = Icons.Filled.Copyright,
                        contentDescription = null,
                        tint = TunerTextSecondary,
                        modifier = Modifier.size(14.dp)
                    )
                    Spacer(Modifier.width(4.dp))
                    Text(
                        text = "$currentYear StreamBox  ·  v$appVersionName",
                        style = MaterialTheme.typography.labelSmall,
                        color = TunerTextSecondary
                    )
                }
            }
        }

        Spacer(Modifier.height(16.dp))
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
    val shape = RoundedCornerShape(12.dp)
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp, vertical = 4.dp)
            .clip(shape)
            .border(width = if (selected) 1.5.dp else 1.dp, color = if (selected) TunerAmber else TunerOutline, shape = shape)
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
