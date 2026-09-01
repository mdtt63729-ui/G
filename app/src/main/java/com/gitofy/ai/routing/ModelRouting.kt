package com.gitofy.ai.routing

import javax.inject.Inject
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch
import kotlinx.coroutines.flow.collect

/**
 * AI Provider Matrix / Model Registry — PRD Section 9.
 * Capability-driven model registry, NOT permanently hardcoding five specific models per provider.
 * Each model record contains: modelId, provider, displayName, contextWindow, inputCapabilities,
 * outputCapabilities, visionSupport, toolCalling, structuredOutput, streaming, codingScore,
 * reasoningScore, latencyClass, costClass, availability, health, fallbackPriority.
 */
@Singleton
class ModelRegistry @Inject constructor(
    private val dynamicModelRepository: com.gitofy.ai.catalog.DynamicModelRepository
) {

    data class ModelRecord(
        val modelId: String,
        val provider: String,
        val displayName: String,
        val contextWindow: Int,
        val inputCapabilities: Set<InputCapability>,
        val outputCapabilities: Set<OutputCapability>,
        val visionSupport: Boolean,
        val toolCalling: Boolean,
        val structuredOutput: Boolean,
        val streaming: Boolean,
        val codingScore: Int,        // 1-10
        val reasoningScore: Int,     // 1-10
        val latencyClass: LatencyClass,
        val costClass: CostClass,
        val availability: Availability,
        val fallbackPriority: Int   // lower = higher priority
    )

    enum class InputCapability { TEXT, IMAGE, CODE, STRUCTURED, AUDIO }
    enum class OutputCapability { TEXT, CODE, STRUCTURED_JSON, PATCH, YAML, MARKDOWN }
    enum class LatencyClass { FAST, MEDIUM, SLOW }
    enum class CostClass { FREE, LOW_COST, MEDIUM_COST, HIGH_COST }
    enum class Availability { AVAILABLE, DEGRADED, UNAVAILABLE, UNKNOWN }

    private val models = mutableListOf<ModelRecord>()

    private val dynamicScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)
    private val dynamicKeys = mutableSetOf<String>()

    init {
        // Seed with known provider models — dynamically updatable
        // PRD Section 10: Model registry must be periodically updated against official availability
        // FIX: Model IDs now match what each provider's API actually expects

        // Gemini
        register(ModelRecord("gemini-3.5-flash", "gemini", "Gemini 3.5 Flash", 1_000_000,
            setOf(InputCapability.TEXT, InputCapability.IMAGE, InputCapability.CODE), setOf(OutputCapability.TEXT, OutputCapability.CODE, OutputCapability.MARKDOWN),
            visionSupport = true, toolCalling = true, structuredOutput = true, streaming = true,
            codingScore = 10, reasoningScore = 9, latencyClass = LatencyClass.FAST, costClass = CostClass.FREE,
            availability = Availability.AVAILABLE, fallbackPriority = 1))

        register(ModelRecord("gemini-2.5-pro", "gemini", "Gemini 2.5 Pro", 2_000_000,
            setOf(InputCapability.TEXT, InputCapability.IMAGE, InputCapability.CODE, InputCapability.STRUCTURED), setOf(OutputCapability.TEXT, OutputCapability.CODE, OutputCapability.STRUCTURED_JSON, OutputCapability.PATCH),
            visionSupport = true, toolCalling = true, structuredOutput = true, streaming = true,
            codingScore = 9, reasoningScore = 10, latencyClass = LatencyClass.MEDIUM, costClass = CostClass.FREE,
            availability = Availability.AVAILABLE, fallbackPriority = 2))

        // NVIDIA NIM — FIX: Model IDs now include vendor prefix
        register(ModelRecord("deepseek-ai/deepseek-v4-flash-0731", "nvidia_nim", "DeepSeek V4 Flash 0731", 1_000_000,
            setOf(InputCapability.TEXT, InputCapability.CODE), setOf(OutputCapability.TEXT, OutputCapability.CODE, OutputCapability.PATCH),
            visionSupport = false, toolCalling = false, structuredOutput = false, streaming = true,
            codingScore = 10, reasoningScore = 9, latencyClass = LatencyClass.FAST, costClass = CostClass.FREE,
            availability = Availability.AVAILABLE, fallbackPriority = 3))

        register(ModelRecord("nvidia/nemotron-3.5-lightning-30b-a3b", "nvidia_nim", "Nemotron 3.5 Lightning 30B", 256_000,
            setOf(InputCapability.TEXT, InputCapability.CODE), setOf(OutputCapability.TEXT, OutputCapability.CODE),
            visionSupport = false, toolCalling = false, structuredOutput = false, streaming = true,
            codingScore = 8, reasoningScore = 7, latencyClass = LatencyClass.FAST, costClass = CostClass.FREE,
            availability = Availability.AVAILABLE, fallbackPriority = 4))

        // OpenRouter — FIX: Model IDs now include full vendor slug
        register(ModelRecord("deepseek/deepseek-v4-flash-latest", "openrouter", "DeepSeek V4 Flash Latest", 1_000_000,
            setOf(InputCapability.TEXT, InputCapability.CODE), setOf(OutputCapability.TEXT, OutputCapability.CODE, OutputCapability.PATCH),
            visionSupport = false, toolCalling = true, structuredOutput = false, streaming = true,
            codingScore = 10, reasoningScore = 9, latencyClass = LatencyClass.FAST, costClass = CostClass.LOW_COST,
            availability = Availability.AVAILABLE, fallbackPriority = 5))

        // OpenCode Zen — FIX: endpoint is opencode.ai/zen/v1
        register(ModelRecord("big-pickle", "opencode_zen", "Big Pickle", 256_000,
            setOf(InputCapability.TEXT, InputCapability.CODE), setOf(OutputCapability.TEXT, OutputCapability.CODE, OutputCapability.PATCH),
            visionSupport = false, toolCalling = false, structuredOutput = false, streaming = true,
            codingScore = 9, reasoningScore = 8, latencyClass = LatencyClass.MEDIUM, costClass = CostClass.FREE,
            availability = Availability.AVAILABLE, fallbackPriority = 6))

        // Sarvam — FIX: model ID is sarvam-105b (not sarvam-1)
        register(ModelRecord("sarvam-105b", "sarvam", "Sarvam 105B Chat", 128_000,
            setOf(InputCapability.TEXT, InputCapability.CODE), setOf(OutputCapability.TEXT, OutputCapability.CODE),
            visionSupport = false, toolCalling = false, structuredOutput = false, streaming = true,
            codingScore = 7, reasoningScore = 8, latencyClass = LatencyClass.FAST, costClass = CostClass.FREE,
            availability = Availability.AVAILABLE, fallbackPriority = 7))

        // Dynamic registry is authoritative for successfully discovered
        // providers, while cached results remain available offline.
        dynamicScope.launch {
            dynamicModelRepository.models.collect { discovered ->
                synchronized(models) {
                    models.removeAll { "${it.provider}:${it.modelId}" in dynamicKeys }
                    dynamicKeys.clear()
                    discovered.forEach { model ->
                        val provider = model.provider.name.lowercase()
                        val key = "$provider:${model.id}"
                        // Replace a static duplicate with the live definition.
                        models.removeAll { "${it.provider}:${it.modelId}" == key }
                        models.add(
                            ModelRecord(
                                modelId = model.id,
                                provider = provider,
                                displayName = model.displayName,
                                contextWindow = model.contextWindow,
                                inputCapabilities = buildSet {
                                    if (model.supportsText) add(InputCapability.TEXT)
                                    if (model.supportsImage) add(InputCapability.IMAGE)
                                    if (model.supportsAudio) add(InputCapability.AUDIO)
                                    add(InputCapability.CODE)
                                },
                                outputCapabilities = setOf(OutputCapability.TEXT, OutputCapability.CODE),
                                visionSupport = model.supportsImage,
                                toolCalling = model.supportsTools,
                                structuredOutput = model.supportsStructuredOutput,
                                streaming = model.supportsStreaming,
                                codingScore = model.codingScore,
                                reasoningScore = model.reasoningScore,
                                latencyClass = when (model.latencyClass) {
                                    com.gitofy.ai.catalog.LatencyClass.FAST -> LatencyClass.FAST
                                    com.gitofy.ai.catalog.LatencyClass.SLOW -> LatencyClass.SLOW
                                    else -> LatencyClass.MEDIUM
                                },
                                costClass = when (model.costTier) {
                                    com.gitofy.ai.catalog.CostTier.FREE -> CostClass.FREE
                                    com.gitofy.ai.catalog.CostTier.FREE_CREDIT, com.gitofy.ai.catalog.CostTier.LOW_COST -> CostClass.LOW_COST
                                    com.gitofy.ai.catalog.CostTier.MEDIUM_COST -> CostClass.MEDIUM_COST
                                    else -> CostClass.HIGH_COST
                                },
                                availability = if (model.status == com.gitofy.ai.catalog.ModelStatus.ACTIVE) Availability.AVAILABLE else Availability.UNKNOWN,
                                fallbackPriority = 50
                            )
                        )
                        dynamicKeys.add(key)
                    }
                }
            }
        }
    }

    fun register(model: ModelRecord) { models.add(model) }
    fun unregister(modelId: String) { models.removeAll { it.modelId == modelId } }
    fun getAllModels(): List<ModelRecord> = models.toList()

    fun getModelsByProvider(provider: String): List<ModelRecord> = models.filter { it.provider == provider }
    fun getAvailableModels(): List<ModelRecord> = models.filter { it.availability == Availability.AVAILABLE }

    fun updateAvailability(modelId: String, availability: Availability) {
        models.replaceAll { if (it.modelId == modelId) it.copy(availability = availability) else it }
    }
}

