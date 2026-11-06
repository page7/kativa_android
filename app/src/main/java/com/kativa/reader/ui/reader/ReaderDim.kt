package com.kativa.reader.ui.reader

/**
 * Content dimming for the reader (AGENTS.md §5: 可调亮度，仅本 App 会话).
 *
 * Deliberately implemented as a translucent black scrim over the page content rather than by
 * writing `WindowManager.LayoutParams.screenBrightness`: touching the system window brightness
 * fights the user's own auto-brightness, survives badly across configuration changes, and would
 * need a restore path on every exit. A scrim is local, needs no permission, and never leaves the
 * device dim after the reader closes.
 *
 * The scrim sits above the pages but below the controls, so the toolbar and the sliders stay at
 * full contrast while only the artwork is dimmed.
 */
object ReaderDim {

    /** Darkest scrim drawn at the lowest selectable brightness. */
    private const val MAX_ALPHA = 0.64f

    /**
     * Scrim alpha for a `ReaderSettings.brightness` value of 0.2..1.0.
     *
     * 1.0 maps to a fully transparent scrim (no dimming at all); 0.2 maps to [MAX_ALPHA], which
     * still leaves the page readable instead of turning the screen black.
     */
    fun alphaFor(brightness: Float): Float {
        val clamped = brightness.coerceIn(0.2f, 1f)
        return ((1f - clamped) * 0.8f).coerceIn(0f, MAX_ALPHA)
    }
}
