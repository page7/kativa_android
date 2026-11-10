package com.kativa.reader.ui.reader

import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Double-tap magnification rules (AGENTS.md §5: 双击放大，放大后可移动可缩放，缩回原比例或再次双击退出).
 *
 * The gesture plumbing itself needs a device, but the arithmetic that decides *where* the page ends
 * up is pure and is where the bugs actually live: a wrong pivot makes the page jump, an unclamped
 * offset lets the reader drag the artwork off screen, and a sloppy "is zoomed?" threshold makes the
 * pager either stay locked after a pinch-out or unlock mid-gesture.
 */
class ReaderZoomTest {

    private val viewport = Size(1080f, 2400f)

    // --- scale bounds --------------------------------------------------------

    @Test
    fun `a fitted page is not zoomed`() {
        assertFalse(ReaderZoom.isZoomed(ReaderZoom.MIN_SCALE))
        // Floating-point dust from an animation must not count as magnification.
        assertFalse(ReaderZoom.isZoomed(1.005f))
        assertTrue(ReaderZoom.isZoomed(1.2f))
    }

    @Test
    fun `scale never goes below the fitted page or above the cap`() {
        assertEquals(ReaderZoom.MIN_SCALE, ReaderZoom.clampScale(0.4f), 0.0001f)
        assertEquals(ReaderZoom.MAX_SCALE, ReaderZoom.clampScale(99f), 0.0001f)
        assertEquals(1.8f, ReaderZoom.clampScale(1.8f), 0.0001f)
    }

    // --- the double tap itself ----------------------------------------------

    @Test
    fun `first double tap magnifies and the second returns to the original size`() {
        assertEquals(ReaderZoom.DOUBLE_TAP_SCALE, ReaderZoom.nextDoubleTapScale(ReaderZoom.MIN_SCALE), 0.0001f)
        assertEquals(
            ReaderZoom.MIN_SCALE,
            ReaderZoom.nextDoubleTapScale(ReaderZoom.DOUBLE_TAP_SCALE),
            0.0001f,
        )
    }

    @Test
    fun `double tap after a manual pinch still exits zoom`() {
        // A pinch can leave any scale in range; the second double tap must always end at 1x.
        listOf(1.3f, 2.5f, 4.9f, ReaderZoom.MAX_SCALE).forEach { scale ->
            assertEquals(ReaderZoom.MIN_SCALE, ReaderZoom.nextDoubleTapScale(scale), 0.0001f)
        }
    }

    @Test
    fun `pinching back to the original size leaves zoom instead of sticking near 1x`() {
        // A gesture that lands on 1.02x is visually a fitted page; keeping it "zoomed" would leave
        // the pager disabled with nothing to pan, which reads as a frozen reader.
        assertTrue(ReaderZoom.shouldExitZoom(1f))
        assertTrue(ReaderZoom.shouldExitZoom(1.02f))
        assertTrue(ReaderZoom.shouldExitZoom(ReaderZoom.EXIT_SCALE))
        assertFalse("genuine magnification must survive a pinch", ReaderZoom.shouldExitZoom(1.6f))
    }

    // --- pivot anchoring -----------------------------------------------------

    @Test
    fun `tapping the centre needs no pan`() {
        val offset = ReaderZoom.offsetForPivot(
            pivot = Offset(viewport.width / 2f, viewport.height / 2f),
            viewport = viewport,
            targetScale = 2.5f,
        )
        assertEquals(0f, offset.x, 0.0001f)
        assertEquals(0f, offset.y, 0.0001f)
    }

    @Test
    fun `tapping the left edge pulls the artwork right so that point stays put`() {
        val offset = ReaderZoom.offsetForPivot(Offset(0f, viewport.height / 2f), viewport, 2.5f)
        assertTrue("expected a positive x translation, got ${offset.x}", offset.x > 0f)
        assertEquals(0f, offset.y, 0.0001f)
    }

    @Test
    fun `tapping the top edge pulls the artwork down`() {
        val offset = ReaderZoom.offsetForPivot(Offset(viewport.width / 2f, 0f), viewport, 2.5f)
        assertTrue("expected a positive y translation, got ${offset.y}", offset.y > 0f)
    }

