package com.example.tuner.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Fullscreen
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.viewmodel.compose.viewModel
import androidx.media3.ui.PlayerView
import com.example.tuner.data.model.Channel
import com.example.tuner.ui.components.StaticOverlay
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerBackground
import com.example.tuner.ui.theme.TunerCyan
import com.example.tuner.ui.theme.TunerTextPrimary
import com.example.tuner.ui.theme.TunerTextSecondary

/**
 * Media3 PlayerView showing the live HLS stream, with a "now playing" header (channel name,
 * group, live/error state) and an in-frame "signal lost" static overlay on repeated failure.
 *
 * [onBack] is non-null in full-screen phone mode (shows a back/collapse control); null for
 * the compact in-list tablet/landscape variant, which has no back affordance of its own.
 *
 * [isFullScreen] switches the video box from a fixed 16:9 box (docked above the channel
 * list) to filling all remaining space — used by the true full-screen player mode, toggled
 * via [onToggleFullScreen] (omit to hide the expand/collapse control entirely).
 */
@Composable
fun PlayerScreen(
    channel: Channel,
    onBack: (() -> Unit)?,
    modifier: Modifier = Modifier,
    isFullScreen: Boolean = false,
    onToggleFullScreen: (() -> Unit)? = null,
    playerViewModel: PlayerViewModel = viewModel()
) {
    val uiState by playerViewModel.uiState.collectAsState()

    LaunchedEffect(channel.streamUrl) {
        playerViewModel.play(channel)
    }

    // Sized to its content (header row + video box) by default, rather than fillMaxSize, so
    // callers can dock it at a fixed height above other content — e.g. the channel list
    // docked below it on phones — instead of it claiming the whole screen. Full-screen mode
    // is the exception: there it fills everything and the video box takes all remaining
    // vertical space instead of a fixed aspect-ratio box.
    Column(
        modifier = modifier
            .then(if (isFullScreen) Modifier.fillMaxSize() else Modifier.fillMaxWidth())
            .background(TunerBackground)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
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
                    text = "${channel.group}  ·  ${channel.source}",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TunerTextSecondary,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }

            LiveIndicator(status = uiState.status)

            if (onToggleFullScreen != null) {
                IconButton(onClick = onToggleFullScreen) {
                    Icon(
                        imageVector = if (isFullScreen) Icons.Filled.FullscreenExit else Icons.Filled.Fullscreen,
                        contentDescription = if (isFullScreen) "Exit full screen" else "Full screen",
                        tint = TunerTextPrimary
                    )
                }
            }
        }

        Box(
            modifier = Modifier
                .fillMaxWidth()
                .then(if (isFullScreen) Modifier.weight(1f) else Modifier.aspectRatio(16f / 9f))
                .background(TunerBackground)
        ) {
            AndroidView(
                factory = { context ->
                    PlayerView(context).apply {
                        player = playerViewModel.exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )

            when (uiState.status) {
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
        }
    }
}

@Composable
private fun LiveIndicator(status: PlaybackUiStatus) {
    val (label, color) = when (status) {
        PlaybackUiStatus.LIVE -> "● LIVE" to TunerCyan
        PlaybackUiStatus.LOADING -> "TUNING…" to TunerTextSecondary
        PlaybackUiStatus.SIGNAL_LOST -> "NO SIGNAL" to MaterialTheme.colorScheme.error
    }
    Text(
        text = label,
        style = MaterialTheme.typography.labelLarge,
        color = color,
        fontWeight = FontWeight.Bold
    )
}
