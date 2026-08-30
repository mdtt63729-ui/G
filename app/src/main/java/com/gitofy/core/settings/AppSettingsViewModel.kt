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

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch { appSettingsRepository.setThemeMode(mode) }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setDynamicColor(enabled) }
    }

    fun setBackgroundSync(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setBackgroundSync(enabled) }
    }
}