    @Test
    fun `the pivoted point lands under the finger after scaling`() {
        val pivot = Offset(300f, 700f)
        val targetScale = 2.5f
        val offset = ReaderZoom.offsetForPivot(pivot, viewport, targetScale)
        val centre = Offset(viewport.width / 2f, viewport.height / 2f)
        val landed = Offset(
            x = centre.x + (pivot.x - centre.x) * targetScale + offset.x,
            y = centre.y + (pivot.y - centre.y) * targetScale + offset.y,
        )
        // Clamped offsets may fall short near the edges, but never overshoot the finger.
        assertTrue("x drifted past the tap: ${landed.x}", landed.x <= pivot.x + 0.5f)
        assertTrue("y drifted past the tap: ${landed.y}", landed.y <= pivot.y + 0.5f)
    }

    // --- pan clamping --------------------------------------------------------

    @Test
    fun `a fitted page cannot be panned at all`() {
        val offset = ReaderZoom.clampOffset(Offset(500f, -800f), ReaderZoom.MIN_SCALE, viewport)
        assertEquals(0f, offset.x, 0.0001f)
        assertEquals(0f, offset.y, 0.0001f)
    }

    @Test
    fun `panning is limited to the overflow the magnification created`() {
        val scale = 2f
        val clamped = ReaderZoom.clampOffset(Offset(10_000f, -10_000f), scale, viewport)
        assertEquals(viewport.width / 2f, clamped.x, 0.0001f)
        assertEquals(-viewport.height / 2f, clamped.y, 0.0001f)
    }

    @Test
    fun `small pans inside the overflow survive untouched`() {
        val clamped = ReaderZoom.clampOffset(Offset(40f, 60f), 3f, viewport)
        assertEquals(40f, clamped.x, 0.0001f)
        assertEquals(60f, clamped.y, 0.0001f)
    }

    @Test
    fun `degenerate viewport never produces NaN offsets`() {
        val zero = Size(0f, 0f)
        val pivot = ReaderZoom.offsetForPivot(Offset(10f, 10f), zero, 2.5f)
        assertEquals(Offset.Zero, pivot)
        assertEquals(Offset.Zero, ReaderZoom.clampOffset(Offset(5f, 5f), 3f, zero))
    }

    // --- state machine: pinch in/out and panning ------------------------------

