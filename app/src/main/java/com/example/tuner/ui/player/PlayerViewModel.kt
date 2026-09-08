package com.example.tuner.ui.player

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import androidx.media3.common.MediaItem
import androidx.media3.common.PlaybackException
import androidx.media3.common.Player
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.LoadControl
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import com.example.tuner.TunerApplication
import com.example.tuner.cast.CastUiState
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
    val errorMessage: String? = null,
    val cast: CastUiState = CastUiState(),
    val volume: Float = 1f
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

    // Many free IPTV sources are only ever tested against VLC and reject unrecognized
    // clients (or ExoPlayer's default "AndroidXMedia3/…" UA) outright — impersonating VLC's
    // user agent here is what actually gets those streams past that gatekeeping. Also raise
    // the connect/read timeouts a little since some of these origins are slow to respond.
    private val httpDataSourceFactory = DefaultHttpDataSource.Factory()
        .setUserAgent("VLC/3.0.20 LibVLC/3.0.20")
        .setConnectTimeoutMs(15_000)
        .setReadTimeoutMs(15_000)
        .setAllowCrossProtocolRedirects(true)

    val exoPlayer: ExoPlayer = ExoPlayer.Builder(application)
        .setLoadControl(loadControl)
        .setMediaSourceFactory(DefaultMediaSourceFactory(application).setDataSourceFactory(httpDataSourceFactory))
        .build()

    private val _uiState = MutableStateFlow(PlayerUiState())
    val uiState: StateFlow<PlayerUiState> = _uiState.asStateFlow()

    private val castSessionManager = getApplication<TunerApplication>().castSessionManager

    private var autoRetryJob: Job? = null

    // 0 = fresh channel, nothing tried yet; 1 = retried the same URL once (handles a slow or
    // transiently-broken manifest fetch); 2 = also tried the opposite http/https scheme
    // (some origins are only reachable one way, e.g. an HTTP source behind an HTTPS-only
    // CDN edge, or vice versa); anything further and it's a genuinely dead stream.
    private var autoRetryStage = 0

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

        // Route to the cast device instead of local playback whenever one is connected —
        // reacts both to a session starting/ending mid-playback and to picking a new channel
        // while already connected (the latter is also handled directly in [play]).
        viewModelScope.launch {
            castSessionManager.state.collect { castState ->
                _uiState.update { it.copy(cast = castState) }
                val channel = _uiState.value.channel ?: return@collect
                if (castState.isConnected) {
                    exoPlayer.pause()
                    castSessionManager.loadChannel(channel)
                } else {
                    exoPlayer.play()
                }
            }
        }
    }

    fun play(channel: Channel) {
        autoRetryJob?.cancel()
        autoRetryStage = 0
        val castState = _uiState.value.cast
        val volume = _uiState.value.volume
        _uiState.value = PlayerUiState(channel = channel, status = PlaybackUiStatus.LOADING, cast = castState, volume = volume)
        startPlayback(channel.streamUrl)
        if (castState.isConnected) {
            exoPlayer.pause()
            castSessionManager.loadChannel(channel)
        }
    }

    /** Disconnects from the cast device, resuming local playback of the current channel. */
    fun stopCasting() = castSessionManager.stopCasting()

    private var volumeBeforeMute = 1f

    /** Sets local playback volume (0f–1f); has no effect on a cast device's own volume. */
    fun setVolume(volume: Float) {
        val clamped = volume.coerceIn(0f, 1f)
        exoPlayer.volume = clamped
        _uiState.update { it.copy(volume = clamped) }
        if (clamped > 0f) volumeBeforeMute = clamped
    }

    fun toggleMute() {
        if (_uiState.value.volume > 0f) setVolume(0f) else setVolume(volumeBeforeMute)
    }

    private fun startPlayback(url: String) {
        exoPlayer.setMediaItem(MediaItem.fromUri(url))
        exoPlayer.prepare()
        exoPlayer.playWhenReady = true
    }

    private fun handlePlaybackFailure(message: String) {
        val channel = _uiState.value.channel ?: return
        val isViaProxy = _uiState.value.isViaProxy
        val swapped = swapScheme(channel.streamUrl)

        when {
            autoRetryStage == 0 -> {
                // One silent automatic retry after a short delay — many failures are slow-
                // loading manifests, not truly dead streams.
                autoRetryStage = 1
                autoRetryJob = viewModelScope.launch {
                    delay(600)
                    startPlayback(if (isViaProxy) proxiedUrl(channel.streamUrl) else channel.streamUrl)
                }
            }
            autoRetryStage == 1 && !isViaProxy && swapped != null -> {
                autoRetryStage = 2
                autoRetryJob = viewModelScope.launch {
                    delay(300)
                    startPlayback(swapped)
                }
            }
            else -> {
                _uiState.update { it.copy(status = PlaybackUiStatus.SIGNAL_LOST, errorMessage = message) }
            }
        }
    }

    /** Manual "Retry" action — retries the direct URL again. */
    fun retryDirect() {
        val channel = _uiState.value.channel ?: return
        autoRetryStage = Int.MAX_VALUE // manual retries don't re-trigger the silent auto-retry chain
        _uiState.update { it.copy(status = PlaybackUiStatus.LOADING, isViaProxy = false, errorMessage = null) }
        startPlayback(channel.streamUrl)
    }

    /**
     * Manual "Retry via proxy" action — routes the same stream through a CORS/relay proxy.
     * Best-effort only; will not reliably defeat IP-based geo-restriction.
     */
    fun retryViaProxy() {
        val channel = _uiState.value.channel ?: return
        autoRetryStage = Int.MAX_VALUE
        _uiState.update { it.copy(status = PlaybackUiStatus.LOADING, isViaProxy = true, errorMessage = null) }
        startPlayback(proxiedUrl(channel.streamUrl))
    }

    private fun proxiedUrl(originalUrl: String): String = CORS_RELAY_PREFIX + originalUrl

    /** Swaps http(s) scheme — some origins are only reachable via the "other" one (e.g. an
     * HTTP-only source behind an HTTPS CDN edge, or vice versa). Null if not http(s) at all. */
    private fun swapScheme(url: String): String? = when {
        url.startsWith("https://", ignoreCase = true) -> "http://" + url.removePrefix("https://")
        url.startsWith("http://", ignoreCase = true) -> "https://" + url.removePrefix("http://")
        else -> null
    }

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
