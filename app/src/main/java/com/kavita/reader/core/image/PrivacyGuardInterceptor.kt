package com.kavita.reader.core.image

import coil3.intercept.Interceptor
import coil3.request.ErrorResult
import coil3.request.ImageResult

/**
 * Last line of defence for privacy mode: refuses cover requests at the image-loader level.
 *
 * The primary guard is [com.kavita.reader.core.designsystem.CoverImage], which does not even build
 * an `ImageRequest` when privacy mode is on. This interceptor exists because that guard is easy to
 * bypass by accident — a new screen that uses `AsyncImage` directly, a cached painter restored by
 * Compose, or a preview composable all reach Coil without going through `CoverImage`. AGENTS.md §6
 * promises "not masked after download, but never requested at all"; a promise you cannot violate
 * by forgetting a parameter is a real invariant.
 *
 * Reader pages are deliberately *not* blocked: privacy mode covers covers, thumbnails and search
 * results only, and hiding the page the user is actively reading would make the reader unusable.
 */
class PrivacyGuardInterceptor(
    private val isPrivacyMode: () -> Boolean,
) : Interceptor {

    override suspend fun intercept(chain: Interceptor.Chain): ImageResult {
        val request = chain.request
        val data = request.data?.toString().orEmpty()
        if (isPrivacyMode() && data.isCoverRequest()) {
            // Coil turns an ErrorResult into the request's `error` painter, so the UI shows its
            // fallback instead of a broken-image placeholder.
            return ErrorResult(
                image = null,
                request = request,
                throwable = PrivacyBlockedException(data),
            )
        }
        return chain.proceed()
    }

    /**
     * Kavita's media endpoints, split into the ones privacy mode hides and the ones it must leave
     * alone. Matching on the path keeps this independent of host, port and query-string order.
     */
    internal fun String.isCoverRequest(): Boolean {
        val path = substringBefore('?')
        return COVER_PATHS.any { path.contains(it) }
    }

    companion object {
        /** Exposed for tests so the "never block reader pages" rule is pinned, not assumed. */
        internal val COVER_PATHS = listOf(
            "/Image/series-cover",
            "/Image/volume-cover",
            "/Image/chapter-cover",
            "/Image/library-cover",
            "/Image/collection-cover",
            "/Image/readinglist-cover",
            "/Image/person-cover",
            // Bookmark thumbnails are "cover-like": a spoiler-y page preview, and
            // docs/FEATURES.md F-17 promises they stay masked in privacy mode.
            "/Image/bookmark",
            "/Reader/thumbnail",
        )
    }
}

/** Signals "privacy mode blocked this fetch". Never shown to the user; it is not an error state. */
class PrivacyBlockedException(url: String) :
    IllegalStateException("Blocked cover request in privacy mode: ${url.substringBefore('?')}")
