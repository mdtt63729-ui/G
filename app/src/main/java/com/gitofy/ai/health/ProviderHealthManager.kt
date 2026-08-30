package com.gitofy.ai.health

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider Health Monitoring — PRD 2 Sections 8, 56, 71-72, 94.
 *
 * Provider Status states (PRD Section 8):
 * Not Configured, Validating, Connected, Invalid, Network Error, Rate Limited, Provider Error
 *
 * Health states (PRD Section 94):
 * HEALTHY, DEGRADED, RATE_LIMITED, INVALID_CREDENTIAL, UNAVAILABLE, NOT_CONFIGURED
 *
 * AI Degraded Mode (PRD Section 72): If one or more providers become unavailable after setup,
 * show "GITOFY AI — Degraded Mode — X of 6 providers available — Automatic fallback is active".
 * The app remains usable as long as at least one compatible provider exists for the requested task.
 *
 * No Silent Provider Switching (PRD Section 73): When fallback occurs, optionally show:
 * "Primary provider unavailable. Switched to Gemini." — configurable.
 */
@Singleton
class ProviderHealthManager @Inject constructor() {

    enum class HealthState {
        HEALTHY,
        DEGRADED,
        RATE_LIMITED,
        INVALID_CREDENTIAL,
        UNAVAILABLE,
        NOT_CONFIGURED
    }

    data class ProviderHealthInfo(
        val provider: com.gitofy.ai.credentials.AiProvider,
        val state: HealthState,
        val latencyMs: Long?,
        val lastChecked: Long,
        val lastError: String?,
        val consecutiveFailures: Int,
        val isAvailable: Boolean
    ) {
        val displayStatus: String get() = when (state) {
            HealthState.HEALTHY -> "Healthy"
            HealthState.DEGRADED -> "Degraded"
            HealthState.RATE_LIMITED -> "Rate Limited"
            HealthState.INVALID_CREDENTIAL -> "Invalid API key"
            HealthState.UNAVAILABLE -> "Unavailable"
            HealthState.NOT_CONFIGURED -> "Not configured"
        }

        val displayIcon: String get() = when (state) {
            HealthState.HEALTHY -> "✓"
            HealthState.DEGRADED -> "⚠"
            HealthState.RATE_LIMITED -> "⚠"
            HealthState.INVALID_CREDENTIAL -> "⚠"
            HealthState.UNAVAILABLE -> "✕"
            HealthState.NOT_CONFIGURED -> "○"
        }
    }

    data class DegradedModeStatus(
        val isDegraded: Boolean,
        val availableProviders: Int,
        val totalMandatory: Int,
        val unavailableProviders: List<com.gitofy.ai.credentials.AiProvider>,
        val message: String
    ) {
        companion object {
            val OK = DegradedModeStatus(false, 0, 0, emptyList(), "All providers healthy")
        }
    }

    private val healthMap = mutableMapOf<com.gitofy.ai.credentials.AiProvider, ProviderHealthInfo>()

    init {
        // Initialize all providers as NOT_CONFIGURED
        com.gitofy.ai.credentials.AiProvider.entries.forEach { provider ->
            healthMap[provider] = ProviderHealthInfo(
                provider = provider,
                state = HealthState.NOT_CONFIGURED,
                latencyMs = null,
                lastChecked = 0,
                lastError = null,
                consecutiveFailures = 0,
                isAvailable = false
            )
        }
    }

    fun getHealth(provider: com.gitofy.ai.credentials.AiProvider): ProviderHealthInfo {
        return healthMap[provider] ?: ProviderHealthInfo(
            provider, HealthState.NOT_CONFIGURED, null, 0, null, 0, false
        )
    }

    fun getAllHealth(): List<ProviderHealthInfo> {
        return com.gitofy.ai.credentials.AiProvider.entries.map { getHealth(it) }
    }

    fun markHealthy(provider: com.gitofy.ai.credentials.AiProvider, latencyMs: Long) {
        healthMap[provider] = ProviderHealthInfo(
            provider = provider, state = HealthState.HEALTHY,
            latencyMs = latencyMs, lastChecked = System.currentTimeMillis(),
            lastError = null, consecutiveFailures = 0, isAvailable = true
        )
    }

