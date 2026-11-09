package com.kativa.reader.core.di

import com.kativa.reader.core.auth.RememberedAccountStore
import com.kativa.reader.core.auth.RememberedAccounts
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/** Binds the DataStore-backed stores to the interfaces the sign-in path depends on. */
@Module
@InstallIn(SingletonComponent::class)
abstract class AuthModule {

    @Binds
    @Singleton
    abstract fun bindRememberedAccounts(store: RememberedAccountStore): RememberedAccounts

    @Binds
    @Singleton
    abstract fun bindSessionSink(store: com.kativa.reader.core.auth.SessionStore): com.kativa.reader.core.auth.SessionSink
}
