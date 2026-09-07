package com.example.tuner.data.repository

import android.content.Context
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.tuner.data.model.Channel
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

private val Context.historyDataStore by preferencesDataStore(name = "watch_history")

private const val MAX_HISTORY_ENTRIES = 200

@Serializable
private data class HistoryEntryDto(
    val name: String,
    val logoUrl: String?,
    val group: String,
    val streamUrl: String,
    val source: String,
    val playedAt: Long
)

/**
 * Persists recently-played channels on-device, most-recent first, capped at
 * [MAX_HISTORY_ENTRIES]. Recording is gated by [historyEnabled] — a user-facing toggle,
 * separate from [clearHistory] which wipes whatever has already been saved.
 */
class HistoryRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val historyKey = stringPreferencesKey("history_json")
    private val historyEnabledKey = booleanPreferencesKey("history_enabled")

    val history: Flow<List<Channel>> = context.historyDataStore.data.map { prefs ->
        val raw = prefs[historyKey] ?: return@map emptyList()
        runCatching {
            json.decodeFromString<List<HistoryEntryDto>>(raw)
                .sortedByDescending { it.playedAt }
                .map { Channel(it.name, it.logoUrl, it.group, it.streamUrl, it.source) }
        }.getOrDefault(emptyList())
    }

    /** Defaults to enabled — the user opts out, not in. */
    val historyEnabled: Flow<Boolean> = context.historyDataStore.data.map { prefs ->
        prefs[historyEnabledKey] ?: true
    }

    suspend fun setHistoryEnabled(enabled: Boolean) {
        context.historyDataStore.edit { prefs -> prefs[historyEnabledKey] = enabled }
    }

    suspend fun recordPlay(channel: Channel) {
        if (!historyEnabled.first()) return

        val current = currentDtos().filterNot { it.source == channel.source && it.streamUrl == channel.streamUrl }
        val updated = (listOf(
            HistoryEntryDto(
                name = channel.name,
                logoUrl = channel.logoUrl,
                group = channel.group,
                streamUrl = channel.streamUrl,
                source = channel.source,
                playedAt = System.currentTimeMillis()
            )
        ) + current).take(MAX_HISTORY_ENTRIES)

        save(updated)
    }

    suspend fun clearHistory() {
        save(emptyList())
    }

    private suspend fun currentDtos(): List<HistoryEntryDto> {
        val raw = context.historyDataStore.data.first()[historyKey] ?: return emptyList()
        return runCatching { json.decodeFromString<List<HistoryEntryDto>>(raw) }.getOrDefault(emptyList())
    }

    private suspend fun save(dtos: List<HistoryEntryDto>) {
        context.historyDataStore.edit { prefs -> prefs[historyKey] = json.encodeToString(dtos) }
    }
}
