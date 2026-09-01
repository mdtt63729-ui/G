package com.gitofy.feature.ai

import com.gitofy.core.designsystem.motion.gitofySlideFadeEnter
import com.gitofy.core.designsystem.motion.gitofySlideFadeExit

import android.net.Uri
import java.io.File
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.animateScrollBy
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material3.*
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Chat
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.viewModelScope
import com.gitofy.ai.catalog.AIModelCatalog
import com.gitofy.ai.catalog.CostTier
import com.gitofy.ai.catalog.EndpointType
import com.gitofy.ai.catalog.ModelStatus
import com.gitofy.ai.credentials.AiProvider
import com.gitofy.core.designsystem.tokens.Dimensions
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.feature.ai.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

// ===========================================================================
// PRD PHASE 2: Gito branding (not "GITO AI")
// PRD PHASE 5: Architecture: UI → GitoViewModel → AIRepository → AIProvider → API
// ===========================================================================

data class AvailableModel(
    val modelId: String,
    val displayName: String,
    val providerName: String,
    val provider: AiProvider,
    val isFree: Boolean = true,
    val isConfigured: Boolean = false,
    val uniqueKey: String,
    val supportsImage: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsVideo: Boolean = false,
    val supportsFiles: Boolean = false,
    val codingScore: Int = 5,
    val reasoningScore: Int = 5,
    val qualityScore: Int = 5,
    // Universal AI Provider Support — set only for models derived from a
    // user-configured provider instance that isn't one of the 6 curated
    // AIModelCatalog providers (Anthropic, DeepSeek, Mistral, Groq, xAI,
    // Together, Fireworks, Cerebras, Cohere, Perplexity, HuggingFace,
    // Ollama, LM Studio, Custom). When set, chat routes through
    // InstanceChatClient by [protocol] instead of through AIGateway.
    val instanceId: String? = null,
    val protocol: com.gitofy.ai.provider.registry.ProviderProtocol? = null
)

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val timestamp: String? = null,
    val isStreaming: Boolean = false,
    // PRD §20: Chat message attachment-aware — attachments persist with their message
    val attachments: List<AttachmentData> = emptyList(),
    // Structured, real-time agent progress steps (read/edit/tool/etc.) shown
    // as collapsible chips while — and after — the agent works on a request.
    val steps: List<com.gitofy.ai.agent.AgentStepEvent> = emptyList()
)

enum class ChatRole { USER, ASSISTANT }

/**
 * PRD §11: Structured attachment model with upload state.
 * Tracks each file's name, MIME type, actual byte size, and processing state.
 */
data class AttachmentData(
    val id: String,
    val uri: String,
    val name: String,
    val mimeType: String?,
    val size: Long = 0,
    val state: AttachmentState = AttachmentState.READY,
    val errorMessage: String? = null
)

enum class AttachmentState {
    PENDING,
    UPLOADING,
    UPLOADED,
    PROCESSING,
    READY,
    FAILED
}

data class ChatConversation(
    val id: String,
    val title: String,
    val messages: List<ChatMessage> = emptyList(),
    val createdAt: Long = System.currentTimeMillis()
)

data class GitoAiUiState(
    val messages: List<ChatMessage> = emptyList(),
    val inputText: String = "",
    val isProcessing: Boolean = false,
    val isStreaming: Boolean = false,
    val availableModels: List<AvailableModel> = emptyList(),
    val selectedModel: AvailableModel? = null,
    val isAutoRoute: Boolean = false,
    val autoRouteModel: AvailableModel? = null,
    val showModelPicker: Boolean = false,
    val error: AIErrorInfo? = null,
    val activeContext: List<AIContextChipData> = emptyList(),
    val attachments: List<AttachmentData> = emptyList(),
    val conversations: List<ChatConversation> = emptyList(),
    val currentConversationId: String? = null,
    val showDrawer: Boolean = false
)

// ===========================================================================
// PRD PHASE 5: GitoViewModel — AIRepository pattern
// ===========================================================================

