package com.example.tuner.ui.channels

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import com.example.tuner.data.model.Channel
import com.example.tuner.data.model.CustomSource
import com.example.tuner.data.model.SourceLoadStatus
import com.example.tuner.data.repository.AppStateRepository
import com.example.tuner.data.repository.ChannelRepository
import com.example.tuner.data.repository.CustomSourceRepository
import com.example.tuner.data.repository.FavoritesRepository
import com.example.tuner.data.repository.HistoryRepository
import com.example.tuner.data.repository.SingleLoadResult
import com.example.tuner.data.repository.TopMode
import com.example.tuner.data.repository.favoriteKey
import com.example.tuner.domain.PlaylistSource
import com.example.tuner.ui.theme.ThemeMode
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

/** List (teletext rows) or Grid (icon + name tiles). */
enum class ChannelViewMode { LIST, GRID }

data class ChannelListUiState(
    val topMode: TopMode = TopMode.CATALOG,
    val region: PlaylistSource.Region = PlaylistSource.REGIONS.first(),
    val category: PlaylistSource.Category = PlaylistSource.CATEGORIES.first(),
    val language: PlaylistSource.Language = PlaylistSource.LANGUAGES.first(),
    val isLoading: Boolean = false,
    val loadedChannels: List<Channel> = emptyList(),
    val filterText: String = "",
    val groupBySource: Boolean = false,
    val viewMode: ChannelViewMode = ChannelViewMode.LIST,
    val customSources: List<CustomSource> = emptyList(),
    val customSourcesStatusMessage: String? = null,
    val loadErrorMessage: String? = null,
    val selectedChannel: Channel? = null,
    val favoriteKeys: Set<String> = emptySet(),
    val historyEnabled: Boolean = true,
    val themeMode: ThemeMode = ThemeMode.SYSTEM
) {
    val filteredChannels: List<Channel>
        get() = if (filterText.isBlank()) {
            loadedChannels
        } else {
            loadedChannels.filter { it.name.contains(filterText, ignoreCase = true) }
        }

    val isCustomSourcesMode: Boolean get() = topMode == TopMode.CUSTOM

    fun isFavorite(channel: Channel): Boolean = favoriteKeys.contains(channel.favoriteKey())
}

