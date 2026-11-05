package com.kativa.reader.core.di

import android.content.Context
import com.kativa.reader.core.network.CleartextGateInterceptor
import com.kativa.reader.core.network.PaginationInterceptor
import com.kativa.reader.core.auth.ActiveServerStore
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideContext(@ApplicationContext context: Context): Context = context

    @Provides
    @Singleton
    fun providePaginationInterceptor(): PaginationInterceptor = PaginationInterceptor()

    @Provides
    @Singleton
    fun provideCleartextGate(activeServerStore: ActiveServerStore): CleartextGateInterceptor =
        CleartextGateInterceptor { activeServerStore.active.value?.allowCleartext == true }

    @Provides
    @DebugBuild
    fun provideDebugBuild(): Boolean = com.kativa.reader.BuildConfig.DEBUG
}
