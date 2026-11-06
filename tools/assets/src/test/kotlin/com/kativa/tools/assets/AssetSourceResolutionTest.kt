package com.kativa.tools.assets

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

/**
 * Guards the asset-source resolution order documented in docs/ASSETS.md §1.
 *
 * The bug this covers: the pipeline claimed to prefer `assets/svg/<id>.svg` but only ever read
 * `assigns/kavita.svg`, so delivered icons were silently replaced by placeholders.
 */
class AssetSourceResolutionTest {

    @get:Rule
    val tmp = TemporaryFolder()

    private val root: File get() = tmp.root

    private fun icon(id: String) = ManifestEntry(id = id, kind = "icon")

    private fun write(relativePath: String, body: String): File {
        val file = File(root, relativePath)
        file.parentFile.mkdirs()
        file.writeText(body)
        return file
    }

    @Test
    fun `assets svg wins over placeholder`() {
        val delivered = write("assets/svg/ic_home.svg", "<svg><!--delivered--></svg>")

        val source = resolveSource(root, icon("ic_home"))

        assertEquals(delivered.canonicalFile, source.file?.canonicalFile)
        assertTrue(source.svg.contains("delivered"))
    }

    @Test
    fun `assigns with matching id is accepted when assets svg is absent`() {
        val dropIn = write("assigns/ic_search.svg", "<svg><!--assigns--></svg>")

        val source = resolveSource(root, icon("ic_search"))

        assertEquals(dropIn.canonicalFile, source.file?.canonicalFile)
    }

    @Test
    fun `assets svg takes precedence over assigns with the same id`() {
        write("assets/svg/ic_more.svg", "<svg><!--assets--></svg>")
        write("assigns/ic_more.svg", "<svg><!--assigns--></svg>")

        val source = resolveSource(root, icon("ic_more"))

        assertTrue(source.svg.contains("assets"))
    }

    @Test
    fun `missing source falls back to a placeholder`() {
        val source = resolveSource(root, icon("ic_close"))

        assertNull(source.file)
        assertTrue(source.svg.contains("<svg"))
    }

    @Test
    fun `launcher falls back to the legacy kavita source`() {
        write("assigns/kavita.svg", "<svg><!--kavita--></svg>")

        val foreground = resolveSource(root, ManifestEntry(id = "ic_launcher_foreground", kind = "launcher-foreground"))
        val legacy = resolveSource(root, ManifestEntry(id = "ic_launcher", kind = "launcher"))

        assertTrue(foreground.svg.contains("kavita"))
        assertTrue(legacy.svg.contains("kavita"))
    }

    @Test
    fun `brand logo wins over the legacy kavita source`() {
        // The app icon is the brand mark; kavita.svg is only kept so older checkouts still render.
        write("assigns/logo.svg", "<svg><!--logo--></svg>")
        write("assigns/kavita.svg", "<svg><!--kavita--></svg>")

        val foreground = resolveSource(root, ManifestEntry(id = "ic_launcher_foreground", kind = "launcher-foreground"))
        val legacy = resolveSource(root, ManifestEntry(id = "ic_launcher", kind = "launcher"))
        val round = resolveSource(root, ManifestEntry(id = "ic_launcher_round", kind = "launcher"))

        assertTrue("foreground should use the brand mark", foreground.svg.contains("logo"))
        assertTrue("legacy icon should use the brand mark", legacy.svg.contains("logo"))
        assertTrue("round icon should use the brand mark", round.svg.contains("logo"))
    }

    @Test
    fun `id specific source wins over the brand logo`() {
        write("assets/svg/ic_launcher_foreground.svg", "<svg><!--explicit--></svg>")
        write("assigns/logo.svg", "<svg><!--logo--></svg>")

        val source = resolveSource(root, ManifestEntry(id = "ic_launcher_foreground", kind = "launcher-foreground"))

        assertTrue(source.svg.contains("explicit"))
    }

    @Test
    fun `icons never pick up the brand logo`() {
        write("assigns/logo.svg", "<svg><!--logo--></svg>")

        assertNull(resolveSource(root, icon("ic_home")).file)
    }

    @Test
    fun `id specific source wins over the legacy launcher fallback`() {
        write("assets/svg/ic_launcher_foreground.svg", "<svg><!--brand--></svg>")
        write("assigns/kavita.svg", "<svg><!--kavita--></svg>")

        val source = resolveSource(root, ManifestEntry(id = "ic_launcher_foreground", kind = "launcher-foreground"))

        assertTrue(source.svg.contains("brand"))
    }

    @Test
    fun `icons never pick up the legacy launcher source`() {
        write("assigns/kavita.svg", "<svg><!--kavita--></svg>")

        val source = resolveSource(root, icon("ic_home"))

        assertNull(source.file)
    }

    @Test(expected = IllegalArgumentException::class)
    fun `empty svg file fails loudly`() {
        write("assets/svg/ic_sort.svg", "   \n")
        resolveSource(root, icon("ic_sort"))
    }

    @Test
    fun `utf8 bom is stripped`() {
        write("assets/svg/ic_edit.svg", "\uFEFF<svg><!--bom--></svg>")

        val source = resolveSource(root, icon("ic_edit"))

        assertTrue(source.svg.startsWith("<svg"))
    }
}
