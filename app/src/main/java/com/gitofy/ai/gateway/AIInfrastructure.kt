package com.gitofy.ai.gateway

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Feature Flags — PRD Section 115.
 * ai_assistant, ai_code_review, ai_build_analysis, ai_patch_generation,
 * ai_pr_generation, ai_workflow_analysis, ai_vision, ai_agent_actions
 *
 * Default: Read-only AI features → enabled if configured
 *          Write actions → disabled until user approval
 */
@Singleton
class AIFeatureFlags @Inject constructor() {

    enum class Flag(val defaultValue: Boolean, val isWriteAction: Boolean) {
        AI_ASSISTANT(true, false),
        AI_CODE_REVIEW(true, false),
        AI_BUILD_ANALYSIS(true, false),
        AI_PATCH_GENERATION(false, true),
        AI_PR_GENERATION(false, true),
        AI_WORKFLOW_ANALYSIS(true, false),
        AI_VISION(true, false),
        AI_AGENT_ACTIONS(false, true)
    }

    private val flags = Flag.entries.associateWith { it.defaultValue }.toMutableMap()

    fun isEnabled(flag: Flag): Boolean = flags[flag] ?: flag.defaultValue
    fun setEnabled(flag: Flag, enabled: Boolean) { flags[flag] = enabled }
    fun getAll(): Map<Flag, Boolean> = flags.toMap()
}

/**
 * AI Usage Limits — PRD Section 46.
 * Per-user request limits, token/context limits, provider-specific limits,
 * daily/monthly budgets, timeout limits, maximum output size.
 */
@Singleton
class AIUsageLimits @Inject constructor() {

    data class Limits(
        val maxRequestsPerDay: Int = 100,
        val maxRequestsPerMonth: Int = 2000,
        val maxTokensPerRequest: Int = 32000,
        val maxOutputTokens: Int = 4000,
        val maxLatencyMs: Long = 30_000,
        val maxConcurrentRequests: Int = 3
    )

    data class UsageState(
        val requestsToday: Int = 0,
        val requestsThisMonth: Int = 0,
        val lastRequestTimestamp: Long = 0,
        val activeRequests: Int = 0
    )

    private var limits = Limits()
    private var state = UsageState()

    fun canMakeRequest(): Pair<Boolean, String?> {
        if (state.requestsToday >= limits.maxRequestsPerDay) {
            return false to "Daily request limit (${limits.maxRequestsPerDay}) exceeded"
        }
        if (state.requestsThisMonth >= limits.maxRequestsPerMonth) {
            return false to "Monthly request limit (${limits.maxRequestsPerMonth}) exceeded"
        }
        if (state.activeRequests >= limits.maxConcurrentRequests) {
            return false to "Too many concurrent requests (${limits.maxConcurrentRequests})"
        }
        return true to null
    }

    fun onRequestStarted() {
        state = state.copy(
            requestsToday = state.requestsToday + 1,
            requestsThisMonth = state.requestsThisMonth + 1,
            lastRequestTimestamp = System.currentTimeMillis(),
            activeRequests = state.activeRequests + 1
        )
    }

    fun onRequestCompleted() {
        state = state.copy(activeRequests = state.activeRequests - 1)
    }

    fun getLimits(): Limits = limits
    fun setLimits(newLimits: Limits) { limits = newLimits }
    fun getUsage(): UsageState = state

    fun resetDaily() { state = state.copy(requestsToday = 0) }
    fun resetMonthly() { state = state.copy(requestsThisMonth = 0) }
}

/**
 * AI Cost Controls — PRD Section 113.
 * Daily budget, monthly budget, maximum request context, maximum output,
 * maximum latency, provider priority, fallback priority.
 */
@Singleton
class AICostControls @Inject constructor() {

    data class CostConfig(
        val dailyBudgetCents: Int = 100, // $1.00
        val monthlyBudgetCents: Int = 2000, // $20.00
        val maxContextTokens: Int = 32000,
        val maxOutputTokens: Int = 4000,
        val maxLatencyMs: Long = 30_000,
        val providerPriority: List<String> = listOf("gemini", "openrouter", "nvidia_nim", "opencode_zen", "custom"),
        val fallbackPriority: List<String> = listOf("openrouter", "gemini", "nvidia_nim", "custom")
    )

    data class CostState(
        val spentTodayCents: Int = 0,
        val spentThisMonthCents: Int = 0
    )

    private var config = CostConfig()
    private var state = CostState()

    fun getConfig(): CostConfig = config
    fun setConfig(newConfig: CostConfig) { config = newConfig }
    fun getState(): CostState = state

    fun canAfford(estimatedCostCents: Int): Boolean {
        return state.spentTodayCents + estimatedCostCents <= config.dailyBudgetCents &&
               state.spentThisMonthCents + estimatedCostCents <= config.monthlyBudgetCents
    }

    fun recordCost(cents: Int) {
        state = state.copy(
            spentTodayCents = state.spentTodayCents + cents,
            spentThisMonthCents = state.spentThisMonthCents + cents
        )
    }
}

/**
 * AI Emergency Controls — PRD Section 114.
 * Administrators can disable: Provider, Model, AI actions, Patch generation, External context transmission.
 */
@Singleton
class AIEmergencyControls @Inject constructor() {

    data class EmergencyConfig(
        val allAIEnabled: Boolean = true,
        val disabledProviders: Set<String> = emptySet(),
        val disabledModels: Set<String> = emptySet(),
        val patchGenerationEnabled: Boolean = true,
        val externalContextEnabled: Boolean = true,
        val lastUpdated: Long = System.currentTimeMillis()
    )

    private var config = EmergencyConfig()

    fun getConfig(): EmergencyConfig = config

    fun disableAllAI() { config = config.copy(allAIEnabled = false) }
    fun enableAllAI() { config = config.copy(allAIEnabled = true) }

    fun disableProvider(provider: String) {
        config = config.copy(disabledProviders = config.disabledProviders + provider)
    }

    fun disableModel(model: String) {
        config = config.copy(disabledModels = config.disabledModels + model)
    }

    fun disablePatchGeneration() { config = config.copy(patchGenerationEnabled = false) }
    fun disableExternalContext() { config = config.copy(externalContextEnabled = false) }

    fun isProviderAllowed(provider: String): Boolean =
        config.allAIEnabled && provider !in config.disabledProviders

    fun isModelAllowed(model: String): Boolean =
        config.allAIEnabled && model !in config.disabledModels
}
