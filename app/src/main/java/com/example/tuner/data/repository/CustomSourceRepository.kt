package com.example.tuner.data.repository

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.example.tuner.data.model.CustomSource
import com.example.tuner.data.model.SourceLoadStatus
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import java.net.URI
import java.util.UUID

private val Context.customSourcesDataStore by preferencesDataStore(name = "custom_sources")

@Serializable
private data class CustomSourceDto(
    val id: String,
    val label: String,
    val url: String,
    val dateAdded: Long,
    val lastLoadStatus: String = "NEVER_LOADED",
    val lastError: String? = null,
    val channelCount: Int = 0
)

private fun CustomSource.toDto() = CustomSourceDto(
    id = id,
    label = label,
    url = url,
    dateAdded = dateAdded,
    lastLoadStatus = lastLoadStatus.name,
    lastError = lastError,
    channelCount = channelCount
)

private fun CustomSourceDto.toModel() = CustomSource(
    id = id,
    label = label,
    url = url,
    dateAdded = dateAdded,
    lastLoadStatus = runCatching { SourceLoadStatus.valueOf(lastLoadStatus) }.getOrDefault(SourceLoadStatus.NEVER_LOADED),
    lastError = lastError,
    channelCount = channelCount
)

/**
 * CRUD + persistence for the user's saved custom-source library. Persists the *full* list
 * (label + url + date added, ordered by date added) in DataStore — not just a recency history.
 */
class CustomSourceRepository(private val context: Context) {

    private val json = Json { ignoreUnknownKeys = true }
    private val sourcesKey = stringPreferencesKey("sources_json")

    val sources: Flow<List<CustomSource>> = context.customSourcesDataStore.data.map { prefs ->
        val raw = prefs[sourcesKey] ?: return@map emptyList()
        runCatching {
            json.decodeFromString<List<CustomSourceDto>>(raw).map { it.toModel() }
        }.getOrDefault(emptyList())
    }

    suspend fun addSource(label: String, url: String) {
        val displayLabel = label.ifBlank { hostnameOf(url) }
        val newSource = CustomSource(
            id = UUID.randomUUID().toString(),
            label = displayLabel,
            url = url,
            dateAdded = System.currentTimeMillis()
        )
        mutate { it + newSource }
    }

    suspend fun updateSource(id: String, label: String, url: String) {
        mutate { list ->
            list.map { source ->
                if (source.id == id) {
                    source.copy(
                        label = label.ifBlank { hostnameOf(url) },
                        url = url,
                        lastLoadStatus = SourceLoadStatus.NEVER_LOADED,
                        lastError = null
                    )
                } else source
            }
        }
    }

    suspend fun removeSource(id: String) {
        mutate { list -> list.filterNot { it.id == id } }
    }

    suspend fun updateStatus(id: String, status: SourceLoadStatus, error: String?, channelCount: Int) {
        mutate { list ->
            list.map { source ->
                if (source.id == id) {
                    source.copy(lastLoadStatus = status, lastError = error, channelCount = channelCount)
                } else source
            }
        }
    }

    suspend fun currentSources(): List<CustomSource> = sources.first()

    fun hostnameOf(url: String): String =
        runCatching { URI(url).host ?: url }.getOrDefault(url)

    private suspend fun mutate(transform: (List<CustomSource>) -> List<CustomSource>) {
        val current = currentSources()
        val updated = transform(current)
        context.customSourcesDataStore.edit { prefs ->
            prefs[sourcesKey] = json.encodeToString(updated.map { it.toDto() })
        }
    }
}