/**
 * Dynamic Routing Logic — PRD Section 17.
 * User Request → Task Classifier → Capability Requirements → Context Estimator →
 * Provider Health → Cost Policy → User Preferences → Model Router → Selected Model
 */
@Singleton
class ModelRouter @Inject constructor(
    private val registry: ModelRegistry,
    private val healthSystem: ProviderHealthSystem,
    private val freeFirstPolicy: FreeFirstCostPolicy
) {

    data class RoutingRequest(
        val taskType: com.gitofy.ai.model.AITaskType,
        val contextSize: Int,
        val requireVision: Boolean,
        val requireToolCalling: Boolean,
        val requireStructuredOutput: Boolean = false,
        val costBudget: com.gitofy.ai.gateway.AIGateway.CostBudget = com.gitofy.ai.gateway.AIGateway.CostBudget.FREE_FIRST,
        val userPreferences: com.gitofy.ai.gateway.AIGateway.UserPreferences
    )

    data class RoutingResult(
        val success: Boolean,
        val selectedModel: ModelRegistry.ModelRecord?,
        val reason: String
    )

    fun route(request: RoutingRequest): RoutingResult {
        val availableModels = registry.getAvailableModels().toMutableList()

        // 1. Filter by context window
        val contextFiltered = availableModels.filter { it.contextWindow >= request.contextSize }
        if (contextFiltered.isEmpty()) return RoutingResult(false, null, "No model with sufficient context window")

        // 2. Filter by vision capability
        val visionFiltered = if (request.requireVision) contextFiltered.filter { it.visionSupport } else contextFiltered
        if (visionFiltered.isEmpty()) return RoutingResult(false, null, "No vision-capable model available")

        // 3. Filter by tool calling
        val toolFiltered = if (request.requireToolCalling) visionFiltered.filter { it.toolCalling } else visionFiltered
        if (toolFiltered.isEmpty()) return RoutingResult(false, null, "No tool-calling model available")

        // 4. Filter by structured output
        val structuredFiltered = if (request.requireStructuredOutput) toolFiltered.filter { it.structuredOutput } else toolFiltered
        if (structuredFiltered.isEmpty()) return RoutingResult(false, null, "No structured-output model available")

        // 5. Check provider health
        val healthFiltered = structuredFiltered.filter { model ->
            val health = healthSystem.getHealth(model.provider)
            // UNKNOWN is eligible: a fresh install has no history yet.
            // Only explicitly unavailable/rate-limited providers are excluded.
            health != ProviderHealthSystem.HealthStatus.UNAVAILABLE &&
                health != ProviderHealthSystem.HealthStatus.RATE_LIMITED
        }
        if (healthFiltered.isEmpty()) return RoutingResult(false, null, "All providers unhealthy or rate-limited")

        // 6. Exact user-selected model override. This prevents the UI model picker
        // from silently routing to a different model.
        request.userPreferences.preferredModel?.let { modelId ->
            val exact = healthFiltered.firstOrNull {
                it.modelId == modelId &&
                    (request.userPreferences.preferredProvider == null || it.provider == request.userPreferences.preferredProvider)
            }
            if (exact != null) return RoutingResult(true, exact, "User-selected model")
        }

        if (request.userPreferences.preferredProvider != null) {
            val preferred = healthFiltered.filter { it.provider == request.userPreferences.preferredProvider }
            if (preferred.isNotEmpty()) {
                return RoutingResult(true, preferred.sortedBy { it.fallbackPriority }.first(), "User-selected provider")
            }
        }

        // 7. Apply cost policy (Free-First by default)
        val sorted = when (request.costBudget) {
            com.gitofy.ai.gateway.AIGateway.CostBudget.FREE_FIRST -> freeFirstPolicy.sort(healthFiltered)
            com.gitofy.ai.gateway.AIGateway.CostBudget.LOW_COST -> healthFiltered.sortedBy { it.costClass.ordinal }
            com.gitofy.ai.gateway.AIGateway.CostBudget.QUALITY_FIRST -> healthFiltered.sortedByDescending { it.reasoningScore }
            com.gitofy.ai.gateway.AIGateway.CostBudget.USER_SELECTED -> healthFiltered.sortedBy { it.fallbackPriority }
        }

        // 8. Task-specific optimization
        val taskOptimized = when (request.taskType) {
            com.gitofy.ai.model.AITaskType.CODE_GENERATION, com.gitofy.ai.model.AITaskType.PATCH_GENERATION ->
                sorted.sortedByDescending { it.codingScore }
            com.gitofy.ai.model.AITaskType.BUILD_FAILURE_ANALYSIS, com.gitofy.ai.model.AITaskType.ARCHITECTURE_REVIEW ->
                sorted.sortedByDescending { it.reasoningScore }
            com.gitofy.ai.model.AITaskType.VISION_UI_ANALYSIS ->
                sorted.filter { it.visionSupport }.sortedByDescending { it.codingScore }
            else -> sorted
        }

        val selected = taskOptimized.firstOrNull()
            ?: return RoutingResult(false, null, "No model matched all criteria")

        return RoutingResult(true, selected, "Routed to ${selected.provider}/${selected.modelId}")
    }
}

