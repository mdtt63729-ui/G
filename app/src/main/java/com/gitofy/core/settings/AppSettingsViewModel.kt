package com.gitofy.core.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * PRD §3.1: Root app settings ViewModel.
 * Provides the app-wide theme/dynamic color state as a StateFlow.
 * GITOFYTheme reads from this to decide Light/Dark/System + dynamic color.
 */
@HiltViewModel
class AppSettingsViewModel @Inject constructor(
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {

    val settings: StateFlow<AppSettings> = appSettingsRepository.settings
        .stateIn(
            scope = viewModelScope,
            started = SharingStarted.WhileSubscribed(5000),
            initialValue = AppSettings()
        )

    init {
        // One-time upgrade: existing installs may already have the old
        // "#5849E8" indigo default persisted to disk from before the brand
        // color changed to sky blue — see migrateLegacyAccentDefault().
        viewModelScope.launch { appSettingsRepository.migrateLegacyAccentDefault() }
    }

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appSettingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setDynamicColor(enabled) }
    }

    fun setBackgroundSync(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setBackgroundSync(enabled) }
    }

    fun setAutoBuild(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setAutoBuild(enabled) }
    }

    fun setBuildVariant(value: String) {
        viewModelScope.launch { appSettingsRepository.setBuildVariant(value) }
    }

    fun setAutoBuildRetryEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setAutoBuildRetryEnabled(enabled) }
    }

    fun setBuildNotifications(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setBuildNotifications(enabled) }
    }

    fun setFontFamily(value: FontFamilyOption) {
        viewModelScope.launch { appSettingsRepository.setFontFamily(value) }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setHapticFeedback(enabled) }
    }

    fun setSyncFrequency(value: SyncFrequency) {
        viewModelScope.launch { appSettingsRepository.setSyncFrequency(value) }
    }

    fun setRestoreLastLocation(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setRestoreLastLocation(enabled) }
    }
}
