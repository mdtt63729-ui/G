package com.gitofy.ai.gateway

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Quality Metrics — PRD Section 65.
 * Track anonymized: AI request success, fallback rate, latency, timeout rate,
 * patch acceptance, patch verification success, user rejection, provider failure.
 * Never use source code or secrets as analytics payloads.
 */
@Singleton
class AIQualityMetrics @Inject constructor() {

    data class Metrics(
        val totalRequests: Int = 0,
        val successfulRequests: Int = 0,
        val failedRequests: Int = 0,
        val fallbackRate: Float = 0f,
        val averageLatencyMs: Long = 0,
        val timeoutRate: Float = 0f,
        val patchAccepted: Int = 0,
        val patchRejected: Int = 0,
        val patchVerificationSuccess: Int = 0,
        val patchVerificationFailed: Int = 0,
        val providerFailureCount: Map<String, Int> = emptyMap()
    ) {
        val successRate: Float get() = if (totalRequests > 0) successfulRequests.toFloat() / totalRequests else 0f
        val patchAcceptanceRate: Float get() {
            val total = patchAccepted + patchRejected
            return if (total > 0) patchAccepted.toFloat() / total else 0f
        }
    }

    private var metrics = Metrics()
    private val latencies = mutableListOf<Long>()
    private val providerFailures = mutableMapOf<String, Int>()

    fun recordSuccess(latencyMs: Long) {
        latencies.add(latencyMs)
        metrics = metrics.copy(
            totalRequests = metrics.totalRequests + 1,
            successfulRequests = metrics.successfulRequests + 1,
            averageLatencyMs = if (latencies.isNotEmpty()) latencies.average().toLong() else 0
        )
    }

    fun recordFailure(provider: String, isTimeout: Boolean = false) {
        providerFailures[provider] = (providerFailures[provider] ?: 0) + 1
        val timeouts = if (isTimeout) 1 else 0
        metrics = metrics.copy(
            totalRequests = metrics.totalRequests + 1,
            failedRequests = metrics.failedRequests + 1,
            timeoutRate = if (metrics.totalRequests > 0) (metrics.timeoutRate * (metrics.totalRequests - 1) + timeouts) / metrics.totalRequests else 0f,
            providerFailureCount = providerFailures.toMap()
        )
    }

    fun recordFallback() {
        val fallbackCount = if (metrics.totalRequests > 0) (metrics.fallbackRate * metrics.totalRequests).toInt() + 1 else 1
        metrics = metrics.copy(fallbackRate = if (metrics.totalRequests > 0) fallbackCount.toFloat() / metrics.totalRequests else 0f)
    }

    fun recordPatchAccepted() { metrics = metrics.copy(patchAccepted = metrics.patchAccepted + 1) }
    fun recordPatchRejected() { metrics = metrics.copy(patchRejected = metrics.patchRejected + 1) }
    fun recordPatchVerificationSuccess() { metrics = metrics.copy(patchVerificationSuccess = metrics.patchVerificationSuccess + 1) }
    fun recordPatchVerificationFailed() { metrics = metrics.copy(patchVerificationFailed = metrics.patchVerificationFailed + 1) }

    fun getMetrics(): Metrics = metrics
}

/**
 * AI Gateway Observability — PRD Section 74.
 * Monitor: Request count, success rate, latency, provider errors, 429, 5xx,
 * timeout, fallback rate, token usage, cost.
 * Never log raw prompts containing secrets or sensitive source code.
 */
@Singleton
class AIGatewayObservability @Inject constructor(
    private val usageAccountant: UsageAccountant,
    private val qualityMetrics: AIQualityMetrics
) {

    data class ObservabilityDashboard(
        val requestCount: Int,
        val successRate: Float,
        val averageLatencyMs: Long,
        val fallbackRate: Float,
        val totalTokensUsed: Int,
        val providerBreakdown: Map<String, Int>,
        val errorBreakdown: Map<String, Int>
    )

    fun getDashboard(): ObservabilityDashboard {
        val usage = usageAccountant.getTodaySummary()
        val metrics = qualityMetrics.getMetrics()
        return ObservabilityDashboard(
            requestCount = usage.totalRequests,
            successRate = usage.successRate,
            averageLatencyMs = usage.averageLatencyMs,
            fallbackRate = usage.fallbackRate,
            totalTokensUsed = usage.totalTokensUsed,
            providerBreakdown = usage.providerBreakdown,
            errorBreakdown = metrics.providerFailureCount
        )
    }
}

/**
 * AI Gateway Secret Management — PRD Section 71.
 * Provider credentials must be stored using:
 * GitHub Actions Secrets for CI, Production secret manager for deployed gateway,
 * Environment variables or secure secret injection.
 * Never: commit API key, hardcode API key, put API key in APK, put API key in GitHub source.
 */
@Singleton
class AIGatewaySecretManager @Inject constructor() {

    data class SecretConfig(
        val secretManagerType: SecretManagerType,
        val isConfigured: Boolean,
        val lastRotatedAt: Long?
    )

    enum class SecretManagerType { GITHUB_ACTIONS_SECRETS, PRODUCTION_SECRET_MANAGER, ENVIRONMENT_VARIABLE }

    fun getConfig(): SecretConfig {
        // In production, this would check actual secret configuration
        return SecretConfig(SecretManagerType.PRODUCTION_SECRET_MANAGER, true, null)
    }

    /**
     * API Key Rotation — PRD Section 72.
     * Gateway must support: Key rotation, Provider disablement, Provider replacement,
     * Emergency credential revocation.
     * No Android app update should be required merely to rotate provider secrets.
     */
    fun rotateKey(provider: String): Result<Unit> {
        // In production, this would trigger key rotation in the secret manager
        return Result.success(Unit)
    }

    fun revokeCredentials(provider: String): Result<Unit> {
        // Emergency credential revocation
        return Result.success(Unit)
    }
}

/**
 * AI Reliability Targets — PRD Section 75.
 * Initial targets:
 * - AI gateway availability > 99.5%
 * - Successful AI requests > 98%
 * - Fallback recovery > 90% for transient provider failures
 * - P95 routing latency < 500 ms excluding model generation
 * These are operational targets, not guarantees.
 */
@Singleton
class AIReliabilityTargets @Inject constructor() {

    data class ReliabilityTargets(
        val gatewayAvailability: Float = 99.5f,
        val successRate: Float = 98f,
        val fallbackRecoveryRate: Float = 90f,
        val p95RoutingLatencyMs: Long = 500
    )

    fun getTargets(): ReliabilityTargets = ReliabilityTargets()

    fun checkTargets(actual: ReliabilityTargets): List<String> {
        val targets = getTargets()
        val warnings = mutableListOf<String>()

        if (actual.gatewayAvailability < targets.gatewayAvailability) {
            warnings.add("Gateway availability ${actual.gatewayAvailability}% below target ${targets.gatewayAvailability}%")
        }
        if (actual.successRate < targets.successRate) {
            warnings.add("Success rate ${actual.successRate}% below target ${targets.successRate}%")
        }
        if (actual.fallbackRecoveryRate < targets.fallbackRecoveryRate) {
            warnings.add("Fallback recovery ${actual.fallbackRecoveryRate}% below target ${targets.fallbackRecoveryRate}%")
        }
        if (actual.p95RoutingLatencyMs > targets.p95RoutingLatencyMs) {
            warnings.add("P95 latency ${actual.p95RoutingLatencyMs}ms above target ${targets.p95RoutingLatencyMs}ms")
        }

        return warnings
    }
}
