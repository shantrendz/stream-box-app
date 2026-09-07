package com.example.tuner.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.tuner.ui.channels.ChannelViewMode
import com.example.tuner.ui.theme.ThemeMode
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private val Context.appStateDataStore by preferencesDataStore(name = "app_state")

/** Which top-level tab was active — Catalog covers the combined Region/Category/Language filters. */
enum class TopMode { CATALOG, CUSTOM, FAVORITES, HISTORY }

/** Region/Category/Language selections plus which top-level tab was active — restored on next launch. */
data class LastCatalogState(
    val topMode: TopMode,
    val regionCode: String,
    val categorySlug: String,
    val languageCode: String
)

/**
 * Persists the last-active top mode, the last Region/Category/Language filter selections, and
 * the last filter/search text across app restarts. The custom-source library itself lives in
 * [CustomSourceRepository].
 */
class AppStateRepository(private val context: Context) {

    private val topModeKey = stringPreferencesKey("last_top_mode")
    private val regionCodeKey = stringPreferencesKey("last_region_code")
    private val categorySlugKey = stringPreferencesKey("last_category_slug")
    private val languageCodeKey = stringPreferencesKey("last_language_code")
    private val filterTextKey = stringPreferencesKey("last_filter_text")
    private val themeModeKey = stringPreferencesKey("theme_mode")
    private val viewModeKey = stringPreferencesKey("channel_view_mode")

    val lastCatalogState: Flow<LastCatalogState> = context.appStateDataStore.data.map { prefs ->
        val topMode = prefs[topModeKey]?.let { raw -> runCatching { TopMode.valueOf(raw) }.getOrNull() } ?: TopMode.CATALOG
        LastCatalogState(
            topMode = topMode,
            regionCode = prefs[regionCodeKey] ?: com.example.tuner.domain.PlaylistSource.Region.ALL_CODE,
            categorySlug = prefs[categorySlugKey] ?: com.example.tuner.domain.PlaylistSource.Category.ALL_SLUG,
            languageCode = prefs[languageCodeKey] ?: com.example.tuner.domain.PlaylistSource.Language.ALL_CODE
        )
    }

    val lastFilterText: Flow<String> = context.appStateDataStore.data.map { prefs ->
        prefs[filterTextKey] ?: ""
    }

    /** Dark / Light / System — defaults to System (follows the device's day/night setting). */
    val themeMode: Flow<ThemeMode> = context.appStateDataStore.data.map { prefs ->
        prefs[themeModeKey]?.let { raw -> runCatching { ThemeMode.valueOf(raw) }.getOrNull() } ?: ThemeMode.SYSTEM
    }

    suspend fun saveTopMode(mode: TopMode) {
        context.appStateDataStore.edit { prefs -> prefs[topModeKey] = mode.name }
    }

    suspend fun saveCatalogSelection(regionCode: String, categorySlug: String, languageCode: String) {
        context.appStateDataStore.edit { prefs ->
            prefs[regionCodeKey] = regionCode
            prefs[categorySlugKey] = categorySlug
            prefs[languageCodeKey] = languageCode
        }
    }

    suspend fun saveFilterText(text: String) {
        context.appStateDataStore.edit { prefs -> prefs[filterTextKey] = text }
    }

    suspend fun saveThemeMode(mode: ThemeMode) {
        context.appStateDataStore.edit { prefs -> prefs[themeModeKey] = mode.name }
    }

    /** List (rows) or Grid (icon tiles) — remembered across restarts like everything else here. */
    val channelViewMode: Flow<ChannelViewMode> = context.appStateDataStore.data.map { prefs ->
        prefs[viewModeKey]?.let { raw -> runCatching { ChannelViewMode.valueOf(raw) }.getOrNull() } ?: ChannelViewMode.LIST
    }

    suspend fun saveChannelViewMode(mode: ChannelViewMode) {
        context.appStateDataStore.edit { prefs -> prefs[viewModeKey] = mode.name }
    }
}
