package com.kavita.reader.core.designsystem

import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.animation.core.tween
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.dp

/**
 * Flat progress bar used for read progress (AGENTS.md §6: no direct Material components in
 * screens).
 *
 * `LinearProgressIndicator` in Material 3 1.3 always draws a 4dp gap plus a stop-indicator dot at
 * the end of the track. On a 3dp cover-card strip that dot is a stray bright pixel, so the bar is
 * drawn directly here: one rounded track, one rounded fill, nothing else.
 */
@Composable
fun KavitaProgressBar(
    progress: Float,
    modifier: Modifier = Modifier,
    height: Dp = 4.dp,
    color: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.12f),
    animated: Boolean = true,
) {
    val target = progress.coerceIn(0f, 1f)
    val animatedValue by animateFloatAsState(
        targetValue = target,
        animationSpec = tween(durationMillis = 300),
        label = "kavita-progress",
    )
    val displayed = if (animated) animatedValue else target

    Canvas(modifier.height(height).fillMaxWidth()) {
        val corner = size.height / 2f
        drawRoundRect(color = trackColor, cornerRadius = CornerRadius(corner))
        val filled = size.width * displayed
        if (filled > 0f) {
            drawRoundRect(
                color = color,
                size = Size(filled, size.height),
                cornerRadius = CornerRadius(minOf(filled, size.height) / 2f),
            )
        }
    }
}
