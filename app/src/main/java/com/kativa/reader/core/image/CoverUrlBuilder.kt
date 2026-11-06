package com.kativa.reader.core.image

import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.designsystem.KativaColors
import com.kativa.reader.core.di.ApplicationScope
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.launch

/**
 * Builds cover/thumbnail URLs for the active server.
 *
 * `?apiKey=` is mandatory for media endpoints; the header alone does not authorise <img>-style
 * fetches on Kavita (AGENTS.md §4.2).
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
 * Process-wide privacy-mode flag, and the only place that decides its value.
 *
 * It owns the observation itself instead of relying on a ViewModel. That matters because privacy
 * mode has to be correct on *every* screen, including the ones pushed on top of home (series
 * detail, search, stream list): those never read a screen-level `privacyMode` field, they fall back
 * to [com.kativa.reader.core.designsystem.LocalPrivacyMode], which is fed from here.
 *
 * The previous implementation nested two `collect` calls inside a ViewModel:
 *
 *     activeServerStore.active.collect { server ->
 *         settingsRepository.isPrivacyMode(server.id).collect { ... }   // never returns
 *     }
 *
 * The inner flow never completes, so the outer collector never saw a second emission. At startup
 * the active server is still `null`, the flag resolved to `false`, and selecting a server was
 * never observed — leaving privacy mode permanently off. It only *looked* right on the home
 * screen, because home passes `coverUrl = null` explicitly; level 2-3 screens ask
 * [com.kativa.reader.core.designsystem.CoverImage] to use the composition default, which stayed
 * `false`. [combine] (rather than nesting) is what keeps the two sources in sync.
 */
@Singleton
class PrivacyModeState @Inject constructor(
    private val activeServerStore: ActiveServerStore,
    private val settings: PrivacySettings,
    /** The process-lifetime scope, so the observation outlives every screen. */
    @ApplicationScope private val scope: CoroutineScope,
) {
    private val _enabled = MutableStateFlow(false)
    val enabled: StateFlow<Boolean> = _enabled.asStateFlow()

    init {
        scope.launch {
            combine(
                activeServerStore.active,
                settings.privacyModeByServer,
            ) { server, stored ->
                val serverId = server?.id
                if (serverId == null) false else stored[serverId] == true
            }.collect { value -> _enabled.value = value }
        }
    }

    /**
     * Optimistic local update so the toggle feels instant; the DataStore write issued by the
     * caller is what makes it durable and re-emits through [enabled].
     */
    fun set(value: Boolean) {
        _enabled.value = value
    }

    /**
     * Single entry point for the UI privacy toggles.
     *
     * Updates the in-memory flag first (every already-composed list re-renders immediately) and
     * then persists it for the active server, which re-emits through [enabled] on confirmation.
     * Centralising this is what keeps the toggle icon, the composition local and the image layer
     * from disagreeing — the previous per-screen copies of this logic each had their own
     * subscription and could drift.
     */
    fun toggleForActiveServer(enabled: Boolean) {
        set(enabled)
        val serverId = activeServerStore.activeId ?: return
        scope.launch { settings.setPrivacyMode(serverId, enabled) }
    }
}

object PrivacyPlaceholderColor {
    val fill = KativaColors.PrivacyFill
}
