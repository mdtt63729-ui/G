package com.gitofy.feature.ai.settings

import com.gitofy.core.designsystem.motion.gitofySlideFadeEnter
import com.gitofy.core.designsystem.motion.gitofySlideFadeExit

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewModelScope
import com.gitofy.ai.credentials.AiProvider
import com.gitofy.ai.health.ProviderHealthManager
import com.gitofy.ai.settings.AIPrivacyControls
import com.gitofy.ai.settings.ProviderInfo
import com.gitofy.ai.settings.UserAIPreferences
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.core.designsystem.tokens.Dimensions
import com.gitofy.feature.ai.components.ProviderConfigState
import com.gitofy.feature.ai.components.ProviderStatePill
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI Provider Settings Screen — PRD 2 Sections 17-20 / Phase 5 Section 10.
 *
 * Settings → AI Providers
 *
 * Each provider row shows: name, enabled/disabled state, configuration
 * state (icon + text), selected model, and an edit action. Grouped in a
 * clean M3 list rather than one dense settings screen.
 */
data class AIProviderSettingsUiState(
    val providers: List<ProviderDisplayInfo> = emptyList(),
    val isLoading: Boolean = false,
    val isDegraded: Boolean = false,
    val degradedMessage: String = ""
)

data class ProviderDisplayInfo(
    val provider: AiProvider,
    val displayName: String,
    val configState: ProviderConfigState,
    val statusDetail: String,
    val selectedModel: String?,
    val keyHint: String?,
    val description: String,
    val enabled: Boolean
)

@HiltViewModel
class AIProviderSettingsViewModel @Inject constructor(
    private val healthManager: ProviderHealthManager,
    private val preferences: UserAIPreferences,
    private val privacyControls: AIPrivacyControls
) : ViewModel() {

    private val _uiState = MutableStateFlow(AIProviderSettingsUiState(isLoading = true))
    val uiState = _uiState.asStateFlow()

    init { loadProviders() }

    fun loadProviders() {
        viewModelScope.launch {
            val providers = AiProvider.entries.map { provider ->
                val health = healthManager.getHealth(provider)
                val info = ProviderInfo.getProviderInfo(provider)
                val configState = when {
                    health.state == ProviderHealthManager.HealthState.NOT_CONFIGURED -> ProviderConfigState.NOT_CONFIGURED
                    health.state == ProviderHealthManager.HealthState.INVALID_CREDENTIAL ||
                        health.state == ProviderHealthManager.HealthState.UNAVAILABLE -> ProviderConfigState.ERROR
                    health.state == ProviderHealthManager.HealthState.HEALTHY -> ProviderConfigState.ACTIVE
                    else -> ProviderConfigState.CONFIGURED
                }
                ProviderDisplayInfo(
                    provider = provider,
                    displayName = provider.displayName,
                    configState = configState,
                    statusDetail = health.displayStatus,
                    selectedModel = null, // Sourced from credential store's saved model, when configured.
                    keyHint = null, // Would be loaded from credential store
                    description = info?.description ?: "",
                    enabled = configState != ProviderConfigState.NOT_CONFIGURED
                )
            }
            val degraded = healthManager.getDegradedModeStatus()
            _uiState.value = AIProviderSettingsUiState(
                providers = providers,
                isLoading = false,
                isDegraded = degraded.isDegraded,
                degradedMessage = degraded.message
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIProviderSettingsScreen(
    onBack: () -> Unit,
    onChangeKey: (AiProvider) -> Unit,
    viewModel: AIProviderSettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { GITOFYTopAppBar(title = "AI Providers", onBack = onBack) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding)) {
            // Degraded mode warning — PRD Section 72
            AnimatedVisibility(visible = uiState.isDegraded, enter = gitofySlideFadeEnter, exit = gitofySlideFadeExit) {
                Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.padding(LocalSpacing.current.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            Icons.Filled.ErrorOutline,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onErrorContainer,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(
                            uiState.degradedMessage,
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onErrorContainer
                        )
                    }
                }
            }

            if (uiState.isLoading) {
                Box(modifier = Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    CircularProgressIndicator()
                }
            } else {
                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    contentPadding = PaddingValues(LocalSpacing.current.lg),
                    verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
                ) {
                    items(uiState.providers, key = { it.provider.name }) { providerInfo ->
                        ProviderSettingsCard(providerInfo, onChangeKey)
                    }
                }
            }
        }
    }
}

