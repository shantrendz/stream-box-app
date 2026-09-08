package com.example.tuner.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.StartOffset
import androidx.compose.animation.core.StartOffsetType
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tuner.R
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerBackground
import com.example.tuner.ui.theme.TunerCyan
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val TV_POWER_ON_MS = 500
private const val CHANNEL_SURF_MS = 700
private const val SETTLE_MS = 350
private const val FADE_OUT_MS = 350
private const val RIPPLE_PERIOD_MS = 2200

/**
 * Branded launch animation: rings of light ripple outward from center while a "screen"
 * panel powers on, flickers through channel numbers like someone surfing with the remote,
 * settles on the StreamBox mark, then crossfades into the real app content.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val panelScale = remember { Animatable(0.6f) }
    var screenGlow by remember { mutableFloatStateOf(0f) }
    var channelNumber by remember { mutableIntStateOf(0) }
    var showMark by remember { mutableStateOf(false) }
    var contentAlpha by remember { mutableFloatStateOf(1f) }

    val rippleTransition = rememberInfiniteTransition(label = "ripple")
    val rippleProgress1 by rippleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(RIPPLE_PERIOD_MS, easing = LinearEasing)),
        label = "ripple1"
    )
    val rippleProgress2 by rippleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(RIPPLE_PERIOD_MS, easing = LinearEasing),
            initialStartOffset = StartOffset(RIPPLE_PERIOD_MS / 3, StartOffsetType.FastForward)
        ),
        label = "ripple2"
    )
    val rippleProgress3 by rippleTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            tween(RIPPLE_PERIOD_MS, easing = LinearEasing),
            initialStartOffset = StartOffset(RIPPLE_PERIOD_MS * 2 / 3, StartOffsetType.FastForward)
        ),
        label = "ripple3"
    )

    val scanlineTransition = rememberInfiniteTransition(label = "scanline")
    val scanlineOffset by scanlineTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing), repeatMode = RepeatMode.Restart),
        label = "scanlineOffset"
    )

    LaunchedEffect(Unit) {
        // Power on: panel scales in with a slight overshoot, screen glow fades up.
        panelScale.animateTo(1f, animationSpec = tween(TV_POWER_ON_MS, easing = EaseOutBack))
        val glowStart = System.currentTimeMillis()
        while (System.currentTimeMillis() - glowStart < 250) {
            screenGlow = ((System.currentTimeMillis() - glowStart) / 250f).coerceIn(0f, 1f)
            delay(16)
        }
        screenGlow = 1f

        // Channel surfing: rapid random channel numbers, like flipping with a remote.
        val surfStart = System.currentTimeMillis()
        while (System.currentTimeMillis() - surfStart < CHANNEL_SURF_MS) {
            channelNumber = Random.nextInt(1, 999)
            delay(70)
        }

        // Settle on the brand mark.
        showMark = true
        delay(SETTLE_MS.toLong())

        // Crossfade into the real app.
        val fadeStart = System.currentTimeMillis()
        while (System.currentTimeMillis() - fadeStart < FADE_OUT_MS) {
            contentAlpha = 1f - ((System.currentTimeMillis() - fadeStart) / FADE_OUT_MS.toFloat()).coerceIn(0f, 1f)
            delay(16)
        }
        onFinished()
    }

    // Resolved once here — Canvas' draw lambda isn't a @Composable context, so these
    // composable-property color accessors can't be called directly inside it.
    val amber = TunerAmber
    val cyan = TunerCyan
    val background = TunerBackground

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(background),
        contentAlignment = Alignment.Center
    ) {
        // Rings of light rippling outward from center — the "eye-catching" ambient motion
        // behind the mark, independent of the power-on/settle sequence above.
        Canvas(modifier = Modifier.fillMaxSize().alpha(contentAlpha)) {
            val center = Offset(this.size.width / 2f, this.size.height / 2f)
            val maxRadius = this.size.minDimension * 0.42f
            listOf(rippleProgress1 to amber, rippleProgress2 to cyan, rippleProgress3 to amber).forEach { (progress, color) ->
                drawCircle(
                    color = color.copy(alpha = (1f - progress) * 0.35f),
                    radius = 24.dp.toPx() + maxRadius * progress,
                    center = center,
                    style = Stroke(width = 2.dp.toPx())
                )
            }
        }

        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(contentAlpha)
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(panelScale.value),
                contentAlignment = Alignment.Center
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val screenRect = Rect(Offset.Zero, this.size)
                    val corner = CornerRadius(screenRect.width * 0.16f)

                    // Screen glow fill.
                    drawRoundRect(
                        color = amber.copy(alpha = 0.16f * screenGlow),
                        topLeft = screenRect.topLeft,
                        size = screenRect.size,
                        cornerRadius = corner
                    )
                    // Panel border.
                    drawRoundRect(
                        color = amber.copy(alpha = 0.7f + 0.3f * screenGlow),
                        topLeft = screenRect.topLeft,
                        size = screenRect.size,
                        cornerRadius = corner,
                        style = Stroke(width = size.minDimension * 0.02f)
                    )

                    // Scanline sweep across the screen while it's "tuning".
                    if (screenGlow > 0f && !showMark) {
                        val scanY = screenRect.top + screenRect.height * scanlineOffset
                        clipPath(Path().apply { addRoundRect(RoundRect(screenRect, corner)) }) {
                            drawLine(
                                color = cyan.copy(alpha = 0.5f),
                                start = Offset(screenRect.left, scanY),
                                end = Offset(screenRect.right, scanY),
                                strokeWidth = size.minDimension * 0.01f
                            )
                        }
                    }
                }

                if (showMark) {
                    Image(
                        painter = painterResource(R.drawable.ic_app_mark),
                        contentDescription = null,
                        modifier = Modifier
                            .size(120.dp)
                            .clip(RoundedCornerShape(20.dp))
                    )
                } else if (screenGlow > 0.5f) {
                    Text(
                        text = channelNumber.toString().padStart(3, '0'),
                        style = MaterialTheme.typography.headlineMedium,
                        color = TunerCyan,
                        fontWeight = FontWeight.Bold
                    )
                }
            }

            Text(
                text = "STREAMBOX",
                style = MaterialTheme.typography.titleLarge,
                color = TunerAmber,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(top = 20.dp)
            )
        }
    }
}
