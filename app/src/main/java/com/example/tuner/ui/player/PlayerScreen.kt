package com.example.tuner.ui.player

import android.view.View
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.VolumeDown
import androidx.compose.material.icons.automirrored.filled.VolumeOff
import androidx.compose.material.icons.automirrored.filled.VolumeUp
import androidx.compose.material.icons.filled.AspectRatio
import androidx.compose.material.icons.filled.Cast
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.AspectRatioFrameLayout
import androidx.media3.ui.PlayerView
import com.example.tuner.cast.CastButton
import com.example.tuner.cast.CastUiState
import com.example.tuner.data.model.Channel
import com.example.tuner.ui.components.StaticOverlay
import com.example.tuner.ui.components.toInitCaps
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerBackground
import com.example.tuner.ui.theme.TunerRed
import com.example.tuner.ui.theme.TunerRedTint
import com.example.tuner.ui.theme.TunerTextPrimary
import com.example.tuner.ui.theme.TunerTextSecondary

private val ResizeModeCycle = listOf(
    AspectRatioFrameLayout.RESIZE_MODE_FIT,
    AspectRatioFrameLayout.RESIZE_MODE_ZOOM,
    AspectRatioFrameLayout.RESIZE_MODE_FILL
)

/**
 * Media3 PlayerView showing the live HLS stream, with a "now playing" header (channel name,
 * group, live/error state, favorite toggle) above the video. Play/pause, live position, and
 * Audio/Video/Speed track selection live in ExoPlayer's own on-screen controller; full-screen
 * and video-fit are our own overlay buttons in the video's top-right corner (ExoPlayer's own
 * fullscreen-button hook proved unreliable across devices, so we don't rely on it).
 *
 * [onBack] is non-null in full-screen phone mode (shows a back/collapse control); null for
 * the compact in-list tablet/landscape variant, which has no back affordance of its own.
 * Pressing it actually stops playback (not just hides the UI) — otherwise the stream would
 * keep playing audio-only with no way back to it.
 *
 * [isFullScreen] switches the video box from a fixed 16:9 box (docked above the channel
 * list) to filling all remaining space. In that mode the header becomes a translucent
 * overlay on top of the video, synced to ExoPlayer's own controller visibility — shown/
 * hidden by tapping the video, like YouTube.
 */
