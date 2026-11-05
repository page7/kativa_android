package com.kativa.reader.core.image

import android.content.Context
import com.kativa.reader.core.auth.ActiveServerStore
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Resolves per-server directories for Coil's disk cache.
 *
 * Privacy mode never reaches this class: cover composables skip request construction entirely
 * when it is on, so nothing is fetched and nothing is written (AGENTS.md §6).
 */
@Singleton
class CoverCachePaths @Inject constructor(
    @ApplicationContext private val context: Context,
    private val activeServerStore: ActiveServerStore,
) {
    fun cacheDir(): java.io.File {
        val serverId = activeServerStore.active.value?.id ?: "none"
        return java.io.File(context.cacheDir, "covers_$serverId").apply { mkdirs() }
    }

    fun clearCurrentServer() {
        cacheDir().deleteRecursively()
    }
}
