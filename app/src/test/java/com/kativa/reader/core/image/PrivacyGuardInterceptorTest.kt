package com.kativa.reader.core.image

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Privacy mode is an invariant, not a convention (AGENTS.md §6).
 *
 * The bug this pins down: privacy mode worked on home but not on any screen pushed above it. Home
 * passed `coverUrl = null` explicitly, so it *looked* right; series detail, search and stream lists
 * relied on `CoverImage`'s default and kept requesting covers. Two independent mistakes combined:
 * the flag itself was never observed after login (nested `collect` in a ViewModel), and covers were
 * gated in two different places instead of one.
 */
class PrivacyGuardInterceptorTest {

    private val interceptor = PrivacyGuardInterceptor { true }

    @Test
    fun `series cover is blocked in privacy mode`() {
        assertTrue(
            interceptor.run {
                "http://10.0.0.5:5500/api/Image/series-cover?seriesId=7&apiKey=k"
                    .isCoverRequest()
            },
        )
    }

    @Test
    fun `every cover endpoint is blocked`() {
        val endpoints = listOf(
            "/api/Image/series-cover",
            "/api/Image/volume-cover",
            "/api/Image/chapter-cover",
            "/api/Image/library-cover",
            "/api/Image/collection-cover",
            "/api/Image/readinglist-cover",
            "/api/Image/person-cover",
            "/api/Reader/thumbnail",
        )
        endpoints.forEach { path ->
            assertTrue(
                "privacy mode must block $path",
                interceptor.run { "https://host$path?apiKey=k".isCoverRequest() },
            )
        }
    }

    /**
     * The reader shows the content the user is deliberately reading. Blocking it would make the
     * app unusable, and privacy mode is defined as covers/thumbnails/search results only.
     */
    @Test
    fun `reader pages are never blocked`() {
        val readerUrls = listOf(
            "http://host/api/Reader/image?chapterId=3&page=0&apiKey=k",
            "http://host/api/Reader/chapter-info?chapterId=3",
            "http://host/api/Reader/get-progress?chapterId=3",
        )
        readerUrls.forEach { url ->
            assertFalse(
                "privacy mode must NOT block $url",
                interceptor.run { url.isCoverRequest() },
            )
        }
    }

    /**
     * `Reader/thumbnail` and `Reader/image` share a prefix. A naive `startsWith("/Reader/")` guard
     * would swallow reader pages; matching whole paths is what keeps them apart.
     */
    @Test
    fun `thumbnail and image are distinguished`() {
        assertTrue(
            interceptor.run { "http://host/api/Reader/thumbnail?chapterId=3&pageNum=1".isCoverRequest() },
        )
        assertFalse(
            interceptor.run { "http://host/api/Reader/image?chapterId=3&page=1".isCoverRequest() },
        )
    }

    @Test
    fun `bookmark thumbnails are blocked in privacy mode`() {
        // Bookmark cards preview the *page*, which is exactly as revealing as a cover.
        assertTrue(
            interceptor.run {
                "http://host/api/Image/bookmark?chapterId=3&pageNum=4&imageOffset=0&apiKey=k"
                    .isCoverRequest()
            },
        )
    }

    @Test
    fun `authorisation is irrelevant - a bare url is still a cover request`() {
        // The path alone decides; privacy mode must not depend on a key being present.
        assertTrue(interceptor.run { "https://host/api/Image/series-cover".isCoverRequest() })
    }
}
