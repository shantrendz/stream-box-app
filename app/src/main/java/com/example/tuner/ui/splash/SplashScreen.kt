package com.example.tuner.ui.splash

import androidx.compose.animation.core.Animatable
import androidx.compose.animation.core.EaseOutBack
import androidx.compose.animation.core.LinearEasing
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
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
import androidx.compose.ui.draw.scale
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.RoundRect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerBackground
import com.example.tuner.ui.theme.TunerCyan
import kotlinx.coroutines.delay
import kotlin.random.Random

private const val TV_POWER_ON_MS = 500
private const val CHANNEL_SURF_MS = 700
private const val SETTLE_MS = 300
private const val FADE_OUT_MS = 350

/**
 * Branded launch animation: a retro CRT TV "powers on" (scale + amber outline draw-in),
 * flickers through channel numbers like someone surfing with the remote, settles on the
 * StreamBox play-triangle, then crossfades into the real app content.
 */
@Composable
fun SplashScreen(onFinished: () -> Unit) {
    val tvScale = remember { Animatable(0.6f) }
    var screenGlow by remember { mutableFloatStateOf(0f) }
    var channelNumber by remember { mutableIntStateOf(0) }
    var showTriangle by remember { mutableStateOf(false) }
    var contentAlpha by remember { mutableFloatStateOf(1f) }

    val scanlineTransition = rememberInfiniteTransition(label = "scanline")
    val scanlineOffset by scanlineTransition.animateFloat(
        initialValue = 0f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(tween(1400, easing = LinearEasing)),
        label = "scanlineOffset"
    )

    LaunchedEffect(Unit) {
        // Power on: TV scales in with a slight overshoot, screen glow fades up.
        tvScale.animateTo(1f, animationSpec = tween(TV_POWER_ON_MS, easing = EaseOutBack))
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
        showTriangle = true
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
            .background(background)
            .padding(48.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.alpha(contentAlpha)
        ) {
            Box(
                modifier = Modifier
                    .size(180.dp)
                    .scale(tvScale.value)
            ) {
                Canvas(modifier = Modifier.fillMaxSize()) {
                    val strokeWidth = size.minDimension * 0.045f
                    val bodyInset = size.minDimension * 0.06f
                    val bodyRect = Rect(
                        Offset(bodyInset, bodyInset * 1.1f),
                        Size(size.width - bodyInset * 2, size.height - bodyInset * 2.6f)
                    )
                    val corner = CornerRadius(bodyRect.width * 0.14f)

                    // Screen glow fill, inset from the body.
                    val screenInset = strokeWidth * 1.6f
                    val screenRect = Rect(
                        bodyRect.left + screenInset,
                        bodyRect.top + screenInset,
                        bodyRect.right - screenInset,
                        bodyRect.bottom - screenInset
                    )
                    drawRoundRect(
                        color = amber.copy(alpha = 0.22f * screenGlow),
                        topLeft = screenRect.topLeft,
                        size = screenRect.size,
                        cornerRadius = CornerRadius(screenRect.width * 0.1f)
                    )

                    // Scanline sweep across the screen while it's "tuning".
                    if (screenGlow > 0f && !showTriangle) {
                        val scanY = screenRect.top + screenRect.height * scanlineOffset
                        clipPath(Path().apply { addRoundRect(RoundRect(screenRect, CornerRadius(screenRect.width * 0.1f))) }) {
                            drawLine(
                                color = cyan.copy(alpha = 0.5f),
                                start = Offset(screenRect.left, scanY),
                                end = Offset(screenRect.right, scanY),
                                strokeWidth = strokeWidth * 0.5f
                            )
                        }
                    }

                    // TV body outline.
                    drawRoundRect(
                        color = amber,
                        topLeft = bodyRect.topLeft,
                        size = bodyRect.size,
                        cornerRadius = corner,
                        style = Stroke(width = strokeWidth, join = StrokeJoin.Round)
                    )

                    // Legs.
                    val legY0 = bodyRect.bottom
                    val legY1 = bodyRect.bottom + bodyInset * 1.6f
                    drawLine(
                        color = amber,
                        start = Offset(bodyRect.left + bodyRect.width * 0.18f, legY0),
                        end = Offset(bodyRect.left + bodyRect.width * 0.06f, legY1),
                        strokeWidth = strokeWidth * 0.85f,
                        cap = StrokeCap.Round
                    )
                    drawLine(
                        color = amber,
                        start = Offset(bodyRect.right - bodyRect.width * 0.18f, legY0),
                        end = Offset(bodyRect.right - bodyRect.width * 0.06f, legY1),
                        strokeWidth = strokeWidth * 0.85f,
                        cap = StrokeCap.Round
                    )

                    // Cyan play-triangle once the channel has "settled".
                    if (showTriangle) {
                        val cx = screenRect.center.x
                        val cy = screenRect.center.y
                        val triSize = screenRect.height * 0.34f
                        val path = Path().apply {
                            moveTo(cx - triSize * 0.6f, cy - triSize)
                            lineTo(cx - triSize * 0.6f, cy + triSize)
                            lineTo(cx + triSize * 0.9f, cy)
                            close()
                        }
                        drawPath(path, color = cyan)
                    }
                }

                if (!showTriangle && screenGlow > 0.5f) {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        Text(
                            text = channelNumber.toString().padStart(3, '0'),
                            style = MaterialTheme.typography.headlineMedium,
                            color = TunerCyan,
                            fontWeight = FontWeight.Bold
                        )
                    }
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

