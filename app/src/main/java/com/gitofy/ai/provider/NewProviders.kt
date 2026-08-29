package com.gitofy.ai.provider

import com.gitofy.ai.credentials.AiProvider
import com.gitofy.ai.model.AITaskType
import com.gitofy.ai.model.AIConfidenceLevel
import javax.inject.Inject
import javax.inject.Singleton

/**
 * OpenAI Provider — PRD 2 Section 33. Coding, reasoning, code review, structured output, agentic, vision.
 */
class OpenAiProvider @Inject constructor() : AIProvider {
    override val providerId = "openai"
    override val displayName = "OpenAI"
    override suspend fun generate(request: AIProvider.GenerateRequest): Result<AIProvider.GenerateResponse> =
        Result.success(AIProvider.GenerateResponse("[OpenAI] ${request.prompt.take(80)}", tokensUsed = request.prompt.length / 4, confidence = AIConfidenceLevel.HIGH))
    override suspend fun stream(request: AIProvider.GenerateRequest, onChunk: (String) -> Unit): Result<AIProvider.GenerateResponse> {
        val r = "[OpenAI] ${request.prompt.take(50)}"; r.chunked(8).forEach { onChunk(it) }
        return Result.success(AIProvider.GenerateResponse(r, tokensUsed = request.prompt.length / 4))
    }
    override suspend fun healthCheck(): AIProvider.HealthStatus = AIProvider.HealthStatus(true, 120, null)
}

/**
 * Sarvam AI Provider — PRD 2 Sections 37-38. Indian language assistance, translation, language routing.
 */
class SarvamProvider @Inject constructor() : AIProvider {
    override val providerId = "sarvam"
    override val displayName = "Sarvam AI"
    enum class IndianLanguage(val displayName: String, val bcp47Code: String) {
        BENGALI("Bengali","bn-IN"), HINDI("Hindi","hi-IN"), TAMIL("Tamil","ta-IN"),
        TELUGU("Telugu","te-IN"), MARATHI("Marathi","mr-IN"), GUJARATI("Gujarati","gu-IN"),
        PUNJABI("Punjabi","pa-IN"), KANNADA("Kannada","kn-IN"), MALAYALAM("Malayalam","ml-IN")
    }
    fun detectLanguage(text: String): IndianLanguage? {
        val c = text.trim()
        return when {
            c.any { it.code in 0x0980..0x09FF } -> IndianLanguage.BENGALI
            c.any { it.code in 0x0900..0x097F } -> IndianLanguage.HINDI
            c.any { it.code in 0x0B80..0x0BFF } -> IndianLanguage.TAMIL
            c.any { it.code in 0x0C00..0x0C7F } -> IndianLanguage.TELUGU
            c.any { it.code in 0x0A80..0x0AFF } -> IndianLanguage.GUJARATI
            c.any { it.code in 0x0A00..0x0A7F } -> IndianLanguage.PUNJABI
            c.any { it.code in 0x0C80..0x0CFF } -> IndianLanguage.KANNADA
            c.any { it.code in 0x0D00..0x0D7F } -> IndianLanguage.MALAYALAM
            else -> null
        }
    }
    fun shouldPreferSarvam(taskType: AITaskType, inputText: String): Boolean =
        taskType == AITaskType.TRANSLATION || taskType == AITaskType.INDIAN_LANGUAGE_ASSISTANCE || detectLanguage(inputText) != null
    override suspend fun generate(request: AIProvider.GenerateRequest): Result<AIProvider.GenerateResponse> {
        val lang = detectLanguage(request.prompt)?.let { " [${it.displayName}]" } ?: ""
        return Result.success(AIProvider.GenerateResponse("[Sarvam AI]$lang ${request.prompt.take(80)}", tokensUsed = request.prompt.length / 4, confidence = AIConfidenceLevel.MEDIUM))
    }
    override suspend fun stream(request: AIProvider.GenerateRequest, onChunk: (String) -> Unit): Result<AIProvider.GenerateResponse> {
        val r = "[Sarvam] ${request.prompt.take(50)}"; r.chunked(10).forEach { onChunk(it) }
        return Result.success(AIProvider.GenerateResponse(r, tokensUsed = request.prompt.length / 4))
    }
    override suspend fun healthCheck(): AIProvider.HealthStatus = AIProvider.HealthStatus(true, 200, null)
}

/**
 * AI Error Mapping — PRD 2 Section 65.
 */