@HiltViewModel
class GitoAiViewModel @Inject constructor(
    @dagger.hilt.android.qualifiers.ApplicationContext private val appContext: android.content.Context,
    private val secureStorage: SecureCredentialStorage,
    private val catalog: AIModelCatalog,
    private val dynamicModelRepository: com.gitofy.ai.catalog.DynamicModelRepository,
    private val aiGateway: com.gitofy.ai.gateway.AIGateway,
    private val githubAiAgent: com.gitofy.ai.agent.GitHubAiAgent,
    private val instanceChatClient: com.gitofy.ai.provider.client.InstanceChatClient
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitoAiUiState())
    val uiState = _uiState.asStateFlow()

    private var streamingJob: Job? = null

    private val autoRouteModel = AUTO_ROUTE_MODEL

    init {
        loadAvailableModels()
        loadConversations()
        viewModelScope.launch {
            dynamicModelRepository.models.collect {
                loadAvailableModels()
            }
        }
        dynamicModelRepository.refreshInBackground()
    }

    private fun loadAvailableModels() {
        val models = mutableListOf<AvailableModel>()
        val configuredProviders = mutableSetOf<AiProvider>()

        // First pass: find which providers have non-blank API keys configured.
        // Older builds could save a key only under the provider-instance id;
        // migrate that key to the canonical id before building the picker so
        // curated providers do not appear configured in Settings but unusable
        // in Chat.
        val providerInstances = secureStorage.getProviderInstances()
        val canonicalIds = mapOf(
            "gemini" to AiProvider.GEMINI,
            "openai" to AiProvider.OPENAI,
            "nvidia" to AiProvider.NVIDIA_NIM,
            "nvidia-nim" to AiProvider.NVIDIA_NIM,
            "nvidia_nim" to AiProvider.NVIDIA_NIM,
            "openrouter" to AiProvider.OPENROUTER,
            "opencode" to AiProvider.OPENCODE_ZEN,
            "opencode-zen" to AiProvider.OPENCODE_ZEN,
            "opencode_zen" to AiProvider.OPENCODE_ZEN,
            "sarvam" to AiProvider.SARVAM
        )
        providerInstances.forEach { instance ->
            val canonical = canonicalIds[instance.definitionId.lowercase()] ?: return@forEach
            val instanceKey = secureStorage.getAiKey(instance.instanceId).orEmpty().trim()
            if (instanceKey.isNotBlank() && !secureStorage.hasAiKey(canonical.name)) {
                secureStorage.saveAiKey(canonical.name, instanceKey)
            }
        }

        for (catalogModel in catalog.getPickerModels()) {
            val isConfigured = secureStorage.getAiKey(catalogModel.provider.name).orEmpty().isNotBlank()
            if (isConfigured) configuredProviders.add(catalogModel.provider)
        }

        // Second pass: only add models from CONFIGURED providers
        // This implements the requirement: "jei provider er API add korbo sei provider er models show korbe"
        val pickerModels = if (configuredProviders.isNotEmpty()) {
            catalog.getPickerModelsForConfiguredProviders(configuredProviders)
        } else {
            // No providers configured yet — show all so user can see what's available
            catalog.getPickerModels()
        }

        for (catalogModel in pickerModels) {
            val isConfigured = secureStorage.getAiKey(catalogModel.provider.name).orEmpty().isNotBlank()

            models.add(
                AvailableModel(
                    modelId = catalogModel.id,
                    displayName = catalogModel.displayName,
                    providerName = catalogModel.provider.displayName,
                    provider = catalogModel.provider,
                    isFree = catalogModel.costTier == CostTier.FREE,
                    isConfigured = isConfigured,
                    uniqueKey = catalogModel.uniqueKey,
                    supportsImage = catalogModel.supportsImage,
                    supportsAudio = catalogModel.supportsAudio,
                    supportsVideo = catalogModel.supportsVideo,
                    supportsFiles = catalogModel.supportsText,
                    codingScore = catalogModel.codingScore,
                    reasoningScore = catalogModel.reasoningScore,
                    qualityScore = ((catalogModel.codingScore + catalogModel.reasoningScore + catalogModel.languageScore) / 3).coerceIn(1, 10)
                )
            )
        }

        // Dynamic registry — live provider models supersede static entries for
        // providers that successfully returned a model list. Cached results are
        // already exposed by DynamicModelRepository, so startup remains usable
        // while a refresh is in flight.
        val dynamicModels = dynamicModelRepository.models.value
        val dynamicByProvider = dynamicModels.groupBy { it.provider }
        dynamicByProvider.forEach { (provider, liveModels) ->
            val isConfigured = provider == AiProvider.OLLAMA ||
                secureStorage.getAiKey(provider.name).orEmpty().isNotBlank()
            if (!isConfigured) return@forEach
            liveModels.filter { it.status == ModelStatus.ACTIVE && it.costTier == CostTier.FREE }.forEach { model ->
                if (models.none { it.provider == model.provider && it.modelId == model.id && it.instanceId == null }) {
                    models.add(
                        AvailableModel(
                            modelId = model.id,
                            displayName = model.displayName,
                            providerName = model.provider.displayName,
                            provider = model.provider,
                            isFree = true,
                            isConfigured = true,
                            uniqueKey = model.uniqueKey,
                            supportsImage = model.supportsImage,
                            supportsAudio = model.supportsAudio,
                            supportsVideo = model.supportsVideo,
                            supportsFiles = model.supportsText,
                            codingScore = model.codingScore,
                            reasoningScore = model.reasoningScore,
                            qualityScore = ((model.codingScore + model.reasoningScore + model.languageScore) / 3).coerceIn(1, 10)
                        )
                    )
                }
            }
        }

        // Universal AI Provider Support — add one AvailableModel per model the
        // user has selected/discovered for every provider INSTANCE that is
        // configured with a real key but isn't one of the 6 curated
        // AIModelCatalog providers above (Anthropic, DeepSeek, Mistral, Groq,
        // xAI, Together, Fireworks, Cerebras, Cohere, Perplexity, HuggingFace,
        // Ollama, LM Studio, Custom). This is the fix for "added a valid key
        // but chat keeps saying to configure it" — previously these 14
        // providers had zero chat models regardless of the saved key.
        val curatedDefinitionIds = setOf("gemini", "openai", "nvidia_nim", "openrouter", "opencode_zen", "sarvam")
        val definitionsById = com.gitofy.ai.provider.registry.BuiltInProviders.all.associateBy { it.id }
        for (instance in secureStorage.getProviderInstances()) {
            if (instance.definitionId in curatedDefinitionIds) continue
            if (!secureStorage.hasAiKey(instance.instanceId)) continue
            val definition = definitionsById[instance.definitionId] ?: continue

            val candidateModelIds = listOfNotNull(instance.selectedModel)
                .ifEmpty { secureStorage.getCachedModels(instance.instanceId) }
                .ifEmpty { definition.defaultModels }
            if (candidateModelIds.isEmpty()) continue

            for (modelId in candidateModelIds.distinct()) {
                models.add(
                    AvailableModel(
                        modelId = modelId,
                        displayName = modelId,
                        providerName = instance.displayName.ifBlank { definition.displayName },
                        provider = AiProvider.CUSTOM,
                        isFree = false,
                        isConfigured = true,
                        uniqueKey = "CUSTOM:${instance.instanceId}:$modelId",
                        instanceId = instance.instanceId,
                        protocol = definition.protocol,
                        supportsImage = modelId.contains("vision", true) || modelId.contains("gemini", true) || modelId.contains("qwen-vl", true),
                        supportsFiles = true,
                        codingScore = if (modelId.contains("code", true) || modelId.contains("coder", true) || modelId.contains("deepseek", true) || modelId.contains("qwen", true) || modelId.contains("gpt", true)) 9 else 6,
                        reasoningScore = if (modelId.contains("reason", true) || modelId.contains("thinking", true) || modelId.contains("o1", true) || modelId.contains("o3", true)) 10 else 7,
                        qualityScore = 7
                    )
                )
            }
        }

        // FIX: restore the model the user actually picked last, not "the
        // first AiProvider (in fixed enum order) that happens to have any
        // saved model". Previously this loop always checked GEMINI, then
        // OPENAI, then NVIDIA_NIM, etc., and stopped at the first hit — so
        // switching to (say) an OpenRouter model could silently snap back to
        // an older saved NVIDIA NIM selection (frequently its DeepSeek
        // model) on the very next load, regardless of what was just chosen.
        // The active-selection pointer records exactly which provider/
        // instance is current, so it is checked first.
        var selected: AvailableModel? = null
        var autoRoute = secureStorage.getActiveModelSelection() == "AUTO_ROUTE"
        when (val activeKey = secureStorage.getActiveModelSelection()) {
            null -> {}
            else -> when {
                activeKey.startsWith("CATALOG:") -> {
                    val providerName = activeKey.removePrefix("CATALOG:")
                    val savedModelId = secureStorage.getSelectedModel(providerName)
                    if (savedModelId != null) {
                        selected = models.find { it.provider.name == providerName && it.instanceId == null && it.modelId == savedModelId }
                    }
                }
                activeKey.startsWith("INSTANCE:") -> {
                    val instanceId = activeKey.removePrefix("INSTANCE:")
                    val savedModelId = secureStorage.getProviderInstances().find { it.instanceId == instanceId }?.selectedModel
                    if (savedModelId != null) {
                        selected = models.find { it.instanceId == instanceId && it.modelId == savedModelId }
                    }
                }
            }
        }

        if (!autoRoute && selected == null) {
            // Legacy fallback for installs from before the active-selection
            // pointer existed. Kept only as a last resort — it can still
            // pick "the first provider in enum order with any saved model"
            // rather than the true last choice, which is exactly the bug
            // above, but it is strictly better than showing no selection.
            for (provider in AiProvider.entries) {
                if (provider == AiProvider.CUSTOM) continue // instance-derived models are matched by instanceId below, not a single shared selection
                val savedModelId = secureStorage.getSelectedModel(provider.name) ?: continue
                val catalogModel = catalog.findModel(provider, savedModelId)
                if (catalogModel != null) {
                    selected = models.find { it.provider == provider && it.modelId == savedModelId }
                    if (selected != null) break
                } else {
                    secureStorage.removeSelectedModel(provider.name)
                }
            }
        }
        if (!autoRoute && selected == null) {
            // Restore the last-picked instance-derived model, if any (persisted per-instance).
            for (instance in secureStorage.getProviderInstances()) {
                val savedModelId = instance.selectedModel ?: continue
                selected = models.find { it.instanceId == instance.instanceId && it.modelId == savedModelId }
                if (selected != null) break
            }
        }

        if (!autoRoute && selected == null) {
            val defaultCatalogModel = catalog.getDefaultModel(configuredProviders)
            if (defaultCatalogModel != null) {
                selected = models.find { it.provider == defaultCatalogModel.provider && it.modelId == defaultCatalogModel.id }
            }
        }

        // FIX: never fall back to models.firstOrNull() here — that could be
        // *any* model in the full catalog (including ones from providers
        // the user never added a key for, e.g. an unconfigured DeepSeek
        // entry), silently making it "the selected model" for chat even
        // though the user never picked it. Only auto-select a model that is
        // actually configured; otherwise leave selection empty so the UI
        // prompts the user to choose/configure one explicitly.
        _uiState.update {
            it.copy(
                availableModels = models,
                selectedModel = if (autoRoute) autoRouteModel else selected ?: models.firstOrNull { it.isConfigured },
                isAutoRoute = autoRoute,
                autoRouteModel = if (autoRoute) null else selected
            )
        }
    }

    // PRD PHASE 20: Chat history persistence
    private fun loadConversations() {
        val saved = secureStorage.getChatConversations()
        if (saved.isNotEmpty()) {
            _uiState.update { it.copy(conversations = saved) }
        }
    }

    private fun saveCurrentConversation() {
        val state = _uiState.value
        if (state.messages.isEmpty()) return

        val convId = state.currentConversationId ?: System.currentTimeMillis().toString()
        val title = state.messages.firstOrNull()?.content?.take(40) ?: "New Chat"
        val conv = ChatConversation(id = convId, title = title, messages = state.messages)

        val updated = (listOf(conv) + state.conversations.filter { it.id != convId }).take(50)
        secureStorage.saveChatConversations(updated)
        _uiState.update { it.copy(conversations = updated, currentConversationId = convId) }
    }

    fun toggleDrawer() {
        _uiState.update { it.copy(showDrawer = !it.showDrawer) }
    }

    fun closeDrawer() {
        _uiState.update { it.copy(showDrawer = false) }
    }

    // PRD PHASE 21: New Chat
    fun newChat() {
        saveCurrentConversation()
        _uiState.update {
            it.copy(
                messages = emptyList(),
                inputText = "",
                currentConversationId = null,
                attachments = emptyList(),
                error = null,
                showDrawer = false
            )
        }
    }

    fun openConversation(convId: String) {
        val conv = _uiState.value.conversations.find { it.id == convId } ?: return
        _uiState.update {
            it.copy(
                messages = conv.messages,
                currentConversationId = convId,
                showDrawer = false
            )
        }
    }

    fun deleteConversation(convId: String) {
        val updated = _uiState.value.conversations.filterNot { it.id == convId }
        secureStorage.saveChatConversations(updated)
        _uiState.update { it.copy(conversations = updated) }
        if (_uiState.value.currentConversationId == convId) {
            newChat()
        }
    }

    fun clearAllHistory() {
        secureStorage.saveChatConversations(emptyList())
        _uiState.update { it.copy(conversations = emptyList()) }
        newChat()
    }

    fun onInputChange(text: String) {
        _uiState.update { it.copy(inputText = text) }
    }

    fun toggleModelPicker() {
        _uiState.update { it.copy(showModelPicker = !it.showModelPicker) }
    }

    fun dismissModelPicker() {
        _uiState.update { it.copy(showModelPicker = false) }
    }

    // PRD PHASE 16: Model persistence — DataStore
    fun selectModel(model: AvailableModel) {
        if (model.uniqueKey == "AUTO_ROUTE") {
            secureStorage.saveActiveModelSelection("AUTO_ROUTE")
            _uiState.update { it.copy(selectedModel = autoRouteModel, isAutoRoute = true, autoRouteModel = null, showModelPicker = false) }
            return
        }
        if (model.instanceId != null) {
            val instances = secureStorage.getProviderInstances()
            val idx = instances.indexOfFirst { it.instanceId == model.instanceId }
            if (idx >= 0) {
                val updated = instances.toMutableList()
                updated[idx] = updated[idx].copy(selectedModel = model.modelId)
                secureStorage.saveProviderInstances(updated)
            }
            secureStorage.saveActiveModelSelection("INSTANCE:${model.instanceId}")
        } else {
            secureStorage.saveSelectedModel(model.provider.name, model.modelId)
            secureStorage.saveActiveModelSelection("CATALOG:${model.provider.name}")
        }
        _uiState.update { it.copy(selectedModel = model, isAutoRoute = false, autoRouteModel = model, showModelPicker = false) }
    }

    private fun chooseAutoRouteCandidates(text: String, attachments: List<AttachmentData>): List<AvailableModel> {
        val state = _uiState.value
        val candidates = state.availableModels.filter { it.isConfigured && it.uniqueKey != "AUTO_ROUTE" && it.isFree }
        val lower = text.lowercase()
        val hasImage = attachments.any { it.mimeType?.startsWith("image/") == true }
        val hasFiles = attachments.isNotEmpty()
        val coding = listOf("code", "coding", "kotlin", "java", "android", "bug", "error", "build", "gradle", "github", "repository", "api", "function", "class", "refactor", "fix", "implement", "program", "ui", "design", "compose").any { lower.contains(it) }
        val vision = hasImage || listOf("image", "screenshot", "photo", "visual", "ui design", "screen").any { lower.contains(it) }
        val reasoning = listOf("analyze", "architecture", "why", "debug", "root cause", "compare", "plan", "complex").any { lower.contains(it) }
        if (candidates.isEmpty()) return emptyList()
        return candidates.filter { !vision || it.supportsImage }.let { pool ->
            val usable = if (pool.isNotEmpty()) pool else candidates
            usable.sortedByDescending { m ->
                var score = m.qualityScore * 100 + m.reasoningScore * 8 + m.codingScore * 8
                if (m.isFree) score += 120
                if (coding) score += m.codingScore * 30
                if (reasoning) score += m.reasoningScore * 25
                if (vision && m.supportsImage) score += 500
                if (hasFiles && m.supportsFiles) score += 100
                if (m.supportsVideo && lower.contains("video")) score += 250
                if (m.providerName.contains("NVIDIA", true) && coding) score += 20
                score
            }
        }
    }

    private fun isFallbackable(error: Throwable): Boolean {
        val m = error.message.orEmpty().lowercase()
        return listOf("429", "500", "502", "503", "504", "timeout", "timed out", "unavailable", "rate limit", "network", "connection", "not found", "invalid model").any { m.contains(it) }
    }

    fun refreshModels() {
        loadAvailableModels()
    }

    fun removeContext(chip: AIContextChipData) {
        _uiState.update { it.copy(activeContext = it.activeContext - chip) }
    }

    fun dismissError() {
        _uiState.update { it.copy(error = null) }
    }

    // PRD PHASE 19: Stop generation — cancel active request
    fun cancelStreaming() {
        streamingJob?.cancel()
        streamingJob = null
        _uiState.update { it.copy(isProcessing = false, isStreaming = false) }
    }

    fun retryLast() {
        val lastUser = _uiState.value.messages.lastOrNull { it.role == ChatRole.USER } ?: return
        _uiState.update { it.copy(error = null, inputText = lastUser.content) }
        sendMessage()
    }

    // Build a textual context from attachments so the AI model understands what
    // files have been shared without raw binary content (which cannot be sent
    // through a text-only chat completion endpoint).
    private fun buildAttachmentContext(attachments: List<AttachmentData>): String {
        if (attachments.isEmpty()) return ""
        return attachments.joinToString(separator = "\n") { attachment ->
            buildString {
                append("[Attachment: ${attachment.name}")
                attachment.mimeType?.let { append(" ($it)") }
                if (attachment.size > 0) append(", ${attachment.size} bytes")
                append("]")
            }
        }
    }

    // PRD §10-11: Attachment handling — read actual file metadata via ContentResolver
    fun addAttachment(uri: String, name: String, mimeType: String?, size: Long) {
        // PRD §12: Use actual metadata from ContentResolver, not estimates
        val androidUri = android.net.Uri.parse(uri)
        val metadata = try {
            FileProcessor.readMetadata(appContext, androidUri)
        } catch (_: Exception) {
            FileMetadata(uri, name, mimeType, size)
        }

        val attachment = AttachmentData(
            id = System.currentTimeMillis().toString() + "_" + metadata.fileName.hashCode(),
            uri = uri,
            name = metadata.fileName,
            mimeType = metadata.mimeType ?: mimeType,
            size = metadata.sizeBytes,
            state = AttachmentState.READY
        )
        _uiState.update { it.copy(attachments = it.attachments + attachment) }
    }

    fun removeAttachment(uri: String) {
        _uiState.update { it.copy(attachments = it.attachments.filterNot { it.uri == uri }) }
    }

    private fun copyZipAttachmentForAgent(attachment: AttachmentData): File {
        val target = File(appContext.filesDir, "gito_ai_${System.nanoTime()}.zip")
        appContext.contentResolver.openInputStream(Uri.parse(attachment.uri))?.use { input ->
            target.outputStream().use { output -> input.copyTo(output) }
        } ?: throw IllegalStateException("Unable to read the ZIP attachment.")
        return target
    }

    // PRD PHASE 6: API logic fix + error handling
    // PRD PHASE 7: Streaming response
    fun sendMessage() {
        val stateBefore = _uiState.value
        val text = stateBefore.inputText.trim()
        val hasAttachments = stateBefore.attachments.isNotEmpty()
        if (text.isEmpty() && !hasAttachments) return

        val selectedModel = stateBefore.selectedModel
        if (!stateBefore.isAutoRoute && (selectedModel == null || !selectedModel.isConfigured)) {
            // Every provider the user can configure in Settings → API
            // Providers now works in chat (curated providers via
            // AIModelCatalog, everything else via InstanceChatClient routed
            // by protocol) — so if we land here it's really just "no key/
            // model selected yet", not "this provider isn't supported".
            val hasAnyConfiguredProvider = secureStorage.getAllAiKeys().isNotEmpty() ||
                secureStorage.getProviderInstances().any { secureStorage.hasAiKey(it.instanceId) }
            val message = if (hasAnyConfiguredProvider) {
                "No chat model is selected yet. Open the model picker (or discover models in Settings → API Providers) and choose one."
            } else {
                "Add an API key to continue."
            }
            _uiState.update { it.copy(error = AIErrorInfo(message, false, true)) }
            return
        }

        val userMsg = ChatMessage(
            id = System.currentTimeMillis().toString(),
            role = ChatRole.USER,
            content = if (text.isNotEmpty()) text else "(attachment)",
            attachments = stateBefore.attachments
        )
        val attachmentContext = buildAttachmentContext(stateBefore.attachments)
        val fullPrompt = when {
            attachmentContext.isNotEmpty() && text.isNotEmpty() -> "$attachmentContext\n\n$text"
            attachmentContext.isNotEmpty() -> attachmentContext
            else -> text
        }

        val autoCandidates = if (stateBefore.isAutoRoute) chooseAutoRouteCandidates(text, stateBefore.attachments) else emptyList()
        if (stateBefore.isAutoRoute && autoCandidates.isEmpty()) {
            _uiState.update { it.copy(error = AIErrorInfo("Auto Route could not find a configured free model matching this task. Configure a provider or select a model manually.", false, true)) }
            return
        }

        _uiState.update { it.copy(messages = it.messages + userMsg, inputText = "", isProcessing = true, isStreaming = true, error = null, attachments = emptyList()) }
        val assistantMsgId = (System.currentTimeMillis() + 1).toString()
        _uiState.update { it.copy(messages = it.messages + ChatMessage(assistantMsgId, ChatRole.ASSISTANT, "", isStreaming = true)) }

        streamingJob?.cancel()
        streamingJob = viewModelScope.launch {
            try {
                if (stateBefore.isAutoRoute) {
                    var lastError: Throwable? = null
                    var used: AvailableModel? = null
                    for ((index, candidate) in autoCandidates.withIndex()) {
                        try {
                            used = candidate
                            _uiState.update { state -> state.copy(autoRouteModel = candidate) }
                            if (candidate.instanceId != null) {
                                val instance = secureStorage.getProviderInstances().find { it.instanceId == candidate.instanceId }
                                    ?: throw RuntimeException("Provider instance unavailable")
                                val apiKey = secureStorage.getAiKey(candidate.instanceId)
                                val protocol = candidate.protocol ?: throw RuntimeException("Provider protocol unavailable")
                                if (apiKey.isNullOrBlank()) throw RuntimeException("Provider key unavailable")
                                val result = instanceChatClient.stream(com.gitofy.ai.provider.client.InstanceChatRequest(
                                    instance = instance, apiKey = apiKey, modelId = candidate.modelId, protocol = protocol,
                                    systemPrompt = "You are Gito AI, a helpful coding assistant.", userPrompt = fullPrompt
                                )) { chunk -> _uiState.update { st -> st.copy(messages = st.messages.map { msg -> if (msg.id == assistantMsgId) msg.copy(content = msg.content + chunk) else msg }) } }
                                result.getOrThrow()
                            } else {
                                val result = aiGateway.processStream(com.gitofy.ai.gateway.AIGateway.GatewayRequest(
                                    taskType = if (stateBefore.attachments.any { it.mimeType?.startsWith("image/") == true }) com.gitofy.ai.model.AITaskType.VISION_UI_ANALYSIS else com.gitofy.ai.model.AITaskType.CODE_GENERATION,
                                    userPrompt = fullPrompt, contextData = emptyMap(), requireVision = candidate.supportsImage && stateBefore.attachments.any { it.mimeType?.startsWith("image/") == true },
                                    costBudget = com.gitofy.ai.gateway.AIGateway.CostBudget.USER_SELECTED,
                                    userPreferences = com.gitofy.ai.gateway.AIGateway.UserPreferences(preferredProvider = candidate.provider.name.lowercase(), preferredModel = candidate.modelId, routingMode = com.gitofy.ai.gateway.AIGateway.RoutingMode.USER_SELECTED)
                                )) { chunk -> _uiState.update { st -> st.copy(messages = st.messages.map { msg -> if (msg.id == assistantMsgId) msg.copy(content = msg.content + chunk) else msg }) } }
                                result.getOrThrow()
                            }
                            _uiState.update { st -> st.copy(autoRouteModel = candidate) }
                            saveCurrentConversation()
                            return@launch
                        } catch (e: Exception) {
                            lastError = e
                            if (!isFallbackable(e)) break
                            if (index < autoCandidates.lastIndex) {
                                _uiState.update { st -> st.copy(messages = st.messages.map { msg -> if (msg.id == assistantMsgId) msg.copy(content = msg.content + "\n\n↻ Switching to ${autoCandidates[index + 1].displayName}…\n\n") else msg }) }
                            }
                        }
                    }
                    throw (lastError ?: RuntimeException("All Auto Route models failed"))
                }

                // By construction, reaching this point means stateBefore.isAutoRoute
                // was false, which (per the guard above) guarantees selectedModel is
                // non-null and configured. The compiler can't follow that control
                // flow across the auto-route block above, so make it explicit here.
                val selectedModel = selectedModel
                    ?: throw RuntimeException("No chat model is selected.")

                // Instance-derived models (any of the 14 providers that
                // aren't part of the curated AIModelCatalog) don't go
                // through the GitHub AI agent or AIGateway — those are both
                // wired to the old hardcoded 7-provider list. They chat
                // directly through InstanceChatClient, routed by protocol.
                // Repository-action tool-calling for these providers is out
                // of scope for now (PRD non-goal) — this restores plain chat.
                if (selectedModel.instanceId != null) {
                    val instance = secureStorage.getProviderInstances().find { it.instanceId == selectedModel.instanceId }
                    val apiKey = secureStorage.getAiKey(selectedModel.instanceId)
                    val protocol = selectedModel.protocol
                    if (instance == null || apiKey.isNullOrBlank() || protocol == null) {
                        throw RuntimeException("This provider is no longer configured. Please check Settings → API Providers.")
                    }
                    val result = instanceChatClient.stream(
                        com.gitofy.ai.provider.client.InstanceChatRequest(
                            instance = instance,
                            apiKey = apiKey,
                            modelId = selectedModel.modelId,
                            protocol = protocol,
                            systemPrompt = "You are Gito AI, a helpful coding assistant.",
                            userPrompt = fullPrompt
                        )
                    ) { chunk ->
                        _uiState.update { state ->
                            state.copy(messages = state.messages.map { msg ->
                                if (msg.id == assistantMsgId) msg.copy(content = msg.content + chunk, isStreaming = true) else msg
                            })
                        }
                    }
                    result.fold(
                        onSuccess = {
                            _uiState.update { state ->
                                state.copy(messages = state.messages.map { msg ->
                                    if (msg.id == assistantMsgId) msg.copy(isStreaming = false) else msg
                                }, isProcessing = false, isStreaming = false)
                            }
                            saveCurrentConversation()
                        },
                        onFailure = { error -> throw error }
                    )
                    return@launch
                }

                if (looksLikeRepositoryAction(fullPrompt)) {
                    val zipAttachment = stateBefore.attachments.firstOrNull {
                        it.name.endsWith(".zip", ignoreCase = true) ||
                            it.mimeType.equals("application/zip", ignoreCase = true) ||
                            it.mimeType.equals("application/x-zip-compressed", ignoreCase = true)
                    }
                    val agentZip = zipAttachment?.let { copyZipAttachmentForAgent(it) }
                    try {
                        val agentResult = githubAiAgent.execute(
                            command = fullPrompt,
                            provider = selectedModel.provider.name.lowercase(),
                            model = selectedModel.modelId,
                            zipPath = agentZip?.absolutePath,
                            onProgress = { step ->
                                _uiState.update { state ->
                                    state.copy(messages = state.messages.map { msg ->
                                        if (msg.id == assistantMsgId) msg.copy(steps = msg.steps + step, isStreaming = true) else msg
                                    })
                                }
                            }
                        )

                        agentResult.fold(
                            onSuccess = { result ->
                                val changed = if (result.changedFiles.isEmpty()) "" else "\n\nChanged: ${result.changedFiles.joinToString()}"
                                _uiState.update { state ->
                                    state.copy(
                                        messages = state.messages.map { msg ->
                                            if (msg.id == assistantMsgId) msg.copy(
                                                content = result.message + changed,
                                                isStreaming = false
                                            ) else msg
                                        },
                                        isProcessing = false,
                                        isStreaming = false
                                    )
                                }
                                saveCurrentConversation()
                            },
                            onFailure = { error ->
                                _uiState.update { state ->
                                    state.copy(
                                        messages = state.messages.filterNot { it.id == assistantMsgId },
                                        isProcessing = false,
                                        isStreaming = false,
                                        error = AIErrorInfo(error.message ?: "GitHub agent request failed.", true, false)
                                    )
                                }
                            }
                        )
                        return@launch
                    } finally {
                        agentZip?.delete()
                    }
                }

                val result = aiGateway.processStream(
                    com.gitofy.ai.gateway.AIGateway.GatewayRequest(
                        taskType = com.gitofy.ai.model.AITaskType.GENERAL_QA,
                        userPrompt = fullPrompt,
                        contextData = emptyMap(),
                        requireVision = false,
                        costBudget = com.gitofy.ai.gateway.AIGateway.CostBudget.USER_SELECTED,
                        userPreferences = com.gitofy.ai.gateway.AIGateway.UserPreferences(
                            preferredProvider = selectedModel.provider.name.lowercase(),
                            preferredModel = selectedModel.modelId,
                            routingMode = com.gitofy.ai.gateway.AIGateway.RoutingMode.USER_SELECTED
                        )
                    )
                ) { chunk ->
                    _uiState.update { state ->
                        state.copy(messages = state.messages.map { msg ->
                            if (msg.id == assistantMsgId) msg.copy(content = msg.content + chunk, isStreaming = true) else msg
                        })
                    }
                }

                result.fold(
                    onSuccess = {
                        _uiState.update { state ->
                            state.copy(messages = state.messages.map { msg ->
                                if (msg.id == assistantMsgId) msg.copy(isStreaming = false) else msg
                            }, isProcessing = false, isStreaming = false)
                        }
                        saveCurrentConversation()
                    },
                    onFailure = { error ->
                        throw error
                    }
                )
            } catch (e: kotlinx.coroutines.CancellationException) {
                _uiState.update { state ->
                    state.copy(messages = state.messages.map { msg ->
                        if (msg.id == assistantMsgId) msg.copy(content = msg.content.ifBlank { "(stopped)" }, isStreaming = false) else msg
                    }, isProcessing = false, isStreaming = false)
                }
            } catch (e: Exception) {
                val message = e.message.orEmpty()
                val errorMsg = when {
                    message.contains("API key is not configured", true) ||
                        message.contains("not configured", true) -> "This AI provider is not configured. Open Settings → API Providers and save a key."
                    message.contains("401") || message.contains("403") -> "The provider rejected the saved API key (HTTP 401/403). Re-test the provider in Settings → API Providers."
                    message.contains("429") -> "Rate limit reached. Try again later."
                    message.contains("timeout", true) -> "Connection timed out."
                    message.contains("404") -> "This model is currently unavailable."
                    message.contains("Unable to resolve host", true) || message.contains("network", true) -> "Connection unavailable."
                    else -> message.substringAfter(": ").ifBlank { "Request failed." }
                }
                _uiState.update { state ->
                    state.copy(messages = state.messages.filterNot { it.id == assistantMsgId }, isProcessing = false, isStreaming = false,
                        error = AIErrorInfo(errorMsg, true, message.contains("401") || message.contains("403")))
                }
            }
        }
    }

    // PRD: AI-to-repository direct change — treat messages that look like a
    // repository action as such (like GitHub Copilot). When the user says
    // "add a file", "fix the bug in X", "update the README" etc., the AI
    // agent should directly make the change on the target repository.
    //
    // FIX: this used to unconditionally return true, so every message —
    // including a plain "hi" — paid the full cost of the GitHub agent path:
    // an eager listRepositories() call requiring a live GitHub connection, a
    // non-streaming LLM call, and JSON tool-decision parsing. That extra
    // machinery was the likely source of chat failing on simple greetings
    // regardless of which model was selected. Casual conversation now goes
    // through the normal fast streaming chat; only messages that actually
    // look like a repository-editing/action request go through the agent.
    private fun looksLikeRepositoryAction(text: String): Boolean {
        val trimmed = text.trim()
        if (trimmed.isBlank()) return false

        // Very short, greeting-like, or purely conversational messages are
        // never repository actions.
        val lower = trimmed.lowercase()
        val conversationalOnly = setOf(
            "hi", "hii", "hiii", "hello", "hey", "yo", "sup",
            "thanks", "thank you", "thx", "ok", "okay", "cool", "nice",
            "bye", "good morning", "good night", "how are you", "who are you"
        )
        if (lower in conversationalOnly) return false

        val actionKeywords = listOf(
            "create", "add", "delete", "remove", "fix", "update", "edit",
            "modify", "change", "rename", "push", "commit", "merge", "clone",
            "branch", "pull request", "pr ", "readme", "file", "folder",
            "repo", "repository", "issue", "release", "tag", "revert",
            "refactor", "implement", "deploy", "upload", "replace", "rewrite"
        )
        return actionKeywords.any { lower.contains(it) }
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
    }

    companion object {
        val AUTO_ROUTE_MODEL = AvailableModel(
            modelId = "__AUTO_ROUTE__",
            displayName = "Auto Route",
            providerName = "Gito Auto Router",
            provider = AiProvider.CUSTOM,
            isFree = true,
            isConfigured = true,
            uniqueKey = "AUTO_ROUTE"
        )
    }
}

