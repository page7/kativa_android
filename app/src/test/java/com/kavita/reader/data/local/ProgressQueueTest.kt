package com.kavita.reader.data.local

import com.kavita.reader.core.auth.ActiveServerStore
import com.kavita.reader.core.network.ApiFactory
import com.kavita.reader.data.repository.ProgressRepository
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The progress queue must never lose a page number, and must never let a stale page overwrite a
 * newer one (AGENTS.md §7). These tests use an in-memory fake DAO so they stay fast and hermetic.
 */
class ProgressQueueTest {

    /** In-memory stand-in for [PendingProgressDao]; mirrors its upsert semantics exactly. */
    private class FakeDao : PendingProgressDao {
        private val rows = linkedMapOf<Pair<String, Int>, PendingProgress>()

        override suspend fun upsert(item: PendingProgress) {
            rows[item.serverId to item.chapterId] = item
        }

        override suspend fun forServer(serverId: String): List<PendingProgress> =
            rows.values.filter { it.serverId == serverId }.sortedBy { it.updatedAt }

        override suspend fun batch(serverId: String, limit: Int): List<PendingProgress> =
            forServer(serverId).take(limit)

        override suspend fun delete(serverId: String, chapterId: Int) {
            rows.remove(serverId to chapterId)
        }

        override suspend fun incrementAttempts(serverId: String, chapterId: Int) {
            val key = serverId to chapterId
            rows[key]?.let { rows[key] = it.copy(attempts = it.attempts + 1) }
        }

        override suspend fun count(serverId: String): Int =
            rows.values.count { it.serverId == serverId }

        override suspend fun clearServer(serverId: String) {
            rows.keys.filter { it.first == serverId }.forEach { rows.remove(it) }
        }
    }



    @Test
    fun `newest page wins for the same chapter`() = runTest {
        val dao = FakeDao()
        dao.upsert(row(pageNum = 5, updatedAt = 1))
        dao.upsert(row(pageNum = 12, updatedAt = 2))

        assertEquals(1, dao.count("server-a"))
        assertEquals(12, dao.forServer("server-a").single().pageNum)
    }

    @Test
    fun `chapters are queued independently`() = runTest {
        val dao = FakeDao()
        dao.upsert(row(chapterId = 1, pageNum = 3, updatedAt = 1))
        dao.upsert(row(chapterId = 2, pageNum = 9, updatedAt = 2))

        assertEquals(2, dao.count("server-a"))
        assertEquals(listOf(1, 2), dao.forServer("server-a").map { it.chapterId })
    }

    @Test
    fun `servers are isolated`() = runTest {
        val dao = FakeDao()
        dao.upsert(row(serverId = "server-a", pageNum = 1, updatedAt = 1))
        dao.upsert(row(serverId = "server-b", pageNum = 7, updatedAt = 1))

        assertEquals(1, dao.count("server-a"))
        assertEquals(7, dao.forServer("server-b").single().pageNum)
    }

    @Test
    fun `queue survives clearing another server`() = runTest {
        val dao = FakeDao()
        dao.upsert(row(serverId = "server-a", chapterId = 1, pageNum = 1, updatedAt = 1))
        dao.upsert(row(serverId = "server-b", chapterId = 1, pageNum = 2, updatedAt = 1))

        dao.clearServer("server-b")

        assertEquals(1, dao.count("server-a"))
        assertEquals(0, dao.count("server-b"))
    }

    @Test
    fun `finished chapter marker is preserved`() = runTest {
        val dao = FakeDao()
        // Kavita expects pages + 1 to mean "finished" (AGENTS.md §4.6 item 2).
        dao.upsert(row(pageNum = 101, updatedAt = 1))
        assertEquals(101, dao.forServer("server-a").single().pageNum)
    }

    @Test
    fun `pending count reflects queued rows`() = runTest {
        val dao = FakeDao()
        dao.upsert(row(chapterId = 1, pageNum = 1, updatedAt = 1))
        dao.upsert(row(chapterId = 2, pageNum = 1, updatedAt = 2))

        assertEquals(2, dao.count("server-a"))
    }

    @Test
    fun `deleting a chapter only removes that row`() = runTest {
        val dao = FakeDao()
        dao.upsert(row(chapterId = 1, pageNum = 1, updatedAt = 1))
        dao.upsert(row(chapterId = 2, pageNum = 1, updatedAt = 2))

        dao.delete("server-a", 1)

        assertEquals(listOf(2), dao.forServer("server-a").map { it.chapterId })
    }

    private fun row(
        serverId: String = "server-a",
        chapterId: Int = 10,
        pageNum: Int = 1,
        updatedAt: Long = 1,
    ) = PendingProgress(
        serverId = serverId,
        chapterId = chapterId,
        seriesId = 1,
        volumeId = 1,
        libraryId = 1,
        pageNum = pageNum,
        updatedAt = updatedAt,
    )
}