@Composable
private fun ProviderSettingsCard(info: ProviderDisplayInfo, onChangeKey: (AiProvider) -> Unit) {
    GITOFYCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(info.displayName, style = MaterialTheme.typography.titleSmall)
                    if (!info.provider.isMandatory) {
                        Text(
                            "Optional",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                ProviderStatePill(state = info.configState)
            }
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                info.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 2,
                overflow = TextOverflow.Ellipsis
            )
            if (info.selectedModel != null) {
                Spacer(modifier = Modifier.height(4.dp))
                Text(
                    "Model: ${info.selectedModel}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            info.keyHint?.let {
                Text(
                    "API Key: $it",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Spacer(modifier = Modifier.height(10.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                if (info.configState == ProviderConfigState.NOT_CONFIGURED) {
                    Button(onClick = { onChangeKey(info.provider) }) {
                        Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Connect")
                    }
                } else {
                    TextButton(onClick = { onChangeKey(info.provider) }) {
                        Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Edit")
                    }
                    TextButton(onClick = { /* Test connection */ }) { Text("Test Connection") }
                    if (info.provider != AiProvider.CUSTOM) {
                        TextButton(onClick = { /* Remove */ }) {
                            Text("Remove", color = MaterialTheme.colorScheme.error)
                        }
                    }
                }
            }
        }
    }
}

/**
 * AI Privacy Settings Screen — PRD 2 Section 53 / Phase 5 Section 11.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AIPrivacySettingsScreen(
    onBack: () -> Unit
) {
    var allowSourceCode by remember { mutableStateOf(true) }
    var allowProjectFiles by remember { mutableStateOf(true) }
    var excludeSecrets by remember { mutableStateOf(true) }
    var confirmLargeUpload by remember { mutableStateOf(true) }

    Scaffold(topBar = { GITOFYTopAppBar(title = "AI Privacy", onBack = onBack) }) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(LocalSpacing.current.lg),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
        ) {
            Text("AI Privacy", style = MaterialTheme.typography.titleMedium)
            Text(
                "Gito sends only the context you choose to your selected provider. " +
                    "Nothing is uploaded to GITOFY's own servers.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            SwitchItem(
                "Allow Source Code to AI",
                "Sends selected file contents to the active provider.",
                allowSourceCode
            ) { allowSourceCode = it }
            SwitchItem(
                "Allow Project Files",
                "Sends non-code project files (configs, docs) when relevant.",
                allowProjectFiles
            ) { allowProjectFiles = it }
            SwitchItem(
                "Exclude Secret Files",
                "Automatically strips known secret/credential file patterns.",
                excludeSecrets
            ) { excludeSecrets = it }
            SwitchItem(
                "Confirm Before Large Upload",
                "Asks before sending unusually large context payloads.",
                confirmLargeUpload
            ) { confirmLargeUpload = it }
            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))
            Text("Default Exclusions", style = MaterialTheme.typography.titleSmall)
            Text(
                "local.properties, .env, *.pem, *.key, credentials.*, service-account*.json, secrets.*",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(8.dp))
            OutlinedButton(
                onClick = { /* Clear AI Session Data */ },
                modifier = Modifier.heightIn(min = Dimensions.minTouchTarget)
            ) { Text("Clear AI Session Data") }
        }
    }
}

@Composable
private fun SwitchItem(
    label: String,
    supportingText: String,
    checked: Boolean,
    onChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.minTouchTarget),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(label, style = MaterialTheme.typography.bodyMedium)
            Text(supportingText, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
