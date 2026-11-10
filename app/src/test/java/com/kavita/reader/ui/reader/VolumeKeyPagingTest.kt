package com.kavita.reader.ui.reader

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Volume-key paging mapping (AGENTS.md §5). */
class VolumeKeyPagingTest {

    @Test
    fun `volume up goes to the previous page`() {
        assertEquals(-1, VolumeKeyPaging.pageDelta(VolumeKeyPaging.KEYCODE_VOLUME_UP)!!)
    }

    @Test
    fun `volume down goes to the next page`() {
        assertEquals(1, VolumeKeyPaging.pageDelta(VolumeKeyPaging.KEYCODE_VOLUME_DOWN)!!)
    }

    @Test
    fun `unrelated keys are not consumed`() {
        assertNull(VolumeKeyPaging.pageDelta(0))
        assertNull(VolumeKeyPaging.pageDelta(4)) // KEYCODE_BACK
        assertNull(VolumeKeyPaging.pageDelta(26)) // KEYCODE_POWER
    }

    @Test
    fun `key codes match android KeyEvent constants`() {
        // Guards against a transcription slip: these ints are duplicated rather than imported so
        // the object stays JVM-testable.
        assertEquals(24, VolumeKeyPaging.KEYCODE_VOLUME_UP)
        assertEquals(25, VolumeKeyPaging.KEYCODE_VOLUME_DOWN)
    }
}
