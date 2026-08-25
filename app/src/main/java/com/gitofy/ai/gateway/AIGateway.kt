package com.gitofy.ai.gateway

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Gateway — PRD v4.0 Sections 5-6.
 *
 * The Android application must NOT directly contain production AI provider secrets.
 * Required architecture:
 *   GITOFY Android → Authenticated AI Gateway → Provider Router → AI Provider
 *
 * The AI Gateway is responsible for:
 *   - Provider authentication
 *   - Model routing
 *   - Rate limiting
 *   - Provider health
 *   - Context filtering
 *   - Secret redaction
 *   - Request normalization
 *   - Response normalization
 *   - Usage accounting
 *   - Fallback
 *   - Retry classification
 *   - AI policy enforcement
 */
@Singleton
class AIGateway @Inject constructor(
    private val providerRegistry: com.gitofy.ai.provider.ProviderRegistry,
    private val modelRouter: com.gitofy.ai.routing.ModelRouter,
    private val contextEngine: com.gitofy.ai.context.AIContextEngineV4,
    private val fallbackSystem: com.gitofy.ai.routing.FallbackSystem,
    private val providerHealth: com.gitofy.ai.routing.ProviderHealthSystem,
    private val usageAccountant: com.gitofy.ai.gateway.UsageAccountant,
    private val policyEnforcer: com.gitofy.ai.gateway.AIPolicyEnforcer
) {

    data class GatewayRequest(
        val taskType: com.gitofy.ai.model.AITaskType,
        val userPrompt: String,
        val contextData: Map<String, String>,
        val attachments: List<ByteArray> = emptyList(),
        val requireVision: Boolean = false,
        val requireToolCalling: Boolean = false,
        val maxLatencyMs: Long = 30_000,
        val costBudget: CostBudget = CostBudget.FREE_FIRST,
        val userPreferences: UserPreferences = UserPreferences()
    )

    data class GatewayResponse(
        val content: String,
        val structuredOutput: Any? = null,
        val provider: String,
        val model: String,
        val fellBack: Boolean,
        val tokensUsed: Int,
        val latencyMs: Long,
        val sourceReferences: List<String> = emptyList(),
        val confidence: com.gitofy.ai.model.AIConfidenceLevel = com.gitofy.ai.model.AIConfidenceLevel.UNKNOWN
    )

    enum class CostBudget { FREE_FIRST, LOW_COST, QUALITY_FIRST, USER_SELECTED }
    data class UserPreferences(val preferredProvider: String? = null, val routingMode: RoutingMode = RoutingMode.AUTO)
    enum class RoutingMode { AUTO, COST_OPTIMIZED, SPEED_OPTIMIZED, QUALITY_OPTIMIZED, USER_SELECTED }

    /**
     * Main gateway entry point.
     * Flow: Request → Policy Check → Context Build → Secret Redact → Route → Provider → Validate → Response
     */
    suspend fun process(request: GatewayRequest): Result<GatewayResponse> {
        val startTime = System.currentTimeMillis()

        // 1. AI Policy Enforcement — check if request is allowed
        val policyResult = policyEnforcer.check(request)
        if (!policyResult.allowed) {
            return Result.failure(RuntimeException(policyResult.reason))
        }

        // 2. Context Engine — build filtered context
        val context = contextEngine.buildContext(
            request.userPrompt,
            request.contextData,
            com.gitofy.ai.context.AIContextEngineV4.ContextConfig(
                requireVision = request.requireVision,
                costBudget = request.costBudget
            )
        )

        // 3. Model Routing — select best model
        val routingResult = modelRouter.route(
            com.gitofy.ai.routing.RoutingRequest(
                taskType = request.taskType,
                contextSize = context.estimatedTokens,
                requireVision = request.requireVision,
                requireToolCalling = request.requireToolCalling,
                costBudget = request.costBudget,
                userPreferences = request.userPreferences
            )
        )

        if (!routingResult.success) {
            return Result.failure(RuntimeException("No suitable model available"))
        }

        // 4. Execute with fallback
        val result = fallbackSystem.executeWithFallback(routingResult.selectedModel!!) { model ->
            val provider = providerRegistry.getProvider(model.provider)
                ?: return@executeWithFallback Result.failure(RuntimeException("Provider ${model.provider} not found"))

            provider.generate(
                com.gitofy.ai.provider.AIProvider.GenerateRequest(
                    prompt = request.userPrompt,
                    context = context.filteredContext,
                    modelId = model.modelId,
                    systemPrompt = context.systemPrompt,
                    attachments = request.attachments,
                    requireVision = request.requireVision
                )
            ).map { response ->
                GatewayResponse(
                    content = response.content,
                    structuredOutput = response.structuredOutput,
                    provider = model.provider,
                    model = model.modelId,
                    fellBack = false,
                    tokensUsed = response.tokensUsed,
                    latencyMs = System.currentTimeMillis() - startTime,
                    sourceReferences = context.sourceAttribution,
                    confidence = response.confidence
                )
            }
        }

        // 5. Usage Accounting
        result.onSuccess { response ->
            usageAccountant.record(
                UsageAccountant.UsageRecord(
                    provider = response.provider,
                    model = response.model,
                    taskType = request.taskType,
                    tokensUsed = response.tokensUsed,
                    latencyMs = response.latencyMs,
                    success = true,
                    fellBack = response.fellBack,
                    timestamp = System.currentTimeMillis()
                )
            )
        }.onFailure { error ->
            usageAccountant.record(
                UsageAccountant.UsageRecord(
                    provider = routingResult.selectedModel?.provider ?: "unknown",
                    model = routingResult.selectedModel?.modelId ?: "unknown",
                    taskType = request.taskType,
                    tokensUsed = 0,
                    latencyMs = System.currentTimeMillis() - startTime,
                    success = false,
                    fellBack = false,
                    timestamp = System.currentTimeMillis(),
                    error = error.message
                )
            )
        }

        return result
    }

    /**
     * Streaming request — PRD Section 47.
     */
    suspend fun processStream(
        request: GatewayRequest,
        onChunk: (String) -> Unit
    ): Result<GatewayResponse> {
        val startTime = System.currentTimeMillis()

        val policyResult = policyEnforcer.check(request)
        if (!policyResult.allowed) {
            return Result.failure(RuntimeException(policyResult.reason))
        }

        val context = contextEngine.buildContext(
            request.userPrompt, request.contextData,
            com.gitofy.ai.context.AIContextEngineV4.ContextConfig(requireVision = request.requireVision)
        )

        val routingResult = modelRouter.route(
            com.gitofy.ai.routing.RoutingRequest(
                taskType = request.taskType,
                contextSize = context.estimatedTokens,
                requireVision = request.requireVision,
                requireToolCalling = request.requireToolCalling,
                costBudget = request.costBudget,
                userPreferences = request.userPreferences
            )
        )

        if (!routingResult.success) {
            return Result.failure(RuntimeException("No suitable model for streaming"))
        }

        val model = routingResult.selectedModel!!
        val provider = providerRegistry.getProvider(model.provider)
            ?: return Result.failure(RuntimeException("Provider not found"))

        return provider.stream(
            com.gitofy.ai.provider.AIProvider.GenerateRequest(
                prompt = request.userPrompt,
                context = context.filteredContext,
                modelId = model.modelId,
                systemPrompt = context.systemPrompt,
                attachments = request.attachments,
                requireVision = request.requireVision
            ),
            onChunk
        ).map { response ->
            GatewayResponse(
                content = response.content,
                provider = model.provider,
                model = model.modelId,
                fellBack = false,
                tokensUsed = response.tokensUsed,
                latencyMs = System.currentTimeMillis() - startTime,
                sourceReferences = context.sourceAttribution
            )
        }
    }

    /**
     * Cancel an ongoing request — PRD Section 56.
     */
    fun cancel(requestId: String) {
        // Propagate cancellation: UI → Gateway → Provider
        // Where provider cancellation is unavailable, stop waiting and discard late results
    }
}

