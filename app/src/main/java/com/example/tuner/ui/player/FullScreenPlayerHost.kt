package com.example.tuner.ui.player

import android.app.Activity
import android.content.pm.ActivityInfo
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import com.example.tuner.data.model.Channel

/**
 * Hosts [PlayerScreen] in true full-screen: system bars hidden (immersive, swipe to reveal),
 * orientation unlocked to follow the sensor so landscape video gets the whole screen width.
 * Both are restored on exit via [DisposableEffect].
 */
@Composable
fun FullScreenPlayerHost(channel: Channel, onExit: () -> Unit) {
    val view = LocalView.current
    val activity = LocalContext.current as? Activity

    DisposableEffect(Unit) {
        val window = activity?.window
        val originalOrientation = activity?.requestedOrientation
        val insetsController = window?.let { WindowInsetsControllerCompat(it, view) }

        if (window != null) {
            WindowCompat.setDecorFitsSystemWindows(window, false)
        }
        insetsController?.apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        onDispose {
            insetsController?.show(WindowInsetsCompat.Type.systemBars())
            if (window != null) {
                WindowCompat.setDecorFitsSystemWindows(window, true)
            }
            activity?.requestedOrientation = originalOrientation ?: ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
    }

    PlayerScreen(
        channel = channel,
        onBack = onExit,
        isFullScreen = true,
        onToggleFullScreen = onExit,
        modifier = Modifier.fillMaxSize()
    )
}
