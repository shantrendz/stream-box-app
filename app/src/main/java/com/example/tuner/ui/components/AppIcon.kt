package com.example.tuner.ui.components

import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tuner.R

/**
 * The app's mark drawn in-app (title bar, About section) — the same play-in-a-TV-frame
 * artwork as the launcher icon (`drawable/ic_launcher_foreground.png`), reused inline so
 * both places always match.
 */
@Composable
fun AppIcon(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    Image(
        painter = painterResource(R.drawable.ic_app_mark),
        contentDescription = null,
        modifier = modifier.size(size)
    )
}