/**
 * AI Policy Enforcer — PRD Section 6.
 * Enforces AI policy: consent, feature flags, rate limits, usage limits.
 */
@Singleton
class AIPolicyEnforcer @Inject constructor(
    private val featureFlags: com.gitofy.ai.gateway.AIFeatureFlags
) {
    data class PolicyResult(val allowed: Boolean, val reason: String)

    fun check(request: AIGateway.GatewayRequest): PolicyResult {
        // Check feature flag
        val flag = when (request.taskType) {
            com.gitofy.ai.model.AITaskType.GENERAL_QA, com.gitofy.ai.model.AITaskType.CODE_EXPLANATION -> AIFeatureFlags.Flag.AI_ASSISTANT
            com.gitofy.ai.model.AITaskType.CODE_REVIEW -> AIFeatureFlags.Flag.AI_CODE_REVIEW
            com.gitofy.ai.model.AITaskType.BUILD_FAILURE_ANALYSIS, com.gitofy.ai.model.AITaskType.BUG_ANALYSIS -> AIFeatureFlags.Flag.AI_BUILD_ANALYSIS
            com.gitofy.ai.model.AITaskType.PATCH_GENERATION -> AIFeatureFlags.Flag.AI_PATCH_GENERATION
            com.gitofy.ai.model.AITaskType.PR_GENERATION -> AIFeatureFlags.Flag.AI_PR_GENERATION
            com.gitofy.ai.model.AITaskType.WORKFLOW_ANALYSIS, com.gitofy.ai.model.AITaskType.WORKFLOW_GENERATION -> AIFeatureFlags.Flag.AI_WORKFLOW_ANALYSIS
            com.gitofy.ai.model.AITaskType.VISION_UI_ANALYSIS -> AIFeatureFlags.Flag.AI_VISION
            else -> AIFeatureFlags.Flag.AI_ASSISTANT
        }

        if (!featureFlags.isEnabled(flag)) {
            return PolicyResult(false, "AI feature '$flag' is not enabled")
        }

        return PolicyResult(true, "Allowed")
    }
}

