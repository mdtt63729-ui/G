package com.gitofy.ai.provider

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Provider Abstraction — PRD Sections 7-8, 10-14.
 * All providers must implement a common interface.
 * The Android application communicates with a normalized GITOFY AI interface
 * rather than provider-specific APIs wherever possible.
 */
interface AIProvider {

    data class GenerateRequest(
        val prompt: String,
        val context: String,
        val modelId: String,
        val systemPrompt: String,
        val attachments: List<ByteArray> = emptyList(),
        val requireVision: Boolean = false,
        val maxOutputTokens: Int = 4000,
        val temperature: Float = 0.7f
    )

    data class GenerateResponse(
        val content: String,
        val structuredOutput: Any? = null,
        val tokensUsed: Int,
        val confidence: com.gitofy.ai.model.AIConfidenceLevel = com.gitofy.ai.model.AIConfidenceLevel.UNKNOWN,
        val sourceReferences: List<String> = emptyList()
    )

    data class HealthStatus(
        val isAvailable: Boolean,
        val latencyMs: Long?,
        val errorMessage: String?
    )

    val providerId: String
    val displayName: String

    suspend fun generate(request: GenerateRequest): Result<GenerateResponse>
    suspend fun stream(request: GenerateRequest, onChunk: (String) -> Unit): Result<GenerateResponse>
    suspend fun analyze(request: GenerateRequest): Result<GenerateResponse> = generate(request)
    suspend fun summarize(request: GenerateRequest): Result<GenerateResponse> = generate(request)
    suspend fun explain(request: GenerateRequest): Result<GenerateResponse> = generate(request)
    suspend fun generatePatch(request: GenerateRequest): Result<GenerateResponse> = generate(request)
    suspend fun classify(request: GenerateRequest): Result<GenerateResponse> = generate(request)
    suspend fun healthCheck(): HealthStatus
}

/**
 * Provider Registry — PRD Section 8.
 * Provider configuration must be dynamic. Model IDs must not be hardcoded throughout the app.
 *
 * ProviderRegistry
 * ├── GeminiProvider
 * ├── NvidiaNimProvider
 * ├── OpenRouterProvider
 * ├── OpenCodeZenProvider
 * └── CustomProvider
 */
@Singleton
class ProviderRegistry @Inject constructor() {

    private val providers = mutableMapOf<String, AIProvider>()

    fun register(provider: AIProvider) {
        providers[provider.providerId] = provider
    }

    fun getProvider(providerId: String): AIProvider? = providers[providerId]

    fun getAllProviders(): List<AIProvider> = providers.values.toList()

    fun getAvailableProviders(): List<AIProvider> = providers.values.filter {
        // Would run healthCheck in production
        true
    }

    fun unregister(providerId: String) { providers.remove(providerId) }
}

// ============================================================
// PRD Section 10 — Gemini Provider
// Gemini integration must support configurable current models.
// Roles: Advanced reasoning, large-context analysis, code generation, fast debugging, multimodal.
// Model registry must be periodically updated against official Google model availability.
// ============================================================
class GeminiProvider @Inject constructor() : AIProvider {
    override val providerId = "gemini"
    override val displayName = "Gemini"

    override suspend fun generate(request: AIProvider.GenerateRequest): Result<AIProvider.GenerateResponse> {
        // In production: calls AI Gateway which routes to Google Gemini API
        // The Android app never contains the API key — it calls the secure gateway
        return Result.success(
            AIProvider.GenerateResponse(
                content = "[Gemini] Analysis complete for: ${request.prompt.take(100)}",
                tokensUsed = request.prompt.length / 4,
                confidence = com.gitofy.ai.model.AIConfidenceLevel.HIGH
            )
        )
    }

    override suspend fun stream(request: AIProvider.GenerateRequest, onChunk: (String) -> Unit): Result<AIProvider.GenerateResponse> {
        // Simulate streaming
        val words = "[Gemini] Streaming response for: ${request.prompt.take(50)}".split(" ")
        words.forEach { onChunk("$it ") }
        return Result.success(
            AIProvider.GenerateResponse(
                content = words.joinToString(" "),
                tokensUsed = request.prompt.length / 4
            )
        )
    }

    override suspend fun healthCheck(): AIProvider.HealthStatus {
        return AIProvider.HealthStatus(true, 150, null)
    }
}

// ============================================================
// PRD Section 11 — NVIDIA NIM Provider
// Provider adapter capable of supporting compatible current NIM-hosted models.
// Roles: High-performance coding, reasoning, agentic workflows, large-scale code analysis, self-hosted/private.
// ============================================================
class NvidiaNimProvider @Inject constructor() : AIProvider {
    override val providerId = "nvidia_nim"
    override val displayName = "NVIDIA NIM"

    override suspend fun generate(request: AIProvider.GenerateRequest): Result<AIProvider.GenerateResponse> {
        return Result.success(
            AIProvider.GenerateResponse(
                content = "[NVIDIA NIM] Analysis: ${request.prompt.take(100)}",
                tokensUsed = request.prompt.length / 4,
                confidence = com.gitofy.ai.model.AIConfidenceLevel.HIGH
            )
        )
    }

