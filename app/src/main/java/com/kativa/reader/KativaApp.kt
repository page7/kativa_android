package com.kativa.reader

import android.app.Application
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.kativa.reader.core.network.ApiFactory
import com.kativa.reader.core.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

@HiltAndroidApp
class KativaApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var apiFactory: ApiFactory

    /** Single source of truth for privacy mode, shared by the theme and the image loader. */
    @Inject
    lateinit var privacyModeState: com.kativa.reader.core.image.PrivacyModeState

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        SingletonImageLoader.setSafe(this)
        applyPersistedLanguage()
    }

    /**
     * The image loader MUST register a network fetcher.
     *
     * `coil-network-okhttp` is a plain JVM dependency: its ServiceLoader target is not picked up
     * on Android, so without an explicit fetcher Coil silently fails every http(s) request and
     * every cover renders as an empty placeholder. That is exactly the bug this fixes.
     */
    override fun newImageLoader(context: PlatformContext): ImageLoader =
        ImageLoader.Builder(context)
            .components {
                add(OkHttpNetworkFetcherFactory(callFactory = { apiFactory.imageHttpClient() }))
                // Second guard: even if a caller bypasses CoverImage, no cover is ever fetched
                // while privacy mode is on (AGENTS.md §6).
                add(com.kativa.reader.core.image.PrivacyGuardInterceptor { privacyModeState.enabled.value })
            }
            .crossfade(true)
            .memoryCache {
                MemoryCache.Builder()
                    .maxSizePercent(context, MEMORY_CACHE_FRACTION)
                    .build()
            }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache").toOkioPath())
                    .maxSizeBytes(DISK_CACHE_BYTES)
                    .build()
            }
            .build()

    /** Re-applies the stored language preference on cold start (zh/en/fr/ja). */
    private fun applyPersistedLanguage() {
        appScope.launch {
            val tag = settingsRepository.language.first()
            val locales = if (tag.isNullOrBlank()) LocaleListCompat.getEmptyLocaleList()
            else LocaleListCompat.forLanguageTags(tag)
            AppCompatDelegate.setApplicationLocales(locales)
        }
    }

    private companion object {
        /** Reading pages are large; give the memory cache a generous share. */
        const val MEMORY_CACHE_FRACTION = 0.25
        const val DISK_CACHE_BYTES = 512L * 1024 * 1024
    }
}
