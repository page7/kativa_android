package com.kavita.reader.core.designsystem

import java.io.File
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Guards the pull-to-refresh contract on every list screen.
 *
 * Pull-to-refresh fails in ways that compile cleanly and look fine in a screenshot:
 *  - the `PullToRefreshBox` wrapper is added but wired to a plain `load()`, so the pull raises the
 *    full-screen spinner and blanks the content it was supposed to refresh in place;
 *  - a screen keeps its old `Box` root, so the `isRefreshing` state is never observed;
 *  - the empty/error states stay outside the refreshable container, which is exactly where a user
 *    reaches for the gesture.
 *
 * None of that is visible to a JVM unit test nor to `assembleDebug`, so — like
 * [ThemeContractTest] — the contract is pinned against the real sources.
 */
class PullToRefreshContractTest {

    private val moduleRoot = File("src/main").takeIf { it.isDirectory } ?: File("app/src/main")

    private fun read(relative: String): String {
        val file = File(moduleRoot, relative)
        assertTrue("Expected file at ${file.absolutePath}", file.isFile)
        return file.readText().replace("\r\n", "\n")
    }

    /** Every list screen that must be refreshable, with the ViewModel that owns its state. */
    private val refreshableScreens = listOf(
        "ui/home/Home" to "ui/home/HomeViewModel",
        "ui/library/Library" to "ui/library/LibraryViewModel",
        "ui/library/LibraryGrid" to "ui/library/LibraryGridViewModel",
        "ui/stream/Stream" to "ui/stream/StreamViewModel",
        "ui/shelf/Shelf" to "ui/shelf/ShelfViewModel",
        "ui/bookmarks/Bookmarks" to "ui/bookmarks/BookmarksViewModel",
        "ui/series/SeriesDetail" to "ui/series/SeriesDetailViewModel",
        "ui/search/Search" to "ui/search/SearchViewModel",
    )

    @Test
    fun `every list screen renders inside the shared refresh container`() {
        refreshableScreens.forEach { (screen, _) ->
            val source = read("java/com/kavita/reader/${screen}Screen.kt")
            assertTrue(
                "$screen must use KavitaPullToRefreshBox — a screen that only has its own Box can " +
                    "never show the refresh indicator.",
                source.contains("KavitaPullToRefreshBox("),
            )
            assertTrue(
                "$screen must bind the container to its own `refreshing` flag, or the gesture " +
                    "runs while the indicator stays at rest.",
                source.contains("refreshing = state.refreshing"),
            )
            assertTrue(
                "$screen must bind onRefresh to the ViewModel's refresh(), not to a plain load(): " +
                    "load() blanks the content instead of refreshing in place.",
                Regex("""onRefresh = viewModel::refresh""").containsMatchIn(source),
            )
        }
    }

    @Test
    fun `every refreshable ViewModel exposes a refresh entry point and a refreshing flag`() {
        refreshableScreens.forEach { (_, viewModel) ->
            val source = read("java/com/kavita/reader/${viewModel}.kt")
            assertTrue(
                "$viewModel must carry a `refreshing` flag in its UiState",
                source.contains("val refreshing: Boolean"),
            )
            assertTrue(
                "$viewModel must expose fun refresh()",
                Regex("""fun refresh\(\)""").containsMatchIn(source),
            )
            assertTrue(
                "$viewModel.refresh() must clear the flag on completion, or the indicator spins " +
                    "forever after the first pull.",
                source.contains("refreshing = false"),
            )
        }
    }

    @Test
    fun `static states are wrapped so they can still be pulled`() {
        // Empty and error screens contain no scrollable child, so without RefreshablePlaceholder
        // the gesture has nothing to attach to and "pull to retry" silently does nothing.
        refreshableScreens.forEach { (screen, _) ->
            val source = read("java/com/kavita/reader/${screen}Screen.kt")
            val hasStaticStates = source.contains("EmptyState(") || source.contains("KavitaErrorState(")
            if (!hasStaticStates) return@forEach
            assertTrue(
                "$screen renders an empty/error state outside RefreshablePlaceholder, so the pull " +
                    "gesture cannot fire exactly where the user wants to retry.",
                source.contains("RefreshablePlaceholder"),
            )
        }
    }

    @Test
    fun `the refresh indicator uses the brand tokens`() {
        val source = read("java/com/kavita/reader/core/designsystem/Refreshable.kt")
        assertTrue(
            "The indicator must use the brand mint, not the M3 default (AGENTS.md §6)",
            source.contains("color = KavitaColors.BrandPrimary"),
        )
        assertTrue(
            "The indicator must sit on the surface colour so it does not punch a hole in the page",
            source.contains("containerColor = MaterialTheme.colorScheme.surface"),
        )
        assertTrue(
            "A pull gesture is unreachable for TalkBack; the container needs an explicit action",
            source.contains("CustomAccessibilityAction"),
        )
    }

    @Test
    fun `copy confirmation uses a toast, not a snackbar`() {
        val source = read("java/com/kavita/reader/ui/series/SeriesDetailScreen.kt")
        assertTrue(
            "The copy confirmation must be a Toast (no action needed, must not occupy layout)",
            source.contains("showToast(copiedMessage)"),
        )
        assertTrue(
            "Showing the copy confirmation through the snackbar host reintroduces the heavy, " +
                "content-covering banner this replaced.",
            !source.contains("snackbarHostState.showSnackbar(copiedMessage)"),
        )
    }
}
