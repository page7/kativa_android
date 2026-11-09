package com.kativa.reader.core.auth

/**
 * Persistent store for per-server sessions.
 *
 * Extracted so the sign-in path can be exercised against a real HTTP stack in unit tests without
 * an Android `Context`; [SessionStore] is the production implementation.
 */
interface SessionSink {
    suspend fun get(serverId: String): Session?
    suspend fun save(session: Session)
    suspend fun clear(serverId: String)
}
