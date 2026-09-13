package com.example.tuner.parental

import com.example.tuner.data.model.Channel
import com.example.tuner.data.repository.SingleLoadResult

/** What the per-channel shield button shows and does while a parent is unlocked. */
enum class KidsShieldState { VISIBLE_IN_KIDS, APPROVED, HIDDEN, NEUTRAL }

/**
 * Kids Mode shows the iptv-org Kids + Animation playlists, plus channels a parent approved
 * from anywhere else, minus channels a parent hid. Channels are matched by stream URL alone —
 * the same stream carries different source labels depending on which playlist it came from.
 */
object KidsVisibility {

    const val KIDS_SOURCE_LABEL = "Kids"

    fun visibleChannels(
        kidsCatalog: List<Channel>,
        approved: List<Channel>,
        hiddenUrls: Set<String>
    ): List<Channel> = (kidsCatalog + approved)
        .distinctBy { it.streamUrl }
        .filterNot { it.streamUrl in hiddenUrls }

    fun mergeKidsPlaylists(kids: SingleLoadResult, animation: SingleLoadResult): SingleLoadResult {
        val loaded = listOf(kids, animation).filterIsInstance<SingleLoadResult.Success>()
        if (loaded.isEmpty()) {
            val message = (kids as SingleLoadResult.Failure).message
            return SingleLoadResult.Failure("Kids channels: $message")
        }
        return SingleLoadResult.Success(loaded.flatMap { it.channels }.distinctBy { it.streamUrl })
    }
}

/** Parent-managed overrides. Hidden and approved are mutually exclusive per stream URL. */
data class KidsLists(val hiddenUrls: Set<String>, val approved: List<Channel>) {

    fun hide(channel: Channel): KidsLists = KidsLists(
        hiddenUrls = hiddenUrls + channel.streamUrl,
        approved = approved.filterNot { it.streamUrl == channel.streamUrl }
    )

    fun allow(channel: Channel): KidsLists = KidsLists(
        hiddenUrls = hiddenUrls - channel.streamUrl,
        approved = approved.filterNot { it.streamUrl == channel.streamUrl } + channel
    )

    fun removeApproval(channel: Channel): KidsLists =
        copy(approved = approved.filterNot { it.streamUrl == channel.streamUrl })
}
