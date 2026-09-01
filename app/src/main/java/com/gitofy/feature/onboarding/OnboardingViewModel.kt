package com.gitofy.feature.onboarding

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.settings.AppSettingsRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class OnboardingViewModel @Inject constructor(
    private val repository: OnboardingRepository,
    // FIX: the Background Sync switch on the onboarding screen used to only
    // write to the onboarding wizard's own private DataStore flag, which no
    // other part of the app ever reads. The real setting the rest of the
    // app checks lives in AppSettingsRepository — so the switch looked like
    // it worked but never actually changed anything. It now writes both.
    private val appSettingsRepository: AppSettingsRepository
) : ViewModel() {
    val state: StateFlow<OnboardingState> = repository.state.stateIn(
        viewModelScope,
        SharingStarted.WhileSubscribed(5_000),
        OnboardingState()
    )

    fun next() = viewModelScope.launch { repository.setStep(state.value.currentStep + 1) }
    fun skip() = viewModelScope.launch { repository.skipAndComplete() }
    fun markGithub() = viewModelScope.launch { repository.setGithubConnected(true) }
    fun markRepository() = viewModelScope.launch { repository.setRepositoryConfigured(true) }
    fun markAi() = viewModelScope.launch { repository.setAiProviderConfigured(true) }
    fun setSync(value: Boolean) = viewModelScope.launch {
        repository.setBackgroundSync(value)
        appSettingsRepository.setBackgroundSync(value)
    }
    fun markAppearance() = viewModelScope.launch { repository.setAppearanceConfigured(true) }
    fun complete() = viewModelScope.launch { repository.complete() }
}
