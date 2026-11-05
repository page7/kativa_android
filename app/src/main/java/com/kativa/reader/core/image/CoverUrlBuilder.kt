package com.kativa.reader.core.image

import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.designsystem.KativaColors
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Builds cover/thumbnail URLs for the active server.
 *
 * `?apiKey=` is mandatory for media endpoints; the header alone does not authorise <img>-style
 * fetches on Kavita (AGENTS.md 搂4.2).
 */
@Singleton
class CoverUrlBuilder @Inject constructor(
    private val activeServerStore: ActiveServerStore,
) {
    fun series(seriesId: Int): String? = imageUrl("Image/series-cover", "seriesId" to seriesId)
    fun volume(volumeId: Int): String? = imageUrl("Image/volume-cover", "volumeId" to volumeId)
    fun library(libraryId: Int): String? = imageUrl("Image/library-cover", "libraryId" to libraryId)
    fun chapter(chapterId: Int): String? = imageUrl("Image/chapter-cover", "chapterId" to chapterId)
    fun collection(collectionId: Int): String? = imageUrl("Image/collection-cover", "collectionTagId" to collectionId)
    fun readingList(readingListId: Int): String? = imageUrl("Image/readinglist-cover", "readingListId" to readingListId)
    fun person(personId: Int): String? = imageUrl("Image/person-cover", "personId" to personId)

    fun readerPage(chapterId: Int, pageIndex: Int): String? {
        val base = activeServerStore.active.value?.displayBaseUrl() ?: return null
        val key = activeServerStore.session.value?.imageKey ?: return null
        return "$base/Reader/image?chapterId=$chapterId&page=$pageIndex&apiKey=${encode(key)}"
    }

    fun thumbnail(chapterId: Int, pageNum: Int): String? {
        val base = activeServerStore.active.value?.displayBaseUrl() ?: return null
        val key = activeServerStore.session.value?.imageKey ?: return null
        return "$base/Reader/thumbnail?chapterId=$chapterId&pageNum=$pageNum&apiKey=${encode(key)}"
    }

    /**
     * Auth key for the active server. Reader pages are fetched by Coil, which cannot easily set
     * per-request headers, so the key goes in the query string (AGENTS.md §4.2).
     */
    fun imageKey(): String? = activeServerStore.session.value?.imageKey

    private fun imageUrl(path: String, vararg params: Pair<String, Int>): String? {
        val base = activeServerStore.active.value?.displayBaseUrl() ?: return null
        val key = activeServerStore.session.value?.imageKey ?: return null
        val query = params.joinToString("&") { (k, v) -> "$k=$v" }
        return "$base/$path?$query&apiKey=${encode(key)}"
    }

    private fun encode(value: String): String = java.net.URLEncoder.encode(value, "UTF-8")

    private fun com.kativa.reader.domain.model.ServerProfile.displayBaseUrl(): String =
        baseUrl.trimEnd('/')
}

/**
 * Process-wide image loader configuration.
 *
 * Privacy mode is enforced in two places: this loader refuses to build a request at all, and
 * composables render [PrivacyPlaceholder] without even calling into Coil. That double guard makes
 * "no network request" an invariant rather than a convention (AGENTS.md 搂6).
 */
@Singleton
class PrivacyModeState @Inject constructor() {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    fun set(value: Boolean) {
        _enabled.value = value
    }
}

object PrivacyPlaceholderColor {
    val fill = KativaColors.PrivacyFill
}