// ===========================================================================
// PRD PHASE 3: Full-screen mode — no bottom navigation
// PRD PHASE 4: Top bar: [☰] Gito  [←]
// PRD PHASE 10: Unified composer — attachment + text + model selector + send
// PRD PHASE 29: Final Gito UI
// ===========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun GitoAiScreen(
    onBack: () -> Unit = {},
    onOpenProviderSettings: () -> Unit = {},
    viewModel: GitoAiViewModel = hiltViewModel()
) {
    val state by viewModel.uiState.collectAsState()
    val listState = rememberLazyListState()
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()

    // Keep the conversation anchored to the newest content while the model is
    // streaming. If the user deliberately scrolls upward, don't fight them.
    val isNearBottom by remember {
        derivedStateOf {
            val total = state.messages.size
            if (total == 0) return@derivedStateOf true
            val lastVisible = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            lastVisible >= total - 2
        }
    }

    // FIX (messages jumping/rushing upward): the previous version ran TWO
    // separate LaunchedEffects that could both fire for the same frame
    // (one keyed on message count, one keyed on streaming content), each
    // calling animateScrollToItem AND a manual scrollBy nudge. Overlapping
    // animations like that fight each other and show up exactly as "all the
    // messages suddenly rush upward" instead of a calm, controlled scroll.
    // A single coroutine, cancelling any in-flight scroll before starting
    // a new one, keeps this smooth and predictable.
    var autoScrollJob by remember { mutableStateOf<Job?>(null) }
    suspend fun scrollConversationToLatest() {
        if (state.messages.isEmpty()) return
        listState.animateScrollToItem(state.messages.lastIndex)
    }

    LaunchedEffect(state.messages.size, state.messages.lastOrNull()?.content) {
        if (isNearBottom) {
            autoScrollJob?.cancel()
            autoScrollJob = launch { scrollConversationToLatest() }
        }
    }

    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) viewModel.refreshModels()
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose { lifecycleOwner.lifecycle.removeObserver(observer) }
    }

    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    ModalNavigationDrawer(
        drawerState = drawerState,
        drawerContent = {
            GitoDrawerContent(
                conversations = state.conversations,
                currentConversationId = state.currentConversationId,
                onNewChat = {
                    viewModel.newChat()
                    scope.launch { drawerState.close() }
                },
                onOpenConversation = { id ->
                    viewModel.openConversation(id)
                    scope.launch { drawerState.close() }
                },
                onDeleteConversation = { viewModel.deleteConversation(it) },
                onClearAll = { viewModel.clearAllHistory() },
                onOpenSettings = {
                    onOpenProviderSettings()
                    scope.launch { drawerState.close() }
                }
            )
        }
    ) {
        Box(modifier = Modifier.fillMaxSize()) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .imePadding()
            ) {
                AnimatedVisibility(
                    visible = state.error != null,
                    enter = gitofySlideFadeEnter,
                    exit = gitofySlideFadeExit
                ) {
                    state.error?.let { error ->
                        AIErrorBanner(
                            error = error,
                            onRetry = { viewModel.retryLast() },
                            onConfigureProvider = onOpenProviderSettings,
                            onDismiss = { viewModel.dismissError() },
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                if (state.messages.isEmpty()) {
                    Box(modifier = Modifier.weight(1f).fillMaxWidth()) {
                        AIEmptyState(
                            onPromptSelected = { prompt ->
                                viewModel.onInputChange(prompt)
                                viewModel.sendMessage()
                            },
                            modifier = Modifier.align(Alignment.TopCenter)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        state = listState,
                        contentPadding = PaddingValues(
                            top = 88.dp,
                            bottom = 16.dp,
                            start = 12.dp,
                            end = 12.dp
                        ),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            when (message.role) {
                                ChatRole.USER -> UserMessageBubble(
                                    content = message.content,
                                    timestamp = message.timestamp,
                                    attachments = message.attachments
                                )
                                ChatRole.ASSISTANT -> {
                                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                        if (message.steps.isNotEmpty()) {
                                            AgentStepsView(steps = message.steps)
                                        }
                                        if (message.isStreaming && message.content.isEmpty()) {
                                            ThinkingAnimation()
                                        } else if (message.content.isNotEmpty()) {
                                            AssistantMessageBubble(
                                                content = message.content,
                                                isStreaming = message.isStreaming,
                                                onCopy = { clipboard.setText(AnnotatedString(message.content)) },
                                                onRetry = if (state.error == null) null else viewModel::retryLast
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                GitoUnifiedComposer(
                    text = state.inputText,
                    onTextChange = viewModel::onInputChange,
                    onSend = viewModel::sendMessage,
                    onCancel = viewModel::cancelStreaming,
                    isProcessing = state.isProcessing,
                    attachments = state.attachments,
                    onRemoveAttachment = viewModel::removeAttachment,
                    selectedModel = state.selectedModel,
                    onModelClick = { viewModel.toggleModelPicker() },
                    onAttachFile = { uri, name, mime, size ->
                        viewModel.addAttachment(uri, name, mime, size)
                    }
                )
            }

            // Immersive translucent header: conversation content can visually
            // pass behind it, while a soft fade keeps the header readable.
            Box(
                modifier = Modifier
                    .fillMaxWidth()
                    .height(92.dp)
                    .background(
                        androidx.compose.ui.graphics.Brush.verticalGradient(
                            0f to MaterialTheme.colorScheme.background.copy(alpha = 0.98f),
                            0.62f to MaterialTheme.colorScheme.background.copy(alpha = 0.76f),
                            1f to MaterialTheme.colorScheme.background.copy(alpha = 0f)
                        )
                    )
                    .windowInsetsPadding(WindowInsets.statusBars)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(68.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(onClick = { scope.launch { drawerState.open() } }) {
                        Icon(Icons.Filled.Menu, contentDescription = "Menu")
                    }
                    Text(
                        text = "Gito",
                        style = MaterialTheme.typography.titleLarge,
                        fontWeight = FontWeight.SemiBold,
                        modifier = Modifier.weight(1f)
                    )
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                }
            }
        }
    }

    if (state.showModelPicker) {
        ModelPickerSheet(
            models = state.availableModels,
            selected = state.selectedModel,
            onSelect = viewModel::selectModel,
            onDismiss = viewModel::dismissModelPicker,
            onOpenProviderSettings = onOpenProviderSettings
        )
    }
}

// ===========================================================================
// Premium "Gito" thinking animation — rotating gradient ring badge + a
// shimmering gradient sweep across the "Thinking" label. Replaces the old
// plain 3-dot pulse with a smoother, more premium feel.
// ===========================================================================

@Composable
private fun ThinkingAnimation() {
    val transition = rememberInfiniteTransition(label = "thinking")

    // Slow continuous rotation for the gradient ring around the badge.
    val ringRotation by transition.animateFloat(
        initialValue = 0f,
        targetValue = 360f,
        animationSpec = infiniteRepeatable(
            animation = tween(2200, easing = LinearEasing)
        ),
        label = "thinkingRingRotation"
    )
    // Gentle breathing scale on the badge so it feels alive, not mechanical.
    val breathe by transition.animateFloat(
        initialValue = 0.92f,
        targetValue = 1f,
        animationSpec = infiniteRepeatable(
            animation = tween(1100, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "thinkingBreathe"
    )
    // Horizontal shimmer sweep across the label text.
    val shimmer by transition.animateFloat(
        initialValue = -1f,
        targetValue = 2f,
        animationSpec = infiniteRepeatable(
            animation = tween(1400, easing = LinearEasing)
        ),
        label = "thinkingShimmer"
    )

    val primary = MaterialTheme.colorScheme.primary
    val baseTextColor = MaterialTheme.colorScheme.onSurfaceVariant

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Box(
            modifier = Modifier
                .size(20.dp)
                .scale(breathe),
            contentAlignment = Alignment.Center
        ) {
            Canvas(modifier = Modifier.matchParentSize()) {
                rotate(ringRotation) {
                    drawArc(
                        brush = Brush.sweepGradient(
                            listOf(
                                primary.copy(alpha = 0f),
                                primary.copy(alpha = 0.25f),
                                primary,
                                primary.copy(alpha = 0.25f),
                                primary.copy(alpha = 0f)
                            )
                        ),
                        startAngle = 0f,
                        sweepAngle = 300f,
                        useCenter = false,
                        style = Stroke(width = 2.4.dp.toPx(), cap = StrokeCap.Round)
                    )
                }
            }
            Box(
                modifier = Modifier
                    .size(8.dp)
                    .clip(CircleShape)
                    .background(primary)
            )
        }
        Spacer(modifier = Modifier.width(10.dp))
        Text(
            "Thinking",
            style = MaterialTheme.typography.bodyMedium.copy(
                brush = Brush.linearGradient(
                    colors = listOf(
                        baseTextColor.copy(alpha = 0.55f),
                        primary,
                        baseTextColor.copy(alpha = 0.55f)
                    ),
                    start = androidx.compose.ui.geometry.Offset(shimmer * 240f, 0f),
                    end = androidx.compose.ui.geometry.Offset(shimmer * 240f + 140f, 0f)
                )
            )
        )
    }
}

// ===========================================================================
// Structured agent step chips — groups consecutive tool/read/edit events
// (separated by each planning round) into collapsible summary rows, e.g.
// "Read 1 file · Used 1 tool" that expand to show each individual step.
// ===========================================================================

private data class StepGroup(
    val summary: String,
    val steps: List<com.gitofy.ai.agent.AgentStepEvent>
)

private fun groupAgentSteps(steps: List<com.gitofy.ai.agent.AgentStepEvent>): List<StepGroup> {
    val groups = mutableListOf<StepGroup>()
    var current = mutableListOf<com.gitofy.ai.agent.AgentStepEvent>()

    fun flush() {
        if (current.isEmpty()) return
        val counts = current.groupingBy { it.kind }.eachCount()
        val parts = mutableListOf<String>()
        counts[com.gitofy.ai.agent.AgentStepKind.READ]?.let { parts += if (it == 1) "Read 1 file" else "Read $it files" }
        counts[com.gitofy.ai.agent.AgentStepKind.SEARCH]?.let { parts += "Searched code" }
        counts[com.gitofy.ai.agent.AgentStepKind.EDIT]?.let { parts += if (it == 1) "Edited 1 file" else "Edited $it files" }
        counts[com.gitofy.ai.agent.AgentStepKind.CREATE_REPO]?.let { parts += "Created repository" }
        counts[com.gitofy.ai.agent.AgentStepKind.BRANCH]?.let { parts += "Created branch" }
        counts[com.gitofy.ai.agent.AgentStepKind.COMMIT]?.let { parts += "Committed changes" }
        counts[com.gitofy.ai.agent.AgentStepKind.PULL_REQUEST]?.let { parts += "Opened pull request" }
        counts[com.gitofy.ai.agent.AgentStepKind.WORKFLOW]?.let { parts += "Started workflow" }
        counts[com.gitofy.ai.agent.AgentStepKind.TOOL]?.let { parts += if (it == 1) "Used 1 tool" else "Used $it tools" }
        counts[com.gitofy.ai.agent.AgentStepKind.ERROR]?.let { parts += "Hit an error" }
        if (parts.isNotEmpty()) {
            groups += StepGroup(parts.joinToString(" · "), current.toList())
        }
        current = mutableListOf()
    }

    for (step in steps) {
        if (step.kind == com.gitofy.ai.agent.AgentStepKind.PLAN) {
            flush()
        } else {
            current.add(step)
        }
    }
    flush()
    return groups
}

@Composable
private fun AgentStepsView(steps: List<com.gitofy.ai.agent.AgentStepEvent>, modifier: Modifier = Modifier) {
    val groups = remember(steps) { groupAgentSteps(steps) }
    if (groups.isEmpty()) return

    var expandedIndex by remember { mutableStateOf(-1) }

    Column(modifier = modifier.fillMaxWidth(), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        groups.forEachIndexed { index, group ->
            val isOpen = expandedIndex == index
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .clip(RoundedCornerShape(10.dp))
                    .background(MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f))
                    .clickable { expandedIndex = if (isOpen) -1 else index }
                    .padding(horizontal = 12.dp, vertical = 8.dp)
            ) {
                Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                    Text(
                        group.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.weight(1f)
                    )
                    Icon(
                        if (isOpen) Icons.Filled.ExpandLess else Icons.Filled.ChevronRight,
                        contentDescription = null,
                        modifier = Modifier.size(16.dp),
                        tint = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                AnimatedVisibility(visible = isOpen) {
                    Column(
                        modifier = Modifier.padding(top = 6.dp),
                        verticalArrangement = Arrangement.spacedBy(3.dp)
                    ) {
                        group.steps.forEach { step ->
                            Text(
                                "•  ${step.label}",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.85f)
                            )
                        }
                    }
                }
            }
        }
    }
}

// PRD PHASE 11: Attachment button on left (inside composer)
// PRD PHASE 12: Attachment preview inside composer
// PRD PHASE 14: Model selector inside composer
// PRD PHASE 17: Keyboard-aware
// PRD PHASE 19: Send/Stop button inside composer
// ===========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun GitoUnifiedComposer(
    text: String,
    onTextChange: (String) -> Unit,
    onSend: () -> Unit,
    onCancel: () -> Unit,
    isProcessing: Boolean,
    attachments: List<AttachmentData>,
    onRemoveAttachment: (String) -> Unit,
    selectedModel: AvailableModel?,
    onModelClick: () -> Unit,
    onAttachFile: (String, String, String?, Long) -> Unit
) {
    // PRD §10/§14: File picker — supports multiple file selection
    val filePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris: List<Uri> ->
        // PRD §14: Multiple file upload — each selected file gets its own attachment
        uris.forEach { uri ->
            // PRD §12: Name and size will be resolved by the ViewModel via ContentResolver
            val name = uri.lastPathSegment ?: "file"
            onAttachFile(uri.toString(), name, null, 0L)
        }
    }

    // Reference: single rounded container holding attachments, text field,
    // and a bottom row with [+] button (left), model pill (center-left), send button (right).
    Surface(
        tonalElevation = 3.dp,
        shadowElevation = 8.dp,
        shape = RoundedCornerShape(24.dp),
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 12.dp, vertical = 8.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 8.dp)
        ) {
            // PRD PHASE 12: Attachment previews
            if (attachments.isNotEmpty()) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    attachments.forEach { att ->
                        AttachmentChip(
                            name = att.name,
                            size = att.size,
                            onRemove = { onRemoveAttachment(att.uri) }
                        )
                    }
                }
                Spacer(modifier = Modifier.height(8.dp))
            }

            // Open text input field (no box/border — container is the visual boundary)
            BasicTextField(
                value = text,
                onValueChange = onTextChange,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 8.dp, vertical = 4.dp),
                enabled = !isProcessing,
                maxLines = 5,
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    color = MaterialTheme.colorScheme.onSurface
                ),
                cursorBrush = androidx.compose.ui.graphics.SolidColor(MaterialTheme.colorScheme.primary),
                decorationBox = { innerTextField ->
                    if (text.isEmpty()) {
                        Text(
                            "Ask Gito...",
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    innerTextField()
                },
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(
                    onSend = {
                        if ((text.isNotBlank() || attachments.isNotEmpty()) && !isProcessing) onSend()
                    }
                )
            )

            // Bottom row: [+] button (left) | model pill | send button (right)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 4.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                // + button on the far left (attach file)
                IconButton(
                    onClick = { filePickerLauncher.launch(arrayOf("*/*")) },
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.surfaceVariant)
                ) {
                    Icon(
                        Icons.Filled.Add,
                        contentDescription = "Attach file",
                        tint = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp)
                    )
                }

                Spacer(modifier = Modifier.width(8.dp))

                // Model selector pill — inside the text capsule, left-aligned
                Surface(
                    onClick = onModelClick,
                    shape = RoundedCornerShape(999.dp),
                    color = MaterialTheme.colorScheme.surfaceVariant,
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = selectedModel?.displayName ?: "Select Model",
                            style = MaterialTheme.typography.labelMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Icon(
                            Icons.Filled.ArrowDropDown,
                            contentDescription = "Select model",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Spacer(modifier = Modifier.weight(1f))

                // PRD PHASE 19: Send/Stop button on the right
                FilledIconButton(
                    onClick = { if (isProcessing) onCancel() else onSend() },
                    enabled = text.isNotBlank() || attachments.isNotEmpty() || isProcessing,
                    modifier = Modifier
                        .size(36.dp)
                        .clip(CircleShape)
                ) {
                    if (isProcessing) {
                        Icon(
                            Icons.Filled.Stop,
                            contentDescription = "Stop generating",
                            modifier = Modifier.size(18.dp)
                        )
                    } else {
                        Icon(
                            Icons.AutoMirrored.Filled.Send,
                            contentDescription = "Send message",
                            modifier = Modifier.size(18.dp)
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun AttachmentChip(name: String, size: Long, onRemove: () -> Unit) {
    Surface(
        color = MaterialTheme.colorScheme.secondaryContainer,
        shape = RoundedCornerShape(8.dp)
    ) {
        Row(
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Icon(
                Icons.Filled.AttachFile,
                contentDescription = null,
                modifier = Modifier.size(14.dp),
                tint = MaterialTheme.colorScheme.onSecondaryContainer
            )
            Spacer(modifier = Modifier.width(4.dp))
            Column {
                Text(
                    name,
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    maxLines = 1
                )
                // PRD §15: Show actual file size in the preview chip
                Text(
                    FileProcessor.formatSize(size),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.6f)
                )
            }
            Spacer(modifier = Modifier.width(4.dp))
            IconButton(onClick = onRemove, modifier = Modifier.size(20.dp)) {
                Icon(
                    Icons.Filled.Close,
                    contentDescription = "Remove attachment",
                    modifier = Modifier.size(14.dp),
                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                )
            }
        }
    }
}

// ===========================================================================
// PRD PHASE 15: Model selection sheet
// ===========================================================================

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ModelPickerSheet(
    models: List<AvailableModel>,
    selected: AvailableModel?,
    onSelect: (AvailableModel) -> Unit,
    onDismiss: () -> Unit,
    onOpenProviderSettings: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var searchQuery by remember { mutableStateOf("") }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)) {
            // PRD PHASE 2: "Gito Models"
            Text(
                "Gito Models",
                style = MaterialTheme.typography.titleMedium,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
            )

            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                placeholder = { Text("Search models...") },
                singleLine = true,
                shape = RoundedCornerShape(20.dp),
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    }
                }
            )

            val pickerModels = listOf(GitoAiViewModel.AUTO_ROUTE_MODEL) + models
            if (models.isEmpty()) {
                Column(modifier = Modifier.padding(16.dp)) {
                    Text(
                        "No models available.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Spacer(modifier = Modifier.height(12.dp))
                    FilledTonalButton(onClick = onOpenProviderSettings) {
                        Text("Configure Provider")
                    }
                }
            } else {
                val filtered = pickerModels.filter { model ->
                    searchQuery.isBlank() ||
                    model.displayName.contains(searchQuery, ignoreCase = true) ||
                    model.providerName.contains(searchQuery, ignoreCase = true)
                }

                val grouped = filtered.groupBy { it.providerName }
                LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                    grouped.forEach { (provider, providerModels) ->
                        item(key = "header_$provider") {
                            Text(
                                provider,
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp)
                            )
                        }
                        items(providerModels, key = { it.uniqueKey }) { model ->
                            val isSelected = selected?.uniqueKey == model.uniqueKey
                            Surface(
                                onClick = { onSelect(model) },
                                color = if (isSelected) MaterialTheme.colorScheme.secondaryContainer
                                        else MaterialTheme.colorScheme.surface,
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 12.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        model.displayName,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal,
                                        modifier = Modifier.weight(1f)
                                    )
                                    if (model.isFree) {
                                        Surface(
                                            color = MaterialTheme.colorScheme.tertiaryContainer,
                                            shape = RoundedCornerShape(8.dp)
                                        ) {
                                            Text(
                                                "FREE",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = MaterialTheme.colorScheme.onTertiaryContainer,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                        Spacer(modifier = Modifier.width(8.dp))
                                    }
                                    Text(
                                        text = if (model.isConfigured) "Connected" else "Connect required",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (model.isConfigured)
                                            MaterialTheme.colorScheme.tertiary
                                        else MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
        }
    }
}

// ===========================================================================
// PRD PHASE 20: Chat history drawer
// ===========================================================================

@Composable
private fun GitoDrawerContent(
    conversations: List<ChatConversation>,
    currentConversationId: String?,
    onNewChat: () -> Unit,
    onOpenConversation: (String) -> Unit,
    onDeleteConversation: (String) -> Unit,
    onClearAll: () -> Unit,
    onOpenSettings: () -> Unit
) {
    ModalDrawerSheet {
        // Header
        Text(
            "Gito",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.Bold,
            modifier = Modifier.padding(16.dp)
        )
        HorizontalDivider()

        // New Chat
        NavigationDrawerItem(
            label = { Text("New Chat") },
            selected = false,
            onClick = onNewChat,
            icon = { Icon(Icons.Filled.Add, contentDescription = null) },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        // Recent chats
        if (conversations.isNotEmpty()) {
            Text(
                "Recent Chats",
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(horizontal = 16.dp, vertical = 4.dp)
            )
            conversations.forEach { conv ->
                NavigationDrawerItem(
                    label = {
                        Text(
                            conv.title,
                            maxLines = 1,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    },
                    selected = conv.id == currentConversationId,
                    onClick = { onOpenConversation(conv.id) },
                    icon = { Icon(Icons.AutoMirrored.Filled.Chat, contentDescription = null) },
                    badge = {
                        IconButton(onClick = { onDeleteConversation(conv.id) }, modifier = Modifier.size(24.dp)) {
                            Icon(Icons.Filled.Delete, contentDescription = "Delete", modifier = Modifier.size(16.dp))
                        }
                    },
                    modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

            NavigationDrawerItem(
                label = { Text("Clear History") },
                selected = false,
                onClick = onClearAll,
                icon = { Icon(Icons.Filled.DeleteSweep, contentDescription = null) },
                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
            )
        }

        HorizontalDivider(modifier = Modifier.padding(vertical = 8.dp))

        NavigationDrawerItem(
            label = { Text("Settings") },
            selected = false,
            onClick = onOpenSettings,
            icon = { Icon(Icons.Filled.Settings, contentDescription = null) },
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp)
        )
    }
}
