package com.example.tuner.ui.components

import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tuner.livecheck.LiveStatus
import com.example.tuner.ui.theme.TunerGreen
import com.example.tuner.ui.theme.TunerRed
import com.example.tuner.ui.theme.TunerTextSecondary

/** Live Check result: hollow = not checked, pulsing grey = checking, green = working, red = not working. */
@Composable
fun LiveStatusDot(status: LiveStatus, modifier: Modifier = Modifier, size: Dp = 8.dp) {
    val description = when (status) {
        LiveStatus.UNCHECKED -> "Stream not checked yet"
        LiveStatus.CHECKING -> "Checking stream"
        LiveStatus.WORKING -> "Stream working"
        LiveStatus.NOT_WORKING -> "Stream not working"
    }
    val base = modifier
        .size(size)
        .clip(CircleShape)
        .semantics { contentDescription = description }

    when (status) {
        LiveStatus.UNCHECKED -> Box(base.border(1.5.dp, TunerTextSecondary, CircleShape))
        LiveStatus.CHECKING -> {
            val transition = rememberInfiniteTransition(label = "liveDot")
            val alpha by transition.animateFloat(
                initialValue = 0.3f,
                targetValue = 1f,
                animationSpec = infiniteRepeatable(tween(700), RepeatMode.Reverse),
                label = "liveDotAlpha"
            )
            Box(base.alpha(alpha).background(TunerTextSecondary))
        }
        LiveStatus.WORKING -> Box(base.background(TunerGreen))
        LiveStatus.NOT_WORKING -> Box(base.background(TunerRed))
    }
}
