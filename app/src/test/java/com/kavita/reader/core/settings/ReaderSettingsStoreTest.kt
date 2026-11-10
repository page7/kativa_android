package com.kavita.reader.core.settings

import com.kavita.reader.core.settings.ReaderSettingsCodec
import com.kavita.reader.core.settings.ReaderSettingsKeys
import com.kavita.reader.core.settings.ReaderSettingsLayers
import com.kavita.reader.core.settings.StoredReaderSettings
import com.kavita.reader.domain.model.PageLayout
import com.kavita.reader.domain.model.ReaderMode
import com.kavita.reader.domain.model.ReadingDirection
import com.kavita.reader.domain.model.ScalingOption
import com.kavita.reader.ui.reader.ReaderSettings
import com.kavita.reader.ui.reader.ReaderSettingsPersistence
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Persistence rules for reader settings.
 *
 * The reader already shipped a run of dead controls, so the two failure modes worth pinning are:
 * the round trip silently losing a field (the user's choice does not survive a reopen), and a
 * stored value leaking into the wrong series or the wrong server.
 */
class ReaderSettingsStoreTest {

    // --- codec round trip ----------------------------------------------------

    @Test
    fun `every persisted field survives the round trip`() {
        val stored = StoredReaderSettings(
            readingDirection = ReadingDirection.RightToLeft.apiValue,
            readerMode = ReaderMode.Webtoon.apiValue,
            layoutMode = PageLayout.DoubleReversed.apiValue,
            scalingOption = ScalingOption.FitToWidth.apiValue,
            coverOffset = false,
            backgroundColor = "#101010",
            showPageNumber = false,
            volumeKeyPaging = true,
            forceLandscape = true,
        )

        assertEquals(stored, ReaderSettingsCodec.decode(ReaderSettingsCodec.encode(stored)))
    }

    @Test
    fun `defaults are encoded explicitly so unset is distinguishable`() {
        // `encodeDefaults = true` matters: without it an all-default object would serialise to
        // `{}`, and the global layer could never be told apart from "never configured".
        val raw = ReaderSettingsCodec.encode(StoredReaderSettings())
        assertTrue("encoder must emit fields, got $raw", raw.contains("showPageNumber"))
        assertEquals(StoredReaderSettings(), ReaderSettingsCodec.decode(raw))
    }

    @Test
    fun `corrupt payload degrades to unset instead of crashing the reader`() {
        assertNull(ReaderSettingsCodec.decode("{not json"))
        assertNull(ReaderSettingsCodec.decode(null))
        assertNull(ReaderSettingsCodec.decode(""))
    }

    @Test
    fun `unknown fields from a future build are ignored`() {
        val future = """{"showPageNumber":false,"someNewSetting":42}"""
        assertEquals(false, ReaderSettingsCodec.decode(future)?.showPageNumber)
    }

    @Test
    fun `unknown enum numbers fall back instead of throwing`() {
        val decoded = ReaderSettingsCodec.decode("""{"readerMode":99,"layoutMode":-7}""")
        val render = ReaderSettingsPersistence.toRenderModel(decoded!!)
        assertEquals(ReaderMode.LeftRight, render.readerMode)
        assertEquals(PageLayout.Single, render.layoutMode)
    }

    // --- brightness is session-only -----------------------------------------

    @Test
    fun `brightness is never persisted`() {
        val live = ReaderSettings.Default.copy(brightness = 0.35f)
        val stored = ReaderSettingsPersistence.toStored(live)

        // Round-tripping must not resurrect a stored brightness: the field simply does not exist.
        val restored = ReaderSettingsPersistence.toRenderModel(
            ReaderSettingsCodec.decode(ReaderSettingsCodec.encode(stored))!!,
            current = ReaderSettings.Default,
        )
        assertEquals(ReaderSettings.Default.brightness, restored.brightness, 0f)
    }

    @Test
    fun `restoring settings keeps the in-session brightness`() {
        // The user dimmed the screen, then changed the paging mode: the dimming must not jump back.
        val current = ReaderSettings.Default.copy(brightness = 0.4f)
        val restored = ReaderSettingsPersistence.toRenderModel(StoredReaderSettings(), current)
        assertEquals(0.4f, restored.brightness, 0f)
    }

    // --- fallback chain ------------------------------------------------------

    @Test
    fun `series wins over global which wins over app default`() {
        val series = StoredReaderSettings(readerMode = ReaderMode.Webtoon.apiValue)
        val global = StoredReaderSettings(readerMode = ReaderMode.UpDown.apiValue)

        assertEquals(series, ReaderSettingsLayers.resolve(series, global))
        assertEquals(global, ReaderSettingsLayers.resolve(null, global))
        assertEquals(ReaderSettingsLayers.AppDefault, ReaderSettingsLayers.resolve(null, null))
    }

    @Test
    fun `restoring a series falls back to global not to factory defaults`() {
        // "恢复本系列的阅读设置" must land on whatever the user chose on the settings screen.
        val global = StoredReaderSettings(forceLandscape = true, showPageNumber = false)
        assertEquals(global, ReaderSettingsLayers.resolve(series = null, global = global))
        assertFalse(global.showPageNumber)
        assertTrue(global.forceLandscape)
    }

    // --- the new landscape flag is honoured ---------------------------------

    @Test
    fun `landscape flag round trips through the render model`() {
        val stored = StoredReaderSettings(forceLandscape = true)
        assertTrue(ReaderSettingsPersistence.toRenderModel(stored).forceLandscape)
        assertTrue(ReaderSettingsPersistence.toStored(ReaderSettings.Default.copy(forceLandscape = true)).forceLandscape)
    }

    // --- keys are per server per series -------------------------------------

    @Test
    fun `series keys never collide across servers or series`() {
        val a = ReaderSettingsKeys.series("s1", 7)
        val b = ReaderSettingsKeys.series("s2", 7)
        val c = ReaderSettingsKeys.series("s1", 8)
        assertEquals(3, setOf(a, b, c).size)
        assertTrue("a series key must not shadow the global layer", a != ReaderSettingsKeys.GLOBAL)
    }

    @Test
    fun `a server prefix only matches its own keys`() {
        val prefix = ReaderSettingsKeys.serverPrefix("s1")
        assertTrue(ReaderSettingsKeys.series("s1", 7).startsWith(prefix))
        // Guard against a server id that is a prefix of another ("s1" vs "s10").
        assertFalse(ReaderSettingsKeys.series("s10", 7).startsWith(prefix))
    }
}
