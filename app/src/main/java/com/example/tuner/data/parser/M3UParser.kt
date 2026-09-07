package com.example.tuner.data.parser

import com.example.tuner.data.model.Channel

/**
 * Parses raw M3U/M3U8 playlist text into [Channel]s, or — if the content has no `#EXTINF`
 * entries at all — treats it as a single bare stream URL and synthesizes one channel named
 * after [sourceLabel].
 *
 * Deliberately dependency-free: a small line-by-line state machine, easy to unit test.
 */
object M3UParser {

    private val ATTR_REGEX = Regex("""([a-zA-Z0-9_-]+)="([^"]*)"""")

    fun parse(raw: String, sourceLabel: String): List<Channel> {
        val lines = raw.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .toList()

        if (lines.none { it.startsWith("#EXTINF", ignoreCase = true) }) {
            // No metadata at all — could be a bare single stream URL, or genuinely empty/junk.
            val bareUrl = lines.firstOrNull { looksLikeUrl(it) }
            return if (bareUrl != null) {
                listOf(
                    Channel(
                        name = sourceLabel,
                        logoUrl = null,
                        group = "Uncategorized",
                        streamUrl = bareUrl,
                        source = sourceLabel
                    )
                )
            } else {
                emptyList()
            }
        }

        val channels = mutableListOf<Channel>()
        var pendingName: String? = null
        var pendingLogo: String? = null
        var pendingGroup: String? = null

        for (line in lines) {
            when {
                line.startsWith("#EXTM3U", ignoreCase = true) -> continue

                line.startsWith("#EXTINF", ignoreCase = true) -> {
                    val (attrs, displayName) = parseExtInf(line)
                    pendingName = displayName.ifBlank { attrs["tvg-name"] ?: "Unnamed channel" }
                    pendingLogo = attrs["tvg-logo"]
                    pendingGroup = attrs["group-title"]?.ifBlank { null } ?: "Uncategorized"
                }

                line.startsWith("#") -> {
                    // Other tag (#EXTGRP, #EXTVLCOPT, ...) — ignored, not a stream URL.
                    continue
                }

                looksLikeUrl(line) -> {
                    if (pendingName != null) {
                        channels += Channel(
                            name = pendingName,
                            logoUrl = pendingLogo,
                            group = pendingGroup ?: "Uncategorized",
                            streamUrl = line,
                            source = sourceLabel
                        )
                    } else {
                        // A stream URL with no preceding #EXTINF — still surface it.
                        channels += Channel(
                            name = sourceLabel,
                            logoUrl = null,
                            group = "Uncategorized",
                            streamUrl = line,
                            source = sourceLabel
                        )
                    }
                    pendingName = null
                    pendingLogo = null
                    pendingGroup = null
                }
            }
        }

        return channels
    }

    private fun looksLikeUrl(s: String): Boolean =
        s.startsWith("http://", ignoreCase = true) || s.startsWith("https://", ignoreCase = true)

    /** Returns (attributes map, display name after the last comma). */
    private fun parseExtInf(line: String): Pair<Map<String, String>, String> {
        // Format: #EXTINF:-1 tvg-id="..." tvg-logo="..." group-title="...",Display Name
        val commaIndex = line.lastIndexOf(',')
        val attrPart = if (commaIndex >= 0) line.substring(0, commaIndex) else line
        val displayName = if (commaIndex >= 0) line.substring(commaIndex + 1).trim() else ""

        val attrs = mutableMapOf<String, String>()
        for (match in ATTR_REGEX.findAll(attrPart)) {
            attrs[match.groupValues[1].lowercase()] = match.groupValues[2]
        }
        return attrs to displayName
    }
}
