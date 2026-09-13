package com.example.tuner.livecheck

enum class LiveStatus { UNCHECKED, CHECKING, WORKING, NOT_WORKING }

/** Checks whether a stream URL is currently playable. Never throws — failures are NOT_WORKING. */
interface StreamProbe {
    suspend fun probe(url: String): LiveStatus
}