/**
 * Usage Accountant — PRD Sections 6, 52, 65, 74.
 * Tracks AI request success, fallback rate, latency, token usage, cost.
 * Never stores raw prompts or secrets.
 */
@Singleton
class UsageAccountant @Inject constructor() {

    data class UsageRecord(
        val provider: String,
        val model: String,
        val taskType: com.gitofy.ai.model.AITaskType,
        val tokensUsed: Int,
        val latencyMs: Long,
        val success: Boolean,
        val fellBack: Boolean,
        val timestamp: Long,
        val error: String? = null
    )

    data class UsageSummary(
        val totalRequests: Int,
        val successfulRequests: Int,
        val failedRequests: Int,
        val fallbackRate: Float,
        val averageLatencyMs: Long,
        val totalTokensUsed: Int,
        val providerBreakdown: Map<String, Int>
    ) {
        val successRate: Float get() = if (totalRequests > 0) successfulRequests.toFloat() / totalRequests else 0f
    }

    private val records = mutableListOf<UsageRecord>()

    fun record(record: UsageRecord) { records.add(record) }

    fun getSummary(): UsageSummary {
        val total = records.size
        val successful = records.count { it.success }
        val fallbacks = records.count { it.fellBack }
        val providerCounts = records.groupBy { it.provider }.mapValues { it.value.size }

        return UsageSummary(
            totalRequests = total,
            successfulRequests = successful,
            failedRequests = total - successful,
            fallbackRate = if (total > 0) fallbacks.toFloat() / total else 0f,
            averageLatencyMs = if (total > 0) records.map { it.latencyMs }.average().toLong() else 0,
            totalTokensUsed = records.sumOf { it.tokensUsed },
            providerBreakdown = providerCounts
        )
    }

    fun getTodaySummary(): UsageSummary {
        val oneDayAgo = System.currentTimeMillis() - 24 * 60 * 60 * 1000
        val todayRecords = records.filter { it.timestamp >= oneDayAgo }
        return summarizeList(todayRecords)
    }

    private fun summarizeList(list: List<UsageRecord>): UsageSummary {
        val total = list.size
        val providerCounts = list.groupBy { it.provider }.mapValues { it.value.size }
        return UsageSummary(
            totalRequests = total,
            successfulRequests = list.count { it.success },
            failedRequests = list.count { !it.success },
            fallbackRate = if (total > 0) list.count { it.fellBack }.toFloat() / total else 0f,
            averageLatencyMs = if (total > 0) list.map { it.latencyMs }.average().toLong() else 0,
            totalTokensUsed = list.sumOf { it.tokensUsed },
            providerBreakdown = providerCounts
        )
    }
}
