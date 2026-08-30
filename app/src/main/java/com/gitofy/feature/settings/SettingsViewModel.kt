package com.gitofy.feature.settings

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.ai.provider.client.ApiProviderClient
import com.gitofy.ai.provider.client.ApiTestResult
import com.gitofy.ai.provider.client.DiscoveredModel
import com.gitofy.ai.provider.registry.ProviderDefinition
import com.gitofy.ai.provider.registry.ProviderInstance
import com.gitofy.ai.provider.registry.ProviderRegistryData
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.core.settings.AiSettingsEvent
import com.gitofy.core.settings.AppSettings
import com.gitofy.core.settings.AppSettingsRepository
import com.gitofy.core.settings.ThemeMode as AppThemeMode
import com.gitofy.domain.usecase.SignOutUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.util.UUID
import javax.inject.Inject

// ── Re-exported types for backward compatibility ─────────────────────────
// These keep existing call sites that reference the old SettingsViewModel
// types compiling without modification.

enum class ThemeMode { LIGHT, DARK, SYSTEM }

enum class ProviderKeyStatus { NOT_CONFIGURED, VALIDATING, CONNECTED, INVALID, ERROR }

data class ProviderKeyInfo(
    val displayName: String,
    val hasKey: Boolean,
    val keyHint: String,
    val status: ProviderKeyStatus,
    val latencyMs: Long? = null,
    val modelCount: Int? = null,
    val error: String? = null,
    val definitionId: String = ""
)

/**
 * Full UI state for the Settings system — PRD §40.
 *
 * [appSettings] is the reactive DataStore-backed settings flow.
 * [providerInstances] is the user's configured provider list.
 * [providerDefinitions] is the built-in registry for the add-provider sheet.
 */
data class SettingsUiState(
    val userLogin: String? = null,
    val userAvatar: String? = null,
    val hasCredentials: Boolean = false,
    val appSettings: AppSettings = AppSettings(),
    val providerInstances: List<ProviderInstance> = emptyList(),
    val providerDefinitions: List<ProviderDefinition> = emptyList(),
    val testResults: Map<String, ApiTestResult> = emptyMap(),
    val discoveredModels: Map<String, List<DiscoveredModel>> = emptyMap(),
    val loadingModels: Set<String> = emptySet(),
    val showAddProviderSheet: Boolean = false,
    val showSignOutDialog: Boolean = false,
    val showResetDialog: Boolean = false,
    val showDeleteProviderDialog: String? = null
)

