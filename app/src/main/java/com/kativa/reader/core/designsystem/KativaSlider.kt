package com.kativa.reader.core.designsystem

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.Dp
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.dp
import kotlin.math.roundToInt

/**
 * Kativa slider (AGENTS.md §6: screens must not style Material components themselves).
 *
 * Material 3 1.3 draws a 16dp tall track, a 4x44dp bar-shaped thumb and a stop indicator dot at
 * the end of the track. That reads as three unrelated shapes on a black reader overlay, and the
 * inactive track falls back to `secondaryContainer` — which is an unstyled Material purple in
 * this theme. This wrapper keeps the official drag gesture, 48dp touch target and TalkBack
 * semantics, and only replaces the visuals:
 *
 *  - 4dp fully rounded track, no gap and no stop dot;
 *  - 14dp round thumb;
 *  - optional floating value bubble while dragging (reader progress bar).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun KativaSlider(
    value: Float,
    onValueChange: (Float) -> Unit,
    modifier: Modifier = Modifier,
    enabled: Boolean = true,
    valueRange: ClosedFloatingPointRange<Float> = 0f..1f,
    steps: Int = 0,
    activeColor: Color = MaterialTheme.colorScheme.primary,
    trackColor: Color = MaterialTheme.colorScheme.onSurface.copy(alpha = 0.16f),
    thumbColor: Color = activeColor,
    /** Called once the finger lifts, after the drag ends. */
    onValueChangeFinished: (() -> Unit)? = null,
    /** When non-null a bubble follows the thumb while dragging, showing this label. */
    valueLabel: (@Composable (Float) -> String)? = null,
) {
    var dragging by remember { mutableStateOf(false) }

    Column(modifier.fillMaxWidth()) {
        if (valueLabel != null) {
            // Space is reserved unconditionally: growing the column mid-drag would push the
            // track away from the finger.
            ValueBubble(
                text = valueLabel(value),
                fraction = fractionOf(value, valueRange),
                visible = dragging,
            )
        }
        Slider(
            value = value,
            onValueChange = {
                dragging = true
                onValueChange(it)
            },
            onValueChangeFinished = {
                dragging = false
                onValueChangeFinished?.invoke()
            },
            modifier = Modifier.fillMaxWidth(),
            enabled = enabled,
            valueRange = valueRange,
            steps = steps,
            track = { state -> KativaTrack(state, activeColor, trackColor) },
            thumb = { KativaThumb(thumbColor) },
        )
    }
}

/**
 * Value bubble that floats above the thumb.
 *
 * The surface is always composed (alpha 0 when idle) so its width can be measured before the
 * first visible frame; otherwise the bubble would flash at the wrong offset for one frame.
 */
@Composable
private fun ValueBubble(
    text: String,
    fraction: Float,
    visible: Boolean,
) {
    var bubbleWidth by remember { mutableIntStateOf(0) }
    val thumbPx = with(LocalDensity.current) { ThumbSize.toPx() }

    BoxWithConstraints(Modifier.fillMaxWidth().height(BubbleSlotHeight)) {
        val fullWidth = constraints.maxWidth.toFloat()
        val center = thumbPx + (fullWidth - thumbPx * 2f).coerceAtLeast(0f) * fraction
        val maxX = (fullWidth - bubbleWidth).coerceAtLeast(0f)
        val x = (center - bubbleWidth / 2f).coerceIn(0f, maxX)

        Surface(
            color = Color.White,
            shape = RoundedCornerShape(8.dp),
            modifier = Modifier
                .onSizeChanged { bubbleWidth = it.width }
                .offset { IntOffset(x.roundToInt(), 0) }
                .alpha(if (visible && bubbleWidth > 0) 1f else 0f),
        ) {
            Text(
                text = text,
                style = MaterialTheme.typography.labelMedium,
                color = Color(0xFF101014),
                maxLines = 1,
                modifier = Modifier.padding(horizontal = 8.dp, vertical = 2.dp),
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun KativaTrack(
    state: SliderState,
    activeColor: Color,
    trackColor: Color,
) {
    val fraction = fractionOf(state.value, state.valueRange)
    Canvas(Modifier.fillMaxWidth().height(TrackHeight)) {
        val radius = CornerRadius(size.height / 2f)
        drawRoundRect(color = trackColor, cornerRadius = radius)
        if (fraction > 0f) {
            drawRoundRect(
                color = activeColor,
                size = Size(size.width * fraction, size.height),
                cornerRadius = CornerRadius(minOf(size.width * fraction, size.height) / 2f),
            )
        }
    }
}

@Composable
private fun KativaThumb(color: Color) {
    // Material 3 top-aligns the thumb slot's content inside the slider's 16dp track container,
    // while it *centres* the track itself. A 14dp dot therefore rides 1dp above the track's centre
    // line (measured on device at 440dpi: 16px of gap above the track vs 12px below, i.e. 3px of
    // upward offset). Nudge it down by half the leftover space so the dot is concentric with the
    // track. Derived from the tokens rather than eyeballed, so it stays correct at other densities.
    Box(
        Modifier
            .size(ThumbSize)
            .offset(y = (ThumbSlotHeight - ThumbSize) / 2)
            .background(color, CircleShape),
    )
}

/** Position of [value] inside [range] as 0..1, safe against a zero-length range. */
internal fun fractionOf(value: Float, range: ClosedFloatingPointRange<Float>): Float {
    val span = range.endInclusive - range.start
    if (span <= 0f) return 0f
    return ((value - range.start) / span).coerceIn(0f, 1f)
}

private val TrackHeight: Dp = 4.dp
private val ThumbSize: Dp = 14.dp

/**
 * Height of the box Material 3 hands to `thumb`/`track` slots (its `InactiveTrackHeight` token).
 * The track is centred inside it but the thumb is top-aligned, which is why [KativaThumb] needs
 * the difference to sit on the track's centre line.
 */
private val ThumbSlotHeight: Dp = 16.dp

/** Reserved strip above the track so the bubble never resizes the layout. */
private val BubbleSlotHeight: Dp = 22.dp