    private fun state() = ReaderZoomState(kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined))

    /**
     * Component-wise comparison.
     *
     * `Offset` is an inline value class over a packed `Long`, so `Offset.Zero == Offset(-0f, 0f)` is
     * **false** — the two differ in a sign bit while describing the same point. Clamping through
     * `coerceIn` legitimately produces that `-0f`, so comparing the whole object hides real results
     * behind a bit-level artefact.
     */
    private fun assertOffset(expected: Offset, actual: Offset) {
        assertEquals("x", expected.x, actual.x, 0.0001f)
        assertEquals("y", expected.y, actual.y, 0.0001f)
    }

    @Test
    fun `pinching out magnifies and pinching back to the original size exits zoom`() {
        val zoom = state()
        val centre = Offset(viewport.width / 2f, viewport.height / 2f)

        zoom.scaleBy(2f, centre, viewport)
        assertEquals(2f, zoom.scale, 0.0001f)
        assertTrue(zoom.isZoomed)

        // Shrinking below the exit threshold snaps straight back to 1x rather than sticking at 1.0x.
        zoom.scaleBy(0.5f, centre, viewport)
        assertEquals(ReaderZoom.MIN_SCALE, zoom.scale, 0.0001f)
        assertOffset(Offset.Zero, zoom.offset)
        assertFalse(zoom.isZoomed)
    }

    @Test
    fun `pinching past the cap stops at the maximum scale`() {
        val zoom = state()
        repeat(6) { zoom.scaleBy(2f, Offset(viewport.width / 2f, viewport.height / 2f), viewport) }
        assertEquals(ReaderZoom.MAX_SCALE, zoom.scale, 0.0001f)
    }

    @Test
    fun `panning a fitted page does nothing and a magnified page stays covered`() {
        val zoom = state()
        zoom.panBy(Offset(120f, -80f), viewport)
        assertOffset(Offset.Zero, zoom.offset)

        zoom.scaleBy(3f, Offset(viewport.width / 2f, viewport.height / 2f), viewport)
        zoom.panBy(Offset(10_000f, -10_000f), viewport)
        assertEquals(viewport.width, zoom.offset.x, 0.0001f)
        assertEquals(-viewport.height, zoom.offset.y, 0.0001f)
    }

    @Test
    fun `reset returns a magnified page to the fitted state`() {
        val zoom = state()
        zoom.scaleBy(2f, Offset(viewport.width / 2f, viewport.height / 2f), viewport)
        assertTrue(zoom.isZoomed)
        zoom.reset()
        assertEquals(ReaderZoom.MIN_SCALE, zoom.scale, 0.0001f)
        assertOffset(Offset.Zero, zoom.offset)
        assertFalse(zoom.isZoomed)
    }

    // --- repeated enter/exit (the "zoom only works once" bug) -----------------

    /**
     * Regression: zoom could be entered exactly once.
     *
     * The first implementation derived "is zoomed?" from the *animating* `scale` and let a cancelled
     * tween still write its final value. A second double tap arriving mid-animation therefore read a
     * stale state, the stale tail then reset `scale` to 1x while the reader still considered itself
     * zoomed, and every subsequent double tap evaluated to "exit" — the page never magnified again.
     *
     * The intent ([ReaderZoomState.targetScale]) must therefore flip synchronously, before any
     * animation runs.
     */
    @Test
    fun `double tap is evaluated against the intent and can be toggled repeatedly`() {
        val zoom = state()
        val centre = Offset(viewport.width / 2f, viewport.height / 2f)

        repeat(5) { cycle ->
            assertFalse("cycle $cycle should start fitted", zoom.isZoomed)
            assertEquals(ReaderZoom.MIN_SCALE, zoom.targetScale, 0.0001f)

            zoom.toggleDoubleTap(centre, viewport)
            assertTrue("cycle $cycle must magnify", zoom.isZoomed)
            assertEquals(ReaderZoom.DOUBLE_TAP_SCALE, zoom.targetScale, 0.0001f)

            zoom.toggleDoubleTap(centre, viewport)
            assertFalse("cycle $cycle must return to the original size", zoom.isZoomed)
            assertEquals(ReaderZoom.MIN_SCALE, zoom.targetScale, 0.0001f)
        }
    }

    @Test
    fun `a second double tap during the animation inverts the previous intent`() {
        // Both taps land inside one 180ms tween: the second one must not be judged by the
        // half-finished scale, and the cancelled tween must not drag the page back to 1x.
        val zoom = state()
        val centre = Offset(viewport.width / 2f, viewport.height / 2f)

        zoom.toggleDoubleTap(centre, viewport) // -> 2.5x, animation now running
        zoom.toggleDoubleTap(centre, viewport) // -> back to 1x, cancels the first
        assertFalse("the second tap must win", zoom.isZoomed)
        assertEquals(ReaderZoom.MIN_SCALE, zoom.targetScale, 0.0001f)

        zoom.toggleDoubleTap(centre, viewport) // -> 2.5x again
        assertTrue("zoom must still be enterable after a cancelled animation", zoom.isZoomed)
        assertEquals(ReaderZoom.DOUBLE_TAP_SCALE, zoom.targetScale, 0.0001f)
    }

    @Test
    fun `pinching does not desynchronise the intent from the rendered scale`() {
        val zoom = state()
        val centre = Offset(viewport.width / 2f, viewport.height / 2f)

        zoom.toggleDoubleTap(centre, viewport)
        zoom.scaleBy(1.5f, centre, viewport)
        assertEquals("target must follow the pinch", zoom.scale, zoom.targetScale, 0.0001f)
        assertTrue(zoom.isZoomed)

        // And a double tap after a pinch exits from wherever the pinch left it.
        zoom.toggleDoubleTap(centre, viewport)
        assertFalse(zoom.isZoomed)
        assertEquals(ReaderZoom.MIN_SCALE, zoom.targetScale, 0.0001f)
    }
}