@HiltViewModel
class SettingsViewModel @Inject constructor(
    private val signOutUseCase: SignOutUseCase,
    private val secureStorage: SecureCredentialStorage,
    private val appSettingsRepository: AppSettingsRepository,
    private val providerRegistry: ProviderRegistryData,
    private val apiClient: ApiProviderClient
) : ViewModel() {

    private val _providerInstances = MutableStateFlow<List<ProviderInstance>>(emptyList())
    private val _testResults = MutableStateFlow<Map<String, ApiTestResult>>(emptyMap())
    private val _discoveredModels = MutableStateFlow<Map<String, List<DiscoveredModel>>>(emptyMap())
    private val _loadingModels = MutableStateFlow<Set<String>>(emptySet())
    private val _showAddProviderSheet = MutableStateFlow(false)
    private val _showResetDialog = MutableStateFlow(false)
    private val _showDeleteProviderDialog = MutableStateFlow<String?>(null)
    private val _events = MutableSharedFlow<AiSettingsEvent>(extraBufferCapacity = 5)

    val events: SharedFlow<AiSettingsEvent> = _events.asSharedFlow()

    val uiState: StateFlow<SettingsUiState> = combine(
        appSettingsRepository.settings,
        _providerInstances,
        _testResults,
        _discoveredModels,
        _loadingModels
    ) { settings, instances, testResults, models, loading ->
        SettingsUiState(
            userLogin = secureStorage.getUserLogin(),
            userAvatar = secureStorage.getUserAvatar(),
            hasCredentials = secureStorage.hasToken(),
            appSettings = settings,
            providerInstances = instances,
            providerDefinitions = providerRegistry.allDefinitions(),
            testResults = testResults,
            discoveredModels = models,
            loadingModels = loading,
            showAddProviderSheet = _showAddProviderSheet.value,
            showResetDialog = _showResetDialog.value,
            showDeleteProviderDialog = _showDeleteProviderDialog.value
        )
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SettingsUiState())

    init {
        loadProviderInstances()
        seedDefaultProviders()
    }

    // ── Provider Instances ───────────────────────────────────────────────

    private fun loadProviderInstances() {
        val instances = secureStorage.getProviderInstances()
        _providerInstances.value = instances
    }

    /**
     * PRD §7 — On fresh install, Gemini and OpenRouter must already exist
     * as configured provider entries ready for API-key setup.
     */
    private fun seedDefaultProviders() {
        val existing = _providerInstances.value
        if (existing.isNotEmpty()) return

        val defaults = providerRegistry.defaultProviderIds().map { id ->
            val def = providerRegistry.getDefinition(id) ?: return@map null
            ProviderInstance(
                instanceId = UUID.randomUUID().toString(),
                definitionId = def.id,
                displayName = def.displayName,
                endpoint = def.defaultEndpoint,
                apiKeyHint = "",
                isEnabled = false,
                isDefault = false,
                isCustom = false
            )
        }.filterNotNull()

        if (defaults.isNotEmpty()) {
            _providerInstances.value = defaults
            secureStorage.saveProviderInstances(defaults)
        }
    }

    /**
     * PRD §9 — Add a provider instance from the add-provider sheet.
     */
    fun addProvider(definitionId: String) {
        val def = providerRegistry.getDefinition(definitionId) ?: return
        val existing = _providerInstances.value
        // Don't add duplicates (unless custom)
        if (definitionId != "custom" && existing.any { it.definitionId == definitionId }) {
            _showAddProviderSheet.value = false
            return
        }
        val newInstance = ProviderInstance(
            instanceId = UUID.randomUUID().toString(),
            definitionId = def.id,
            displayName = def.displayName,
            endpoint = def.defaultEndpoint,
            apiKeyHint = "",
            isEnabled = false,
            isDefault = false,
            isCustom = def.id == "custom"
        )
        val updated = existing + newInstance
        _providerInstances.value = updated
        secureStorage.saveProviderInstances(updated)
        _showAddProviderSheet.value = false
    }

    /**
     * PRD §13 — Save provider configuration (API key, endpoint, model).
     */
    fun saveProviderConfig(
        instanceId: String,
        apiKey: String,
        endpoint: String,
        modelId: String? = null,
        customHeaders: Map<String, String> = emptyMap()
    ) {
        val instances = _providerInstances.value.toMutableList()
        val idx = instances.indexOfFirst { it.instanceId == instanceId }
        if (idx < 0) return

        val current = instances[idx]
        val hint = if (apiKey.length > 4) "••••••••••••${apiKey.takeLast(4)}" else ""
        val updated = current.copy(
            endpoint = endpoint,
            apiKeyHint = hint,
            selectedModel = modelId ?: current.selectedModel,
            customHeaders = customHeaders.ifEmpty { current.customHeaders },
            isEnabled = true
        )
        instances[idx] = updated
        _providerInstances.value = instances
        secureStorage.saveProviderInstances(instances)
        // Store the actual key securely under both the provider instance id
        // and the canonical provider id. The Gito chat catalog uses the
        // canonical provider id, while the settings system uses instance ids.
        // Keeping both prevents a saved key from appearing missing in Gito.
        secureStorage.saveAiKey(instanceId, apiKey)
        canonicalProviderId(current.definitionId)?.let { providerId ->
            secureStorage.saveAiKey(providerId, apiKey)
            modelId?.let { secureStorage.saveSelectedModel(providerId, it) }
        }

        // PRD §15 — auto-trigger connection test after save
        testConnection(instanceId)
    }

    private fun canonicalProviderId(definitionId: String): String? = when (definitionId.lowercase()) {
        "gemini" -> "GEMINI"
        "openai" -> "OPENAI"
        "nvidia", "nvidia-nim", "nvidia_nim", "nvidia-nim-api" -> "NVIDIA_NIM"
        "openrouter" -> "OPENROUTER"
        "opencode", "opencode-zen", "opencode_zen" -> "OPENCODE_ZEN"
        "sarvam", "sarvam-ai", "sarvam_ai" -> "SARVAM"
        "custom" -> "CUSTOM"
        else -> null
    }

    /**
     * PRD §53 — Delete a provider instance (with confirmation).
     */
    fun deleteProvider(instanceId: String) {
        val instances = _providerInstances.value.toMutableList()
        val target = instances.find { it.instanceId == instanceId } ?: return

        // If this was the default provider, clear the default
        if (target.isDefault) {
            viewModelScope.launch {
                appSettingsRepository.setDefaultProvider("")
                appSettingsRepository.setDefaultModel("")
            }
        }

        // Remove the key and cached models. Remove both storage aliases so
        // deleting a provider can never leave a stale chat credential.
        secureStorage.removeAiKey(instanceId)
        canonicalProviderId(target.definitionId)?.let { secureStorage.removeAiKey(it) }
        instances.removeAll { it.instanceId == instanceId }
        _providerInstances.value = instances
        secureStorage.saveProviderInstances(instances)

        // Clear test results
        _testResults.update { it - instanceId }
        _discoveredModels.update { it - instanceId }
        _showDeleteProviderDialog.value = null
    }

    /**
     * PRD §13 — Enable/disable a provider.
     */
    fun setProviderEnabled(instanceId: String, enabled: Boolean) {
        val instances = _providerInstances.value.toMutableList()
        val idx = instances.indexOfFirst { it.instanceId == instanceId }
        if (idx < 0) return
        instances[idx] = instances[idx].copy(isEnabled = enabled)
        _providerInstances.value = instances
        secureStorage.saveProviderInstances(instances)
    }

    /**
     * PRD §53 — Set a provider as the default.
     */
    fun setDefaultProvider(instanceId: String) {
        val instances = _providerInstances.value.toMutableList()
        instances.forEachIndexed { i, inst ->
            instances[i] = inst.copy(isDefault = inst.instanceId == instanceId)
        }
        _providerInstances.value = instances
        secureStorage.saveProviderInstances(instances)

        val def = instances.find { it.instanceId == instanceId }
        viewModelScope.launch {
            appSettingsRepository.setDefaultProvider(def?.definitionId ?: "")
            def?.selectedModel?.let { appSettingsRepository.setDefaultModel(it) }
        }
    }

    // ── Test Connection — PRD §15 ────────────────────────────────────────

    fun testConnection(instanceId: String) {
        val instance = _providerInstances.value.find { it.instanceId == instanceId } ?: return
        val apiKey = secureStorage.getAiKey(instanceId) ?: run {
            _testResults.update { it + (instanceId to ApiTestResult.Failed("No API key configured")) }
            return
        }

        _testResults.update { it + (instanceId to ApiTestResult.Testing) }

        viewModelScope.launch {
            val result = withContext(Dispatchers.IO) {
                apiClient.testConnection(instance, apiKey)
            }
            _testResults.update { it + (instanceId to result) }
        }
    }

    // ── Model Discovery — PRD §17 ────────────────────────────────────────

    fun loadModels(instanceId: String) {
        // Check cache first — PRD §17: do not request model lists every time
        val instance = _providerInstances.value.find { it.instanceId == instanceId } ?: return
        val cached = secureStorage.getCachedModels(instanceId)
        if (cached.isNotEmpty()) {
            _discoveredModels.update {
                it + (instanceId to cached.map { id -> DiscoveredModel(id, id) })
            }
            return
        }

        val apiKey = secureStorage.getAiKey(instanceId) ?: run {
            _events.tryEmit(AiSettingsEvent.Error("No API key for ${instance.displayName}"))
            return
        }

        _loadingModels.update { it + instanceId }

        viewModelScope.launch {
            val models = withContext(Dispatchers.IO) {
                apiClient.discoverModels(instance, apiKey)
            }
            _discoveredModels.update { it + (instanceId to models) }
            _loadingModels.update { it - instanceId }

            // Cache the results
            if (models.isNotEmpty()) {
                secureStorage.saveCachedModels(instanceId, models.map { it.id })
            }
        }
    }

    fun refreshModels(instanceId: String) {
        // Force-refresh: bypass cache
        secureStorage.saveCachedModels(instanceId, emptyList())
        _discoveredModels.update { it - instanceId }
        loadModels(instanceId)
    }

    // ── Settings setters (delegated to repository) ───────────────────────

    fun setThemeMode(mode: ThemeMode) {
        viewModelScope.launch {
            appSettingsRepository.setThemeMode(
                when (mode) {
                    ThemeMode.LIGHT -> AppThemeMode.LIGHT
                    ThemeMode.DARK -> AppThemeMode.DARK
                    ThemeMode.SYSTEM -> AppThemeMode.SYSTEM
                }
            )
        }
    }

    fun setDynamicColor(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setDynamicColor(enabled) }
    }

    fun setBackgroundSync(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setBackgroundSync(enabled) }
    }

    fun setAmoledMode(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setAmoledMode(enabled) }
    }

    fun setAccentColor(hex: String) {
        viewModelScope.launch { appSettingsRepository.setAccentColor(hex) }
    }

    fun setUiDensity(density: com.gitofy.core.settings.UiDensity) {
        viewModelScope.launch { appSettingsRepository.setUiDensity(density) }
    }

    fun setAnimationLevel(level: com.gitofy.core.settings.AnimationLevel) {
        viewModelScope.launch { appSettingsRepository.setAnimationLevel(level) }
    }

    fun setFontSize(size: com.gitofy.core.settings.FontSize) {
        viewModelScope.launch { appSettingsRepository.setFontSize(size) }
    }

    fun setDefaultModel(modelId: String) {
        viewModelScope.launch { appSettingsRepository.setDefaultModel(modelId) }
    }

    fun setModelTemperature(value: Float) {
        viewModelScope.launch { appSettingsRepository.setModelTemperature(value) }
    }

    fun setModelTopP(value: Float) {
        viewModelScope.launch { appSettingsRepository.setModelTopP(value) }
    }

    fun setModelMaxTokens(value: Int) {
        viewModelScope.launch { appSettingsRepository.setModelMaxTokens(value) }
    }

    fun setModelContext(value: Int) {
        viewModelScope.launch { appSettingsRepository.setModelContext(value) }
    }

    fun setAgentMode(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setAgentMode(enabled) }
    }

    fun setAutoToolExecution(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setAutoToolExecution(enabled) }
    }

    fun setConfirmDangerousActions(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setConfirmDangerousActions(enabled) }
    }

    fun setMaxAgentIterations(value: Int) {
        viewModelScope.launch { appSettingsRepository.setMaxAgentIterations(value) }
    }

    fun setAutoErrorFixing(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setAutoErrorFixing(enabled) }
    }

    fun setAutoBuildRetry(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setAutoBuildRetry(enabled) }
    }

    fun setAiResponseStyle(style: String) {
        viewModelScope.launch { appSettingsRepository.setAiResponseStyle(style) }
    }

    fun setEditorFontSize(value: Int) {
        viewModelScope.launch { appSettingsRepository.setEditorFontSize(value) }
    }

    fun setEditorLineNumbers(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setEditorLineNumbers(enabled) }
    }

    fun setEditorWordWrap(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setEditorWordWrap(enabled) }
    }

    fun setEditorSyntaxHighlighting(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setEditorSyntaxHighlighting(enabled) }
    }

    fun setEditorBracketMatching(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setEditorBracketMatching(enabled) }
    }

    fun setEditorAutoIndent(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setEditorAutoIndent(enabled) }
    }

    fun setEditorAutoSave(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setEditorAutoSave(enabled) }
    }

    fun setEditorHighlightCurrentLine(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setEditorHighlightCurrentLine(enabled) }
    }

    fun setEditorTabSize(value: Int) {
        viewModelScope.launch { appSettingsRepository.setEditorTabSize(value) }
    }

    fun setEditorUseSpaces(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setEditorUseSpaces(enabled) }
    }

    fun setEditorMinimap(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setEditorMinimap(enabled) }
    }

    fun setOpenLastProject(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setOpenLastProject(enabled) }
    }

    fun setWorkspaceAutoSave(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setWorkspaceAutoSave(enabled) }
    }

    fun setConfirmBeforeDelete(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setConfirmBeforeDelete(enabled) }
    }

    fun setRestoreWorkspaceLayout(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setRestoreWorkspaceLayout(enabled) }
    }

    fun setGitDefaultBranch(value: String) {
        viewModelScope.launch { appSettingsRepository.setGitDefaultBranch(value) }
    }

    fun setGitConfirmDestructive(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setGitConfirmDestructive(enabled) }
    }

    fun setGitAutoPush(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setGitAutoPush(enabled) }
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

    fun setNotifyBuildCompleted(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setNotifyBuildCompleted(enabled) }
    }

    fun setNotifyBuildFailed(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setNotifyBuildFailed(enabled) }
    }

    fun setNotifyAITaskCompleted(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setNotifyAITaskCompleted(enabled) }
    }

    fun setNotifyAITaskFailed(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setNotifyAITaskFailed(enabled) }
    }

    fun setNotifyGitCompleted(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setNotifyGitCompleted(enabled) }
    }

    fun setNotifyGitFailed(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setNotifyGitFailed(enabled) }
    }

    fun setNotifyAppErrors(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setNotifyAppErrors(enabled) }
    }

    fun setAnalyticsEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setAnalyticsEnabled(enabled) }
    }

    fun setCrashReportingEnabled(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setCrashReportingEnabled(enabled) }
    }

    fun setDebugMode(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setDebugMode(enabled) }
    }

    fun setExperimentalFeatures(enabled: Boolean) {
        viewModelScope.launch { appSettingsRepository.setExperimentalFeatures(enabled) }
    }

    // ── Destructive operations ───────────────────────────────────────────

    fun signOut() {
        signOutUseCase()
    }

    fun showAddProviderSheet() { _showAddProviderSheet.value = true }
    fun hideAddProviderSheet() { _showAddProviderSheet.value = false }

    fun showResetDialog() { _showResetDialog.value = true }
    fun hideResetDialog() { _showResetDialog.value = false }

    fun showDeleteProviderDialog(instanceId: String) {
        _showDeleteProviderDialog.value = instanceId
    }
    fun hideDeleteProviderDialog() {
        _showDeleteProviderDialog.value = null
    }

    /**
     * PRD §29 — Reset all non-credential settings to safe defaults.
     * API keys are preserved unless [clearCredentials] is true.
     */
    fun resetAllSettings(clearCredentials: Boolean = false) {
        viewModelScope.launch {
            appSettingsRepository.resetAllSettings()
            if (clearCredentials) {
                secureStorage.clearAllAiKeys()
                secureStorage.clearCachedModels()
                // Re-seed default providers (Gemini, OpenRouter — no keys)
                _providerInstances.value = emptyList()
                secureStorage.saveProviderInstances(emptyList())
                seedDefaultProviders()
            }
            _showResetDialog.value = false
            _events.tryEmit(AiSettingsEvent.SettingsReset)
        }
    }

    /**
     * PRD §26 — Clear cached data (model metadata, temporary files).
     */
    fun clearCachedModels() {
        secureStorage.clearCachedModels()
        _discoveredModels.value = emptyMap()
        _events.tryEmit(AiSettingsEvent.CacheCleared)
    }

    /**
     * PRD §26 — Clear all stored AI credentials.
     */
    fun clearAllCredentials() {
        viewModelScope.launch {
            secureStorage.clearAllAiKeys()
            secureStorage.clearCachedModels()
            // Update instances to reflect no keys
            val instances = _providerInstances.value.map {
                it.copy(apiKeyHint = "", isEnabled = false, selectedModel = null)
            }
            _providerInstances.value = instances
            secureStorage.saveProviderInstances(instances)
            _events.tryEmit(AiSettingsEvent.CredentialsCleared)
        }
    }
}