class ChannelListViewModel(
    private val channelRepository: ChannelRepository,
    private val customSourceRepository: CustomSourceRepository,
    private val appStateRepository: AppStateRepository,
    private val favoritesRepository: FavoritesRepository,
    private val historyRepository: HistoryRepository
) : ViewModel() {

    private val _uiState = MutableStateFlow(ChannelListUiState())
    val uiState: StateFlow<ChannelListUiState> = _uiState.asStateFlow()

    // Guards against a slow in-flight load overwriting state after a newer selection/mode switch.
    private var loadRequestId: Long = 0L

    // Favorites/History stream continuously from local storage rather than a one-shot fetch,
    // so edits (starring/unstarring, a new play) reflect immediately while viewing them.
    private var localModeJob: Job? = null

    init {
        viewModelScope.launch {
            customSourceRepository.sources.collect { sources ->
                _uiState.update { it.copy(customSources = sources) }
            }
        }
        viewModelScope.launch {
            favoritesRepository.favoriteKeys.collect { keys ->
                _uiState.update { it.copy(favoriteKeys = keys) }
            }
        }
        viewModelScope.launch {
            historyRepository.historyEnabled.collect { enabled ->
                _uiState.update { it.copy(historyEnabled = enabled) }
            }
        }
        viewModelScope.launch {
            appStateRepository.themeMode.collect { mode ->
                _uiState.update { it.copy(themeMode = mode) }
            }
        }
        viewModelScope.launch {
            appStateRepository.channelViewMode.collect { mode ->
                _uiState.update { it.copy(viewMode = mode) }
            }
        }
        restoreLastState()
    }

    private fun restoreLastState() {
        viewModelScope.launch {
            val savedFilter = appStateRepository.lastFilterText.first()
            _uiState.update { it.copy(filterText = savedFilter) }

            val saved = appStateRepository.lastCatalogState.first()
            val region = PlaylistSource.REGIONS.firstOrNull { it.code == saved.regionCode } ?: PlaylistSource.REGIONS.first()
            val category = PlaylistSource.CATEGORIES.firstOrNull { it.slug == saved.categorySlug } ?: PlaylistSource.CATEGORIES.first()
            val language = PlaylistSource.LANGUAGES.firstOrNull { it.code == saved.languageCode } ?: PlaylistSource.LANGUAGES.first()
            _uiState.update { it.copy(region = region, category = category, language = language) }

            when (saved.topMode) {
                TopMode.CATALOG -> reloadCatalog()
                TopMode.CUSTOM -> activateCustomSourcesMode(persist = false)
                TopMode.FAVORITES -> selectFavoritesMode(persist = false)
                TopMode.HISTORY -> selectHistoryMode(persist = false)
            }
        }
    }

    private fun persistCatalogSelection() {
        val state = _uiState.value
        viewModelScope.launch {
            appStateRepository.saveCatalogSelection(state.region.code, state.category.slug, state.language.code)
        }
    }

    /** Selecting any of Region/Category/Language switches back to Catalog mode if not already there. */
    fun selectRegion(region: PlaylistSource.Region, persist: Boolean = true) {
        localModeJob?.cancel()
        _uiState.update { it.copy(topMode = TopMode.CATALOG, region = region) }
        if (persist) {
            viewModelScope.launch { appStateRepository.saveTopMode(TopMode.CATALOG) }
            persistCatalogSelection()
        }
        reloadCatalog()
    }

    fun selectCategory(category: PlaylistSource.Category, persist: Boolean = true) {
        localModeJob?.cancel()
        _uiState.update { it.copy(topMode = TopMode.CATALOG, category = category) }
        if (persist) {
            viewModelScope.launch { appStateRepository.saveTopMode(TopMode.CATALOG) }
            persistCatalogSelection()
        }
        reloadCatalog()
    }

    fun selectLanguage(language: PlaylistSource.Language, persist: Boolean = true) {
        localModeJob?.cancel()
        _uiState.update { it.copy(topMode = TopMode.CATALOG, language = language) }
        if (persist) {
            viewModelScope.launch { appStateRepository.saveTopMode(TopMode.CATALOG) }
            persistCatalogSelection()
        }
        reloadCatalog()
    }

    /** Tapping the "StreamBox" title jumps straight back to the default, unfiltered catalog —
     * clears Region/Category/Language back to "All" and any search text, whatever mode
     * (Favorites/History/Custom sources/filtered Catalog) the user was in. */
    fun resetToAllChannels() {
        localModeJob?.cancel()
        _uiState.update {
            it.copy(
                topMode = TopMode.CATALOG,
                region = PlaylistSource.REGIONS.first(),
                category = PlaylistSource.CATEGORIES.first(),
                language = PlaylistSource.LANGUAGES.first(),
                filterText = ""
            )
        }
        viewModelScope.launch {
            appStateRepository.saveTopMode(TopMode.CATALOG)
            appStateRepository.saveFilterText("")
        }
        persistCatalogSelection()
        reloadCatalog()
    }

    /** Re-fetches using the current Region + Category + Language selection (combined filters). */
    fun reloadCatalog() {
        val requestId = ++loadRequestId
        val state = _uiState.value
        viewModelScope.launch {
            setLoading(true)
            val result = channelRepository.loadCatalog(state.region, state.category, state.language)
            applySingleResult(requestId, result)
        }
    }

    fun activateCustomSourcesMode(persist: Boolean = true) {
        localModeJob?.cancel()
        val sources = _uiState.value.customSources
        _uiState.update { it.copy(topMode = TopMode.CUSTOM, customSources = sources) }
        if (persist) viewModelScope.launch { appStateRepository.saveTopMode(TopMode.CUSTOM) }
        reloadCustomSources()
    }

    /** Starred channels — streamed live from local storage, no network fetch. */
    fun selectFavoritesMode(persist: Boolean = true) {
        loadRequestId++ // invalidate any in-flight network load
        _uiState.update { it.copy(topMode = TopMode.FAVORITES, isLoading = false, loadErrorMessage = null, customSourcesStatusMessage = null) }
        if (persist) viewModelScope.launch { appStateRepository.saveTopMode(TopMode.FAVORITES) }
        localModeJob?.cancel()
        localModeJob = viewModelScope.launch {
            favoritesRepository.favorites.collect { favorites ->
                _uiState.update { it.copy(loadedChannels = favorites) }
            }
        }
    }

    /** Recently-played channels — streamed live from local storage, no network fetch. */
    fun selectHistoryMode(persist: Boolean = true) {
        loadRequestId++
        _uiState.update { it.copy(topMode = TopMode.HISTORY, isLoading = false, loadErrorMessage = null, customSourcesStatusMessage = null) }
        if (persist) viewModelScope.launch { appStateRepository.saveTopMode(TopMode.HISTORY) }
        localModeJob?.cancel()
        localModeJob = viewModelScope.launch {
            historyRepository.history.collect { history ->
                _uiState.update { it.copy(loadedChannels = history) }
            }
        }
    }

    fun reloadCustomSources() {
        val requestId = ++loadRequestId
        viewModelScope.launch {
            setLoading(true)
            val sources = customSourceRepository.currentSources()
            _uiState.update { it.copy(customSources = sources) }
            val result = channelRepository.loadCustomSources(sources)
            if (requestId != loadRequestId) return@launch

            for (source in sources) {
                val failure = result.failures.firstOrNull { it.first.id == source.id }
                val channelCount = result.channels.count { it.source == source.label }
                if (failure != null) {
                    customSourceRepository.updateStatus(source.id, SourceLoadStatus.FAILED, failure.second, 0)
                } else {
                    customSourceRepository.updateStatus(source.id, SourceLoadStatus.OK, null, channelCount)
                }
            }

            val statusMessage = if (result.totalCount == 0) {
                "No custom sources saved yet — add one below."
            } else {
                buildString {
                    append("${result.succeededCount} of ${result.totalCount} sources loaded")
                    if (result.failures.isNotEmpty()) {
                        append(" — failed: ")
                        append(result.failures.joinToString(", ") { (source, msg) -> "${source.label} ($msg)" })
                    }
                }
            }

            _uiState.update {
                it.copy(
                    isLoading = false,
                    loadedChannels = result.channels,
                    customSourcesStatusMessage = statusMessage,
                    loadErrorMessage = null
                )
            }
        }
    }

    private suspend fun applySingleResult(requestId: Long, result: SingleLoadResult) {
        if (requestId != loadRequestId) return
        when (result) {
            is SingleLoadResult.Success -> _uiState.update {
                it.copy(isLoading = false, loadedChannels = result.channels, loadErrorMessage = null, customSourcesStatusMessage = null)
            }
            is SingleLoadResult.Failure -> _uiState.update {
                it.copy(isLoading = false, loadedChannels = emptyList(), loadErrorMessage = result.message, customSourcesStatusMessage = null)
            }
        }
    }

    private fun setLoading(loading: Boolean) {
        _uiState.update { it.copy(isLoading = loading) }
    }

    fun setFilterText(text: String) {
        _uiState.update { it.copy(filterText = text) }
        viewModelScope.launch { appStateRepository.saveFilterText(text) }
    }

    fun toggleGroupBySource() {
        _uiState.update { it.copy(groupBySource = !it.groupBySource) }
    }

    fun setViewMode(mode: ChannelViewMode) {
        _uiState.update { it.copy(viewMode = mode) }
        viewModelScope.launch { appStateRepository.saveChannelViewMode(mode) }
    }

    fun selectChannel(channel: Channel) {
        _uiState.update { it.copy(selectedChannel = channel) }
        viewModelScope.launch { historyRepository.recordPlay(channel) }
    }

    fun clearSelectedChannel() {
        _uiState.update { it.copy(selectedChannel = null) }
    }

    fun toggleFavorite(channel: Channel) {
        viewModelScope.launch { favoritesRepository.toggleFavorite(channel) }
    }

    fun setHistoryEnabled(enabled: Boolean) {
        viewModelScope.launch { historyRepository.setHistoryEnabled(enabled) }
    }

    fun clearHistory() {
        viewModelScope.launch { historyRepository.clearHistory() }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appStateRepository.saveThemeMode(mode) }
    }

    // --- Custom source library CRUD, delegated to the repository, then reload if active. ---

    fun addCustomSource(label: String, url: String) {
        viewModelScope.launch {
            customSourceRepository.addSource(label, url)
            if (_uiState.value.isCustomSourcesMode) reloadCustomSources()
        }
    }

    fun updateCustomSource(id: String, label: String, url: String) {
        viewModelScope.launch {
            customSourceRepository.updateSource(id, label, url)
            if (_uiState.value.isCustomSourcesMode) reloadCustomSources()
        }
    }

    fun removeCustomSource(id: String) {
        viewModelScope.launch {
            customSourceRepository.removeSource(id)
            if (_uiState.value.isCustomSourcesMode) reloadCustomSources()
        }
    }

    fun retryCustomSource(id: String) {
        if (_uiState.value.isCustomSourcesMode) reloadCustomSources()
    }

    fun isWellFormedUrl(url: String): Boolean = channelRepository.isWellFormedUrl(url)

    fun suggestedLabelFor(url: String): String = customSourceRepository.hostnameOf(url)

    companion object {
        fun factory(
            channelRepository: ChannelRepository,
            customSourceRepository: CustomSourceRepository,
            appStateRepository: AppStateRepository,
            favoritesRepository: FavoritesRepository,
            historyRepository: HistoryRepository
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                ChannelListViewModel(
                    channelRepository,
                    customSourceRepository,
                    appStateRepository,
                    favoritesRepository,
                    historyRepository
                )
            }
        }
    }
}