    fun markConfigured(provider: com.gitofy.ai.credentials.AiProvider) {
        healthMap[provider] = healthMap[provider]?.copy(
            state = HealthState.HEALTHY, isAvailable = true, lastChecked = System.currentTimeMillis()
        ) ?: ProviderHealthInfo(provider, HealthState.HEALTHY, null, System.currentTimeMillis(), null, 0, true)
    }

    fun markDegraded(provider: com.gitofy.ai.credentials.AiProvider, error: String? = null) {
        val current = healthMap[provider] ?: return
        val newFailures = current.consecutiveFailures + 1
        healthMap[provider] = current.copy(
            state = HealthState.DEGRADED,
            lastChecked = System.currentTimeMillis(),
            lastError = error,
            consecutiveFailures = newFailures,
            isAvailable = newFailures < 5 // After 5 consecutive failures, mark unavailable
        )
    }

    fun markRateLimited(provider: com.gitofy.ai.credentials.AiProvider) {
        val current = healthMap[provider] ?: return
        healthMap[provider] = current.copy(
            state = HealthState.RATE_LIMITED,
            lastChecked = System.currentTimeMillis(),
            isAvailable = false
        )
    }

    fun markInvalidCredential(provider: com.gitofy.ai.credentials.AiProvider) {
        val current = healthMap[provider] ?: return
        healthMap[provider] = current.copy(
            state = HealthState.INVALID_CREDENTIAL,
            lastChecked = System.currentTimeMillis(),
            lastError = "Invalid API key",
            isAvailable = false
        )
    }

    fun markUnavailable(provider: com.gitofy.ai.credentials.AiProvider, error: String? = null) {
        val current = healthMap[provider] ?: return
        healthMap[provider] = current.copy(
            state = HealthState.UNAVAILABLE,
            lastChecked = System.currentTimeMillis(),
            lastError = error,
            isAvailable = false
        )
    }

    fun markNotConfigured(provider: com.gitofy.ai.credentials.AiProvider) {
        healthMap[provider] = ProviderHealthInfo(
            provider = provider, state = HealthState.NOT_CONFIGURED,
            latencyMs = null, lastChecked = System.currentTimeMillis(),
            lastError = null, consecutiveFailures = 0, isAvailable = false
        )
    }

    /**
     * Get degraded mode status — PRD Section 72.
     */
    fun getDegradedModeStatus(): DegradedModeStatus {
        val all = getAllHealth()
        val mandatory = com.gitofy.ai.credentials.AiProvider.mandatory
        val mandatoryHealth = mandatory.map { getHealth(it) }
        val available = mandatoryHealth.count { it.isAvailable }
        val unavailable = mandatory.filter { !getHealth(it).isAvailable }

        return if (available < mandatory.size) {
            DegradedModeStatus(
                isDegraded = true,
                availableProviders = available,
                totalMandatory = mandatory.size,
                unavailableProviders = unavailable,
                message = "Degraded Mode — $available of ${mandatory.size} providers available. Automatic fallback is active."
            )
        } else DegradedModeStatus.OK
    }

    /**
     * Test all providers — PRD Section 56 (API Health Dashboard: Test All).
     * Health checks must not run excessively.
     */
    fun shouldRunHealthCheck(provider: com.gitofy.ai.credentials.AiProvider, minIntervalMs: Long = 5 * 60 * 1000): Boolean {
        val health = getHealth(provider)
        if (health.lastChecked == 0L) return true
        return System.currentTimeMillis() - health.lastChecked > minIntervalMs
    }
}

/**
 * AI Provider Fallback Notification — PRD Section 73.
 * No Silent Provider Switching: When fallback occurs, optionally show notification.
 */
@Singleton
class FallbackNotifier @Inject constructor() {

    data class FallbackNotification(
        val fromProvider: String,
        val toProvider: String,
        val reason: String,
        val timestamp: Long,
        val isUserVisible: Boolean
    )

    private val notifications = mutableListOf<FallbackNotification>()
    var showFallbackNotifications: Boolean = true

    fun notifyFallback(from: String, to: String, reason: String) {
        val notification = FallbackNotification(from, to, reason, System.currentTimeMillis(), showFallbackNotifications)
        notifications.add(notification)
    }

    fun getRecentNotifications(count: Int = 10): List<FallbackNotification> {
        return notifications.takeLast(count)
    }

    fun clearNotifications() { notifications.clear() }
}
