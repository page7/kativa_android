package com.kativa.tools.assets

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test

/**
 * Keeps `assets/manifest.json` honest about which entries are actually delivered.
 *
 * `status = final` is the doc-level claim "this entry is not placeholder artwork" (docs/ASSETS.md §2).
 * Without this guard the flag could be flipped by hand while the SVG was never dropped in, and the
 * only symptom would be a placeholder PNG that still looks plausible at 24dp.
 */
class DeliveredIconsTest {

    /** `tools/assets` has no access to the repo root through Gradle, so walk up until we find it. */
    private fun repoRoot(): File {
        var dir: File? = File(System.getProperty("user.dir")).absoluteFile
        while (dir != null) {
            if (File(dir, "assets/manifest.json").isFile) return dir
            dir = dir.parentFile
        }
        error("assets/manifest.json not found above ${System.getProperty("user.dir")}")
    }

    private fun manifest(root: File): AssetManifest =
        ManifestJson.parse(File(root, "assets/manifest.json").readText())

    @Test
    fun `every entry marked final has a delivered svg`() {
        val root = repoRoot()
        val delivered = manifest(root).entries.filter { it.status == "final" }
        assumeTrue("no entry marked final yet", delivered.isNotEmpty())

        val missing = delivered
            .filter { !File(root, "assets/svg/${it.id}.svg").isFile }
            .map { it.id }

        assertTrue(
            "These entries claim status=final but have no assets/svg/<id>.svg, so they still render " +
                "the generated placeholder: $missing",
            missing.isEmpty(),
        )
    }

    @Test
    fun `delivered icons follow the tint rules from docs ASSETS 1_1`() {
        val root = repoRoot()
        val icons = manifest(root).entries
            .filter { it.kind == "icon" && it.status == "final" }
            .filter { File(root, "assets/svg/${it.id}.svg").isFile }

        val problems = icons.mapNotNull { entry ->
            val svg = File(root, "assets/svg/${entry.id}.svg").readText()
            when {
                // Icons are tinted with a SrcIn blend at runtime, so any baked-in colour is dead
                // weight; more importantly `currentColor` does not resolve in jsvg and renders black
                // or nothing depending on the element.
                svg.contains("currentColor") -> "${entry.id}: still uses currentColor"
                svg.contains("stroke-width=\"2\"") -> "${entry.id}: stroke-width is still 2 (spec is 1.5)"
                else -> null
            }
        }

        assertTrue("Icon sources are not normalised: $problems", problems.isEmpty())
    }
}
