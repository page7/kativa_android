package com.kavita.reader.data.repository

import com.kavita.reader.domain.model.ContentFormat
import com.kavita.reader.domain.model.SeriesSummary
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The series cache exists to stop redundant fetches (home folder previews, rotation, back-nav).
 * The rules that matter: never leak across servers, never cache failures, and stay bounded.
 */
class SeriesListCacheTest {

    private fun series(id: Int, name: String = "S$id") = SeriesSummary(
        id = id,
        name = name,
        libraryId = 1,
        libraryName = "L",
        format = ContentFormat.Archive,
        pages = 10,
        pagesRead = 0,
    )

    @Test
    fun `stores and returns a value`() = runTest {
        val cache = SeriesListCache()
        val key = cache.key("server-a", "lib", 1, 1)
        cache.put(key, listOf(series(1)))

        assertEquals(1, cache.get(key)?.size)
        assertEquals("S1", cache.get(key)?.first()?.name)
    }

    @Test
    fun `different keys do not collide`() = runTest {
        val cache = SeriesListCache()
        val a = cache.key("server-a", "lib", 1, 1)
        val b = cache.key("server-a", "lib", 2, 1)
        cache.put(a, listOf(series(1)))
        cache.put(b, listOf(series(2), series(3)))

        assertEquals(1, cache.get(a)?.size)
        assertEquals(2, cache.get(b)?.size)
    }

    @Test
    fun `keys include the server so sessions cannot leak`() = runTest {
        val cache = SeriesListCache()
        val a = cache.key("server-a", "lib", 1)
        val b = cache.key("server-b", "lib", 1)
        cache.put(a, listOf(series(1)))

        assertNull(cache.get(b))
    }

    @Test
    fun `clearing one server leaves the other intact`() = runTest {
        val cache = SeriesListCache()
        val a = cache.key("server-a", "lib", 1)
        val b = cache.key("server-b", "lib", 1)
        cache.put(a, listOf(series(1)))
        cache.put(b, listOf(series(2)))

        cache.clearServer("server-a")

        assertNull(cache.get(a))
        assertEquals(1, cache.get(b)?.size)
    }

    @Test
    fun `filter parameters are part of the key`() = runTest {
        val cache = SeriesListCache()
        val unfiltered = cache.key("s", "lib", 1, 40, 1, 1, true, "")
        val filtered = cache.key("s", "lib", 1, 40, 1, 1, true, "17:5:Urasawa")

        assertTrue(unfiltered != filtered)
    }

    @Test
    fun `entry count stays bounded`() = runTest {
        val cache = SeriesListCache()
        repeat(SeriesListCache.MAX_ENTRIES + 10) { i ->
            cache.put(cache.key("s", "lib", i), listOf(series(i)))
        }
        // The oldest entries are evicted, the newest survive.
        assertNull(cache.get(cache.key("s", "lib", 0)))
        assertEquals(1, cache.get(cache.key("s", "lib", SeriesListCache.MAX_ENTRIES + 9))?.size)
    }

    @Test
    fun `clear empties everything`() = runTest {
        val cache = SeriesListCache()
        val key = cache.key("s", "lib", 1)
        cache.put(key, listOf(series(1)))
        cache.clear()
        assertNull(cache.get(key))
    }

    @Test
    fun `a miss returns null rather than throwing`() = runTest {
        val cache = SeriesListCache()
        assertNull(cache.get(cache.key("s", "never-cached")))
    }
}
