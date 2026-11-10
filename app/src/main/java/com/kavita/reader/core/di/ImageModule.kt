package com.kavita.reader.core.di

import com.kavita.reader.core.image.PrivacySettings
import com.kavita.reader.core.settings.SettingsRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImageModule {

    /**
     * [com.kavita.reader.core.image.PrivacyModeState] depends on the [PrivacySettings] slice rather
     * than the concrete repository so its regression tests can run as plain JVM tests.
     */
    @Binds
    @Singleton
    abstract fun bindPrivacySettings(impl: SettingsRepository): PrivacySettings
}
