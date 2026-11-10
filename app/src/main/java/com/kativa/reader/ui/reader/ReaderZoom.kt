package com.kativa.reader.ui.reader

import androidx.compose.animation.core.FastOutSlowInEasing
import androidx.compose.animation.core.animate
import androidx.compose.animation.core.tween
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxScope
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.pointerInput
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch

/**
 * Double-tap magnification for a reader page (AGENTS.md §5 / docs/FLOWS.md "双击").
 *
 * The reader page is a pager slot: while the page is at its original size the pager owns every
 * horizontal/vertical drag, so paging keeps working exactly as before. Only once the page is
 * magnified does the slot claim pan/pinch gestures and the pager's own scrolling is switched off
 * (`userScrollEnabled = false`), which avoids two gesture systems arbitrating over the same drag.
 *
 * Zoom ends in three ways, all of them explicit:
 *  - double-tapping again;
 *  - pinching back down to the original scale (the page snaps to 1x and releases the pager);
 *  - turning the page (the next slot starts unzoomed).
 *
 * All geometry lives here as pure functions so the rules can be unit-tested on the JVM
 * (`ReaderZoomTest`) instead of being buried in a composable.
 */
object ReaderZoom {

    /** Never smaller than the fitted page, otherwise zoom would crop the artwork by itself. */
    const val MIN_SCALE = 1f

    /** Upper bound: 5x is plenty to read small lettering without turning the page into mush. */
    const val MAX_SCALE = 5f

    /** Magnification applied by the first double-tap. */
    const val DOUBLE_TAP_SCALE = 2.5f

    /** Tolerance so floating-point noise does not keep the reader "zoomed" at 1.0000001x. */
    private const val EPSILON = 0.01f

    /**
     * Pinching below this counts as "back to the original size" and leaves zoom entirely.
     *
     * Without a real exit threshold a gesture that lands on 1.02x leaves the page technically
     * magnified — the pager stays disabled and the reader looks stuck — while the user sees a
     * fitted page. `EPSILON` is too tight for that: it only guards floating-point dust.
     */
    const val EXIT_SCALE = 1.05f

    fun isZoomed(scale: Float): Boolean = scale > MIN_SCALE + EPSILON

    /**
     * Whether a pinch result counts as "back to the original size" and must leave zoom.
     *
     * Kept as a named predicate so the rule is unit-tested and no call site can quietly use the
     * tighter `isZoomed` tolerance instead (which leaves the reader stuck at 1.02x with paging
     * disabled).
     */
    fun shouldExitZoom(scale: Float): Boolean = scale <= EXIT_SCALE

    fun clampScale(scale: Float): Float = scale.coerceIn(MIN_SCALE, MAX_SCALE)

    /** Scale a double tap should end on: magnify when fitted, return to the original size otherwise. */
    fun nextDoubleTapScale(scale: Float): Float = if (isZoomed(scale)) MIN_SCALE else DOUBLE_TAP_SCALE

    /**
     * Offset that keeps [pivot] (the tap point, in viewport coordinates) over the same artwork
     * after scaling to [targetScale] about the viewport centre.
     *
     * Compose applies scale about the layer's centre and *then* the translation, so a point `p`
     * lands at `centre + (p - centre) * s + t`. Solving for the tap staying put gives
     * `t = (centre - pivot) * (s - 1)`, clamped so no empty edge is exposed.
     */
    fun offsetForPivot(pivot: Offset, viewport: Size, targetScale: Float): Offset {
        if (viewport.width <= 0f || viewport.height <= 0f) return Offset.Zero
        val centre = Offset(viewport.width / 2f, viewport.height / 2f)
        val delta = Offset(
            x = (centre.x - pivot.x) * (targetScale - 1f),
            y = (centre.y - pivot.y) * (targetScale - 1f),
        )
        return clampOffset(delta, targetScale, viewport)
    }

    /**
     * Keeps the scaled page covering the viewport: panning is limited to the overflow the
     * magnification created, so the reader can never drag the artwork off into black.
     */
    fun clampOffset(offset: Offset, scale: Float, viewport: Size): Offset {
        if (viewport.width <= 0f || viewport.height <= 0f) return Offset.Zero
        val maxX = (viewport.width * (scale - 1f) / 2f).coerceAtLeast(0f)
        val maxY = (viewport.height * (scale - 1f) / 2f).coerceAtLeast(0f)
        return Offset(
            x = offset.x.coerceIn(-maxX, maxX),
            y = offset.y.coerceIn(-maxY, maxY),
        )
    }
}

