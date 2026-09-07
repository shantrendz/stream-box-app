package com.example.tuner.ui.components

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.size
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp
import com.example.tuner.ui.theme.TunerAmber
import com.example.tuner.ui.theme.TunerCyan

/**
 * The app's mark drawn in-app (title bar, About section) — same flat retro-CRT-TV shape as
 * the launcher icon (`drawable/ic_launcher_foreground.xml`), just redrawn in Compose so it
 * can sit inline with text at any size without needing a raster/vector resource lookup.
 */
@Composable
fun AppIcon(modifier: Modifier = Modifier, size: Dp = 32.dp) {
    val amber = TunerAmber
    val cyan = TunerCyan

    Canvas(modifier = modifier.size(size)) {
        // Coordinates mirror ic_launcher_foreground.xml's 108x108 viewport, scaled to fit.
        val s = this.size.minDimension / 108f
        val strokeWidth = 5.5f * s
        val legStrokeWidth = 5f * s

        val bodyRect = Rect(Offset(20f * s, 30f * s), Size(68f * s, 44f * s))
        val corner = CornerRadius(8f * s)

        drawLine(
            color = amber,
            start = Offset(32f * s, 74f * s),
            end = Offset(24f * s, 86f * s),
            strokeWidth = legStrokeWidth,
            cap = StrokeCap.Round
        )
        drawLine(
            color = amber,
            start = Offset(76f * s, 74f * s),
            end = Offset(84f * s, 86f * s),
            strokeWidth = legStrokeWidth,
            cap = StrokeCap.Round
        )
        drawRoundRect(
            color = amber,
            topLeft = bodyRect.topLeft,
            size = bodyRect.size,
            cornerRadius = corner,
            style = Stroke(width = strokeWidth, join = StrokeJoin.Round, cap = StrokeCap.Round)
        )
        val triangle = Path().apply {
            moveTo(45f * s, 43f * s)
            lineTo(45f * s, 61f * s)
            lineTo(65f * s, 52f * s)
            close()
        }
        drawPath(triangle, color = cyan)
    }
}
