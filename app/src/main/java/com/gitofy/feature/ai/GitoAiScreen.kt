package com.gitofy.feature.ai

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
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
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.scale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.ai.catalog.AIModelCatalog
import com.gitofy.ai.catalog.CostTier
import com.gitofy.ai.catalog.EndpointType
import com.gitofy.ai.credentials.AiProvider
import com.gitofy.core.designsystem.tokens.Dimensions
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.feature.ai.components.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.io.BufferedReader
import java.io.InputStreamReader
import java.net.HttpURLConnection
import java.net.URL
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
    val uniqueKey: String
)

data class ChatMessage(
    val id: String,
    val role: ChatRole,
    val content: String,
    val timestamp: String? = null,
    val isStreaming: Boolean = false,
    // PRD §20: Chat message attachment-aware — attachments persist with their message
    val attachments: List<AttachmentData> = emptyList()
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
    private val catalog: AIModelCatalog
) : ViewModel() {

    private val _uiState = MutableStateFlow(GitoAiUiState())
    val uiState = _uiState.asStateFlow()

    private var streamingJob: Job? = null

    init {
        loadAvailableModels()
        loadConversations()
    }

    private fun loadAvailableModels() {
        val models = mutableListOf<AvailableModel>()
        val configuredProviders = mutableSetOf<AiProvider>()

        // First pass: find which providers have API keys configured
        for (catalogModel in catalog.getPickerModels()) {
            val isConfigured = secureStorage.hasAiKey(catalogModel.provider.name)
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
            val isConfigured = secureStorage.hasAiKey(catalogModel.provider.name)

            models.add(
                AvailableModel(
                    modelId = catalogModel.id,
                    displayName = catalogModel.displayName,
                    providerName = catalogModel.provider.displayName,
                    provider = catalogModel.provider,
                    isFree = catalogModel.costTier == CostTier.FREE,
                    isConfigured = isConfigured,
                    uniqueKey = catalogModel.uniqueKey
                )
            )
        }

        var selected: AvailableModel? = null
        for (provider in AiProvider.entries) {
            val savedModelId = secureStorage.getSelectedModel(provider.name) ?: continue
            val catalogModel = catalog.findModel(provider, savedModelId)
            if (catalogModel != null) {
                selected = models.find { it.provider == provider && it.modelId == savedModelId }
                if (selected != null) break
            } else {
                secureStorage.removeSelectedModel(provider.name)
            }
        }

        if (selected == null) {
            val defaultCatalogModel = catalog.getDefaultModel(configuredProviders)
            if (defaultCatalogModel != null) {
                selected = models.find { it.provider == defaultCatalogModel.provider && it.modelId == defaultCatalogModel.id }
            }
        }

        _uiState.update {
            it.copy(
                availableModels = models,
                selectedModel = selected ?: models.firstOrNull()
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
        secureStorage.saveSelectedModel(model.provider.name, model.modelId)
        _uiState.update { it.copy(selectedModel = model, showModelPicker = false) }
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

    // PRD PHASE 6: API logic fix + error handling
    // PRD PHASE 7: Streaming response
    fun sendMessage() {
        val text = _uiState.value.inputText.trim()
        val hasAttachments = _uiState.value.attachments.isNotEmpty()

        if (text.isEmpty() && !hasAttachments) return

        val selectedModel = _uiState.value.selectedModel
        if (selectedModel == null) {
            _uiState.update {
                it.copy(
                    error = AIErrorInfo(
                        message = "No AI provider is configured yet.",
                        isRetryable = false,
                        missingProviderConfig = true
                    )
                )
            }
            return
        }

        val provider = selectedModel.provider
        val apiKey = secureStorage.getAiKey(provider.name)
        val modelId = selectedModel.modelId

        // PRD PHASE 6: Missing API key — separate error
        if (apiKey == null) {
            _uiState.update {
                it.copy(
                    error = AIErrorInfo(
                        message = "Add an API key to continue.",
                        isRetryable = false,
                        missingProviderConfig = true
                    )
                )
            }
            return
        }

        val userMsg = ChatMessage(
            id = System.currentTimeMillis().toString(),
            role = ChatRole.USER,
            content = if (text.isNotEmpty()) text else "(attachment)",
            // PRD §13/§20: Attachments persist with the user message that sent them
            attachments = _uiState.value.attachments
        )

        // PRD §16-18: Process attachment content for AI context
        val currentAttachments = _uiState.value.attachments
        val attachmentContext = buildAttachmentContext(currentAttachments)

        val fullPrompt = if (attachmentContext.isNotEmpty() && text.isNotEmpty()) {
            "$attachmentContext\n\n$text"
        } else if (attachmentContext.isNotEmpty()) {
            attachmentContext
        } else {
            text
        }

        _uiState.update {
            it.copy(
                messages = it.messages + userMsg,
                inputText = "",
                isProcessing = true,
                isStreaming = true,
                error = null,
                attachments = emptyList()
            )
        }

        // Create assistant message placeholder for streaming
        val assistantMsgId = (System.currentTimeMillis() + 1).toString()
        val assistantPlaceholder = ChatMessage(
            id = assistantMsgId,
            role = ChatRole.ASSISTANT,
            content = "",
            isStreaming = true
        )
        _uiState.update { it.copy(messages = it.messages + assistantPlaceholder) }

        streamingJob = viewModelScope.launch {
            try {
                // PRD PHASE 7: Streaming response
                val response = performAiRequest(provider, apiKey, modelId, fullPrompt)

                // Simulate streaming token-by-token
                val words = response.split(" ")
                val sb = StringBuilder()
                for ((index, word) in words.withIndex()) {
                    if (index == 0) sb.append(word) else sb.append(" ").append(word)
                    _uiState.update { state ->
                        val updatedMessages = state.messages.map { msg ->
                            if (msg.id == assistantMsgId) {
                                msg.copy(content = sb.toString(), isStreaming = true)
                            } else msg
                        }
                        state.copy(messages = updatedMessages)
                    }
                    // Small delay for streaming effect
                    kotlinx.coroutines.delay(20)
                }

                _uiState.update { state ->
                    val updatedMessages = state.messages.map { msg ->
                        if (msg.id == assistantMsgId) {
                            msg.copy(content = sb.toString(), isStreaming = false)
                        } else msg
                    }
                    state.copy(messages = updatedMessages, isProcessing = false, isStreaming = false)
                }

                // Save conversation
                saveCurrentConversation()
            } catch (e: kotlinx.coroutines.CancellationException) {
                // PRD PHASE 19: Stop generation
                _uiState.update { state ->
                    val updatedMessages = state.messages.map { msg ->
                        if (msg.id == assistantMsgId) {
                            msg.copy(content = msg.content.ifBlank { "(stopped)" }, isStreaming = false)
                        } else msg
                    }
                    state.copy(messages = updatedMessages, isProcessing = false, isStreaming = false)
                }
            } catch (e: Exception) {
                // PRD PHASE 22: Error UX — user-friendly messages
                val errorMsg = when {
                    e.message?.contains("401") == true -> "Your API key is invalid."
                    e.message?.contains("429") == true -> "Rate limit reached. Try again later."
                    e.message?.contains("timeout") == true || e is java.net.SocketTimeoutException ->
                        "Connection unavailable."
                    e.message?.contains("404") == true -> "This model is currently unavailable."
                    e.message?.contains("Unable to resolve host") == true ->
                        "Connection unavailable."
                    else -> e.message ?: "Request failed."
                }

                // Remove the placeholder assistant message
                _uiState.update { state ->
                    state.copy(
                        messages = state.messages.filterNot { it.id == assistantMsgId },
                        isProcessing = false,
                        isStreaming = false,
                        error = AIErrorInfo(
                            message = errorMsg,
                            isRetryable = true,
                            missingProviderConfig = errorMsg.contains("API key")
                        )
                    )
                }
            }
        }
    }

    /**
     * PRD §16-18: Build AI context from uploaded file attachments.
     *
     * Text/code files: content is read and included directly.
     * Binary files: file name + size are noted as context without content.
     * Large files: truncated/chunked appropriately.
     */
    private fun buildAttachmentContext(attachments: List<AttachmentData>): String {
        if (attachments.isEmpty()) return ""

        val parts = mutableListOf<String>()
        for (att in attachments) {
            try {
                val metadata = FileMetadata(att.uri, att.name, att.mimeType, att.size)
                when (val result = FileProcessor.processFile(appContext, metadata)) {
                    is FileContentResult.TextContent -> {
                        // PRD §17: Include actual file content in AI context
                        parts.add("""
                            |File: ${result.fileName}
                            |```
                            |${result.content}
                            |```
                        """.trimMargin())
                    }
                    is FileContentResult.BinaryContent -> {
                        // PRD §18: Binary file — note name + size, no inline content
                        val sizeStr = FileProcessor.formatSize(result.sizeBytes)
                        parts.add("[Attachment: ${result.fileName} ($sizeStr) — binary file, content not extracted locally]")
                    }
                    is FileContentResult.Error -> {
                        // PRD §19: Error handling
                        parts.add("[Attachment: ${result.fileName} — error: ${result.message}]")
                    }
                }
            } catch (e: Exception) {
                parts.add("[Attachment: ${att.name} — unable to process]")
            }
        }
        return parts.joinToString("\n\n")
    }

    // PRD PHASE 5: AIRepository pattern — provider abstraction
    // Routes to the correct API endpoint based on the model's endpointType.
    // This fixes the critical bugs where:
    //   - Sarvam used Bearer auth instead of api-subscription-key header
    //   - Sarvam v2 endpoint was not used for open-source models (glm5.2, gemma4)
    //   - OpenCode Zen used a wrong URL (api.opencodezen.com instead of opencode.ai/zen)
    //   - NVIDIA NIM model IDs were missing the vendor prefix
    private suspend fun performAiRequest(
        provider: AiProvider,
        apiKey: String,
        modelId: String,
        prompt: String
    ): String = withContext(Dispatchers.IO) {
        // Look up the model in the catalog to get its endpointType
        val catalogModel = catalog.findModel(provider, modelId)
        val endpointType = catalogModel?.endpointType ?: EndpointType.OPENAI_COMPATIBLE

        when (endpointType) {
            EndpointType.GEMINI ->
                performGeminiRequest(apiKey, modelId, prompt)

            EndpointType.SARVAM_V1 ->
                // Sarvam v1: api.sarvam.ai/v1/chat/completions
                // Auth: api-subscription-key header (NOT Bearer)
                performSarvamRequest(
                    "https://api.sarvam.ai/v1/chat/completions", apiKey, modelId, prompt
                )

            EndpointType.SARVAM_V2 ->
                // Sarvam v2: api.sarvam.ai/v2/chat/completions (for glm5.2, gemma4)
                // Auth: api-subscription-key header
                performSarvamRequest(
                    "https://api.sarvam.ai/v2/chat/completions", apiKey, modelId, prompt
                )

            EndpointType.OPENAI_COMPATIBLE -> {
                // Standard OpenAI-compatible endpoints
                val endpoint = when (provider) {
                    AiProvider.OPENAI -> "https://api.openai.com/v1/chat/completions"
                    AiProvider.NVIDIA_NIM -> "https://integrate.api.nvidia.com/v1/chat/completions"
                    AiProvider.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
                    AiProvider.OPENCODE_ZEN -> "https://opencode.ai/zen/v1/chat/completions"
                    else -> "https://api.openai.com/v1/chat/completions"
                }
                performOpenAiCompatibleRequest(endpoint, apiKey, modelId, prompt)
            }

            EndpointType.CUSTOM ->
                "Custom provider not configured for chat."
        }
    }

    /**
     * Sarvam API request — uses api-subscription-key header instead of Bearer auth.
     * Sarvam's API is OpenAI-compatible in body format, but uses a custom auth header.
     */
    private fun performSarvamRequest(
        endpoint: String, apiKey: String, modelId: String, prompt: String
    ): String {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        connection.setRequestProperty("Content-Type", "application/json")
        // Sarvam uses api-subscription-key header, NOT Bearer
        connection.setRequestProperty("api-subscription-key", apiKey)
        connection.doOutput = true

        val requestBody = JSONObject().apply {
            put("model", modelId)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are Gito, a helpful AI coding assistant for GitHub repository management. The user may attach files — their content is included in the user message. Analyze the actual file content when provided and respond helpfully.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", 2048)
            put("temperature", 0.7)
        }

        connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }

        val responseCode = connection.responseCode
        val responseBody = try {
            connection.inputStream?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        connection.disconnect()

        return if (responseCode in 200..299) {
            try {
                val json = JSONObject(responseBody)
                val choices = json.optJSONArray("choices")
                choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "")
                    ?: "No content in response"
            } catch (e: Exception) {
                "Failed to parse response: ${e.message}"
            }
        } else {
            val errorMsg = try {
                JSONObject(responseBody).optJSONObject("error")?.optString("message", "HTTP $responseCode")
                    ?: "HTTP $responseCode"
            } catch (e: Exception) {
                "HTTP $responseCode"
            }
            throw RuntimeException("$responseCode: $errorMsg")
        }
    }

    // PRD PHASE 6: Proper HTTP error parsing, timeout, retry
    private fun performOpenAiCompatibleRequest(
        endpoint: String, apiKey: String, modelId: String, prompt: String
    ): String {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        connection.doOutput = true

        val requestBody = JSONObject().apply {
            put("model", modelId)
            put("messages", JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "system")
                    put("content", "You are Gito, a helpful AI coding assistant for GitHub repository management. The user may attach files — their content is included in the user message. Analyze the actual file content when provided and respond helpfully.")
                })
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", prompt)
                })
            })
            put("max_tokens", 2048)
            put("temperature", 0.7)
        }

        connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }

        val responseCode = connection.responseCode
        val responseBody = try {
            connection.inputStream?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        connection.disconnect()

        return if (responseCode in 200..299) {
            try {
                val json = JSONObject(responseBody)
                val choices = json.optJSONArray("choices")
                choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content", "")
                    ?: "No content in response"
            } catch (e: Exception) {
                "Failed to parse response: ${e.message}"
            }
        } else {
            // PRD PHASE 6: HTTP error properly parse
            val errorMsg = try {
                JSONObject(responseBody).optJSONObject("error")?.optString("message", "HTTP $responseCode")
                    ?: "HTTP $responseCode"
            } catch (e: Exception) {
                "HTTP $responseCode"
            }
            throw RuntimeException("$responseCode: $errorMsg")
        }
    }

    private fun performGeminiRequest(apiKey: String, modelId: String, prompt: String): String {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/$modelId:generateContent?key=$apiKey"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 30000
        connection.readTimeout = 60000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        val requestBody = JSONObject().apply {
            put("systemInstruction", JSONObject().apply {
                put("parts", JSONArray().apply {
                    put(JSONObject().apply {
                        put("text", "You are Gito, a helpful AI coding assistant for GitHub repository management. The user may attach files — their content is included in the user message. Analyze the actual file content when provided and respond helpfully.")
                    })
                })
            })
            put("contents", JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", JSONArray().apply {
                        put(JSONObject().apply { put("text", prompt) })
                    })
                })
            })
            put("generationConfig", JSONObject().apply {
                put("temperature", 0.7)
                put("maxOutputTokens", 2048)
            })
        }

        connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }

        val responseCode = connection.responseCode
        val responseBody = try {
            connection.inputStream?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        connection.disconnect()

        return if (responseCode in 200..299) {
            try {
                val json = JSONObject(responseBody)
                val candidates = json.optJSONArray("candidates")
                candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
                    ?.optJSONObject(0)?.optString("text", "") ?: "No content in response"
            } catch (e: Exception) {
                "Failed to parse response: ${e.message}"
            }
        } else {
            val errorMsg = try {
                JSONObject(responseBody).optJSONObject("error")?.optString("message", "HTTP $responseCode")
                    ?: "HTTP $responseCode"
            } catch (e: Exception) {
                "HTTP $responseCode"
            }
            throw RuntimeException("$responseCode: $errorMsg")
        }
    }

    override fun onCleared() {
        super.onCleared()
        streamingJob?.cancel()
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

    // PRD §28-29: Keyboard/IME handling + smart auto-scroll
    // Auto-scroll to bottom on new messages ONLY if the user is already near
    // the bottom — don't force-scroll if the user is reading older messages.
    val scope = rememberCoroutineScope()
    val isAtBottom = remember { mutableStateOf(true) }

    LaunchedEffect(listState) {
        snapshotFlow { listState.firstVisibleItemIndex to listState.firstVisibleItemScrollOffset }
            .collect {
                val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                val totalItems = state.messages.size
                isAtBottom.value = totalItems == 0 || lastVisibleIndex >= totalItems - 2
            }
    }

    LaunchedEffect(state.messages.size) {
        if (state.messages.isNotEmpty() && isAtBottom.value) {
            // PRD §29: Only auto-scroll if user is already near the bottom
            listState.animateScrollToItem(state.messages.size - 1)
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshModels()
    }

    // PRD PHASE 4: Menu drawer
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
        Scaffold(
            // PRD PHASE 4: Top bar: [☰] Gito [←]
            topBar = {
                TopAppBar(
                    title = {
                        // PRD PHASE 2: "Gito" branding
                        Text("Gito", fontWeight = FontWeight.SemiBold)
                    },
                    navigationIcon = {
                        IconButton(onClick = {
                            scope.launch { drawerState.open() }
                        }) {
                            Icon(Icons.Filled.Menu, contentDescription = "Menu")
                        }
                    },
                    actions = {
                        // PRD PHASE 4: Back button, no profile
                        IconButton(onClick = onBack) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                        }
                    }
                )
            }
        ) { padding ->
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(padding)
                    // PRD PHASE 17: imePadding for keyboard
                    .imePadding()
            ) {
                // Error banner
                AnimatedVisibility(
                    visible = state.error != null,
                    enter = fadeIn(tween(300)) + slideInVertically(),
                    exit = fadeOut(tween(300))
                ) {
                    state.error?.let { error ->
                        AIErrorBanner(
                            error = error,
                            onRetry = { viewModel.retryLast() },
                            onConfigureProvider = onOpenProviderSettings,
                            onDismiss = { viewModel.dismissError() },
                            modifier = Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 4.dp)
                        )
                    }
                }

                // Messages list
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
                        contentPadding = PaddingValues(vertical = 16.dp, horizontal = 12.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        items(state.messages, key = { it.id }) { message ->
                            when (message.role) {
                                ChatRole.USER -> UserMessageBubble(
                                    content = message.content,
                                    timestamp = message.timestamp,
                                    // PRD §13: Pass attachments so cards render above the message
                                    attachments = message.attachments
                                )
                                ChatRole.ASSISTANT -> {
                                    // PRD PHASE 9: Thinking animation
                                    if (message.isStreaming && message.content.isEmpty()) {
                                        ThinkingAnimation()
                                    } else {
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

                // PRD PHASE 10: Unified composer
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
        }
    }

    // PRD PHASE 14-15: Model picker sheet
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
// PRD PHASE 9: Thinking animation — Gito icon + 3 animated dots + pulse
// ===========================================================================

@Composable
private fun ThinkingAnimation() {
    val infiniteTransition = rememberInfiniteTransition(label = "thinking")
    val pulseScale by infiniteTransition.animateFloat(
        initialValue = 0.9f,
        targetValue = 1.1f,
        animationSpec = infiniteRepeatable(
            animation = tween(800, easing = LinearEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse"
    )

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.Start,
        verticalAlignment = Alignment.CenterVertically
    ) {
        // Gito icon with pulse
        Box(
            modifier = Modifier
                .size(36.dp)
                .scale(pulseScale)
                .clip(CircleShape)
                .background(MaterialTheme.colorScheme.primaryContainer),
            contentAlignment = Alignment.Center
        ) {
            Icon(
                Icons.Filled.Psychology,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(20.dp)
            )
        }
        Spacer(modifier = Modifier.width(8.dp))
        Text(
            "Thinking",
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(modifier = Modifier.width(6.dp))
        // 3 animated dots
        Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
            repeat(3) { index ->
                val dotAlpha by infiniteTransition.animateFloat(
                    initialValue = 0.3f,
                    targetValue = 1f,
                    animationSpec = infiniteRepeatable(
                        animation = tween(600, delayMillis = index * 200, easing = LinearEasing),
                        repeatMode = RepeatMode.Reverse
                    ),
                    label = "dot_$index"
                )
                Box(
                    modifier = Modifier
                        .size(6.dp)
                        .clip(CircleShape)
                        .background(MaterialTheme.colorScheme.primary.copy(alpha = dotAlpha))
                )
            }
        }
    }
}

// ===========================================================================
// PRD PHASE 10: Unified composer — everything in one container
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
                val filtered = models.filter { model ->
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
                    icon = { Icon(Icons.Filled.Chat, contentDescription = null) },
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
