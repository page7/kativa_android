package com.kativa.reader.data.repository

import com.kativa.reader.core.auth.ActiveServerStore
import com.kativa.reader.core.auth.RememberedAccount
import com.kativa.reader.core.auth.RememberedAccounts
import com.kativa.reader.core.auth.Session
import com.kativa.reader.core.auth.SessionExpiryBus
import com.kativa.reader.core.auth.SessionSink
import com.kativa.reader.core.network.ApiFactory
import com.kativa.reader.core.network.ErrorMapper
import com.kativa.reader.core.network.KativaError
import com.kativa.reader.core.network.KativaFailure
import com.kativa.reader.core.network.Outcome
import com.kativa.reader.data.remote.dto.LoginRequest
import javax.inject.Inject
import javax.inject.Singleton
import retrofit2.HttpException

/**
 * Sign-in, session bootstrap and the `image-only` auth key provisioning.
 *
 * Kavita's `POST Account/login` returns the full user, including `authKeys`. We pick the key
 * named `image-only` for cover/reader URLs; if it does not exist we create it once (AGENTS.md §4.2).
 */
@Singleton
class AuthRepository @Inject constructor(
    private val apiFactory: ApiFactory,
    private val sessionStore: SessionSink,
    private val activeServerStore: ActiveServerStore,
    private val rememberedAccounts: RememberedAccounts,
    private val sessionExpiryBus: SessionExpiryBus,
) {

    suspend fun loginWithPassword(username: String, password: String): Outcome<Session> =
        login(LoginRequest(username = username, password = password), username)

    suspend fun loginWithApiKey(apiKey: String): Outcome<Session> =
        login(LoginRequest(apiKey = apiKey), username = null)

    private suspend fun login(request: LoginRequest, username: String?): Outcome<Session> {
        val server = activeServerStore.active.value
            ?: return Outcome.Failure(KativaFailure(KativaError.Unknown))

        return try {
            val user = apiFactory.account().login(request)
            val token = user.token
                ?: return Outcome.Failure(KativaFailure(KativaError.Parse))
            val refresh = user.refreshToken
                ?: return Outcome.Failure(KativaFailure(KativaError.Parse))

            val imageKey = resolveImageKey(user.authKeys) ?: createImageKey() ?: user.apiKey
            if (imageKey.isNullOrBlank()) {
                return Outcome.Failure(KativaFailure(KativaError.Parse))
            }

            val session = Session(
                serverId = server.id,
                userId = user.id,
                username = user.username,
                roles = user.roles,
                token = token,
                refreshToken = refresh,
                imageKey = imageKey,
                kavitaVersion = user.kavitaVersion.orEmpty(),
            )
            sessionStore.save(session)
            activeServerStore.updateSession(session)
            // Remember the non-secret half of the sign-in so an expired session can be renewed
            // with just the password (never the username again).
            rememberedAccounts.save(
                RememberedAccount(
                    serverId = server.id,
                    username = username?.trim()?.takeIf { it.isNotBlank() } ?: user.username,
                ),
            )
            Outcome.Success(session)
        } catch (t: Throwable) {
            Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
        }
    }

    /** Re-reads the account to refresh roles and preferences after a permission change. */
    suspend fun refreshAccount(): Outcome<Session> {
        return try {
            val user = apiFactory.account().currentUser()
            val existing = activeServerStore.session.value
                ?: return Outcome.Failure(KativaFailure(KativaError.Unauthorized()))

            val imageKey = resolveImageKey(user.authKeys) ?: existing.imageKey
            val session = existing.copy(
                userId = user.id,
                username = user.username,
                roles = user.roles,
                imageKey = imageKey,
                kavitaVersion = user.kavitaVersion ?: existing.kavitaVersion,
            )
            sessionStore.save(session)
            activeServerStore.updateSession(session)
            Outcome.Success(session)
        } catch (t: Throwable) {
            Outcome.Failure(KativaFailure(ErrorMapper.from(t)))
        }
    }

    private fun resolveImageKey(keys: List<com.kativa.reader.data.remote.dto.AuthKeyDto>): String? =
        keys.firstOrNull { it.name.equals(IMAGE_KEY_NAME, ignoreCase = true) }?.key
            ?.takeIf { it.isNotBlank() }

    private suspend fun createImageKey(): String? = try {
        apiFactory.account().createAuthKey(
            com.kativa.reader.data.remote.dto.CreateAuthKeyRequest(
                name = IMAGE_KEY_NAME,
                keyLength = 32,
            ),
        ).key.takeIf { it.isNotBlank() }
    } catch (t: Throwable) {
        // Read-only accounts cannot create keys; AuthRepository then falls back to the OPDS key.
        if (t is HttpException && t.code() == 403) null else null
    }

    suspend fun signOut(serverId: String) {
        sessionStore.clear(serverId)
        if (activeServerStore.activeId == serverId) activeServerStore.updateSession(null)
    }

    /**
     * Marks this server's stored token as unusable after a 401.
     *
     * The remembered account survives: the next sign-in only needs the password.
     */
    suspend fun invalidateSession(serverId: String, notify: Boolean = true) {
        sessionStore.clear(serverId)
        if (activeServerStore.activeId == serverId) activeServerStore.updateSession(null)
        if (notify) sessionExpiryBus.notifyExpired(serverId)
    }

    private companion object {
        const val IMAGE_KEY_NAME = "image-only"
    }
}