/**
 * Mutable zoom state of the visible page: [scale] relative to the fitted size plus the pan
 * [offset] in pixels. Created by [rememberReaderZoomState] so animations survive recomposition but
 * never leak past the reader screen.
 *
 * **The mode is a separate, synchronous flag — never inferred from [scale].** That distinction is
 * the whole bug this class was rewritten for: the first version derived "am I zoomed?" from the
 * animating scale, so a second double tap arriving while the 180ms animation was still running read
 * a stale target, and the cancelled animation could still write its final `scale` afterwards. The
 * reader then sat at 1.0x while still claiming to be zoomed and every later double tap evaluated to
 * "exit" — zoom could be entered exactly once, then never again.
 *
 * [targetScale] is the user's intent and changes instantly and synchronously; [scale] only chases it
 * for rendering. Gestures and the pager always consult the intent.
 */
@Stable
class ReaderZoomState internal constructor(private val scope: CoroutineScope) {

    /** Rendered magnification. Between [ReaderZoom.MIN_SCALE] and [ReaderZoom.MAX_SCALE]. */
    var scale by mutableFloatStateOf(ReaderZoom.MIN_SCALE)
        private set

    var offset by mutableStateOf(Offset.Zero)
        private set

    /**
     * Where the page is heading: 1x = fitted, anything above = magnified.
     *
     * Set synchronously by gestures so a burst of double taps is evaluated against the *last
     * intent*, not against a half-finished animation.
     */
    var targetScale by mutableFloatStateOf(ReaderZoom.MIN_SCALE)
        private set

    /**
     * True while the page is magnified; the pager is paused and pan/pinch owns the slot.
     *
     * Not `derivedStateOf { isZoomed(scale) }` any more: that made the state lag the intent by one
     * animation (see the class comment).
     */
    val isZoomed: Boolean get() = ReaderZoom.isZoomed(targetScale)

    private var animation: Job? = null
    /**
     * Monotonic token. A cancelled coroutine can still run one more frame, so the animation checks
     * that it is still the current one before writing — otherwise the stale tail would clobber the
     * fresh target.
     */
    private var animationId = 0

    /** Snap back to the fitted page (page change, mode change, or leaving zoom). */
    fun reset() {
        animationId++
        animation?.cancel()
        animation = null
        scale = ReaderZoom.MIN_SCALE
        targetScale = ReaderZoom.MIN_SCALE
        offset = Offset.Zero
    }

    /**
     * A double tap either magnifies around [pivot] or returns to the original size. The second
     * half of the animation is what makes "再次双击退出缩放" readable instead of a hard cut.
     */
    fun toggleDoubleTap(pivot: Offset, viewport: Size) {
        // Decide from the *intent*, not from the animating `scale`: a double tap during the
        // previous animation must still invert whatever the user last asked for.
        val target = ReaderZoom.nextDoubleTapScale(targetScale)
        val nextOffset = if (ReaderZoom.isZoomed(target)) {
            ReaderZoom.offsetForPivot(pivot, viewport, target)
        } else {
            Offset.Zero
        }
        animateTo(target, nextOffset)
    }

    /** Drives [scale]/[offset] towards an already-decided intent; the intent itself is set at once. */
    private fun animateTo(target: Float, nextOffset: Offset) {
        // Publish the intent synchronously: the pager, tap zones and the next double tap all read
        // this, so nothing waits for the 180ms tween to finish.
        targetScale = target
        animationId++
        val token = animationId
        animation?.cancel()

        val startScale = scale
        val startOffset = offset
        animation = scope.launch {
            animate(
                initialValue = 0f,
                targetValue = 1f,
                animationSpec = tween(durationMillis = 180, easing = FastOutSlowInEasing),
            ) { fraction, _ ->
                // A cancelled animation can still deliver a final frame; ignoring it is what keeps
                // the stale tail from overwriting a newer intent.
                if (token != animationId) return@animate
                scale = startScale + (target - startScale) * fraction
                offset = startOffset + (nextOffset - startOffset) * fraction
            }
            if (token != animationId) return@launch
            scale = target
            offset = nextOffset
        }
    }

