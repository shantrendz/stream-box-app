package com.example.tuner.ui.channels

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.ui.graphics.Brush
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.asPaddingValues
import androidx.compose.foundation.layout.fillMaxHeight
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.navigationBars
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.windowInsetsPadding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.grid.rememberLazyGridState
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.GridView
import androidx.compose.material.icons.filled.KeyboardArrowUp
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.UnfoldLess
import androidx.compose.material.icons.filled.UnfoldMore
import androidx.compose.material.icons.automirrored.filled.ViewList
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.SwitchDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import com.example.tuner.data.model.Channel
import com.example.tuner.data.repository.TopMode
import com.example.tuner.data.repository.favoriteKey
import com.example.tuner.ui.components.AppIcon
import com.example.tuner.ui.components.ChannelGridTile
import com.example.tuner.ui.components.ChannelRow
import com.example.tuner.ui.components.GroupHeader
import com.example.tuner.ui.components.RegionCategoryToolbar
import com.example.tuner.ui.player.FullScreenPlayerHost
import com.example.tuner.ui.player.PlayerScreen
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerBackground
import com.example.tuner.ui.theme.TunerGradientBottom
import com.example.tuner.ui.theme.TunerGradientTop
import com.example.tuner.ui.theme.TunerOutline
import com.example.tuner.ui.theme.TunerTextPrimary
import com.example.tuner.ui.theme.TunerTextSecondary

private const val WIDE_LAYOUT_MIN_DP = 700