@Composable
fun PlayerScreen(
    channel: Channel,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isFullScreen: Boolean = false,
    onToggleFullScreen: (() -> Unit)? = null,
    isFavorite: Boolean = false,
    onToggleFavorite: (() -> Unit)? = null,
    playerViewModel: PlayerViewModel = viewModel()
) {
    val uiState by playerViewModel.uiState.collectAsState()
    var controlsVisible by remember { mutableStateOf(true) }
    var resizeModeIndex by remember { mutableIntStateOf(0) }

    LaunchedEffect(channel.streamUrl) {
        playerViewModel.play(channel)
    }

    // Leaving the player should actually stop the stream, not just hide its UI — otherwise
    // ExoPlayer keeps decoding audio in the background with no way back to the video.
    val stoppingOnBack: (() -> Unit)? = onBack?.let { original ->
        { playerViewModel.stop(); original() }
    }

    // Sized to its content (header + video) by default, rather than fillMaxSize, so callers
    // can dock it at a fixed height above other content — e.g. the channel list docked below
    // it on phones — instead of it claiming the whole screen. Full-screen mode is the
    // exception: there it fills everything and the video box takes all remaining vertical
    // space instead of a fixed aspect-ratio box.
    Column(
        modifier = modifier
            .then(if (isFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .background(TunerBackground)
    ) {
        if (!isFullScreen) {
            PlayerTopBar(
                channel = channel,
                status = uiState.status,
                onBack = stoppingOnBack,
                isFavorite = isFavorite,
                onToggleFavorite = onToggleFavorite,
                overlay = false,
                cast = uiState.cast
            )
        }

        VideoSurface(
            modifier = if (isFullScreen) Modifier.weight(1f) else Modifier,
            isFullScreen = isFullScreen,
            controlsVisible = controlsVisible,
            onControlsVisibilityChanged = { controlsVisible = it },
            onToggleFullScreen = onToggleFullScreen,
            resizeMode = ResizeModeCycle[resizeModeIndex],
            onCycleResizeMode = { resizeModeIndex = (resizeModeIndex + 1) % ResizeModeCycle.size },
            playerViewModel = playerViewModel,
            status = uiState.status,
            cast = uiState.cast,
            onStopCasting = playerViewModel::stopCasting,
            volume = uiState.volume,
            onVolumeChange = playerViewModel::setVolume,
            topBar = {
                PlayerTopBar(
                    channel = channel,
                    status = uiState.status,
                    onBack = stoppingOnBack,
                    isFavorite = isFavorite,
                    onToggleFavorite = onToggleFavorite,
                    overlay = true,
                    isFullScreen = isFullScreen,
                    onToggleFullScreen = onToggleFullScreen,
                    onCycleResizeMode = { resizeModeIndex = (resizeModeIndex + 1) % ResizeModeCycle.size },
                    cast = uiState.cast
                )
            }
        )
    }
}

/**
 * The video box itself, extracted to its own composable so the full-screen overlay top bar —
 * which uses [AnimatedVisibility] — isn't lexically nested inside the outer [Column], which
 * would otherwise make that call ambiguous with `ColumnScope.AnimatedVisibility`.
 */
@Composable
private fun VideoSurface(
    modifier: Modifier = Modifier,
    isFullScreen: Boolean,
    controlsVisible: Boolean,
    onControlsVisibilityChanged: (Boolean) -> Unit,
    onToggleFullScreen: (() -> Unit)?,
    resizeMode: Int,
    onCycleResizeMode: () -> Unit,
    playerViewModel: PlayerViewModel,
    status: PlaybackUiStatus,
    cast: CastUiState,
    onStopCasting: () -> Unit,
    volume: Float,
    onVolumeChange: (Float) -> Unit,
    topBar: @Composable () -> Unit
) {
    Box(
        modifier = modifier
            .fillMaxWidth()
            .then(if (isFullScreen) Modifier else Modifier.aspectRatio(16f / 9f))
            .background(TunerBackground)
    ) {
        AndroidView(
            factory = { context ->
                PlayerView(context).apply {
                    player = playerViewModel.exoPlayer
                    useController = true
                    setControllerVisibilityListener(
                        PlayerView.ControllerVisibilityListener { visibility ->
                            onControlsVisibilityChanged(visibility == View.VISIBLE)
                        }
                    )
                }
            },
            update = { view -> view.resizeMode = resizeMode },
            modifier = Modifier.fillMaxSize()
        )

        if (cast.isConnected) {
            CastingOverlay(deviceName = cast.deviceName, onStopCasting = onStopCasting, modifier = Modifier.fillMaxSize())
        } else when (status) {
            PlaybackUiStatus.LOADING -> {
                CircularProgressIndicator(
                    color = TunerAmber,
                    modifier = Modifier.align(Alignment.Center)
                )
            }
            PlaybackUiStatus.SIGNAL_LOST -> {
                StaticOverlay(
                    onRetry = { playerViewModel.retryDirect() },
                    onRetryViaProxy = { playerViewModel.retryViaProxy() },
                    modifier = Modifier.fillMaxSize()
                )
            }
            PlaybackUiStatus.LIVE -> Unit
        }

        if (isFullScreen) {
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopCenter).fillMaxWidth()
            ) {
                topBar()
            }
        }

        if (!isFullScreen) {
            // Docked mode has no top overlay bar inside the video box (its header sits
            // outside, above), so this corner is free for our own controls.
            AnimatedVisibility(
                visible = controlsVisible,
                enter = fadeIn(),
                exit = fadeOut(),
                modifier = Modifier.align(Alignment.TopEnd).padding(top = 8.dp, end = 8.dp)
            ) {
                DockedVideoControls(
                    isFullScreen = isFullScreen,
                    onToggleFullScreen = onToggleFullScreen,
                    onCycleResizeMode = onCycleResizeMode,
                    cast = cast
                )
            }
        }

        // Same bottom row as ExoPlayer's own time/settings controls, sitting just to their
        // left rather than in the top-right cluster with fullscreen/resize/cast.
        AnimatedVisibility(
            visible = controlsVisible,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier.align(Alignment.BottomEnd).padding(end = 64.dp, bottom = 8.dp)
        ) {
            VolumeControl(volume = volume, onVolumeChange = onVolumeChange)
        }
    }
}

