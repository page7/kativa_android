package com.kativa.reader.core.di

import javax.inject.Qualifier

/** True for debug builds; gates verbose HTTP logging. */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class DebugBuild
