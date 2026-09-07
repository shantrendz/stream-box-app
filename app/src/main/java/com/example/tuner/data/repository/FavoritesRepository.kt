package com.example.tuner.data.repository

import android.content.Context
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

private val Context.favoritesDataStore by preferencesDataStore(name = "favorites")

@Serializable
private data class FavoriteChannelDto(
    val name: String,
    val logoUrl: String?,
    val group: String,
    val streamUrl: String,
    val source: String,
    val dateAdded: Long
)

private fun favoriteKey(source: String, streamUrl: String) = "$source|$streamUrl"

/** Stable identity for favoriting/history: (source, streamUrl) — Channel itself has no id. */
fun Channel.favoriteKey(): String = favoriteKey(source, streamUrl)

/**
 * Persists the user's starred channels on-device (full channel snapshots, not just keys) so
 * the Favorites view works standalone without re-fetching any playlist.
 */
class FavoritesRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val favoritesKey = stringPreferencesKey("favorites_json")

    /** Ordered newest-first list of favorited channels. */
    val favorites: Flow<List<Channel>> = context.favoritesDataStore.data.map { prefs ->
        val raw = prefs[favoritesKey] ?: return@map emptyList()
        runCatching {
            json.decodeFromString<List<FavoriteChannelDto>>(raw)
                .sortedByDescending { it.dateAdded }
                .map { Channel(it.name, it.logoUrl, it.group, it.streamUrl, it.source) }
        }.getOrDefault(emptyList())
    }

    /** Just the keys, for cheap "is this channel favorited" lookups in the channel list UI. */
    val favoriteKeys: Flow<Set<String>> = context.favoritesDataStore.data.map { prefs ->
        val raw = prefs[favoritesKey] ?: return@map emptySet()
        runCatching {
            json.decodeFromString<List<FavoriteChannelDto>>(raw)
                .map { favoriteKey(it.source, it.streamUrl) }
                .toSet()
        }.getOrDefault(emptySet())
    }

    suspend fun toggleFavorite(channel: Channel) {
        val current = currentDtos()
        val key = channel.favoriteKey()
        val updated = if (current.any { favoriteKey(it.source, it.streamUrl) == key }) {
            current.filterNot { favoriteKey(it.source, it.streamUrl) == key }
        } else {
            current + FavoriteChannelDto(
                name = channel.name,
                logoUrl = channel.logoUrl,
                group = channel.group,
                streamUrl = channel.streamUrl,
                source = channel.source,
                dateAdded = System.currentTimeMillis()
            )
        }
        save(updated)
    }

    private suspend fun currentDtos(): List<FavoriteChannelDto> {
        val raw = context.favoritesDataStore.data.first()[favoritesKey] ?: return emptyList()
        return runCatching { json.decodeFromString<List<FavoriteChannelDto>>(raw) }.getOrDefault(emptyList())
    }

    private suspend fun save(dtos: List<FavoriteChannelDto>) {
        context.favoritesDataStore.edit { prefs -> prefs[favoritesKey] = json.encodeToString(dtos) }
    }
}
