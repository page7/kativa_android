package com.kavita.reader

import android.app.Application
import android.app.UiModeManager
import android.os.Build
import androidx.appcompat.app.AppCompatDelegate
import androidx.core.os.LocaleListCompat
import coil3.ImageLoader
import coil3.PlatformContext
import coil3.SingletonImageLoader
import coil3.disk.DiskCache
import coil3.memory.MemoryCache
import coil3.network.okhttp.OkHttpNetworkFetcherFactory
import coil3.request.crossfade
import com.kavita.reader.core.network.ApiFactory
import com.kavita.reader.core.designsystem.ThemeMode
import com.kavita.reader.core.settings.SettingsRepository
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okio.Path.Companion.toOkioPath

@HiltAndroidApp
class KavitaApp : Application(), SingletonImageLoader.Factory {

    @Inject
    lateinit var settingsRepository: SettingsRepository

    @Inject
    lateinit var apiFactory: ApiFactory

    /** Single source of truth for privacy mode, shared by the theme and the image loader. */
    @Inject
    lateinit var privacyModeState: com.kavita.reader.core.image.PrivacyModeState

    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        SingletonImageLoader.setSafe(this)
        applyPersistedLanguage()
        applyPersistedNightMode()
        // Mirror the theme into SharedPreferences so the next launch can resolve it synchronously.
        appScope.launch { settingsRepository.observeThemeForWindow() }
    }

    /**
     * Aligns the *resource* configuration with the user's theme choice.
     *
     * Two separate mechanisms are needed, because the splash exists at two different times:
     *
     *  - [AppCompatDelegate.setDefaultNightMode] drives this process, i.e. Compose's first frame
     *    and every resource lookup once the app is alive;
     *  - [UiModeManager.setApplicationNightMode] (API 33+) is the only thing that can influence the
     *    *system* splash window, which system_server builds from the activity theme before the app
     *    process exists. Without it the splash always follows the device setting — so a user who
     *    picked the dark theme on a light-mode phone still got a white flash (`#FAFAFB`).
     *
     * On API < 33 the system splash simply follows the device theme; the Compose splash right
     * after it is already correct.
     */
    @Suppress("DEPRECATION")
    private fun applyPersistedNightMode() {
        val mode = when (settingsRepository.cachedThemeMode()) {
            ThemeMode.Dark -> AppCompatDelegate.MODE_NIGHT_YES
            ThemeMode.Light -> AppCompatDelegate.MODE_NIGHT_NO
            ThemeMode.System -> AppCompatDelegate.MODE_NIGHT_FOLLOW_SYSTEM
        }
        AppCompatDelegate.setDefaultNightMode(mode)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            val manager = getSystemService(UiModeManager::class.java) ?: return
            manager.setApplicationNightMode(
                when (settingsRepository.cachedThemeMode()) {
                    ThemeMode.Dark -> UiModeManager.MODE_NIGHT_YES
                    ThemeMode.Light -> UiModeManager.MODE_NIGHT_NO
                    ThemeMode.System -> UiModeManager.MODE_NIGHT_AUTO
                },
            )
        }
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
                add(com.kavita.reader.core.image.PrivacyGuardInterceptor { privacyModeState.enabled.value })
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
