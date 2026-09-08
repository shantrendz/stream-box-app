package com.example.tuner.cast

import android.view.ContextThemeWrapper
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.mediarouter.app.MediaRouteButton
import com.google.android.gms.cast.framework.CastButtonFactory

/**
 * The standard Cast "wireless icon" button — tapping it opens the system device picker for
 * any Chromecast-type device on the network. Wraps the platform [MediaRouteButton] since the
 * Cast SDK has no Compose-native equivalent; visible only when Cast is actually available
 * (see [CastUiState.isAvailable]) so it doesn't show up as a dead button where it can't work.
 *
 * [MediaRouteButton] resolves its tint via AppCompat theme attributes (colorPrimary,
 * colorControlNormal, etc.) that this app's plain `android:Theme.Material` doesn't define —
 * without them it reads a fully transparent background and crashes computing contrast. It's
 * wrapped in a minimal AppCompat theme here rather than changing the app's main theme.
 */
@Composable
fun CastButton(modifier: Modifier = Modifier) {
    AndroidView(
        modifier = modifier,
        factory = { context ->
            val themedContext = ContextThemeWrapper(context, androidx.appcompat.R.style.Theme_AppCompat_DayNight)
            MediaRouteButton(themedContext).apply {
                CastButtonFactory.setUpMediaRouteButton(themedContext, this)
            }
        }
    )
}
