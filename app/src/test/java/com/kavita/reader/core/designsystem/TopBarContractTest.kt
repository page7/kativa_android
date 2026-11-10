package com.kavita.reader.core.designsystem

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the top-bar contract (AGENTS.md §6).
 *
 * The light theme now paints the top bar with the brand mint instead of a white surface. Two
 * things can silently undo that and are invisible to compilation:
 *
 *  1. a screen going back to Material's `TopAppBar`, which would render a plain surface-coloured
 *     bar and quietly opt out of the brand band;
 *  2. content drawn on the mint band using the mint accent or the theme's grey `outline`, which
 *     disappears into the background (that is what [LocalOnBrandSurface] exists to prevent).
 */
class TopBarContractTest {

    private val moduleRoot = File("src/main").takeIf { it.isDirectory } ?: File("app/src/main")

    private fun sourcesUnder(relative: String): List<File> {
        val root = File(moduleRoot, relative)
        assertTrue("Expected sources at ${root.absolutePath}", root.isDirectory)
        return root.walkTopDown().filter { it.isFile && it.extension == "kt" }.toList()
    }

    @Test
    fun `screens use the design-system top bar`() {
        val offenders = sourcesUnder("java/com/kavita/reader/ui")
            .filter { file ->
                file.readLines().any { line ->
                    line.trimStart().startsWith("TopAppBar(") ||
                        line.contains("material3.TopAppBar\n") ||
                        line.trim() == "import androidx.compose.material3.TopAppBar"
                }
            }
            .map { it.name }

        assertTrue(
            "Screens must use KavitaTopAppBar (core/designsystem), not Material's TopAppBar: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `light theme top bar is painted with the brand container colour`() {
        val file = File(moduleRoot, "java/com/kavita/reader/core/designsystem/KavitaTopAppBar.kt")
        assertTrue("Expected ${file.absolutePath}", file.isFile)
        val text = file.readText().replace("\r\n", "\n")

        assertTrue(
            "KavitaTopAppBar must use the brand container colour in the light theme",
            text.contains("containerColor = scheme.primaryContainer"),
        )
        assertTrue(
            "KavitaTopAppBar must publish LocalOnBrandSurface so accents can switch to ink",
            text.contains("LocalOnBrandSurface provides"),
        )
        assertTrue(
            "KavitaTopAppBar must draw its content with onPrimaryContainer (white on mint fails AA)",
            text.contains("titleContentColor = scheme.onPrimaryContainer"),
        )
    }
}
