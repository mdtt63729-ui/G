package com.gitofy.ai.provider

import com.gitofy.ai.credentials.AiProvider
import com.gitofy.ai.model.AITaskType
import javax.inject.Inject
import javax.inject.Singleton

/** Normalized provider error mapping used by gateway/fallback UI. */
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
            408 -> Triple(AIErrorType.AI_TIMEOUT, true, true)
            413 -> Triple(AIErrorType.AI_CONTEXT_TOO_LARGE, false, true)
            429 -> Triple(AIErrorType.AI_RATE_LIMIT, true, true)
            400, 422 -> Triple(AIErrorType.AI_INVALID_REQUEST, false, false)
            500, 502, 503, 504 -> Triple(AIErrorType.AI_PROVIDER_ERROR, true, true)
            null -> when {
                errorBody?.contains("timeout", true) == true -> Triple(AIErrorType.AI_TIMEOUT, true, true)
                errorBody?.contains("network", true) == true -> Triple(AIErrorType.AI_NETWORK_ERROR, true, true)
                else -> Triple(AIErrorType.AI_UNKNOWN_ERROR, false, true)
            }
            else -> Triple(AIErrorType.AI_UNKNOWN_ERROR, false, true)
        }
        return NormalizedError(type, "$provider: ${type.displayName}", transient, fallback)
    }
}

@Singleton
class RetryPolicy @Inject constructor() {
    data class RetryConfig(val maxRetries: Int = 2, val initialDelayMs: Long = 750, val maxDelayMs: Long = 8_000, val backoffMultiplier: Double = 2.0)
    fun shouldRetry(error: AIErrorMapping.NormalizedError, attempt: Int, config: RetryConfig = RetryConfig()) = error.isTransient && attempt < config.maxRetries
    fun getDelay(attempt: Int, config: RetryConfig = RetryConfig()): Long = minOf((config.initialDelayMs * Math.pow(config.backoffMultiplier, attempt.toDouble())).toLong(), config.maxDelayMs)
}

@Singleton
class AIRequestDeduplicator @Inject constructor() {
    private val active = java.util.concurrent.ConcurrentHashMap<String, Long>()
    private val ttl = 5 * 60 * 1000L
    fun shouldProceed(requestId: String): Boolean { cleanup(); return active.putIfAbsent(requestId, System.currentTimeMillis()) == null }
    fun markStarted(requestId: String) { active[requestId] = System.currentTimeMillis() }
    fun markCompleted(requestId: String) { active.remove(requestId) }
    private fun cleanup() { val now = System.currentTimeMillis(); active.entries.removeIf { now - it.value > ttl } }
}

@Singleton
class AIProviderPriorityControls @Inject constructor() {
    data class ProviderPriority(
        val coding: List<AiProvider> = listOf(AiProvider.OPENAI, AiProvider.GEMINI, AiProvider.NVIDIA_NIM, AiProvider.OPENROUTER),
        val reasoning: List<AiProvider> = listOf(AiProvider.OPENAI, AiProvider.GEMINI, AiProvider.NVIDIA_NIM),
        val vision: List<AiProvider> = listOf(AiProvider.GEMINI, AiProvider.OPENAI),
        val language: List<AiProvider> = listOf(AiProvider.SARVAM, AiProvider.GEMINI, AiProvider.OPENAI),
        val fastResponse: List<AiProvider> = listOf(AiProvider.OPENROUTER, AiProvider.OPENCODE_ZEN),
        val general: List<AiProvider> = AiProvider.entries.filter { it != AiProvider.CUSTOM }
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
