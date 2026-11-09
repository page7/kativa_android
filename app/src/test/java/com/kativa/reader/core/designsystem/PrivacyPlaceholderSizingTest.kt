package com.kativa.reader.core.designsystem

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Geometry guard for the privacy placeholder.
 *
 * [PrivacyCoverPlaceholder] must only *fill* the box its caller hands it. It used to apply
 * `aspectRatio(2f / 3f)` unconditionally, which is invisible on poster slots (their caller already
 * passes the same ratio) but broke the home folder mosaic: the folder slot is square, so a 2:3
 * placeholder grew taller than the slot, `padding(INSET)` lost its top rim, and the artwork was
 * only clipped by the outer card instead of being inset and rounded like the covers it replaces.
 *
 * Compose layout bugs are invisible to unit tests, so this pins the contract in the source the
 * same way [com.kativa.reader.ThemeContractTest] pins the theme/activity contract.
 */
class PrivacyPlaceholderSizingTest {

    private val moduleRoot = File("src/main").takeIf { it.isDirectory } ?: File("app/src/main")

    /** Source text of one composable: from its `fun` header to the first line that is just `}`. */
    private fun bodyOf(relativePath: String, function: String): String {
        val file = File(moduleRoot, relativePath)
        assertTrue("Expected file at ${file.absolutePath}", file.isFile)
        val text = file.readText().replace("\r\n", "\n")
        val start = text.indexOf("fun $function(")
        assertTrue("Expected `fun $function(` in $relativePath", start >= 0)
        val end = text.indexOf("\n}", start)
        assertTrue("Could not find the end of $function in $relativePath", end > start)
        return text.substring(start, end)
    }

    @Test
    fun `privacy placeholder does not impose its own aspect ratio`() {
        val body = bodyOf(
            "java/com/kativa/reader/core/designsystem/Components.kt",
            "PrivacyCoverPlaceholder",
        )
        assertFalse(
            "PrivacyCoverPlaceholder must fill the caller's box; an internal aspectRatio() " +
                "overflows square slots such as the home folder mosaic",
            body.contains("aspectRatio("),
        )
    }

    @Test
    fun `folder privacy panel shares the mosaic cell inset and the cover radius`() {
        val body = bodyOf("java/com/kativa/reader/ui/home/LibraryFolderCard.kt", "LibraryFolderCard")
        assertTrue(
            "Folder privacy panel must fill the padded slot (INSET applies on all four sides)",
            body.contains("PrivacyCoverPlaceholder("),
        )
        assertTrue(
            "Folder privacy panel must use the cover radius (KativaDimens.PosterRadius, 12dp) so " +
                "its rounding matches every other cover instead of looking like a sharp box",
            body.contains("shape = PrivacyPanelShape"),
        )
        assertFalse(
            "Folder privacy panel must NOT reuse the 3dp mosaic-cell radius: it replaces all four " +
                "cells with one large surface, so per-cell rounding is far too small",
            body.contains("shape = MosaicCellShape"),
        )
    }

    @Test
    fun `privacy panel radius is the shared cover radius token`() {
        val source = File(moduleRoot, "java/com/kativa/reader/ui/home/LibraryFolderCard.kt")
            .readText()
            .replace("\r\n", "\n")
        assertTrue(
            "PrivacyPanelShape must be derived from KativaDimens.PosterRadius so the panel can " +
                "never drift away from the cover corners",
            source.contains("PrivacyPanelShape = RoundedCornerShape(KativaDimens.PosterRadius.dp)"),
        )
    }
}
