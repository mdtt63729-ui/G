package com.gitofy.feature.ai.setup

import com.gitofy.core.designsystem.motion.gitofySlideFadeEnter
import com.gitofy.core.designsystem.motion.gitofySlideFadeExit

import androidx.compose.animation.AnimatedVisibility
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
import com.gitofy.ai.setup.ProviderSetupState
import com.gitofy.ai.setup.ProviderStatus
import com.gitofy.ai.setup.SetupStep
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.theme.GITOFYStatusColors
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.core.designsystem.tokens.Dimensions

/**
 * AI Setup Screen — Phase 5 Section 9.
 *
 * AI Setup → Choose Provider → Configure Credentials → Choose Model
 *   → Test Connection → Complete
 *
 * Uses progressive disclosure: only one provider's credential form is
 * expanded at a time so the screen never shows every provider setting
 * simultaneously.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AISetupWizardScreen(onComplete: () -> Unit, viewModel: AISetupViewModel = hiltViewModel()) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(topBar = { GITOFYTopAppBar(title = "AI Setup", onBack = null) }) { padding ->
        Column(modifier = Modifier.fillMaxSize().padding(padding).verticalScroll(rememberScrollState())) {
            when (uiState.step) {
                SetupStep.INTRODUCTION -> IntroductionStep(viewModel)
                SetupStep.PROVIDER_CONFIG -> ProviderConfigStep(viewModel)
                SetupStep.SECURITY_CONFIRMATION -> {
                    LaunchedEffect(Unit) { viewModel.showSecurityConfirmation() }
                    Box(modifier = Modifier.fillMaxWidth().padding(32.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                SetupStep.COMPLETE -> CompleteStep(onComplete)
            }
        }
    }
    if (uiState.showSecurityConfirmation) {
        AlertDialog(
            onDismissRequest = { viewModel.dismissSecurityConfirmation() },
            icon = { Icon(Icons.Filled.Shield, contentDescription = null) },
            title = { Text("Security Notice") },
            text = {
                Text(
                    "Your API keys are stored encrypted on this device.\n\n" +
                        "GITOFY does not upload your API keys to its own server.\n\n" +
                        "AI requests may send your selected code/content to the AI provider you choose."
                )
            },
            confirmButton = {
                TextButton(onClick = { viewModel.dismissSecurityConfirmation(); viewModel.completeSetup() }) {
                    Text("I've Understood")
                }
            },
            dismissButton = {
                TextButton(onClick = { viewModel.dismissSecurityConfirmation() }) { Text("Back") }
            }
        )
    }
}

@Composable
private fun IntroductionStep(viewModel: AISetupViewModel) {
    Column(modifier = Modifier.padding(LocalSpacing.current.lg), verticalArrangement = Arrangement.spacedBy(16.dp)) {
        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Text("Power your coding workflow with multiple AI providers.", style = MaterialTheme.typography.headlineSmall)
        Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
            listOf("Gemini", "OpenAI", "NVIDIA NIM", "OpenRouter", "OpenCode Zen", "Sarvam AI", "Optional Custom Provider").forEach {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Filled.Circle, contentDescription = null, modifier = Modifier.size(6.dp), tint = MaterialTheme.colorScheme.onSurfaceVariant)
                    Spacer(modifier = Modifier.width(8.dp))
                    Text(it, style = MaterialTheme.typography.bodyMedium)
                }
            }
        }
        Text(
            "Keys are stored securely using Android Keystore encryption.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Button(
            onClick = { viewModel.goToStep(SetupStep.PROVIDER_CONFIG) },
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.minTouchTarget)
        ) { Text("Continue") }
    }
}

@Composable
private fun ProviderConfigStep(viewModel: AISetupViewModel) {
    val uiState = viewModel.uiState.collectAsStateWithLifecycle().value
    // Progressive disclosure: expand exactly one not-yet-connected provider at a time.
    var expandedProvider by remember {
        mutableStateOf(
            AiProvider.entries.firstOrNull { uiState.providerStates[it]?.status != ProviderStatus.CONNECTED }
        )
    }

    Column(modifier = Modifier.padding(LocalSpacing.current.lg), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("AI Setup", style = MaterialTheme.typography.titleLarge)
        Text(
            "${uiState.configuredCount} / ${uiState.totalMandatory} required providers configured",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        LinearProgressIndicator(
            progress = { if (uiState.totalMandatory > 0) uiState.configuredCount.toFloat() / uiState.totalMandatory else 0f },
            modifier = Modifier.fillMaxWidth()
        )
        AiProvider.entries.forEach { provider ->
            val state = uiState.providerStates[provider] ?: ProviderSetupState()
            ProviderCard(
                provider = provider,
                state = state,
                viewModel = viewModel,
                expanded = expandedProvider == provider,
                onToggleExpanded = {
                    expandedProvider = if (expandedProvider == provider) null else provider
                }
            )
        }
        Button(
            onClick = { viewModel.showSecurityConfirmation() },
            enabled = uiState.canFinish,
            modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.minTouchTarget)
        ) {
            Text(if (uiState.canFinish) "Finish Setup" else "Configure all mandatory providers")
        }
    }
}

@Composable
private fun statusIconAndColor(status: ProviderStatus): Pair<androidx.compose.ui.graphics.vector.ImageVector, androidx.compose.ui.graphics.Color> =
    when (status) {
        ProviderStatus.CONNECTED -> Icons.Filled.CheckCircle to GITOFYStatusColors.success
        ProviderStatus.VALIDATING -> Icons.Filled.Sync to GITOFYStatusColors.warning
        ProviderStatus.INVALID -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        ProviderStatus.NETWORK_ERROR -> Icons.Filled.WifiOff to MaterialTheme.colorScheme.error
        ProviderStatus.RATE_LIMITED -> Icons.Filled.Timer to GITOFYStatusColors.warning
        ProviderStatus.PROVIDER_ERROR -> Icons.Filled.ErrorOutline to MaterialTheme.colorScheme.error
        ProviderStatus.NOT_CONFIGURED -> Icons.Filled.RadioButtonUnchecked to androidx.compose.ui.graphics.Color.Gray
    }

private fun statusText(status: ProviderStatus): String = when (status) {
    ProviderStatus.CONNECTED -> "Connected"
    ProviderStatus.VALIDATING -> "Testing connection…"
    ProviderStatus.INVALID -> "Invalid API key"
    ProviderStatus.NETWORK_ERROR -> "Network error"
    ProviderStatus.RATE_LIMITED -> "Rate limited"
    ProviderStatus.PROVIDER_ERROR -> "Provider error"
    ProviderStatus.NOT_CONFIGURED -> "Not configured"
}

@Composable
private fun ProviderCard(
    provider: AiProvider,
    state: ProviderSetupState,
    viewModel: AISetupViewModel,
    expanded: Boolean,
    onToggleExpanded: () -> Unit
) {
    val (icon, tint) = statusIconAndColor(state.status)
    GITOFYCard(
        modifier = Modifier.fillMaxWidth(),
        onClick = if (state.status != ProviderStatus.CONNECTED) onToggleExpanded else null
    ) {
        Column(modifier = Modifier.padding(LocalSpacing.current.lg)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(icon, contentDescription = null, tint = tint, modifier = Modifier.size(20.dp))
                Spacer(modifier = Modifier.width(8.dp))
                Column(modifier = Modifier.weight(1f)) {
                    Text(provider.displayName, style = MaterialTheme.typography.titleSmall)
                    Text(statusText(state.status), style = MaterialTheme.typography.bodySmall, color = tint)
                }
                if (!provider.isMandatory) {
                    Text("Optional", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                }
                if (state.status != ProviderStatus.CONNECTED) {
                    Icon(
                        if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                        contentDescription = if (expanded) "Collapse" else "Expand",
                        modifier = Modifier.size(20.dp)
                    )
                }
            }

            if (state.status == ProviderStatus.CONNECTED) {
                Spacer(modifier = Modifier.height(8.dp))
                Text("API Key: ${state.keyHint}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                TextButton(onClick = { viewModel.removeProvider(provider) }) {
                    Text("Remove", color = MaterialTheme.colorScheme.error)
                }
            }

            AnimatedVisibility(
                visible = expanded && state.status != ProviderStatus.CONNECTED,
                enter = gitofySlideFadeEnter,
                exit = gitofySlideFadeExit
            ) {
                Column {
                    Spacer(modifier = Modifier.height(8.dp))
                    OutlinedTextField(
                        value = state.apiKeyInput,
                        onValueChange = { viewModel.updateApiKey(provider, it) },
                        label = { Text("API Key") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true,
                        visualTransformation = if (state.apiKeyVisible) VisualTransformation.None else PasswordVisualTransformation(),
                        trailingIcon = {
                            IconButton(onClick = { viewModel.toggleApiKeyVisibility(provider) }) {
                                Icon(
                                    if (state.apiKeyVisible) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                    contentDescription = "Show/Hide"
                                )
                            }
                        },
                        isError = state.error != null,
                        supportingText = state.error?.let { { Text(it, color = MaterialTheme.colorScheme.error) } }
                    )
                    Spacer(modifier = Modifier.height(8.dp))
                    Button(
                        onClick = { viewModel.validateProvider(provider) },
                        enabled = state.apiKeyInput.isNotBlank() && state.status != ProviderStatus.VALIDATING,
                        modifier = Modifier.heightIn(min = Dimensions.minTouchTarget)
                    ) {
                        if (state.status == ProviderStatus.VALIDATING) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("Testing connection…")
                        } else {
                            Text("Test Connection")
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun CompleteStep(onComplete: () -> Unit) {
    Column(
        modifier = Modifier.fillMaxSize().padding(LocalSpacing.current.lg),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Icon(Icons.Default.CheckCircle, contentDescription = null, modifier = Modifier.size(64.dp), tint = MaterialTheme.colorScheme.primary)
        Spacer(modifier = Modifier.height(12.dp))
        Text("Setup Complete!", style = MaterialTheme.typography.headlineSmall)
        Text("All required AI providers connected.", style = MaterialTheme.typography.bodyMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
        Text(
            "Your API keys are encrypted and stored securely on this device.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.height(24.dp))
        Button(onClick = onComplete, modifier = Modifier.fillMaxWidth().heightIn(min = Dimensions.minTouchTarget)) {
            Text("Get Started")
        }
    }
}
