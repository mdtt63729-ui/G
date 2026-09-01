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
        val reasoningTokens: Int = 0,
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

    fun getAvailableProviders(): List<AIProvider> = providers.values.toList()

    fun unregister(providerId: String) { providers.remove(providerId) }
}
