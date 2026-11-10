package com.kavita.reader.ui.reader

/**
 * Maps an Android key code to the page delta it should apply while volume-key paging is on.
 *
 * Kept as a plain function (no `KeyEvent`) so the mapping is unit-testable on the JVM: the
 * Android key codes are just ints, and the reader must never depend on `android.view.KeyEvent`
 * in a test.
 *
 * Volume-up goes *backwards*, matching every physical ebook reader: the key is at the top of the
 * device, so "up" reads as "previous".
 */
object VolumeKeyPaging {
    /** `android.view.KeyEvent.KEYCODE_VOLUME_UP` */
    const val KEYCODE_VOLUME_UP = 24

    /** `android.view.KeyEvent.KEYCODE_VOLUME_DOWN` */
    const val KEYCODE_VOLUME_DOWN = 25

    /**
     * -1 for volume-up (previous page), +1 for volume-down (next page), null for anything else
     * so the caller can leave normal key handling untouched.
     */
    fun pageDelta(keyCode: Int): Int? = when (keyCode) {
        KEYCODE_VOLUME_UP -> -1
        KEYCODE_VOLUME_DOWN -> 1
        else -> null
    }
}