/**
 * Free-First Cost Policy — PRD Section 18.
 * Free-first, not free-only.
 * Priority: 1. Free model satisfying requirements, 2. Low-cost, 3. User-selected premium, 4. Emergency fallback.
 * A free endpoint must never be treated as guaranteed production capacity.
 */
@Singleton
class FreeFirstCostPolicy @Inject constructor() {

    fun sort(models: List<ModelRegistry.ModelRecord>): List<ModelRegistry.ModelRecord> {
        return models.sortedWith(
            compareBy<ModelRegistry.ModelRecord>
                { it.costClass.ordinal }           // FREE first, then LOW_COST, etc.
                .thenBy { it.fallbackPriority }     // Then by priority
                .thenByDescending { it.codingScore } // Then by coding capability
        )
    }
}

/**
 * AI Fallback System — PRD Section 19.
 * Fallback must consider capability compatibility.
 * Fallback reasons: 429, 5xx, Timeout, Provider unavailable, Model unavailable,
 * Context limit exceeded, Capability mismatch, Temporary network failure.
 * Do NOT fallback on invalid user input unless the fallback can meaningfully solve it.
 */
@Singleton
class FallbackSystem @Inject constructor(
    private val registry: ModelRegistry,
    private val healthSystem: ProviderHealthSystem
) {

    enum class FallbackReason { RATE_LIMITED_429, SERVER_ERROR_5XX, TIMEOUT, PROVIDER_UNAVAILABLE, MODEL_UNAVAILABLE, CONTEXT_LIMIT_EXCEEDED, CAPABILITY_MISMATCH, NETWORK_FAILURE }

    suspend fun <T> executeWithFallback(
        primaryModel: ModelRegistry.ModelRecord,
        execute: suspend (ModelRegistry.ModelRecord) -> Result<T>
    ): Result<T> {
        // Try primary
        val primaryResult = execute(primaryModel)
        if (primaryResult.isSuccess) return primaryResult

        // Mark primary as degraded based on error
        val error = primaryResult.exceptionOrNull()?.message ?: ""
        val reason = classifyError(error)

        if (reason == null) return primaryResult // Not a fallback-able error

        // Mark provider health
        when (reason) {
            FallbackReason.RATE_LIMITED_429 -> healthSystem.markRateLimited(primaryModel.provider)
            FallbackReason.TIMEOUT -> healthSystem.markDegraded(primaryModel.provider)
            FallbackReason.PROVIDER_UNAVAILABLE -> healthSystem.markUnavailable(primaryModel.provider)
            else -> {}
        }

        // Find compatible fallback model
        val fallbackModel = findCompatibleFallback(primaryModel, reason)
            ?: return primaryResult // No compatible fallback available

        // Try fallback
        return execute(fallbackModel)
    }

    private fun classifyError(error: String): FallbackReason? {
        return when {
            error.contains("429") -> FallbackReason.RATE_LIMITED_429
            error.contains("500") || error.contains("502") || error.contains("503") -> FallbackReason.SERVER_ERROR_5XX
            error.contains("timeout", ignoreCase = true) -> FallbackReason.TIMEOUT
            error.contains("unavailable", ignoreCase = true) -> FallbackReason.PROVIDER_UNAVAILABLE
            error.contains("model", ignoreCase = true) && error.contains("not found", ignoreCase = true) -> FallbackReason.MODEL_UNAVAILABLE
            error.contains("context", ignoreCase = true) && error.contains("limit", ignoreCase = true) -> FallbackReason.CONTEXT_LIMIT_EXCEEDED
            error.contains("network", ignoreCase = true) -> FallbackReason.NETWORK_FAILURE
            else -> null // Don't fallback on invalid user input
        }
    }

    private fun findCompatibleFallback(
        originalModel: ModelRegistry.ModelRecord,
        reason: FallbackReason
    ): ModelRegistry.ModelRecord? {
        val availableModels = registry.getAvailableModels()
            .filter { it.modelId != originalModel.modelId }
            .filter { it.provider != originalModel.provider || reason == FallbackReason.MODEL_UNAVAILABLE }
            .filter { healthSystem.getHealth(it.provider) == ProviderHealthSystem.HealthStatus.AVAILABLE }

        // Match capabilities
        val capabilityMatched = availableModels.filter { model ->
            model.contextWindow >= originalModel.contextWindow &&
            (!originalModel.visionSupport || model.visionSupport) &&
            (!originalModel.toolCalling || model.toolCalling)
        }

        return capabilityMatched.sortedBy { it.fallbackPriority }.firstOrNull()
    }
}

