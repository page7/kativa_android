package com.kavita.reader.ui.navigation

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Reader routes gained an optional, **0-based** page index so a bookmark can open the exact page
 * that was saved instead of the user's saved progress.
 *
 * Two invariants matter:
 *  - the page is optional, so plain reader links keep working (and stay backward compatible with
 *    the existing deep link `reader/{chapterId}`);
 *  - page 0 is a *valid* target (first page), so it must not be mistaken for "no target".
 */
class ReaderRouteTest {

    @Test
    fun plainChapterRouteHasNoPageArgument() {
        val route = Routes.reader(42)

        assertEquals("${Routes.READER}/42", route)
        assertFalse(route.contains(Routes.ARG_PAGE))
    }

    @Test
    fun bookmarkTargetCarriesTheZeroBasedIndex() {
        val route = Routes.reader(chapterId = 42, pageIndex = 0)

        assertTrue("page 0 is a real target: $route", route.contains("${Routes.ARG_PAGE}=0"))
    }

    @Test
    fun pageIndexIsAppendedAsAQueryParameter() {
        val route = Routes.reader(chapterId = 42, pageIndex = 17)

        assertEquals("${Routes.READER}/42?${Routes.ARG_PAGE}=17", route)
    }

    @Test
    fun missingOrNegativePageFallsBackToResume() {
        assertEquals("${Routes.READER}/42", Routes.reader(42, pageIndex = null))
        assertEquals("${Routes.READER}/42", Routes.reader(42, pageIndex = -1))
    }

    @Test
    fun bookmarksRouteIsDistinctFromTheShelfRoutes() {
        assertFalse(Routes.BOOKMARKS == Routes.SHELF)
        assertTrue(Routes.BOOKMARKS.isNotBlank())
    }
}