@Composable
fun ChannelListScreen(
    viewModel: ChannelListViewModel,
    onOpenCustomSourceManager: () -> Unit,
    onOpenSettings: () -> Unit,
    modifier: Modifier = Modifier
) {
    val uiState by viewModel.uiState.collectAsState()
    var isFullScreenPlayer by remember { mutableStateOf(false) }
    val selected = uiState.selectedChannel

    if (isFullScreenPlayer && selected != null) {
        FullScreenPlayerHost(
            channel = selected,
            onExit = { isFullScreenPlayer = false },
            isFavorite = uiState.favoriteKeys.contains(selected.favoriteKey()),
            onToggleFavorite = { viewModel.toggleFavorite(selected) }
        )
        return
    }

    // Title bar is always the first thing on screen — above the player, not scrolled away
    // with the list — per the "title always top" requirement.
    Column(modifier.fillMaxSize().background(TunerBackground)) {
        TunerTitleBar(
            viewMode = uiState.viewMode,
            onSetViewMode = viewModel::setViewMode,
            onOpenCustomSourceManager = onOpenCustomSourceManager,
            onOpenSettings = onOpenSettings,
            onResetToAllChannels = viewModel::resetToAllChannels
        )

        BoxWithConstraints(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
        ) {
            val isWide = maxWidth.value >= WIDE_LAYOUT_MIN_DP

            if (isWide) {
                Row(Modifier.fillMaxSize()) {
                    ChannelListPane(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier
                            .weight(0.42f)
                            .fillMaxHeight()
                    )
                    Box(
                        modifier = Modifier
                            .weight(0.58f)
                            .fillMaxHeight()
                            .background(TunerBackground)
                    ) {
                        if (selected != null) {
                            PlayerScreen(
                                channel = selected,
                                onBack = null,
                                onToggleFullScreen = { isFullScreenPlayer = true },
                                isFavorite = uiState.favoriteKeys.contains(selected.favoriteKey()),
                                onToggleFavorite = { viewModel.toggleFavorite(selected) },
                                modifier = Modifier.fillMaxSize()
                            )
                        } else {
                            NoChannelSelectedPlaceholder()
                        }
                    }
                }
            } else {
                // Phone/portrait: the player docks at a fixed height at the top (when a
                // channel is playing) with the toolbar, search filter, and full channel
                // list always visible below it, so switching channels never requires
                // leaving the player.
                Column(Modifier.fillMaxSize()) {
                    if (selected != null) {
                        PlayerScreen(
                            channel = selected,
                            onBack = { viewModel.clearSelectedChannel() },
                            onToggleFullScreen = { isFullScreenPlayer = true },
                            isFavorite = uiState.favoriteKeys.contains(selected.favoriteKey()),
                            onToggleFavorite = { viewModel.toggleFavorite(selected) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    ChannelListPane(
                        uiState = uiState,
                        viewModel = viewModel,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f)
                    )
                }
            }
        }
    }
}

@Composable
private fun TunerTitleBar(
    viewMode: ChannelViewMode,
    onSetViewMode: (ChannelViewMode) -> Unit,
    onOpenCustomSourceManager: () -> Unit,
    onOpenSettings: () -> Unit,
    onResetToAllChannels: () -> Unit,
    modifier: Modifier = Modifier
) {
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(Brush.verticalGradient(listOf(TunerGradientTop, TunerGradientBottom)))
            .padding(horizontal = 12.dp, vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Row(
            verticalAlignment = Alignment.CenterVertically,
            modifier = Modifier.clickable(onClick = onResetToAllChannels)
        ) {
            AppIcon(size = 34.dp)
            Text(
                text = "StreamBox",
                style = MaterialTheme.typography.titleLarge,
                color = TunerAmber,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        Box(Modifier.weight(1f))
        IconButton(onClick = { onSetViewMode(if (viewMode == ChannelViewMode.LIST) ChannelViewMode.GRID else ChannelViewMode.LIST) }) {
            Icon(
                imageVector = if (viewMode == ChannelViewMode.LIST) Icons.Filled.GridView else Icons.AutoMirrored.Filled.ViewList,
                contentDescription = if (viewMode == ChannelViewMode.LIST) "Switch to icon view" else "Switch to list view",
                tint = TunerTextPrimary
            )
        }
        IconButton(onClick = onOpenCustomSourceManager) {
            Icon(Icons.Filled.Link, contentDescription = "Manage custom sources", tint = TunerTextPrimary)
        }
        IconButton(onClick = onOpenSettings) {
            Icon(Icons.Filled.Settings, contentDescription = "Settings", tint = TunerTextPrimary)
        }
    }
}

@Composable
private fun NoChannelSelectedPlaceholder() {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Text(
            text = "SELECT A CHANNEL TO BEGIN",
            style = MaterialTheme.typography.titleMedium,
            color = TunerTextSecondary
        )
    }
}

@Composable
private fun ChannelListPane(
    uiState: ChannelListUiState,
    viewModel: ChannelListViewModel,
    modifier: Modifier = Modifier
) {
    Column(modifier.background(TunerBackground)) {
        RegionCategoryToolbar(
            topMode = uiState.topMode,
            region = uiState.region,
            category = uiState.category,
            language = uiState.language,
            onSelectRegion = viewModel::selectRegion,
            onSelectCategory = viewModel::selectCategory,
            onSelectLanguage = viewModel::selectLanguage,
            onSelectCustomSources = viewModel::activateCustomSourcesMode,
            onSelectFavorites = viewModel::selectFavoritesMode,
            onSelectHistory = viewModel::selectHistoryMode,
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 12.dp, vertical = 4.dp)
        )

        OutlinedTextField(
            value = uiState.filterText,
            onValueChange = viewModel::setFilterText,
            placeholder = { Text("Search channels…", style = MaterialTheme.typography.bodyLarge) },
            textStyle = MaterialTheme.typography.bodyLarge,
            singleLine = true,
            shape = RoundedCornerShape(24.dp),
            trailingIcon = {
                if (uiState.filterText.isNotEmpty()) {
                    IconButton(onClick = { viewModel.setFilterText("") }, modifier = Modifier.size(36.dp)) {
                        Icon(
                            Icons.Filled.Close,
                            contentDescription = "Clear search",
                            tint = TunerTextSecondary,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            },
            colors = OutlinedTextFieldDefaults.colors(
                focusedBorderColor = TunerAmber,
                unfocusedBorderColor = TunerOutline,
                focusedTextColor = TunerTextPrimary,
                unfocusedTextColor = TunerTextPrimary,
                cursorColor = TunerAmber
            ),
            modifier = Modifier
                .fillMaxWidth()
                .height(54.dp)
                .padding(horizontal = 8.dp, vertical = 2.dp)
        )

        if (uiState.isCustomSourcesMode) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text(
                    text = "Group by source",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TunerTextSecondary,
                    modifier = Modifier.weight(1f)
                )
                Switch(
                    checked = uiState.groupBySource,
                    onCheckedChange = { viewModel.toggleGroupBySource() },
                    colors = SwitchDefaults.colors(checkedThumbColor = TunerAmber, checkedTrackColor = TunerAmber.copy(alpha = 0.4f))
                )
            }

        }

        if (uiState.topMode == TopMode.HISTORY && !uiState.historyEnabled) {
            Text(
                text = "Saving watch history is turned off — see Settings to re-enable it.",
                style = MaterialTheme.typography.labelSmall,
                color = TunerTextSecondary,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        uiState.loadErrorMessage?.let { message ->
            Text(
                text = "Couldn't load playlist: $message",
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        if (uiState.isLoading) {
            Text(
                text = "Loading…",
                style = MaterialTheme.typography.bodyMedium,
                color = TunerTextSecondary,
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)
            )
        }

        ChannelList(
            channels = uiState.filteredChannels,
            groupBySource = uiState.groupBySource && uiState.isCustomSourcesMode,
            showSourceTag = uiState.isCustomSourcesMode,
            viewMode = uiState.viewMode,
            selectedChannel = uiState.selectedChannel,
            favoriteKeys = uiState.favoriteKeys,
            isSearching = uiState.filterText.isNotBlank(),
            statusMessage = uiState.customSourcesStatusMessage,
            emptyMessage = emptyMessageFor(uiState.topMode),
            onSelect = viewModel::selectChannel,
            onToggleFavorite = viewModel::toggleFavorite,
            modifier = Modifier.fillMaxSize()
        )
    }
}

private fun emptyMessageFor(mode: TopMode): String = when (mode) {
    TopMode.FAVORITES -> "NO FAVORITES YET — TAP ★ ON A CHANNEL"
    TopMode.HISTORY -> "NO WATCH HISTORY YET"
    else -> "NO CHANNELS FOUND"
}

private data class NumberedChannel(val index: Int, val channel: Channel)

@Composable
private fun ChannelList(
    channels: List<Channel>,
    groupBySource: Boolean,
    showSourceTag: Boolean,
    viewMode: ChannelViewMode,
    selectedChannel: Channel?,
    favoriteKeys: Set<String>,
    isSearching: Boolean,
    statusMessage: String? = null,
    emptyMessage: String,
    onSelect: (Channel) -> Unit,
    onToggleFavorite: (Channel) -> Unit,
    modifier: Modifier = Modifier
) {
    val grouped = remember(channels, groupBySource) {
        val groupKeySelector: (Channel) -> String = if (groupBySource) { c -> c.source } else { c -> c.group }
        val sorted = channels.sortedWith(compareBy(groupKeySelector).thenBy { it.name.lowercase() })
        var counter = 0
        sorted
            .map { channel -> counter++; NumberedChannel(counter, channel) }
            .groupBy { groupKeySelector(it.channel) }
            .toSortedMap()
    }

    // All categories start open; the user collapses ones they don't want to see. A search
    // in progress always shows every group that has matches — a hit inside a collapsed
    // category shouldn't stay hidden — so manual collapse state is ignored while searching.
    val collapsedGroups = remember { mutableStateMapOf<String, Boolean>() }

    if (channels.isEmpty()) {
        Box(modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
            Text(
                text = emptyMessage,
                style = MaterialTheme.typography.titleMedium,
                color = TunerTextSecondary
            )
        }
        return
    }

    val coroutineScope = rememberCoroutineScope()

    Column(modifier) {
        if (grouped.size > 1 || statusMessage != null) {
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (statusMessage != null) {
                    Text(
                        text = statusMessage,
                        style = MaterialTheme.typography.labelSmall,
                        color = TunerTextSecondary,
                        modifier = Modifier.weight(1f)
                    )
                } else {
                    Spacer(Modifier.weight(1f))
                }

                if (grouped.size > 1) {
                    val anyCollapsed = grouped.keys.any { collapsedGroups[it] == true }
                    TextButton(
                        onClick = {
                            grouped.keys.forEach { groupName -> collapsedGroups[groupName] = !anyCollapsed }
                        }
                    ) {
                        Icon(
                            imageVector = if (anyCollapsed) Icons.Filled.UnfoldMore else Icons.Filled.UnfoldLess,
                            contentDescription = null,
                            tint = TunerAmber,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = if (anyCollapsed) "Expand all" else "Collapse all",
                            style = MaterialTheme.typography.labelLarge,
                            color = TunerAmber
                        )
                    }
                }
            }
        }

        Box(Modifier.weight(1f)) {
        if (viewMode == ChannelViewMode.GRID) {
            val gridState = rememberLazyGridState()
            LazyVerticalGrid(
                columns = GridCells.Adaptive(minSize = 96.dp),
                state = gridState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = WindowInsets.navigationBars.asPaddingValues()
            ) {
                grouped.forEach { (groupName, numberedChannels) ->
                    val isExpanded = isSearching || collapsedGroups[groupName] != true
                    item(key = "header:$groupName", span = { GridItemSpan(maxLineSpan) }) {
                        GroupHeader(
                            title = groupName,
                            channelCount = numberedChannels.size,
                            isExpanded = isExpanded,
                            onToggle = { collapsedGroups[groupName] = collapsedGroups[groupName] != true }
                        )
                    }
                    if (isExpanded) {
                        items(numberedChannels, key = { "${it.channel.source}:${it.channel.streamUrl}:${it.index}" }) { numbered ->
                            ChannelGridTile(
                                channel = numbered.channel,
                                isSelected = numbered.channel == selectedChannel,
                                isFavorite = favoriteKeys.contains(numbered.channel.favoriteKey()),
                                onClick = { onSelect(numbered.channel) },
                                onToggleFavorite = { onToggleFavorite(numbered.channel) },
                                modifier = Modifier.padding(4.dp)
                            )
                        }
                    }
                }
            }
            ScrollToTopButton(
                visible = gridState.firstVisibleItemIndex > 0,
                onClick = { coroutineScope.launch { gridState.animateScrollToItem(0) } }
            )
        } else {
            val listState = rememberLazyListState()
            LazyColumn(
                state = listState,
                modifier = Modifier.fillMaxSize(),
                contentPadding = WindowInsets.navigationBars.asPaddingValues()
            ) {
                grouped.forEach { (groupName, numberedChannels) ->
                    val isExpanded = isSearching || collapsedGroups[groupName] != true
                    item(key = "header:$groupName") {
                        GroupHeader(
                            title = groupName,
                            channelCount = numberedChannels.size,
                            isExpanded = isExpanded,
                            onToggle = { collapsedGroups[groupName] = collapsedGroups[groupName] != true }
                        )
                    }
                    if (isExpanded) {
                        items(numberedChannels, key = { "${it.channel.source}:${it.channel.streamUrl}:${it.index}" }) { numbered ->
                            ChannelRow(
                                index = numbered.index,
                                channel = numbered.channel,
                                isSelected = numbered.channel == selectedChannel,
                                isFavorite = favoriteKeys.contains(numbered.channel.favoriteKey()),
                                showSourceTag = showSourceTag,
                                onClick = { onSelect(numbered.channel) },
                                onToggleFavorite = { onToggleFavorite(numbered.channel) }
                            )
                        }
                    }
                }
            }
            ScrollToTopButton(
                visible = listState.firstVisibleItemIndex > 0,
                onClick = { coroutineScope.launch { listState.animateScrollToItem(0) } }
            )
        }
        }
    }
}

@Composable
private fun BoxScope.ScrollToTopButton(visible: Boolean, onClick: () -> Unit) {
    AnimatedVisibility(
        visible = visible,
        enter = fadeIn(),
        exit = fadeOut(),
        modifier = Modifier
            .align(Alignment.BottomEnd)
            .windowInsetsPadding(WindowInsets.navigationBars)
            .padding(16.dp)
    ) {
        FloatingActionButton(
            onClick = onClick,
            containerColor = TunerAmber,
            contentColor = TunerBackground
        ) {
            Icon(Icons.Filled.KeyboardArrowUp, contentDescription = "Scroll to top")
        }
    }
}
