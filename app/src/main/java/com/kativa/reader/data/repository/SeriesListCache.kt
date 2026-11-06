package com.kativa.reader.data.repository

import com.kativa.reader.core.network.Outcome
import com.kativa.reader.domain.model.SeriesSummary
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Short-lived in-memory cache for series lists.
 *
 * Why: the home screen needs 4 covers per library just to draw folder tiles, and every library
 * grid re-fetches on rotation. Without a cache that is a redundant request each time.
 *
 * Design constraints (kept deliberately simple):
 *  - keyed by `serverId + request signature`, so switching servers cannot leak results;
 *  - bounded entry count, so a long browsing session cannot grow without limit;
 *  - **never** caches failures, so a transient error is always retryable;
 *  - TTL is short (reading progress changes server-side), and callers can force a refresh.
 */
@Singleton
class SeriesListCache @Inject constructor() {

    data class Entry(val items: List<SeriesSummary>, val storedAt: Long, val totalItems: Int = 0)

    private val mutex = Mutex()
    private val store = LinkedHashMap<String, Entry>()
    private val _version = MutableStateFlow(0L)

    /** Bumped whenever the cache is invalidated, so screens can react if desired. */
    val version: StateFlow<Long> = _version.asStateFlow()

    suspend fun get(key: String): List<SeriesSummary>? = getEntry(key)?.items

    /**
     * Full entry lookup, including the server-reported total.
     *
     * Folder tiles show a count, so a cache hit must restore the total as well — otherwise the
     * tile would silently drop back to the page size.
     */
    suspend fun getPage(key: String): SeriesPage? = getEntry(key)?.let { entry ->
        SeriesPage(items = entry.items, totalItems = entry.totalItems)
    }

    private suspend fun getEntry(key: String): Entry? = mutex.withLock {
        val entry = store[key] ?: return@withLock null
        if (System.currentTimeMillis() - entry.storedAt > TTL_MS) {
            store.remove(key)
            null
        } else {
            // Refresh LRU order.
            store.remove(key)
            store[key] = entry
            entry
        }
    }

    suspend fun put(key: String, items: List<SeriesSummary>) = putPage(key, SeriesPage(items, 0))

    suspend fun putPage(key: String, page: SeriesPage) = mutex.withLock {
        store.remove(key)
        store[key] = Entry(page.items, System.currentTimeMillis(), page.totalItems)
        while (store.size > MAX_ENTRIES) {
            val oldest = store.keys.firstOrNull() ?: break
            store.remove(oldest)
        }
    }

    /** Drops everything; used on sign-out / server switch. */
    suspend fun clear() = mutex.withLock {
        store.clear()
        _version.value = _version.value + 1
    }

    /** Drops entries belonging to one server (all keys are prefixed with it). */
    suspend fun clearServer(serverId: String) = mutex.withLock {
        store.keys.filter { it.startsWith("$serverId|") }.forEach { store.remove(it) }
        _version.value = _version.value + 1
    }

    /** Builds a stable key; callers must include every request parameter that changes results. */
    fun key(serverId: String?, vararg parts: Any?): String =
        buildString {
            append(serverId ?: "none")
            append('|')
            parts.forEach { append(it).append('|') }
        }

    companion object {
        /** Long enough to make back-navigation instant, short enough to stay truthful. */
        const val TTL_MS = 2 * 60 * 1000L
        const val MAX_ENTRIES = 40

        /** Marker type kept for readability at call sites that only cache successful reads. */
        fun isCacheable(outcome: Outcome<List<SeriesSummary>>): Boolean = outcome is Outcome.Success
    }
}
