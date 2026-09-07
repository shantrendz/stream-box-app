package com.example.tuner.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.DefaultLoadControl
import com.example.tuner.data.model.Channel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch

enum class PlaybackUiStatus { LOADING, LIVE, SIGNAL_LOST }

data class PlayerUiState(
    val channel: Channel? = null,
    val status: PlaybackUiStatus = PlaybackUiStatus.LOADING,
    val isViaProxy: Boolean = false,
    val errorMessage: String? = null
)

/**
 * Best-effort "retry via proxy" relay. This is NOT a geo-block bypass — IP-based geo
 * restriction is enforced server-side by the broadcaster and a client-side relay cannot
 * reliably defeat it. It only helps with missing-header/CORS-style network quirks.
 */
private const val CORS_RELAY_PREFIX = "https://corsproxy.io/?url="

class PlayerViewModel(application: Application) : AndroidViewModel(application) {

    // Small live-edge buffer defaults: these are live broadcasts, not VOD, so we don't
    // want to build up a large buffer that drifts the viewer away from the live edge.
    private val loadControl: LoadControl = DefaultLoadControl.Builder()
        .setBufferDurationsMs(
            /* minBufferMs = */ 8_000,
            /* maxBufferMs = */ 20_000,
            /* bufferForPlaybackMs = */ 1_000,
            /* bufferForPlaybackAfterRebufferMs = */ 2_000
        )
        .build()

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application)
        .setLoadControl(loadControl)
        .build()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private var autoRetryJob: Job? = null
    private var hasAutoRetried = false

    init {
        exoPlayer.addListener(object : Player.Listener {
            override fun onPlaybackStateChanged(playbackState: Int) {
                when (playbackState) {
                    Player.STATE_READY -> _uiState.update { it.copy(status = PlaybackUiStatus.LIVE, errorMessage = null) }
                    Player.STATE_BUFFERING -> _uiState.update { it.copy(status = PlaybackUiStatus.LOADING) }
                    else -> Unit
                }
            }

            override fun onPlayerError(error: PlaybackException) {
                handlePlaybackFailure(error.message ?: "Playback error")
            }
        })
    }

    fun play(channel: Channel) {
        autoRetryJob?.cancel()
        hasAutoRetried = false
        _uiState.value = PlayerUiState(channel = channel, status = PlaybackUiStatus.LOADING)
        startPlayback(channel.streamUrl)
    }

    private fun startPlayback(url: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun handlePlaybackFailure(message: String) {
        val channel = _uiState.value.channel ?: return

        if (!hasAutoRetried) {
            // One silent automatic retry after a short delay — many failures are slow-loading
            // manifests, not truly dead streams.
            hasAutoRetried = true
            autoRetryJob = viewModelScope.launch {
                delay(600)
                val currentUrl = if (_uiState.value.isViaProxy) proxiedUrl(channel.streamUrl) else channel.streamUrl
                startPlayback(currentUrl)
            }
        } else {
            _uiState.update { it.copy(status = PlaybackUiStatus.SIGNAL_LOST, errorMessage = message) }
        }
    }

    /** Manual "Retry" action — retries the direct URL again. */
    fun retryDirect() {
        val channel = _uiState.value.channel ?: return
        hasAutoRetried = true // manual retries don't re-trigger the silent auto-retry path
        _uiState.update { it.copy(status = PlaybackUiStatus.LOADING, isViaProxy = false, errorMessage = null) }
        startPlayback(channel.streamUrl)
    }

    /**
     * Manual "Retry via proxy" action — routes the same stream through a CORS/relay proxy.
     * Best-effort only; will not reliably defeat IP-based geo-restriction.
     */
    fun retryViaProxy() {
        val channel = _uiState.value.channel ?: return
        hasAutoRetried = true
        _uiState.update { it.copy(status = PlaybackUiStatus.LOADING, isViaProxy = true, errorMessage = null) }
        startPlayback(proxiedUrl(channel.streamUrl))
    }

    private fun proxiedUrl(originalUrl: String): String = CORS_RELAY_PREFIX + originalUrl

    fun stop() {
        exoPlayer.stop()
        exoPlayer.clearMediaItems()
        autoRetryJob?.cancel()
        _uiState.value = PlayerUiState()
    }

    override fun onCleared() {
        autoRetryJob?.cancel()
        exoPlayer.release()
        super.onCleared()
    }
}