    override suspend fun stream(request: AIProvider.GenerateRequest, onChunk: (String) -> Unit): Result<AIProvider.GenerateResponse> {
        val response = "[NVIDIA NIM] Streaming: ${request.prompt.take(50)}"
        response.chunked(10).forEach { onChunk(it) }
        return Result.success(AIProvider.GenerateResponse(response, tokensUsed = request.prompt.length / 4))
    }

    override suspend fun healthCheck(): AIProvider.HealthStatus {
        return AIProvider.HealthStatus(true, 200, null)
    }
}

// ============================================================
// PRD Section 12 — OpenRouter Provider
// Multi-model routing provider. Used for provider redundancy, model diversity,
// cost optimization, fallback, specialized coding models, alternative reasoning models.
// ============================================================
class OpenRouterProvider @Inject constructor() : AIProvider {
    override val providerId = "openrouter"
    override val displayName = "OpenRouter"

    override suspend fun generate(request: AIProvider.GenerateRequest): Result<AIProvider.GenerateResponse> {
        return Result.success(
            AIProvider.GenerateResponse(
                content = "[OpenRouter] Response: ${request.prompt.take(100)}",
                tokensUsed = request.prompt.length / 4,
                confidence = com.gitofy.ai.model.AIConfidenceLevel.MEDIUM
            )
        )
    }

    override suspend fun stream(request: AIProvider.GenerateRequest, onChunk: (String) -> Unit): Result<AIProvider.GenerateResponse> {
        val response = "[OpenRouter] Stream: ${request.prompt.take(50)}"
        response.chunked(8).forEach { onChunk(it) }
        return Result.success(AIProvider.GenerateResponse(response, tokensUsed = request.prompt.length / 4))
    }

    override suspend fun healthCheck(): AIProvider.HealthStatus {
        return AIProvider.HealthStatus(true, 180, null)
    }
}

// ============================================================
// PRD Section 13 — OpenCode Zen Provider
// Dedicated provider adapter. Supports model discovery/configuration, coding tasks,
// agentic tasks, fallback, health checks, streaming where supported.
// ============================================================
class OpenCodeZenProvider @Inject constructor() : AIProvider {
    override val providerId = "opencode_zen"
    override val displayName = "OpenCode Zen"

    override suspend fun generate(request: AIProvider.GenerateRequest): Result<AIProvider.GenerateResponse> {
        return Result.success(
            AIProvider.GenerateResponse(
                content = "[OpenCode Zen] Result: ${request.prompt.take(100)}",
                tokensUsed = request.prompt.length / 4,
                confidence = com.gitofy.ai.model.AIConfidenceLevel.MEDIUM
            )
        )
    }

    override suspend fun stream(request: AIProvider.GenerateRequest, onChunk: (String) -> Unit): Result<AIProvider.GenerateResponse> {
        val response = "[OpenCode Zen] Stream: ${request.prompt.take(50)}"
        response.chunked(9).forEach { onChunk(it) }
        return Result.success(AIProvider.GenerateResponse(response, tokensUsed = request.prompt.length / 4))
    }

    override suspend fun healthCheck(): AIProvider.HealthStatus {
        return AIProvider.HealthStatus(true, 250, null)
    }
}

// ============================================================
// PRD Section 14 — Custom Provider
 // Supports optional custom providers: Custom OpenAI-compatible endpoint,
// self-hosted inference server, enterprise AI gateway, private NIM deployment, future provider.
// Required config: Base URL, Auth method, Model ID, Capabilities, Context limit, Timeout, Priority.
// Sensitive credentials must remain server-side.
// ============================================================
class CustomProvider @Inject constructor() : AIProvider {
    override val providerId = "custom"
    override val displayName = "Custom Provider"

    var config: CustomProviderConfig? = null

    data class CustomProviderConfig(
        val baseUrl: String,
        val authMethod: String, // "bearer", "api_key", "none"
        val modelId: String,
        val contextLimit: Int = 32000,
        val timeoutMs: Long = 30_000,
        val priority: Int = 100,
        val supportsVision: Boolean = false,
        val supportsStreaming: Boolean = true,
        val supportsToolCalling: Boolean = false,
        val supportsStructuredOutput: Boolean = false
    )

    override suspend fun generate(request: AIProvider.GenerateRequest): Result<AIProvider.GenerateResponse> {
        val cfg = config ?: return Result.failure(RuntimeException("Custom provider not configured"))
        return Result.success(
            AIProvider.GenerateResponse(
                content = "[Custom:${cfg.baseUrl}] Response: ${request.prompt.take(80)}",
                tokensUsed = request.prompt.length / 4
            )
        )
    }

    override suspend fun stream(request: AIProvider.GenerateRequest, onChunk: (String) -> Unit): Result<AIProvider.GenerateResponse> {
        val cfg = config ?: return Result.failure(RuntimeException("Custom provider not configured"))
        val response = "[Custom:${cfg.baseUrl}] Stream: ${request.prompt.take(50)}"
        response.chunked(10).forEach { onChunk(it) }
        return Result.success(AIProvider.GenerateResponse(response, tokensUsed = request.prompt.length / 4))
    }

    override suspend fun healthCheck(): AIProvider.HealthStatus {
        if (config == null) return AIProvider.HealthStatus(false, null, "Not configured")
        return AIProvider.HealthStatus(true, 300, null)
    }
}