    /** Single-finger drag while magnified. */
    fun panBy(delta: Offset, viewport: Size) {
        animationId++
        animation?.cancel()
        animation = null
        offset = ReaderZoom.clampOffset(offset + delta, scale, viewport)
    }

    /**
     * Pinch while magnified. The content point under [centroid] stays anchored, and shrinking back
     * to the original size leaves zoom altogether (docs/FLOWS.md: 缩小到原始比例退出缩放).
     */
    fun scaleBy(factor: Float, centroid: Offset, viewport: Size) {
        animationId++
        animation?.cancel()
        animation = null
        val previous = scale
        if (previous <= 0f) return
        val target = ReaderZoom.clampScale(previous * factor)
        if (ReaderZoom.shouldExitZoom(target)) {
            // Snap all the way to 1x instead of leaving a near-invisible magnification behind.
            reset()
            return
        }
        val centre = Offset(viewport.width / 2f, viewport.height / 2f)
        val anchor = centroid - centre - offset
        val ratio = target / previous
        targetScale = target
        scale = target
        offset = ReaderZoom.clampOffset(offset + anchor * (1f - ratio), target, viewport)
    }
}

@Composable
fun rememberReaderZoomState(vararg keys: Any?): ReaderZoomState {
    val scope = rememberCoroutineScope()
    return remember(*keys) { ReaderZoomState(scope) }
}

/**
 * Renders [content] through [state] magnification.
 *
 * Only this composable knows how zoom is *drawn* and how *panning/pinching* is driven, so the paged
 * reader and the Webtoon strip cannot drift apart. The double tap lives on the caller's gesture node
 * instead, because `detectTapGestures` consumes the down event: handling taps here would swallow the
 * reader's three tap zones no matter how they were wired.
 *
 * Two nested boxes, and that detail matters:
 *  - the **outer** box is *not* transformed, so pointer deltas arrive in viewport pixels and the
 *    page tracks the finger exactly. Attaching `detectTransformGestures` to the scaled layer would
 *    divide every delta by the scale and make panning lag;
 *  - `clipToBounds` also lives on the outer box: `graphicsLayer(clip = true)` clips the layer's own
 *    content, not the scaled result, so a magnified page would otherwise paint over the neighbouring
 *    page and the chrome bars.
 *
 * **Double tap is deliberately NOT handled here.** `detectTapGestures` consumes the initial down,
 * and this node is a child of the pager's own gesture node: handling taps here would swallow the
 * three tap zones (previous / menu / next) no matter how they are wired. The caller keeps the tap
 * detector on an outer node and only calls [ReaderZoomState.toggleDoubleTap] from it. Pan/pinch can
 * live here because `detectTransformGestures` does not consume the down event.
 */
@Composable
fun ZoomablePage(
    zoomState: ReaderZoomState,
    modifier: Modifier = Modifier,
    /** False for pages the pager pre-composed but the user is not looking at. */
    active: Boolean = true,
    content: @Composable BoxScope.() -> Unit,
) {
    if (!active) {
        Box(modifier = modifier) { content() }
        return
    }

    Box(
        modifier = modifier
            .clipToBounds()
            .then(
                if (zoomState.isZoomed) {
                    Modifier.pointerInput(zoomState) {
                        detectTransformGestures { centroid, pan, gestureZoom, _ ->
                            val viewport = Size(size.width.toFloat(), size.height.toFloat())
                            if (gestureZoom != 1f) {
                                zoomState.scaleBy(gestureZoom, centroid, viewport)
                            }
                            if (pan != Offset.Zero) {
                                zoomState.panBy(pan, viewport)
                            }
                        }
                    }
                } else {
                    Modifier
                },
            ),
    ) {
        Box(
            Modifier
                .fillMaxSize()
                .graphicsLayer {
                    // Read inside the draw phase: panning and the magnification animation therefore
                    // never recompose the page, only re-draw its layer.
                    scaleX = zoomState.scale
                    scaleY = zoomState.scale
                    translationX = zoomState.offset.x
                    translationY = zoomState.offset.y
                },
            content = content,
        )
    }
}
