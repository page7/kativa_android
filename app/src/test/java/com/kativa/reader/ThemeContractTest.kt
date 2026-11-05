package com.kativa.reader

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * Runtime-contract guard that compiles fine but crashes on launch when violated.
 *
 * The M0 APK shipped with `MainActivity : AppCompatActivity` and a theme whose parent was the
 * platform `android:Theme.Material.NoActionBar`. Every compile and unit test passed; the app
 * crashed immediately with:
 *
 *   IllegalStateException: You need to use a Theme.AppCompat theme (or descendant)
 *     at AppCompatDelegateImpl.createSubDecor
 *
 * This test reads the real source files, so the mistake cannot ship again. See AGENTS.md §8.1.
 */
class ThemeContractTest {

    private val moduleRoot = File("src/main").takeIf { it.isDirectory } ?: File("app/src/main")

    private fun read(relative: String): String {
        val file = File(moduleRoot, relative)
        assertTrue("Expected file at ${file.absolutePath}", file.isFile)
        return file.readText()
    }

    @Test
    fun `activity theme must descend from an AppCompat theme`() {
        val manifest = read("AndroidManifest.xml")
        val themes = read("res/values/themes.xml")

        val themeName = Regex("""android:theme="@style/([A-Za-z0-9_.]+)"""")
            .find(manifest)?.groupValues?.get(1)
            ?: fail("AndroidManifest declares no android:theme").let { return }

        val parent = Regex(
            """<style name="${Regex.escape(themeName)}"[^>]*parent="([^"]+)"""",
        ).find(themes)?.groupValues?.get(1)
            ?: fail("Theme '$themeName' is not declared in res/values/themes.xml").let { return }

        val appCompatDescendant = parent.startsWith("Theme.AppCompat") ||
            parent.startsWith("Theme.MaterialComponents") ||
            parent.startsWith("Theme.Material3")

        assertTrue(
            buildString {
                append("Theme '$themeName' has parent '$parent', which is NOT an AppCompat theme.\n")
                append("MainActivity extends AppCompatActivity, so the app will crash on launch with:\n")
                append("  IllegalStateException: You need to use a Theme.AppCompat theme (or descendant)\n")
                append("Fix: parent=\"Theme.AppCompat.DayNight.NoActionBar\". See AGENTS.md §8.1.")
            },
            appCompatDescendant,
        )
    }

    @Test
    fun `every declared activity is exported or explicitly internal`() {
        val manifest = read("AndroidManifest.xml")
        val activities = Regex("""<activity\b[^>]*>""").findAll(manifest).map { it.value }.toList()
        assertTrue("expected at least one <activity>", activities.isNotEmpty())

        activities.forEach { tag ->
            assertTrue(
                "Every <activity> must declare android:exported explicitly (required since API 31): $tag",
                tag.contains("android:exported="),
            )
        }
    }

    @Test
    fun `application class is declared`() {
        val manifest = read("AndroidManifest.xml")
        assertTrue(
            "Application must declare android:name so Hilt (@HiltAndroidApp) can bootstrap",
            manifest.contains("android:name=\".KativaApp\""),
        )
    }

    /**
     * Format strings with positional args must not mix argument types: an `%1$d` fed a String
     * throws `IllegalFormatConversionException` at runtime, which unit tests never see.
     */
    @Test
    fun `format strings use consistent placeholder types`() {
        val self = File(moduleRoot, "res/values/strings.xml")
        assertTrue(self.isFile)
        val text = self.readText()

        val problems = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(text)
            .mapNotNull { match ->
                val name = match.groupValues[1]
                val value = match.groupValues[2]
                val typed = Regex("""%(\d+)\$([sd])""").findAll(value).toList()
                if (typed.isEmpty()) return@mapNotNull null
                val byIndex = typed.groupBy { it.groupValues[1] }
                val conflicts = byIndex.filter { (_, uses) -> uses.map { it.groupValues[2] }.distinct().size > 1 }
                if (conflicts.isEmpty()) null else "$name: conflicting placeholders ${conflicts.keys}"
            }
            .toList()

        assertTrue("Conflicting format placeholders: $problems", problems.isEmpty())
    }
}
