package com.example.tuner.cast

import android.content.Context
import android.util.Log
import com.example.tuner.data.model.Channel
import com.google.android.gms.cast.MediaInfo
import com.google.android.gms.cast.MediaLoadRequestData
import com.google.android.gms.cast.MediaMetadata
import com.google.android.gms.cast.framework.CastContext
import com.google.android.gms.cast.framework.CastSession
import com.google.android.gms.cast.framework.SessionManagerListener
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

data class CastUiState(
    val isAvailable: Boolean = false,
    val isConnected: Boolean = false,
    val deviceName: String? = null
)

/**
 * Thin wrapper around the Cast SDK's [CastContext] — process-scoped (held by
 * [com.example.tuner.TunerApplication]) so the connection survives screen navigation.
 * [PlayerViewModel][com.example.tuner.ui.player.PlayerViewModel] observes [state] to decide
 * whether to route the current channel to a cast device instead of the local ExoPlayer.
 *
 * `CastContext.getSharedInstance` requires Google Play services and a real/emulator device
 * that supports it, and throws on some configurations — [isAvailable] reflects whether it
 * initialized, so the UI can hide the cast button entirely when it didn't.
 */
class CastSessionManager(context: Context) {

    private val castContext: CastContext? =
        runCatching { CastContext.getSharedInstance(context.applicationContext) }
            .onFailure { Log.e("CastSessionManager", "CastContext init failed", it) }
            .getOrNull()

    private val _state = MutableStateFlow(CastUiState(isAvailable = castContext != null))
    val state: StateFlow<CastUiState> = _state.asStateFlow()

    private val listener = object : SessionManagerListener<CastSession> {
        override fun onSessionStarted(session: CastSession, sessionId: String) = onConnected(session)
        override fun onSessionResumed(session: CastSession, wasSuspended: Boolean) = onConnected(session)
        override fun onSessionEnded(session: CastSession, error: Int) = onDisconnected()
        override fun onSessionSuspended(session: CastSession, reason: Int) = onDisconnected()
        override fun onSessionStarting(session: CastSession) = Unit
        override fun onSessionStartFailed(session: CastSession, error: Int) = onDisconnected()
        override fun onSessionEnding(session: CastSession) = Unit
        override fun onSessionResuming(session: CastSession, sessionId: String) = Unit
        override fun onSessionResumeFailed(session: CastSession, error: Int) = onDisconnected()

        private fun onConnected(session: CastSession) {
            _state.update { it.copy(isConnected = true, deviceName = session.castDevice?.friendlyName) }
        }

        private fun onDisconnected() {
            _state.update { it.copy(isConnected = false, deviceName = null) }
        }
    }

    init {
        castContext?.sessionManager?.addSessionManagerListener(listener, CastSession::class.java)
        castContext?.sessionManager?.currentCastSession?.let { session ->
            _state.update { it.copy(isConnected = true, deviceName = session.castDevice?.friendlyName) }
        }
    }

    /** Loads the given channel's live HLS stream onto the currently connected cast device. */
    fun loadChannel(channel: Channel) {
        val remoteMediaClient = castContext?.sessionManager?.currentCastSession?.remoteMediaClient ?: return
        val metadata = MediaMetadata(MediaMetadata.MEDIA_TYPE_TV_SHOW).apply {
            putString(MediaMetadata.KEY_TITLE, channel.name)
            putString(MediaMetadata.KEY_SUBTITLE, channel.group)
        }
        val mediaInfo = MediaInfo.Builder(channel.streamUrl)
            .setStreamType(MediaInfo.STREAM_TYPE_LIVE)
            .setContentType("application/x-mpegurl")
            .setMetadata(metadata)
            .build()
        remoteMediaClient.load(MediaLoadRequestData.Builder().setMediaInfo(mediaInfo).build())
    }

    fun stopCasting() {
        castContext?.sessionManager?.endCurrentSession(true)
    }
}
