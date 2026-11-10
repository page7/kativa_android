package com.kavita.reader.core.designsystem

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the theme contract for the three things that were wrong and are invisible to tests:
 *
 *  1. the splash backdrop was hard-coded dark, so the light theme opened on a near-black screen;
 *  2. filled surfaces used the *deep* green (`#2D7A56`), which reads as "darkened green" instead
 *     of the brand mint;
 *  3. the privacy placeholder used one dark grey for both themes.
 *
 * All three are colour decisions in resources / composables, so compilation cannot catch a
 * regression. This is the same source-pinning approach as [TopBarContractTest].
 */
class ThemeParityContractTest {

    private val moduleRoot = File("src/main").takeIf { it.isDirectory } ?: File("app/src/main")

    private fun read(relative: String): String {
        val file = File(moduleRoot, relative)
        assertTrue("Expected file at ${file.absolutePath}", file.isFile)
        return file.readText().replace("\r\n", "\n")
    }

    @Test
    fun `splash window background follows the night qualifier`() {
        val light = read("res/values/colors.xml")
        val dark = read("res/values-night/colors.xml")

        assertTrue(
            "values/colors.xml must define a *light* kavita_window_background (the splash frame " +
                "before Compose draws follows resource qualifiers, not runtime colour changes)",
            Regex("""<color name="kavita_window_background">#FF(F|E)[0-9A-F]{5}</color>""").containsMatchIn(light),
        )
        assertTrue(
            "values-night/colors.xml must define the dark kavita_window_background",
            dark.contains("""<color name="kavita_window_background">#FF0B0B0F</color>"""),
        )
    }

    @Test
    fun `theme is DayNight so the system splash can pick a backdrop`() {
        listOf("res/values/themes.xml", "res/values-v27/themes.xml").forEach { path ->
            val themes = read(path)
            assertTrue(
                "$path must descend from Theme.AppCompat.DayNight.* so values-night is honoured " +
                    "even when the device is in light mode",
                themes.contains("parent=\"Theme.AppCompat.DayNight.NoActionBar\""),
            )
        }
    }

    @Test
    fun `splash and welcome screens use the theme background`() {
        listOf(
            "java/com/kavita/reader/ui/splash/SplashScreen.kt",
            "java/com/kavita/reader/ui/onboarding/WelcomeScreen.kt",
        ).forEach { path ->
            val source = read(path)
            assertTrue(
                "$path must paint MaterialTheme.colorScheme.background, not a fixed dark token",
                source.contains("background(MaterialTheme.colorScheme.background)"),
            )
            assertTrue(
                "$path must not hard-code KavitaColors.Background as its backdrop",
                !source.contains("background(KavitaColors.Background)"),
            )
        }
    }

    @Test
    fun `primary button uses the brand mint with on-brand ink`() {
        val components = read("java/com/kavita/reader/core/designsystem/Components.kt")
        val start = components.indexOf("fun KavitaPrimaryButton(")
        assertTrue("Expected KavitaPrimaryButton in Components.kt", start >= 0)
        val body = components.substring(start, components.indexOf("\n}", start))

        assertTrue(
            "KavitaPrimaryButton must fill with KavitaColors.BrandPrimary (the mint), not a deepened green",
            body.contains("containerColor = KavitaColors.BrandPrimary"),
        )
        assertTrue(
            "KavitaPrimaryButton content must use OnBrand (white on mint fails AA)",
            body.contains("contentColor = KavitaColors.OnBrand"),
        )
        assertTrue(
            "KavitaPrimaryButton must not fill with BrandPrimaryDeep",
            !body.contains("containerColor = KavitaColors.BrandPrimaryDeep"),
        )
    }

    @Test
    fun `selected segment uses the brand mint with on-brand ink`() {
        val segmented = read("java/com/kavita/reader/core/designsystem/SegmentedChoice.kt")
        assertTrue(
            "Selected segment must fill with BrandPrimary",
            segmented.contains("if (isSelected) KavitaColors.BrandPrimary else Color.Transparent"),
        )
        assertTrue(
            "Selected segment label must use OnBrand, not White",
            segmented.contains("if (isSelected) KavitaColors.OnBrand"),
        )
    }

    @Test
    fun `switches go through the design-system wrapper`() {
        val uiRoot = File(moduleRoot, "java/com/kavita/reader/ui")
        assertTrue("Expected UI sources at ${uiRoot.absolutePath}", uiRoot.isDirectory)

        val offenders = uiRoot.walkTopDown()
            .filter { it.isFile && it.extension == "kt" }
            .filter { file ->
                file.readLines().any { it.trimStart().startsWith("Switch(") }
            }
            .map { it.name }
            .toList()

        assertTrue(
            "Screens must use KavitaSwitch (core/designsystem) so the track follows the brand " +
                "mint instead of Material's dark `primary`: $offenders",
            offenders.isEmpty(),
        )
    }

    @Test
    fun `privacy placeholder fill follows the active theme`() {
        val components = read("java/com/kavita/reader/core/designsystem/Components.kt")
        val start = components.indexOf("fun PrivacyCoverPlaceholder(")
        assertTrue("Expected PrivacyCoverPlaceholder", start >= 0)
        val body = components.substring(start, components.indexOf("\n}", start))

        assertTrue(
            "PrivacyCoverPlaceholder must pick PrivacyFillLight in the light theme",
            body.contains("KavitaColors.PrivacyFillLight"),
        )
        assertTrue(
            "PrivacyCoverPlaceholder must pick PrivacyFill in the dark theme",
            body.contains("KavitaColors.PrivacyFill") && body.contains("LocalDarkTheme"),
        )
    }

    @Test
    fun `privacy fill colours differ per theme and the light one is actually light`() {
        val tokens = read("java/com/kavita/reader/core/designsystem/Tokens.kt")
        assertTrue(
            "Dark privacy fill must stay #3A3A3A",
            tokens.contains("PrivacyFill = Color(0xFF3A3A3A)"),
        )
        assertTrue(
            "Light privacy fill must be a light grey, not the dark one",
            Regex("""PrivacyFillLight = Color\(0xFF(E|F)[0-9A-F]{5}\)""").containsMatchIn(tokens),
        )
    }
}
