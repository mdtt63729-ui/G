package com.gitofy.feature.ai.setup

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.ai.credentials.AiProvider
import com.gitofy.ai.setup.AISetupViewModel
import com.gitofy.ai.setup.ProviderStatus
import com.gitofy.ai.setup.SetupStep
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.theme.LocalSpacing

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISetupWizardScreen(onComplete: () -> Unit, viewModel: AISetupViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    Scaffold(topBar = { GITOFYTopAppBar(title = "AI Setup", onBack = null) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            when (uiState.step) {
                SetupStep.INTRODUCTION -> IntroductionStep(viewModel)
                SetupStep.PROVIDER_CONFIG -> ProviderConfigStep(viewModel)
                SetupStep.SECURITY_CONFIRMATION -> { LaunchedEffect(Unit) { viewModel.showSecurityConfirmation() }; Text("Confirming...", modifier = Modifier.padding(16.dp)) }
                SetupStep.COMPLETE -> CompleteStep(onComplete)
            }
        }
    }
    if (uiState.showSecurityConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSecurityConfirmation() },
            title = { Text("Security Notice") },
            text = { Text("Your API keys are stored encrypted on this device.\n\nGITOFY does not upload your API keys to its own server.\n\nAI requests may send your selected code/content to the AI provider you choose.") },
            confirmButton = { TextButton(onClick = { viewModel.dismissSecurityConfirmation(); viewModel.completeSetup() }) { Text("I've Understood") } },
            dismissButton = { TextButton(onClick = { viewModel.dismissSecurityConfirmation() }) { Text("Back") } }
        )
    }
}

@Composable
private fun IntroductionStep(viewModel: AISetupViewModel) {
    Column(modifier = Modifier.padding(LocalSpacing.current.lg), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Default.Psychology, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Power your coding workflow with multiple AI providers.", style = MaterialTheme.typography.headlineSmall)
        listOf("Gemini", "OpenAI", "NVIDIA NIM", "OpenRouter", "OpenCode Zen", "Sarvam AI", "Optional Custom Provider").forEach { Text("• $it") }
        Text("Keys are stored securely using Android Keystore encryption.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Button(onClick = { viewModel.goToStep(SetupStep.PROVIDER_CONFIG) }, modifier = Modifier.fillMaxWidth()) { Text("Continue") }
    }
}

@Composable
private fun ProviderConfigStep(viewModel: AISetupViewModel) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    Column(modifier = Modifier.padding(LocalSpacing.current.lg), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("AI Setup", style = MaterialTheme.typography.titleLarge)
        Text("${uiState.configuredCount} / ${uiState.totalMandatory} required providers configured", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        LinearProgressIndicator(progress = { if (uiState.totalMandatory > 0) uiState.configuredCount.toFloat() / uiState.totalMandatory else 0f }, modifier = Modifier.fillMaxWidth())
        AiProvider.entries.forEach { provider ->
            val state = uiState.providerStates[provider] ?: com.gitofy.ai.setup.ProviderSetupState()
            ProviderCard(provider, state, viewModel)
        }
        Button(onClick = { viewModel.showSecurityConfirmation() }, enabled = uiState.canFinish, modifier = Modifier.fillMaxWidth()) {
            Text(if (uiState.canFinish) "Finish Setup" else "Configure all mandatory providers")
        }
    }
}

@Composable
private fun ProviderCard(provider: AiProvider, state: com.gitofy.ai.setup.ProviderSetupState, viewModel: AISetupViewModel) {
    GITOFYCard(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(provider.displayName, style = MaterialTheme.typography.titleSmall, modifier = Modifier.weight(1f))
                if (!provider.isMandatory) Text("Optional", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
            }
            val (statusText, statusColor) = when (state.status) {
                ProviderStatus.CONNECTED -> "Connected" to MaterialTheme.colorScheme.primary
                ProviderStatus.VALIDATING -> "Validating..." to MaterialTheme.colorScheme.tertiary
                ProviderStatus.INVALID -> "Invalid" to MaterialTheme.colorScheme.error
                else -> "Not configured" to MaterialTheme.colorScheme.onSurfaceVariant
            }
            Text(statusText, style = MaterialTheme.typography.bodySmall, color = statusColor)
            if (state.status != ProviderStatus.CONNECTED) {
                Spacer(modifier = Modifier.height(8.dp))
                OutlinedTextField(
                    value = state.apiKeyInput, onValueChange = { viewModel.updateApiKey(provider, it) },
                    label = { Text("API Key") }, modifier = Modifier.fillMaxWidth(), singleLine = true,
                    visualTransformation = if (state.apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                    trailingIcon = { IconButton(onClick = { viewModel.toggleApiKeyVisibility(provider) }) { Icon(if (state.apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility, contentDescription = "Show/Hide") } },
                    isError = state.error != null, supportingText = state.error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                )
                Button(onClick = { viewModel.validateProvider(provider) }, enabled = state.apiKeyInput.isNotBlank() && state.status != ProviderStatus.VALIDATING) { Text("Validate") }
            } else {
                Spacer(modifier = Modifier.height(8.dp))
                Text("API Key: ${state.keyHint}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { viewModel.removeProvider(provider) }) { Text("Remove", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun CompleteStep(onComplete: () -> Unit) {
    Column(modifier = Modifier.fillMaxSize().padding(LocalSpacing.current.lg), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Setup Complete!", style = MaterialTheme.typography.headlineSmall)
        Text("All required AI providers connected.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text("Your API keys are encrypted and stored securely on this device.", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth()) { Text("Get Started") }
    }
}
