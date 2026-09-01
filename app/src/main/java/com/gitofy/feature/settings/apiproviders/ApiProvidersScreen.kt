package com.gitofy.feature.settings.apiproviders


import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.input.VisualTransformation
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.ai.provider.client.ApiTestResult
import com.gitofy.ai.provider.client.DiscoveredModel
import com.gitofy.ai.provider.registry.ProviderDefinition
import com.gitofy.ai.provider.registry.ProviderInstance
import com.gitofy.core.designsystem.components.GITOFYButton
import com.gitofy.core.designsystem.components.GITOFYButtonType
import com.gitofy.core.designsystem.components.GITOFYCard
import com.gitofy.core.designsystem.components.GITOFYTopAppBar
import com.gitofy.core.designsystem.components.SettingRow
import com.gitofy.core.designsystem.components.SettingRowDivider
import com.gitofy.core.designsystem.theme.LocalSpacing
import com.gitofy.feature.settings.SettingsViewModel

/**
 * PRD §8 — API Providers screen with provider list, add-provider sheet,
 * provider config page, test connection and model discovery.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ApiProvidersScreen(
    onBack: () -> Unit,
    viewModel: SettingsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var selectedInstance by remember { mutableStateOf<ProviderInstance?>(null) }
    var showAddSheet by remember { mutableStateOf(false) }
    var searchQuery by remember { mutableStateOf("") }
    var showDeleteDialog by remember { mutableStateOf<String?>(null) }

    val snackbarHostState = remember { SnackbarHostState() }

    // Collect one-shot events
    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is com.gitofy.core.settings.AiSettingsEvent.Error ->
                    snackbarHostState.showSnackbar(event.message)
                else -> {}
            }
        }
    }

    if (selectedInstance != null) {
        ProviderConfigPage(
            instance = selectedInstance!!,
            viewModel = viewModel,
            onBack = { selectedInstance = null }
        )
        return
    }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        snackbarHost = { SnackbarHost(snackbarHostState) },
        topBar = {
            GITOFYTopAppBar(
                title = "API Providers",
                onBack = onBack,
                actions = {
                    IconButton(onClick = { showAddSheet = true }) {
                        Icon(Icons.Default.Add, contentDescription = "Add Provider")
                    }
                }
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = { showAddSheet = true },
                icon = { Icon(Icons.Default.Add, contentDescription = null) },
                text = { Text("Add Provider") },
                containerColor = MaterialTheme.colorScheme.primaryContainer,
                contentColor = MaterialTheme.colorScheme.onPrimaryContainer
            )
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            item {
                Text(
                    "Manage your AI provider connections. Add API keys, test connections, and select models.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.sm)
                )
            }

            items(
                items = uiState.providerInstances,
                key = { it.instanceId }
            ) { instance ->
                ProviderCard(
                    instance = instance,
                    testResult = uiState.testResults[instance.instanceId],
                    onClick = { selectedInstance = instance },
                    onSetDefault = { viewModel.setDefaultProvider(instance.instanceId) },
                    onToggleEnabled = { viewModel.setProviderEnabled(instance.instanceId, !instance.isEnabled) },
                    onDelete = { showDeleteDialog = instance.instanceId }
                )
            }

            if (uiState.providerInstances.isEmpty()) {
                item {
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Text(
                            "No providers configured. Tap 'Add Provider' to get started.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }
        }
    }

    // PRD §9 — Add Provider bottom sheet
    if (showAddSheet) {
        AddProviderSheet(
            definitions = uiState.providerDefinitions,
            existingInstanceIds = uiState.providerInstances.map { it.definitionId }.toSet(),
            searchQuery = searchQuery,
            onSearchQueryChange = { searchQuery = it },
            onSelect = { defId ->
                viewModel.addProvider(defId)
                showAddSheet = false
                searchQuery = ""
            },
            onDismiss = {
                showAddSheet = false
                searchQuery = ""
            }
        )
    }

    // PRD §53 — Delete confirmation
    showDeleteDialog?.let { instanceId ->
        AlertDialog(
            onDismissRequest = { showDeleteDialog = null },
            title = { Text("Delete Provider") },
            text = { Text("Are you sure you want to remove this provider? This will delete the API key and configuration.") },
            confirmButton = {
                TextButton(onClick = {
                    viewModel.deleteProvider(instanceId)
                    showDeleteDialog = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            },
            dismissButton = {
                TextButton(onClick = { showDeleteDialog = null }) { Text("Cancel") }
            }
        )
    }
}

// ── Provider Card ──────────────────────────────────────────────────────────

@Composable
private fun ProviderCard(
    instance: ProviderInstance,
    testResult: ApiTestResult?,
    onClick: () -> Unit,
    onSetDefault: () -> Unit,
    onToggleEnabled: () -> Unit,
    onDelete: () -> Unit
) {
    GITOFYCard(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = LocalSpacing.current.lg),
        onClick = onClick
    ) {
        Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        instance.displayName,
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.Medium
                    )
                    Text(
                        if (instance.apiKeyHint.isNotBlank()) instance.apiKeyHint
                        else "Not configured",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                // Status badge
                when (testResult) {
                    is ApiTestResult.Success -> StatusChip("Connected", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, Icons.Default.CheckCircle)
                    is ApiTestResult.Testing -> CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                    is ApiTestResult.Failed -> StatusChip("Error", MaterialTheme.colorScheme.errorContainer, MaterialTheme.colorScheme.error, Icons.Default.Error)
                    else -> {
                        if (instance.apiKeyHint.isNotBlank()) {
                            StatusChip("Saved", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.Cloud)
                        } else {
                            StatusChip("Not set", MaterialTheme.colorScheme.surfaceVariant, MaterialTheme.colorScheme.onSurfaceVariant, Icons.Default.CloudOff)
                        }
                    }
                }
            }

            // Default model
            instance.selectedModel?.let { model ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    "Model: $model",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            // Error message
            (testResult as? ApiTestResult.Failed)?.let { result ->
                Spacer(modifier = Modifier.height(2.dp))
                Text(
                    result.message,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            // Actions
            Spacer(modifier = Modifier.height(8.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                if (instance.isDefault) {
                    StatusChip("Default", MaterialTheme.colorScheme.primaryContainer, MaterialTheme.colorScheme.primary, Icons.Default.Star)
                } else {
                    TextButton(onClick = onSetDefault, contentPadding = PaddingValues(horizontal = 12.dp, vertical = 0.dp)) {
                        Text("Set Default", style = MaterialTheme.typography.labelSmall)
                    }
                }
                Spacer(modifier = Modifier.weight(1f))
                Switch(
                    checked = instance.isEnabled,
                    onCheckedChange = { onToggleEnabled() },
                    modifier = Modifier.scale(0.8f)
                )
                IconButton(onClick = onDelete, modifier = Modifier.size(32.dp)) {
                    Icon(Icons.Default.Delete, contentDescription = "Delete", tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(18.dp))
                }
            }
        }
    }
}

@Composable
private fun StatusChip(
    text: String,
    containerColor: androidx.compose.ui.graphics.Color,
    contentColor: androidx.compose.ui.graphics.Color,
    icon: androidx.compose.ui.graphics.vector.ImageVector
) {
    Surface(
        color = containerColor,
        shape = RoundedCornerShape(999.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(icon, contentDescription = null, modifier = Modifier.size(14.dp), tint = contentColor)
            Spacer(modifier = Modifier.width(4.dp))
            Text(text, style = MaterialTheme.typography.labelSmall, color = contentColor)
        }
    }
}

// ── Add Provider Sheet — PRD §9 ───────────────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun AddProviderSheet(
    definitions: List<ProviderDefinition>,
    existingInstanceIds: Set<String>,
    searchQuery: String,
    onSearchQueryChange: (String) -> Unit,
    onSelect: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val filtered = if (searchQuery.isBlank()) definitions
    else definitions.filter {
        it.displayName.contains(searchQuery, ignoreCase = true) ||
        it.description.contains(searchQuery, ignoreCase = true)
    }

    ModalBottomSheet(onDismissRequest = onDismiss) {
        Column(modifier = Modifier.fillMaxWidth()) {
            Text(
                "Add Provider",
                style = MaterialTheme.typography.titleLarge,
                fontWeight = FontWeight.Bold,
                modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.sm)
            )
            OutlinedTextField(
                value = searchQuery,
                onValueChange = onSearchQueryChange,
                placeholder = { Text("Search providers...") },
                leadingIcon = { Icon(Icons.Default.Search, contentDescription = null) },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = LocalSpacing.current.lg),
                singleLine = true
            )
            Spacer(modifier = Modifier.height(LocalSpacing.current.sm))

            LazyColumn(
                modifier = Modifier.heightIn(max = 400.dp),
                contentPadding = PaddingValues(bottom = 32.dp)
            ) {
                items(
                    items = filtered,
                    key = { it.id }
                ) { def ->
                    val isAdded = def.id in existingInstanceIds && def.id != "custom"
                    ProviderSelectorRow(
                        definition = def,
                        isAdded = isAdded,
                        onClick = { onSelect(def.id) }
                    )
                }
            }
        }
    }
}

@Composable
private fun ProviderSelectorRow(
    definition: ProviderDefinition,
    isAdded: Boolean,
    onClick: () -> Unit
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(enabled = !isAdded, onClick = onClick)
            .padding(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.md),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Surface(
            shape = RoundedCornerShape(10.dp),
            color = MaterialTheme.colorScheme.primaryContainer,
            modifier = Modifier.size(36.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Icon(
                    Icons.Default.Cloud,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp),
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }
        }
        Spacer(modifier = Modifier.width(LocalSpacing.current.md))
        Column(modifier = Modifier.weight(1f)) {
            Text(definition.displayName, style = MaterialTheme.typography.bodyMedium)
            Text(
                definition.description,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1
            )
        }
        if (isAdded) {
            Icon(Icons.Default.Check, contentDescription = "Added", tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
        }
    }
}

// ── Provider Configuration Page — PRD §13 ─────────────────────────────────

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ProviderConfigPage(
    instance: ProviderInstance,
    viewModel: SettingsViewModel,
    onBack: () -> Unit
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    // SettingsViewModel replaces the ProviderInstance after every save/model
    // change. Do not keep using the original navigation snapshot or the page
    // can display stale endpoint/model state.
    val currentInstance = uiState.providerInstances
        .firstOrNull { it.instanceId == instance.instanceId } ?: instance
    val testResult = uiState.testResults[currentInstance.instanceId]
    val discoveredModels = uiState.discoveredModels[currentInstance.instanceId] ?: emptyList()
    val isLoadingModels = currentInstance.instanceId in uiState.loadingModels

    var apiKey by remember(currentInstance.instanceId) { mutableStateOf("") }
    var endpoint by remember(currentInstance.instanceId) { mutableStateOf(currentInstance.endpoint) }

    LaunchedEffect(currentInstance.endpoint) {
        endpoint = currentInstance.endpoint
    }
    var showKey by remember { mutableStateOf(false) }
    var showModelPicker by remember { mutableStateOf(false) }

    Scaffold(
        containerColor = MaterialTheme.colorScheme.background,
        topBar = {
            GITOFYTopAppBar(title = currentInstance.displayName, onBack = onBack)
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .background(MaterialTheme.colorScheme.background),
            contentPadding = PaddingValues(vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(2.dp)
        ) {
            // API Key
            item {
                SectionHeaderSmall("API Key")
                GITOFYCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LocalSpacing.current.lg)
                ) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                        OutlinedTextField(
                            value = apiKey,
                            onValueChange = { apiKey = it },
                            label = { Text("API Key") },
                            singleLine = true,
                            visualTransformation = if (showKey) VisualTransformation.None else PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password),
                            trailingIcon = {
                                IconButton(onClick = { showKey = !showKey }) {
                                    Icon(
                                        if (showKey) Icons.Default.VisibilityOff else Icons.Default.Visibility,
                                        contentDescription = if (showKey) "Hide" else "Show"
                                    )
                                }
                            },
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (currentInstance.apiKeyHint.isNotBlank()) {
                            Spacer(modifier = Modifier.height(4.dp))
                            Text(
                                "Current: ${currentInstance.apiKeyHint}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                }
            }

            // Endpoint
            item {
                SectionHeaderSmall("Endpoint")
                GITOFYCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LocalSpacing.current.lg)
                ) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it },
                            label = { Text("API Endpoint") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            }

            // Default Model
            item {
                SectionHeaderSmall("Default Model")
                GITOFYCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LocalSpacing.current.lg),
                    onClick = { showModelPicker = true }
                ) {
                    Row(
                        modifier = Modifier.padding(LocalSpacing.current.md),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Model", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                currentInstance.selectedModel ?: "No model selected",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Icon(Icons.Default.ExpandMore, contentDescription = null)
                    }
                }
            }

            // Status
            item {
                SectionHeaderSmall("Status")
                GITOFYCard(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = LocalSpacing.current.lg)
                ) {
                    Column(modifier = Modifier.padding(LocalSpacing.current.md)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            when (testResult) {
                                is ApiTestResult.Success -> {
                                    Icon(Icons.Default.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Connection successful", color = MaterialTheme.colorScheme.primary)
                                    testResult.modelCount?.let { count ->
                                        Text(" • $count models available", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    }
                                }
                                is ApiTestResult.Testing -> {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Testing connection...")
                                }
                                is ApiTestResult.Failed -> {
                                    Icon(Icons.Default.Error, contentDescription = null, tint = MaterialTheme.colorScheme.error, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text(testResult.message, color = MaterialTheme.colorScheme.error)
                                }
                                else -> {
                                    Icon(Icons.Default.Help, contentDescription = null, tint = MaterialTheme.colorScheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
                                    Spacer(modifier = Modifier.width(8.dp))
                                    Text("Not tested", color = MaterialTheme.colorScheme.onSurfaceVariant)
                                }
                            }
                        }
                    }
                }
            }

            // Actions
            item {
                Column(modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = LocalSpacing.current.sm)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        GITOFYButton(
                            text = "Save",
                            onClick = {
                                viewModel.saveProviderConfig(
                                    instanceId = currentInstance.instanceId,
                                    apiKey = apiKey,
                                    endpoint = endpoint,
                                    modelId = currentInstance.selectedModel
                                )
                            },
                            modifier = Modifier.weight(1f)
                        )
                        GITOFYButton(
                            text = "Test",
                            onClick = { viewModel.testConnection(currentInstance.instanceId) },
                            type = GITOFYButtonType.Outlined,
                            modifier = Modifier.weight(1f)
                        )
                    }
                    Spacer(modifier = Modifier.height(8.dp))
                    GITOFYButton(
                        text = if (isLoadingModels) "Loading models..." else "Load Models",
                        onClick = { viewModel.loadModels(currentInstance.instanceId) },
                        type = GITOFYButtonType.Tonal,
                        fullWidth = true,
                        loading = isLoadingModels
                    )
                }
            }

            // Discovered models
            if (discoveredModels.isNotEmpty()) {
                item { SectionHeaderSmall("Available Models") }
                items(discoveredModels, key = { it.id }) { model ->
                    val isSelected = currentInstance.selectedModel == model.id
                    SettingRow(
                        title = model.displayName,
                        supportingText = model.id,
                        icon = if (isSelected) Icons.Default.RadioButtonChecked else Icons.Default.RadioButtonUnchecked,
                        onClick = {
                            viewModel.selectProviderModel(
                                instanceId = currentInstance.instanceId,
                                modelId = model.id
                            )
                        }
                    )
                    SettingRowDivider()
                }
            }
        }
    }

    // Model picker dialog
    if (showModelPicker) {
        ModelPickerDialog(
            models = discoveredModels,
            currentModel = currentInstance.selectedModel,
            onPick = { modelId ->
                viewModel.selectProviderModel(
                    instanceId = currentInstance.instanceId,
                    modelId = modelId
                )
                showModelPicker = false
            },
            onLoadModels = { viewModel.loadModels(currentInstance.instanceId) },
            onDismiss = { showModelPicker = false }
        )
    }
}


@Composable
private fun SectionHeaderSmall(title: String) {
    Text(
        title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(horizontal = LocalSpacing.current.lg, vertical = 4.dp)
    )
}

@Composable
private fun ModelPickerDialog(
    models: List<DiscoveredModel>,
    currentModel: String?,
    onPick: (String) -> Unit,
    onLoadModels: () -> Unit,
    onDismiss: () -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Select Model") },
        text = {
            if (models.isEmpty()) {
                Column {
                    Text("No models loaded yet.")
                    Spacer(modifier = Modifier.height(8.dp))
                    TextButton(onClick = onLoadModels) { Text("Load Models") }
                }
            } else {
                LazyColumn(modifier = Modifier.heightIn(max = 300.dp)) {
                    items(models, key = { it.id }) { model ->
                        Row(
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { onPick(model.id) }
                                .padding(vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            RadioButton(
                                selected = currentModel == model.id,
                                onClick = { onPick(model.id) }
                            )
                            Spacer(modifier = Modifier.width(8.dp))
                            Column {
                                Text(model.displayName, style = MaterialTheme.typography.bodyMedium)
                                Text(model.id, style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
