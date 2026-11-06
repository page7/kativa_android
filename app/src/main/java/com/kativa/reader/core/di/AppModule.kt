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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

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

    /**
     * Process-lifetime scope for observers that must not die with a screen.
     *
     * [SupervisorJob] so one failed collector cannot cancel the rest; [Dispatchers.Default]
     * because DataStore reads are IO-bound and this scope never touches the main thread.
     */
    @Provides
    @Singleton
    @ApplicationScope
    fun provideApplicationScope(): CoroutineScope =
        CoroutineScope(SupervisorJob() + Dispatchers.Default)
}
