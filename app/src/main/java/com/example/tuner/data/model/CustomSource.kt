package com.example.tuner.data.model

enum class SourceLoadStatus {
    NEVER_LOADED,
    LOADING,
    OK,
    FAILED
}

/**
 * A user-saved custom playlist/stream URL. Persisted in DataStore as the full library
 * (not just recents) so every entry survives app restarts.
 */
data class CustomSource(
    val id: String,
    val label: String,
    val url: String,
    val dateAdded: Long,
    val lastLoadStatus: SourceLoadStatus = SourceLoadStatus.NEVER_LOADED,
    val lastError: String? = null,
    val channelCount: Int = 0
)
