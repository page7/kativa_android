package com.kavita.reader.core.di

import android.content.Context
import androidx.room.Room
import com.kavita.reader.data.local.KavitaDatabase
import com.kavita.reader.data.local.PendingProgressDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): KavitaDatabase =
        Room.databaseBuilder(context, KavitaDatabase::class.java, "kavita.db")
            // No destructive fallback: schema changes must ship a Migration (AGENTS.md §7).
            .build()

    @Provides
    fun providePendingProgressDao(db: KavitaDatabase): PendingProgressDao = db.pendingProgressDao()
}
