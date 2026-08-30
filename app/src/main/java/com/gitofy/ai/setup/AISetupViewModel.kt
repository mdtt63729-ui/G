package com.gitofy.ai.setup

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.ai.credentials.AiProvider
import com.gitofy.ai.credentials.AiCredentialStore
import com.gitofy.ai.credentials.ProviderCredential
import com.gitofy.ai.credentials.ApiKeyValidator
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI Setup Wizard ViewModel — PRD 2 Sections 5-11, 57-60.
 */
data class AISetupUiState(
    val step: SetupStep = SetupStep.INTRODUCTION,
    val providerStates: Map<AiProvider, ProviderSetupState> = AiProvider.entries.associateWith { ProviderSetupState() },
    val showSecurityConfirmation: Boolean = false,
    val isComplete: Boolean = false,
    val error: String? = null
) {
    val configuredCount: Int get() = providerStates.count { it.value.status == ProviderStatus.CONNECTED }
    val totalMandatory: Int get() = AiProvider.mandatory.size
    val allMandatoryConfigured: Boolean get() = AiProvider.mandatory.all { providerStates[it]?.status == ProviderStatus.CONNECTED }
    val canFinish: Boolean get() = allMandatoryConfigured
}

data class ProviderSetupState(
    val status: ProviderStatus = ProviderStatus.NOT_CONFIGURED,
    val apiKeyInput: String = "",
    val apiKeyVisible: Boolean = false,
    val keyHint: String? = null,
    val error: String? = null
)

enum class ProviderStatus { NOT_CONFIGURED, VALIDATING, CONNECTED, INVALID, NETWORK_ERROR, RATE_LIMITED, PROVIDER_ERROR }
enum class SetupStep { INTRODUCTION, PROVIDER_CONFIG, SECURITY_CONFIRMATION, COMPLETE }

@HiltViewModel
class AISetupViewModel @Inject constructor(
    private val credentialStore: AiCredentialStore,
    private val apiKeyValidator: ApiKeyValidator
) : ViewModel() {

    private val _uiState = MutableStateFlow(AISetupUiState())
    val uiState = _uiState.asStateFlow()

    init { checkExistingCredentials() }

    private fun checkExistingCredentials() {
        viewModelScope.launch {
            AiProvider.entries.forEach { provider ->
                if (credentialStore.hasCredential(provider)) {
                    credentialStore.getCredential(provider)?.let { cred ->
                        updateProviderState(provider) { it.copy(status = ProviderStatus.CONNECTED, keyHint = cred.keyHint) }
                    }
                }
            }
        }
    }

    fun updateApiKey(provider: AiProvider, key: String) { updateProviderState(provider) { it.copy(apiKeyInput = key, error = null) } }
    fun toggleApiKeyVisibility(provider: AiProvider) { updateProviderState(provider) { it.copy(apiKeyVisible = !it.apiKeyVisible) } }

    fun validateProvider(provider: AiProvider) {
        val input = _uiState.value.providerStates[provider]?.apiKeyInput ?: ""
        if (input.isBlank()) { updateProviderState(provider) { it.copy(error = "Please enter an API key") }; return }
        val formatResult = apiKeyValidator.validateFormat(provider, input)
        if (!formatResult.isValid) { updateProviderState(provider) { it.copy(status = ProviderStatus.INVALID, error = formatResult.error) }; return }
        updateProviderState(provider) { it.copy(status = ProviderStatus.VALIDATING, error = null) }
        viewModelScope.launch {
            val hint = apiKeyValidator.getKeyHint(input)
            credentialStore.saveCredential(provider, ProviderCredential(provider, input.toByteArray(), hint, System.currentTimeMillis(), true))
            updateProviderState(provider) { it.copy(status = ProviderStatus.CONNECTED, keyHint = hint, apiKeyInput = "", error = null) }
        }
    }

    fun removeProvider(provider: AiProvider) { viewModelScope.launch { credentialStore.removeCredential(provider); updateProviderState(provider) { ProviderSetupState() } } }
    fun goToStep(step: SetupStep) { _uiState.update { it.copy(step = step) } }
    fun showSecurityConfirmation() { _uiState.update { it.copy(showSecurityConfirmation = true) } }
    fun dismissSecurityConfirmation() { _uiState.update { it.copy(showSecurityConfirmation = false) } }
    fun completeSetup() { if (_uiState.value.canFinish) _uiState.update { it.copy(step = SetupStep.COMPLETE, isComplete = true, showSecurityConfirmation = false) } }

    private fun updateProviderState(provider: AiProvider, update: (ProviderSetupState) -> ProviderSetupState) {
        _uiState.update { state -> val current = state.providerStates[provider] ?: ProviderSetupState(); state.copy(providerStates = state.providerStates + (provider to update(current))) }
    }
}
