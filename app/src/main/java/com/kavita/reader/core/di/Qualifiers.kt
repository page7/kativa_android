package com.kavita.reader.core.di

import javax.inject.Qualifier

/** True for debug builds; gates verbose HTTP logging. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DebugBuild

/**
 * Process-lifetime [kotlinx.coroutines.CoroutineScope] for state that must outlive any screen.
 *
 * Used by [com.kavita.reader.core.image.PrivacyModeState], which keeps observing the active
 * server and its stored privacy flag even while no ViewModel is alive.
 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class ApplicationScope