/** Shown over the video area while a channel is routed to a cast device instead of playing
 * locally — the local ExoPlayer is paused, so there's nothing to show behind it. */
@Composable
private fun CastingOverlay(deviceName: String?, onStopCasting: () -> Unit, modifier: Modifier = Modifier) {
    Box(modifier = modifier.background(TunerBackground), contentAlignment = Alignment.Center) {
        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Icon(Icons.Filled.Cast, contentDescription = null, tint = TunerAmber, modifier = Modifier.size(40.dp))
            Spacer(Modifier.width(0.dp))
            Text(
                text = if (deviceName != null) "Casting to $deviceName" else "Casting",
                style = MaterialTheme.typography.titleMedium,
                color = TunerTextPrimary,
                modifier = Modifier.padding(top = 12.dp)
            )
            Button(
                onClick = onStopCasting,
                colors = ButtonDefaults.buttonColors(containerColor = TunerAmber, contentColor = TunerBackground),
                modifier = Modifier.padding(top = 16.dp)
            ) {
                Text("Stop casting")
            }
        }
    }
}

/** Volume icon that expands a slider to its left when tapped — placed near ExoPlayer's own
 * settings gear, on the same bottom row, rather than in the top-right control cluster. */
@Composable
private fun VolumeControl(volume: Float, onVolumeChange: (Float) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Row(verticalAlignment = Alignment.CenterVertically) {
        AnimatedVisibility(visible = expanded, enter = fadeIn(), exit = fadeOut()) {
            Slider(
                value = volume,
                onValueChange = onVolumeChange,
                colors = SliderDefaults.colors(thumbColor = TunerAmber, activeTrackColor = TunerAmber),
                modifier = Modifier.width(100.dp)
            )
        }
        IconButton(
            onClick = { expanded = !expanded },
            modifier = Modifier
                .clip(CircleShape)
                .background(TunerBackground.copy(alpha = 0.55f))
        ) {
            Icon(
                imageVector = when {
                    volume <= 0f -> Icons.AutoMirrored.Filled.VolumeOff
                    volume < 0.5f -> Icons.AutoMirrored.Filled.VolumeDown
                    else -> Icons.AutoMirrored.Filled.VolumeUp
                },
                contentDescription = "Volume",
                tint = TunerTextPrimary
            )
        }
    }
}

@Composable
private fun DockedVideoControls(
    isFullScreen: Boolean,
    onToggleFullScreen: (() -> Unit)?,
    onCycleResizeMode: () -> Unit,
    cast: CastUiState = CastUiState()
) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        IconButton(
            onClick = onCycleResizeMode,
            modifier = Modifier
                .clip(CircleShape)
                .background(TunerBackground.copy(alpha = 0.55f))
        ) {
            Icon(Icons.Filled.AspectRatio, contentDescription = "Change video size", tint = TunerTextPrimary)
        }
        if (cast.isAvailable) {
            Spacer(Modifier.width(8.dp))
            Box(
                modifier = Modifier
                    .size(48.dp)
                    .clip(CircleShape)
                    .background(TunerBackground.copy(alpha = 0.55f)),
                contentAlignment = Alignment.Center
            ) {
                CastButton(modifier = Modifier.size(32.dp))
            }
        }
        if (onToggleFullScreen != null) {
            Spacer(Modifier.width(8.dp))
            IconButton(
                onClick = onToggleFullScreen,
                modifier = Modifier
                    .clip(CircleShape)
                    .background(TunerBackground.copy(alpha = 0.55f))
            ) {
                Icon(
                    imageVector = if (isFullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                    contentDescription = if (isFullScreen) "Exit full screen" else "Full screen",
                    tint = TunerTextPrimary
                )
            }
        }
    }
}

