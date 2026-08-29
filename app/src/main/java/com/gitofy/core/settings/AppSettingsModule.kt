package com.gitofy.core.settings

import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent

/**
 * AppSettingsRepository is @Singleton with @Inject constructor,
 * so Hilt auto-provides it. This module is a placeholder for
 * future @Provides if needed.
 */
@Module
@InstallIn(SingletonComponent::class)
object AppSettingsModule
