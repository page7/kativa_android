package com.kavita.reader

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
            manifest.contains("android:name=\".KavitaApp\""),
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

    /**
     * A parameterised string must be called WITH its arguments.
     *
     * `stringResource(R.string.search_min_length)` compiles fine but renders the literal text
     * `Type at least %1$d characters` — no crash, no lint error, just broken UI. Two of these
     * shipped (search empty title and minimum length), and only a human looking at the screen
     * found them. This scans the Kotlin sources for `stringResource(...)` calls that reference a
     * placeholder-bearing key without passing any argument.
     */
    @Test
    fun `parameterised strings are never rendered without their arguments`() {
        val stringsXml = File(moduleRoot, "res/values/strings.xml").readText()
        val parameterised = Regex("""<string name="([^"]+)"[^>]*>(.*?)</string>""", RegexOption.DOT_MATCHES_ALL)
            .findAll(stringsXml)
            .filter { it.groupValues[2].contains("%") }
            .map { it.groupValues[1] }
            .toSet()

        assertTrue("No parameterised strings found — parser is broken", parameterised.isNotEmpty())

        val sourceRoot = File(moduleRoot, "java")
        assertTrue("Expected Kotlin sources at ${sourceRoot.absolutePath}", sourceRoot.isDirectory)

        val problems = mutableListOf<String>()
        sourceRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .forEach { file ->
                val text = file.readText()
                Regex("""stringResource\(\s*R\.string\.([A-Za-z0-9_]+)\s*\)""")
                    .findAll(text)
                    .forEach { match ->
                        val key = match.groupValues[1]
                        if (key in parameterised) {
                            val line = text.take(match.range.first).count { it == '\n' } + 1
                            problems += "${file.name}:$line uses R.string.$key without arguments"
                        }
                    }
            }

        assertTrue(
            buildString {
                append("These strings contain format placeholders but are rendered without arguments,\n")
                append("so the raw %1\$s / %1\$d will appear on screen:\n")
                problems.forEach { append("  - ").append(it).append('\n') }
            },
            problems.isEmpty(),
        )
    }
}