/**
 * "Now playing" header: back, channel name/group, live/error badge, and favorite toggle. In
 * full-screen (overlay) mode it also carries the full-screen and video-fit controls, since
 * that's the only header shown there; in docked mode those two live as a separate overlay in
 * the video's corner instead (see [DockedVideoControls]), to avoid duplicating them.
 */
@Composable
private fun PlayerTopBar(
    channel: Channel,
    status: PlaybackUiStatus,
    onBack: (() -> Unit)?,
    isFavorite: Boolean,
    onToggleFavorite: (() -> Unit)?,
    overlay: Boolean,
    isFullScreen: Boolean = false,
    onToggleFullScreen: (() -> Unit)? = null,
    onCycleResizeMode: (() -> Unit)? = null,
    cast: CastUiState = CastUiState()
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .then(
                if (overlay) {
                    Modifier.background(
                        Brush.verticalGradient(
                            listOf(TunerBackground.copy(alpha = 0.85f), TunerBackground.copy(alpha = 0f))
                        )
                    )
                } else {
                    Modifier
                }
            )
            .padding(horizontal = 12.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (onBack != null) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to channel list", tint = TunerTextPrimary)
            }
        }

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = channel.name,
                style = MaterialTheme.typography.titleLarge,
                color = TunerTextPrimary,
                fontWeight = FontWeight.Bold,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
            Text(
                text = "${channel.group.toInitCaps()}  ·  ${channel.source}",
                style = MaterialTheme.typography.bodyMedium,
                color = TunerTextSecondary,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }

        LiveIndicator(status = status)

        if (onToggleFavorite != null) {
            IconButton(onClick = onToggleFavorite) {
                Icon(
                    imageVector = if (isFavorite) Icons.Filled.Star else Icons.Filled.StarBorder,
                    contentDescription = if (isFavorite) "Remove from favorites" else "Add to favorites",
                    tint = if (isFavorite) TunerAmber else TunerTextPrimary
                )
            }
        }

        if (overlay && onCycleResizeMode != null) {
            DockedVideoControls(
                isFullScreen = isFullScreen,
                onToggleFullScreen = onToggleFullScreen,
                onCycleResizeMode = onCycleResizeMode,
                cast = cast
            )
        }
    }
}

@Composable
private fun LiveIndicator(status: PlaybackUiStatus) {
    val (label, color, tint) = when (status) {
        PlaybackUiStatus.LIVE -> Triple("LIVE", TunerRed, TunerRedTint)
        PlaybackUiStatus.LOADING -> Triple("TUNING…", TunerTextSecondary, TunerTextSecondary.copy(alpha = 0.12f))
        PlaybackUiStatus.SIGNAL_LOST -> Triple("NO SIGNAL", MaterialTheme.colorScheme.error, MaterialTheme.colorScheme.error.copy(alpha = 0.14f))
    }
    Row(
        modifier = Modifier
            .clip(RoundedCornerShape(50))
            .background(tint)
            .padding(horizontal = 10.dp, vertical = 4.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        if (status == PlaybackUiStatus.LIVE) {
            Box(
                modifier = Modifier
                    .size(6.dp)
                    .clip(CircleShape)
                    .background(color)
            )
            Spacer(Modifier.width(5.dp))
        }
        Text(
            text = label,
            style = MaterialTheme.typography.labelLarge,
            color = color,
            fontWeight = FontWeight.Bold
        )
    }
}