/**
 * Provider Health System — PRD Section 20.
 * Health states: AVAILABLE, DEGRADED, RATE_LIMITED, UNAVAILABLE, UNKNOWN.
 * Track: Latency, Error rate, Timeout rate, Recent 429s, Recent 5xx, Successful requests.
 * Unhealthy providers should temporarily receive lower routing priority.
 */
@Singleton
class ProviderHealthSystem @Inject constructor() {

    enum class HealthStatus { AVAILABLE, DEGRADED, RATE_LIMITED, UNAVAILABLE, UNKNOWN }

    data class ProviderHealth(
        val status: HealthStatus,
        val latencyMs: Long,
        val errorRate: Float,
        val successCount: Int,
        val failureCount: Int,
        val lastUpdated: Long
    )

    private val healthMap = mutableMapOf<String, ProviderHealth>()

    fun getHealth(provider: String): HealthStatus {
        return healthMap[provider]?.status ?: HealthStatus.UNKNOWN
    }

    fun getProviderHealth(provider: String): ProviderHealth {
        return healthMap[provider] ?: ProviderHealth(HealthStatus.UNKNOWN, 0, 0f, 0, 0, 0)
    }

    fun recordSuccess(provider: String, latencyMs: Long) {
        val current = healthMap[provider] ?: ProviderHealth(HealthStatus.AVAILABLE, 0, 0f, 0, 0, 0)
        val newSuccess = current.successCount + 1
        val newLatency = (current.latencyMs * current.successCount + latencyMs) / newSuccess
        val newErrorRate = current.failureCount.toFloat() / (newSuccess + current.failureCount)
        val newStatus = if (newErrorRate < 0.1f) HealthStatus.AVAILABLE else if (newErrorRate < 0.3f) HealthStatus.DEGRADED else HealthStatus.UNAVAILABLE
        healthMap[provider] = current.copy(status = newStatus, latencyMs = newLatency, errorRate = newErrorRate, successCount = newSuccess, lastUpdated = System.currentTimeMillis())
    }

    fun markRateLimited(provider: String) {
        val current = healthMap[provider] ?: ProviderHealth(HealthStatus.AVAILABLE, 0, 0f, 0, 0, 0)
        healthMap[provider] = current.copy(status = HealthStatus.RATE_LIMITED, lastUpdated = System.currentTimeMillis())
    }

    fun markDegraded(provider: String) {
        val current = healthMap[provider] ?: ProviderHealth(HealthStatus.AVAILABLE, 0, 0f, 0, 0, 0)
        healthMap[provider] = current.copy(status = HealthStatus.DEGRADED, lastUpdated = System.currentTimeMillis())
    }

    fun markUnavailable(provider: String) {
        healthMap[provider] = ProviderHealth(HealthStatus.UNAVAILABLE, 0, 1f, 0, 0, System.currentTimeMillis())
    }

    fun recover(provider: String) {
        val current = healthMap[provider] ?: return
        healthMap[provider] = current.copy(status = HealthStatus.AVAILABLE, lastUpdated = System.currentTimeMillis())
    }
}
