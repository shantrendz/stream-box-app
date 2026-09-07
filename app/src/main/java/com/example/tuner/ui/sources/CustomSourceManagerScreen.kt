package com.example.tuner.ui.sources

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.material3.IconButton
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tuner.data.model.CustomSource
import com.example.tuner.data.model.SourceLoadStatus
import com.example.tuner.ui.channels.ChannelListViewModel
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerBackground
import com.example.tuner.ui.theme.TunerCyan
import com.example.tuner.ui.theme.TunerOutline
import com.example.tuner.ui.theme.TunerRed
import com.example.tuner.ui.theme.TunerTextPrimary
import com.example.tuner.ui.theme.TunerTextSecondary

/**
 * Add / edit / remove screen for the saved custom-source library, plus per-entry status and
 * a manual retry action. Every saved URL loads together (in Custom Sources mode) rather than
 * one at a time.
 */
@Composable
fun CustomSourceManagerScreen(
    viewModel: ChannelListViewModel,
    onBack: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var editingSource by remember { mutableStateOf<CustomSource?>(null) }
    var showAddForm by remember { mutableStateOf(false) }

    Column(
        modifier = modifier
            .fillMaxSize()
            .background(TunerBackground)
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
                text = "CUSTOM SOURCES",
                style = MaterialTheme.typography.titleLarge,
                color = TunerAmber,
                fontWeight = FontWeight.Bold
            )
        }

        if (showAddForm || editingSource != null) {
            SourceForm(
                initial = editingSource,
                suggestedLabel = viewModel::suggestedLabelFor,
                isValidUrl = viewModel::isWellFormedUrl,
                onSave = { label, url ->
                    val existing = editingSource
                    if (existing != null) {
                        viewModel.updateCustomSource(existing.id, label, url)
                    } else {
                        viewModel.addCustomSource(label, url)
                    }
                    editingSource = null
                    showAddForm = false
                },
                onCancel = {
                    editingSource = null
                    showAddForm = false
                }
            )
        } else {
            Button(
                onClick = { showAddForm = true },
                colors = ButtonDefaults.buttonColors(containerColor = TunerAmber, contentColor = Color.Black),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            ) {
                Text("+ ADD SOURCE")
            }
        }

        if (uiState.customSources.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    text = "No custom sources saved yet.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TunerTextSecondary
                )
            }
        } else {
            LazyColumn(modifier = Modifier.fillMaxSize()) {
                items(uiState.customSources, key = { it.id }) { source ->
                    SourceRow(
                        source = source,
                        onEdit = { editingSource = source; showAddForm = false },
                        onRemove = { viewModel.removeCustomSource(source.id) },
                        onRetry = { viewModel.retryCustomSource(source.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun SourceForm(
    initial: CustomSource?,
    suggestedLabel: (String) -> String,
    isValidUrl: (String) -> Boolean,
    onSave: (label: String, url: String) -> Unit,
    onCancel: () -> Unit
) {
    var label by remember { mutableStateOf(initial?.label ?: "") }
    var url by remember { mutableStateOf(initial?.url ?: "") }
    var showUrlError by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .border(1.dp, TunerOutline)
            .padding(12.dp)
    ) {
        OutlinedTextField(
            value = label,
            onValueChange = { label = it },
            label = { Text("Source label") },
            placeholder = { Text(if (url.isNotBlank()) suggestedLabel(url) else "e.g. My Provider") },
            singleLine = true,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        Spacer(Modifier.height(8.dp))

        OutlinedTextField(
            value = url,
            onValueChange = { url = it; showUrlError = false },
            label = { Text("Playlist / stream URL") },
            placeholder = { Text("https://…") },
            singleLine = true,
            isError = showUrlError,
            colors = fieldColors(),
            modifier = Modifier.fillMaxWidth()
        )

        if (showUrlError) {
            Text(
                text = "Enter a well-formed http(s) URL.",
                style = MaterialTheme.typography.labelSmall,
                color = TunerRed
            )
        }

        Spacer(Modifier.height(8.dp))

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Button(
                onClick = {
                    if (isValidUrl(url)) {
                        onSave(label, url)
                    } else {
                        showUrlError = true
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = TunerAmber, contentColor = Color.Black)
            ) {
                Text(if (initial != null) "SAVE" else "ADD")
            }
            TextButton(onClick = onCancel) {
                Text("CANCEL", color = TunerTextSecondary)
            }
        }
    }
}

@Composable
private fun fieldColors() = OutlinedTextFieldDefaults.colors(
    focusedBorderColor = TunerAmber,
    unfocusedBorderColor = TunerOutline,
    focusedTextColor = TunerTextPrimary,
    unfocusedTextColor = TunerTextPrimary,
    cursorColor = TunerAmber
)

@Composable
private fun SourceRow(
    source: CustomSource,
    onEdit: () -> Unit,
    onRemove: () -> Unit,
    onRetry: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .border(width = 1.dp, color = TunerOutline)
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(source.label, style = MaterialTheme.typography.bodyLarge, color = TunerTextPrimary, fontWeight = FontWeight.SemiBold)
            Text(source.url, style = MaterialTheme.typography.labelSmall, color = TunerTextSecondary)
            StatusLine(source)
        }

        if (source.lastLoadStatus == SourceLoadStatus.FAILED) {
            IconButton(onClick = onRetry) {
                Icon(Icons.Filled.Refresh, contentDescription = "Retry this source", tint = TunerAmber)
            }
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, contentDescription = "Edit", tint = TunerTextSecondary)
        }
        IconButton(onClick = onRemove) {
            Icon(Icons.Filled.Delete, contentDescription = "Remove", tint = TunerRed)
        }
    }
}

@Composable
private fun StatusLine(source: CustomSource) {
    val (text, color) = when (source.lastLoadStatus) {
        SourceLoadStatus.OK -> "${source.channelCount} channels loaded" to TunerCyan
        SourceLoadStatus.FAILED -> "Failed: ${source.lastError ?: "unknown error"}" to TunerRed
        SourceLoadStatus.LOADING -> "Loading…" to TunerTextSecondary
        SourceLoadStatus.NEVER_LOADED -> "Not loaded yet" to TunerTextSecondary
    }
    Text(text = text, style = MaterialTheme.typography.labelSmall, color = color)
}
