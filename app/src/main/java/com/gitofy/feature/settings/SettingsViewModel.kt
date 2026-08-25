package com.gitofy.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class SettingsUiState(
    val userLogin: String? = null,
    val userAvatar: String? = null,
    val hasCredentials: Boolean = false,
    val themeMode: ThemeMode = ThemeMode.SYSTEM,
    val dynamicColor: Boolean = true,
    val backgroundSync: Boolean = true
)

enum class ThemeMode { LIGHT, DARK, SYSTEM }

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val signOutUseCase: SignOutUseCase,
    private val secureStorage: SecureCredentialStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(SettingsUiState())
    val uiState = _uiState.asStateFlow()

    init {
        _uiState.value = SettingsUiState(
            userLogin = secureStorage.getUserLogin(),
            userAvatar = secureStorage.getUserAvatar(),
            hasCredentials = secureStorage.hasToken()
        )
    }

    fun signOut() {
        signOutUseCase()
    }

    fun setThemeMode(mode: ThemeMode) {
        _uiState.value = _uiState.value.copy(themeMode = mode)
    }

    fun setDynamicColor(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(dynamicColor = enabled)
    }

    fun setBackgroundSync(enabled: Boolean) {
        _uiState.value = _uiState.value.copy(backgroundSync = enabled)
    }
}
