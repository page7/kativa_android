package com.kativa.reader.core.auth

import kotlinx.coroutines.flow.Flow

/**
 * The slice of account storage the sign-in path needs.
 *
 * Extracted so [com.kativa.reader.data.repository.AuthRepository] can be tested without an Android
 * `Context`; the only production implementation is [RememberedAccountStore].
 */
interface RememberedAccounts {

    /** Every remembered account keyed by server id. */
    val accounts: Flow<Map<String, RememberedAccount>>

    suspend fun get(serverId: String): RememberedAccount?

    suspend fun save(account: RememberedAccount)

    suspend fun clear(serverId: String)
}
