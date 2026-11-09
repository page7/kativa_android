package com.kativa.reader.core.designsystem

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the poster-card veil across themes.
 *
 * Every list (home streams, library grid, search, shelf, bookmarks, stream) renders through
 * [SeriesPosterCard], which fades the bottom of the cover so the title stays readable. The fade
 * was hard-coded black + white ink, which is right on a dark theme but paints a grey smudge
 * under black-on-white cards in light mode. The light theme must fade to *white* and flip the
 * title to black instead.
 *
 * Colour decisions are invisible to compilation, so this pins the source the same way
 * [ThemeParityContractTest] does.
 */
class PosterScrimContractTest {

    private val moduleRoot = File("src/main").takeIf { it.isDirectory } ?: File("app/src/main")

    private fun read(relative: String): String {
        val file = File(moduleRoot, relative)
        assertTrue("Expected file at ${file.absolutePath}", file.isFile)
        return file.readText().replace("\r\n", "\n")
    }

    @Test
    fun `poster scrim and ink follow the active theme`() {
        val source = read("java/com/kativa/reader/core/designsystem/SeriesPosterCard.kt")

        assertTrue(
            "SeriesPosterCard must read LocalDarkTheme so the veil can differ per theme",
            source.contains("LocalDarkTheme.current"),
        )
        assertTrue(
            "SeriesPosterCard must pick PosterScrimDark/PosterScrimLight by theme, not one " +
                "hard-coded colour for both",
            source.contains(
                "if (dark) KativaColors.PosterScrimDark else KativaColors.PosterScrimLight",
            ),
        )
        assertTrue(
            "Light theme title must be black (white ink on a white veil is invisible)",
            source.contains("if (dark) Color.White else Color.Black"),
        )
        assertTrue(
            "The gradient must consume the themed scrim token, not a hard-coded colour",
            source.contains("listOf(Color.Transparent, scrim)"),
        )
        assertTrue(
            "The gradient must not be pinned to black for both themes",
            !source.contains("listOf(Color.Transparent, Color.Black.copy(alpha = 0.72f))"),
        )
    }

    @Test
    fun `scrim tokens are opposite polarities and stay opaque enough for text`() {
        val tokens = read("java/com/kativa/reader/core/designsystem/Tokens.kt")

        val dark = Regex("""PosterScrimDark = Color\(0x([0-9A-F]{2})000000\)""")
            .find(tokens)?.groupValues?.get(1)
        assertTrue(
            "PosterScrimDark must be a black-based colour (alpha byte then 000000), got '$dark'",
            dark != null && dark.toInt(16) >= 0xB8,
        )

        val light = Regex("""PosterScrimLight = Color\(0x([0-9A-F]{2})FFFFFF\)""")
            .find(tokens)?.groupValues?.get(1)
        assertTrue(
            "PosterScrimLight must be a white-based colour opaque enough for black text " +
                "(alpha byte ≥ 0xB8 ≈ 72%), got '$light'",
            light != null && light.toInt(16) >= 0xB8,
        )
    }
}
