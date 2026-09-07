package com.example.tuner.data.model

/**
 * A single playable IPTV channel parsed from an M3U playlist entry (or synthesized from a
 * bare stream URL saved as a custom source).
 */
data class Channel(
    val name: String,
    val logoUrl: String?,
    val group: String,
    val streamUrl: String,
    val source: String
)
