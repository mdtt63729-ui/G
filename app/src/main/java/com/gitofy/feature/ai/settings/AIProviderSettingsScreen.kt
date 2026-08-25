package com.gitofy.feature.ai.settings

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
import com.gitofy.ai.settings.UserAIPreferences
import com.gitofy.ai.settings.ProviderInfo
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.theme.LocalSpacing
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * AI Provider Settings Screen — PRD 2 Sections 17-20.
 *
 * Settings → AI Providers
 *
 * Display: Provider name, Connected status, API key hint, Default model, Connection health.
 * Actions: Change API Key, Test Connection, Remove Key.
 */
data class AIProviderSettingsUiState(
    val providers: List<ProviderDisplayInfo> = emptyList(),
    val isLoading: Boolean = false
)

data class ProviderDisplayInfo(
    val provider: AiProvider,
    val displayName: String,
    val isConnected: Boolean,
    val healthStatus: String,
    val keyHint: String?,
    val description: String
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
                ProviderDisplayInfo(
                    provider = provider,
                    displayName = provider.displayName,
                    isConnected = health.isAvailable,
                    healthStatus = health.displayStatus,
                    keyHint = null, // Would be loaded from credential store
                    description = info?.description ?: ""
                )
            }
            _uiState.value = AIProviderSettingsUiState(providers = providers, isLoading = false)
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
        // Degraded mode warning — PRD Section 72
        val degraded = remember { healthManager(viewModel).getDegradedModeStatus() }
        if (degraded.isDegraded) {
            Surface(color = MaterialTheme.colorScheme.errorContainer, modifier = Modifier.fillMaxWidth()) {
                Text(
                    "⚠ ${degraded.message}",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(LocalSpacing.current.md)
                )
            }
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(LocalSpacing.current.lg),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
        ) {
            items(uiState.providers) { providerInfo ->
                ProviderSettingsCard(providerInfo, onChangeKey)
            }
        }
    }
}

@Composable
private fun ProviderSettingsCard(info: ProviderDisplayInfo, onChangeKey: (AiProvider) -> Unit) {
    GITOFYCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(info.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (info.isConnected) {
                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Connected", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.primary)
                } else {
                    Text(info.healthStatus, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
            }
            Spacer(modifier = Modifier.height(4.dp))
            Text(info.description, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 2, overflow = TextOverflow.Ellipsis)
            info.keyHint?.let {
                Text("API Key: $it", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            Spacer(modifier = Modifier.height(8.dp))
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                TextButton(onClick = { onChangeKey(info.provider) }) { Text("Change API Key") }
                TextButton(onClick = { /* Test connection */ }) { Text("Test Connection") }
                if (info.isConnected && info.provider != AiProvider.CUSTOM) {
                    TextButton(onClick = { /* Remove */ }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
                }
            }
        }
    }
}

// Helper to access health manager from composable
@Composable
private fun healthManager(viewModel: AIProviderSettingsViewModel): ProviderHealthManager {
    // This is a workaround — in production, healthManager would be injected directly
    return ProviderHealthManager()
}

/**
 * AI Privacy Settings Screen — PRD 2 Section 53.
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
            modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState()).padding(LocalSpacing.current.lg),
            verticalArrangement = Arrangement.spacedBy(LocalSpacing.current.sm)
        ) {
            Text("AI Privacy", style = MaterialTheme.typography.titleMedium)
            SwitchItem("Allow Source Code to AI", allowSourceCode) { allowSourceCode = it }
            SwitchItem("Allow Project Files", allowProjectFiles) { allowProjectFiles = it }
            SwitchItem("Exclude Secret Files", excludeSecrets) { excludeSecrets = it }
            SwitchItem("Confirm Before Large Upload", confirmLargeUpload) { confirmLargeUpload = it }
            Divider()
            Text("Default Exclusions", style = MaterialTheme.typography.titleSmall)
            Text("local.properties, .env, *.pem, *.key, credentials.*, service-account*.json, secrets.*",
                 style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            TextButton(onClick = { /* Clear AI Session Data */ }) { Text("Clear AI Session Data") }
        }
    }
}

@Composable
private fun SwitchItem(label: String, checked: Boolean, onChange: (Boolean) -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
        Switch(checked = checked, onCheckedChange = onChange)
    }
}
