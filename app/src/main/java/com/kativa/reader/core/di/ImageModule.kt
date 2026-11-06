package com.kativa.reader.core.di

import com.kativa.reader.core.image.PrivacySettings
import com.kativa.reader.core.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImageModule {

    /**
     * [com.kativa.reader.core.image.PrivacyModeState] depends on the [PrivacySettings] slice rather
     * than the concrete repository so its regression tests can run as plain JVM tests.
     */
    @Binds
    @Singleton
    abstract fun bindPrivacySettings(impl: SettingsRepository): PrivacySettings
}
