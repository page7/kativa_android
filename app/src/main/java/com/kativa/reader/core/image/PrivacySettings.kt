package com.kativa.reader.core.image

import kotlinx.coroutines.flow.Flow

/**
 * The slice of settings that privacy mode needs.
 *
 * Extracted so [PrivacyModeState] — the one component every screen's cover rendering depends on —
 * can be tested without an Android `Context`. `SettingsRepository` implements it; see
 * [com.kativa.reader.core.di.ImageModule].
 */
interface PrivacySettings {

    /**
     * Every stored privacy flag keyed by server id.
     *
     * A map rather than one flow per server: an observer must react to any server's flag changing
     * while the active id is stable, which per-server flows cannot express without
     * re-subscribing.
     */
    val privacyModeByServer: Flow<Map<String, Boolean>>

    suspend fun setPrivacyMode(serverId: String, enabled: Boolean)
}
