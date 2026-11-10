package com.kavita.reader.data.local

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase

/**
 * Offline-safe progress queue.
 *
 * One row per (server, chapter): the newest write always wins, so a flaky connection cannot
 * replay stale page numbers (AGENTS.md §7).
 */
@Entity(tableName = "pending_progress", primaryKeys = ["serverId", "chapterId"])
data class PendingProgress(
    val serverId: String,
    val chapterId: Int,
    val seriesId: Int,
    val volumeId: Int,
    val libraryId: Int,
    val pageNum: Int,
    val bookScrollId: String? = null,
    val updatedAt: Long,
    val attempts: Int = 0,
)

@Dao
interface PendingProgressDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: PendingProgress)

    @Query("SELECT * FROM pending_progress WHERE serverId = :serverId ORDER BY updatedAt ASC")
    suspend fun forServer(serverId: String): List<PendingProgress>

    @Query("SELECT * FROM pending_progress WHERE serverId = :serverId LIMIT :limit")
    suspend fun batch(serverId: String, limit: Int): List<PendingProgress>

    @Query("DELETE FROM pending_progress WHERE serverId = :serverId AND chapterId = :chapterId")
    suspend fun delete(serverId: String, chapterId: Int)

    @Query("UPDATE pending_progress SET attempts = attempts + 1 WHERE serverId = :serverId AND chapterId = :chapterId")
    suspend fun incrementAttempts(serverId: String, chapterId: Int)

    @Query("SELECT COUNT(*) FROM pending_progress WHERE serverId = :serverId")
    suspend fun count(serverId: String): Int

    @Query("DELETE FROM pending_progress WHERE serverId = :serverId")
    suspend fun clearServer(serverId: String)
}

@Database(entities = [PendingProgress::class], version = 1, exportSchema = true)
abstract class KavitaDatabase : RoomDatabase() {
    abstract fun pendingProgressDao(): PendingProgressDao
}
