package com.kavita.reader.core.di

import com.kavita.reader.core.auth.RememberedAccountStore
import com.kavita.reader.core.auth.RememberedAccounts
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
    abstract fun bindSessionSink(store: com.kavita.reader.core.auth.SessionStore): com.kavita.reader.core.auth.SessionSink
}
