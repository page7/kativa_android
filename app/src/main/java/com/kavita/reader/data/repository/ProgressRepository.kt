package com.kavita.reader.data.repository

import com.kavita.reader.core.auth.ActiveServerStore
import com.kavita.reader.data.local.PendingProgress
import com.kavita.reader.data.local.PendingProgressDao
import com.kavita.reader.data.remote.api.ReaderApi
import com.kavita.reader.core.network.ApiFactory
import com.kavita.reader.core.network.KavitaError
import com.kavita.reader.data.remote.dto.SaveProgressRequest
import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock

/**
 * Progress persistence with a write-behind queue.
 *
 * Behaviour (AGENTS.md §7):
 *  - every save is written to Room first, then attempted on the wire;
 *  - a successful call removes the row;
 *  - offline/failed calls stay queued, one row per chapter (newest page wins);
 *  - `flush()` (called when the app resumes or the reader closes) retries them.
 */
@Singleton
class ProgressRepository @Inject constructor(
    private val apiFactory: ApiFactory,
    private val dao: PendingProgressDao,
    private val activeServerStore: ActiveServerStore,
) {
    private val flushMutex = Mutex()

    private val serverId: String? get() = activeServerStore.activeId

    /**
     * Queues and (best-effort) pushes one chapter's progress.
     *
     * @param pageNum 1-based. Use `totalPages + 1` to mark the chapter as finished.
     */
    suspend fun save(
        chapterId: Int,
        seriesId: Int,
        volumeId: Int,
        libraryId: Int,
        pageNum: Int,
        bookScrollId: String? = null,
    ) {
        val server = serverId ?: return
        val entry = PendingProgress(
            serverId = server,
            chapterId = chapterId,
            seriesId = seriesId,
            volumeId = volumeId,
            libraryId = libraryId,
            pageNum = pageNum,
            bookScrollId = bookScrollId,
            updatedAt = System.currentTimeMillis(),
        )
        dao.upsert(entry)

        // Best-effort immediate push; a failure is not an error the reader has to handle.
        runCatching {
            apiFactory.reader().saveProgress(
                SaveProgressRequest(
                    libraryId = libraryId,
                    seriesId = seriesId,
                    volumeId = volumeId,
                    chapterId = chapterId,
                    pageNum = pageNum,
                    bookScrollId = bookScrollId,
                ),
            )
            dao.delete(server, chapterId)
        }
    }

    /** Retries every queued row for the active server. Safe to call repeatedly. */
    suspend fun flush(): Int = flushMutex.withLock {
        val server = serverId ?: return@withLock 0
        var sent = 0
        val pending = dao.forServer(server)
        for (item in pending) {
            try {
                apiFactory.reader().saveProgress(
                    SaveProgressRequest(
                        libraryId = item.libraryId,
                        seriesId = item.seriesId,
                        volumeId = item.volumeId,
                        chapterId = item.chapterId,
                        pageNum = item.pageNum,
                        bookScrollId = item.bookScrollId,
                    ),
                )
                dao.delete(server, item.chapterId)
                sent++
            } catch (t: Throwable) {
                dao.incrementAttempts(server, item.chapterId)
                // A 4xx will never succeed by retrying; drop it so the queue cannot wedge.
                if (t is retrofit2.HttpException && t.code() in 400..499 && t.code() != 408) {
                    dao.delete(server, item.chapterId)
                }
            }
        }
        sent
    }

    suspend fun pendingCount(): Int = serverId?.let { dao.count(it) } ?: 0

    /** Reads the server value so the UI can restore the last rendered page. */
    suspend fun remoteProgress(chapterId: Int): com.kavita.reader.data.remote.dto.ProgressDto? =
        runCatching { apiFactory.reader().progress(chapterId) }.getOrNull()
}