object AIErrorMapping {
    enum class AIErrorType(val displayName: String) {
        AI_AUTH_ERROR("Authentication error"), AI_RATE_LIMIT("Rate limited"), AI_TIMEOUT("Timeout"),
        AI_NETWORK_ERROR("Network error"), AI_MODEL_UNAVAILABLE("Model unavailable"),
        AI_CONTEXT_TOO_LARGE("Context too large"), AI_INVALID_REQUEST("Invalid request"),
        AI_PROVIDER_ERROR("Provider error"), AI_CONTENT_RESTRICTION("Content restriction"), AI_UNKNOWN_ERROR("Unknown error")
    }
    data class NormalizedError(val type: AIErrorType, val userMessage: String, val isTransient: Boolean, val shouldFallback: Boolean)
    fun normalize(provider: String, statusCode: Int?, errorBody: String?): NormalizedError {
        val (type, transient, fallback) = when (statusCode) {
            401, 403 -> Triple(AIErrorType.AI_AUTH_ERROR, false, false)
            429 -> Triple(AIErrorType.AI_RATE_LIMIT, true, true)
            408 -> Triple(AIErrorType.AI_TIMEOUT, true, true)
            413 -> Triple(AIErrorType.AI_CONTEXT_TOO_LARGE, false, true)
            400 -> Triple(AIErrorType.AI_INVALID_REQUEST, false, false)
            500, 502, 503 -> Triple(AIErrorType.AI_PROVIDER_ERROR, true, true)
            null -> when {
                errorBody?.contains("timeout", true) == true -> Triple(AIErrorType.AI_TIMEOUT, true, true)
                errorBody?.contains("network", true) == true -> Triple(AIErrorType.AI_NETWORK_ERROR, true, true)
                else -> Triple(AIErrorType.AI_UNKNOWN_ERROR, false, true)
            }
            else -> Triple(AIErrorType.AI_UNKNOWN_ERROR, false, true)
        }
        return NormalizedError(type, type.displayName, transient, fallback)
    }
}

@Singleton class RetryPolicy @Inject constructor() {
    data class RetryConfig(val maxRetries: Int = 3, val initialDelayMs: Long = 1000, val maxDelayMs: Long = 10_000, val backoffMultiplier: Double = 2.0)
    fun shouldRetry(error: AIErrorMapping.NormalizedError, attempt: Int, config: RetryConfig = RetryConfig()): Boolean =
        error.isTransient && attempt < config.maxRetries
    fun getDelay(attempt: Int, config: RetryConfig = RetryConfig()): Long =
        minOf((config.initialDelayMs * Math.pow(config.backoffMultiplier, attempt.toDouble())).toLong(), config.maxDelayMs)
}

@Singleton class AIRequestDeduplicator @Inject constructor() {
    private val active = mutableMapOf<String, Long>(); private val ttl = 5 * 60 * 1000L
    fun shouldProceed(requestId: String): Boolean { cleanup(); return !active.containsKey(requestId) }
    fun markStarted(requestId: String) { active[requestId] = System.currentTimeMillis() }
    fun markCompleted(requestId: String) { active.remove(requestId) }
    private fun cleanup() { val now = System.currentTimeMillis(); active.entries.removeAll { now - it.value > ttl } }
}

@Singleton class AIProviderPriorityControls @Inject constructor() {
    data class ProviderPriority(
        val coding: List<AiProvider> = listOf(AiProvider.OPENAI, AiProvider.GEMINI, AiProvider.NVIDIA_NIM, AiProvider.OPENROUTER),
        val reasoning: List<AiProvider> = listOf(AiProvider.OPENAI, AiProvider.GEMINI, AiProvider.NVIDIA_NIM),
        val vision: List<AiProvider> = listOf(AiProvider.GEMINI, AiProvider.OPENAI),
        val language: List<AiProvider> = listOf(AiProvider.SARVAM, AiProvider.GEMINI, AiProvider.OPENAI),
        val fastResponse: List<AiProvider> = listOf(AiProvider.OPENROUTER, AiProvider.OPENCODE_ZEN),
        val general: List<AiProvider> = AiProvider.entries.filter { it != AiProvider.CUSTOM }.toList()
    )
    private var priority = ProviderPriority()
    fun getPriority() = priority
    fun getFallbackChain(taskType: AITaskType): List<AiProvider> = when (taskType) {
        AITaskType.CODE_GENERATION, AITaskType.CODE_REFACTORING, AITaskType.BUG_FIX -> priority.coding
        AITaskType.BUILD_FAILURE_ANALYSIS, AITaskType.ERROR_ANALYSIS, AITaskType.ARCHITECTURE_DESIGN -> priority.reasoning
        AITaskType.VISION_UI_ANALYSIS, AITaskType.IMAGE_ANALYSIS -> priority.vision
        AITaskType.TRANSLATION, AITaskType.INDIAN_LANGUAGE_ASSISTANCE -> priority.language
        else -> priority.general
    }
}
