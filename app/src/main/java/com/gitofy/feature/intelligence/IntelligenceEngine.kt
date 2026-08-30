package com.gitofy.feature.intelligence

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Developer Intelligence Dashboard — PRD v7.0 Section 109.
 * Answers: What needs my attention? Why does it matter? What changed?
 * What failed? What should I do next?
 */
@Singleton
class IntelligenceDashboard @Inject constructor() {

    data class DashboardData(
        val attentionItems: List<com.gitofy.domain.model.AttentionItem>,
        val projectHealth: List<ProjectHealthSummary>,
        val activeCI: List<ActiveCIBuild>,
        val releaseStatus: ReleaseStatus,
        val recommendations: List<com.gitofy.domain.model.DeveloperRecommendation>
    )

    data class ProjectHealthSummary(
        val repoName: String,
        val ciStatus: HealthIcon,
        val prCount: Int,
        val issueCount: Int,
        val recentActivity: Boolean
    )

    enum class HealthIcon { HEALTHY, NEEDS_ATTENTION, CRITICAL, UNKNOWN }

    data class ActiveCIBuild(
        val repoName: String,
        val runName: String,
        val status: BuildStatus
    )

    enum class BuildStatus { RUNNING, QUEUED, SUCCESS, FAILURE }

    data class ReleaseStatus(
        val tagName: String?,
        val readiness: com.gitofy.domain.model.ReleaseReadiness?
    )
}

/**
 * Attention Center — PRD v7.0 Section 110.
 * Unified priority feed: Build failed, PR waiting, Deployment failed, Issue assigned, Release completed.
 * Priority is determined by configurable rules.
 */
@Singleton
class AttentionCenter @Inject constructor() {

    data class AttentionRule(
        val type: String,
        val priority: com.gitofy.domain.model.AttentionPriority,
        val isEnabled: Boolean
    )

    private val defaultRules = listOf(
        AttentionRule("BUILD_FAILED", com.gitofy.domain.model.AttentionPriority.CRITICAL, true),
        AttentionRule("DEPLOYMENT_FAILED", com.gitofy.domain.model.AttentionPriority.CRITICAL, true),
        AttentionRule("PR_REVIEW_REQUEST", com.gitofy.domain.model.AttentionPriority.HIGH, true),
        AttentionRule("ISSUE_ASSIGNED", com.gitofy.domain.model.AttentionPriority.MEDIUM, true),
        AttentionRule("RELEASE_COMPLETED", com.gitofy.domain.model.AttentionPriority.LOW, true),
        AttentionRule("WORKFLOW_SUCCESS", com.gitofy.domain.model.AttentionPriority.LOW, false)
    )

    fun getRules(): List<AttentionRule> = defaultRules

    fun getEnabledRules(): List<AttentionRule> = defaultRules.filter { it.isEnabled }
}

/**
 * AI Root-Cause Engine — PRD v7.0 Section 115.
 * Architecture: Failure → Collect Evidence → Normalize → Pattern Analysis →
 * Historical Comparison → AI Reasoning → Root Cause Candidate → Confidence → Suggested Fix
 */
@Singleton
class AIRootCauseEngine @Inject constructor(
    private val failureInspector: com.gitofy.feature.ci.CIFailureInspector
) {

    data class RootCauseResult(
        val observed: String,
        val likelyCause: String,
        val confidence: com.gitofy.domain.model.AIConfidence,
        val evidence: String,
        val suggestedAction: String,
        val historicalContext: String?
    )

    fun analyze(
        logs: String,
        historicalFailures: List<String> = emptyList()
    ): RootCauseResult {
        val deterministic = failureInspector.analyze(logs)

        // Historical comparison
        val historicalMatch = historicalFailures.find { historicalFailureInspector ->
            deterministic.pattern in historicalFailureInspector
        }

        val confidence = when {
            historicalMatch != null -> com.gitofy.domain.model.AIConfidence.HIGH
            deterministic.category != com.gitofy.feature.ci.CIFailureInspector.FailureCategory.UNKNOWN ->
                com.gitofy.domain.model.AIConfidence.MEDIUM
            else -> com.gitofy.domain.model.AIConfidence.LOW
        }

        return RootCauseResult(
            observed = deterministic.category.name.lowercase().replace("_", " ").replaceFirstChar { it.uppercase() },
            likelyCause = deterministic.pattern,
            confidence = confidence,
            evidence = deterministic.relevantLog,
            suggestedAction = deterministic.suggestedAction,
            historicalContext = historicalMatch?.let { "Similar failure found in historical data: ${it.take(100)}" }
        )
    }
}

/**
 * Historical Failure Correlation — PRD v7.0 Section 116.
 * Compare current failure with previous runs.
 * Never claim two failures are identical solely from superficial textual similarity.
 */
@Singleton
class HistoricalFailureCorrelation @Inject constructor() {

    data class CorrelationResult(
        val isMatch: Boolean,
        val confidence: Float,
        val previousFix: String?,
        val warning: String?
    )

    fun correlate(
        currentFailure: String,
        historicalFailures: List<Pair<String, String>> // (failureLog, fixApplied)
    ): CorrelationResult {
        var bestMatch: Pair<String, String>? = null
        var bestScore = 0f

        for ((failure, fix) in historicalFailures) {
            // Use Jaccard similarity on word sets, not just text contains
            val currentWords = currentFailure.lowercase().split(Regex("\\s+")).toSet()
            val historicalWords = failure.lowercase().split(Regex("\\s+")).toSet()
            val intersection = currentWords.intersect(historicalWords).size
            val union = currentWords.union(historicalWords).size
            val score = if (union > 0) intersection.toFloat() / union else 0f

            if (score > bestScore) {
                bestScore = score
                bestMatch = failure to fix
            }
        }

        val isMatch = bestScore > 0.6f
        return CorrelationResult(
            isMatch = isMatch,
            confidence = bestScore,
            previousFix = bestMatch?.second,
            warning = if (isMatch && bestScore < 0.8f)
                "Correlation is based on textual similarity. Failures may have different underlying causes."
            else null
        )
    }
}

/**
 * Smart PR Intelligence — PRD v7.0 Section 117.
 * For each PR: Change Size, Risk, CI Status, Review Status, Files Affected, Dependencies, Potential Impact.
 * Risk scoring must be transparent and heuristic.
 */
@Singleton
class SmartPRIntelligence @Inject constructor() {

    data class PRIntelligence(
        val changeSize: ChangeSize,
        val riskLevel: RiskLevel,
        val riskReasons: List<String>,
        val ciStatus: String?,
        val reviewStatus: String?,
        val filesAffected: Int,
        val dependencyChanges: List<String>,
        val potentialImpact: String
    )

    enum class ChangeSize { SMALL, MEDIUM, LARGE, VERY_LARGE }
    enum class RiskLevel { LOW, MEDIUM, HIGH, CRITICAL }

    fun analyze(
        changedFiles: List<String>,
        additions: Int,
        deletions: Int,
        ciStatus: String?,
        reviewStatus: String?
    ): PRIntelligence {
        val totalChanges = additions + deletions
        val changeSize = when {
            totalChanges < 50 -> ChangeSize.SMALL
            totalChanges < 300 -> ChangeSize.MEDIUM
            totalChanges < 1000 -> ChangeSize.LARGE
            else -> ChangeSize.VERY_LARGE
        }

        val riskReasons = mutableListOf<String>()

        // Check for security-sensitive files
        if (changedFiles.any { it.contains("security", ignoreCase = true) || it.contains("auth", ignoreCase = true) }) {
            riskReasons.add("Security-sensitive files modified")
        }
        // Check for build config changes
        if (changedFiles.any { it.contains("build.gradle") || it.contains("AndroidManifest") }) {
            riskReasons.add("Build configuration changed")
        }
        // Check for dependency changes
        if (changedFiles.any { it.contains("build.gradle") || it.contains("build.gradle.kts") }) {
            riskReasons.add("Potential dependency changes")
        }
        // Check for core module changes
        if (changedFiles.any { it.contains("/core/", ignoreCase = true) || it.contains("/domain/", ignoreCase = true) }) {
            riskReasons.add("Core module modifications")
        }

        val riskLevel = when {
            riskReasons.size >= 3 -> RiskLevel.CRITICAL
            riskReasons.size >= 2 -> RiskLevel.HIGH
            riskReasons.size >= 1 -> RiskLevel.MEDIUM
            else -> RiskLevel.LOW
        }

        return PRIntelligence(
            changeSize = changeSize,
            riskLevel = riskLevel,
            riskReasons = riskReasons,
            ciStatus = ciStatus,
            reviewStatus = reviewStatus,
            filesAffected = changedFiles.size,
            dependencyChanges = emptyList(),
            potentialImpact = "This PR affects ${changedFiles.size} files with ${additions}+ ${deletions}- changes."
        )
    }
}

/**
 * Global Operation Engine — PRD v7.0 Section 133.
 * Unify background operations under OperationManager.
 * Operation types: ZIP_IMPORT, GIT_PUSH, WORKFLOW_SYNC, ARTIFACT_DOWNLOAD, RELEASE_UPLOAD, REPOSITORY_SYNC, AI_ANALYSIS
 */
@Singleton
class GlobalOperationEngine @Inject constructor() {

    data class GlobalOperation(
        val id: String,
        val type: OperationType,
        val account: String,
        val repository: String?,
        val state: OperationState,
        val progress: Float,
        val startedAt: Long,
        val completedAt: Long?,
        val error: String?,
        val recoveryAction: String?
    )

    enum class OperationType {
        ZIP_IMPORT, GIT_PUSH, WORKFLOW_SYNC, ARTIFACT_DOWNLOAD,
        RELEASE_UPLOAD, REPOSITORY_SYNC, AI_ANALYSIS
    }

    enum class OperationState { QUEUED, RUNNING, COMPLETED, FAILED, CANCELLED, RECOVERING }

    private val operations = mutableMapOf<String, GlobalOperation>()

    fun registerOperation(op: GlobalOperation) { operations[op.id] = op }
    fun getOperation(id: String): GlobalOperation? = operations[id]
    fun getActiveOperations(): List<GlobalOperation> = operations.values.filter { it.state == OperationState.RUNNING || it.state == OperationState.QUEUED }.toList()
    fun getAllOperations(): List<GlobalOperation> = operations.values.sortedByDescending { it.startedAt }.toList()
}

/**
 * Smart Notifications — PRD v7.0 Section 123.
 * Instead of notifying every event, prioritize: Critical, High, Medium, Low.
 * Users can configure thresholds.
 */
@Singleton
class SmartNotificationManager @Inject constructor() {

    data class NotificationThreshold(
        val criticalEnabled: Boolean = true,
        val highEnabled: Boolean = true,
        val mediumEnabled: Boolean = true,
        val lowEnabled: Boolean = false
    )

    private var threshold = NotificationThreshold()

    fun shouldNotify(priority: com.gitofy.domain.model.AttentionPriority): Boolean {
        return when (priority) {
            com.gitofy.domain.model.AttentionPriority.CRITICAL -> threshold.criticalEnabled
            com.gitofy.domain.model.AttentionPriority.HIGH -> threshold.highEnabled
            com.gitofy.domain.model.AttentionPriority.MEDIUM -> threshold.mediumEnabled
            com.gitofy.domain.model.AttentionPriority.LOW -> threshold.lowEnabled
        }
    }

    fun updateThreshold(newThreshold: NotificationThreshold) {
        threshold = newThreshold
    }
}

/**
 * Cross-Repository Intelligence — PRD v7.0 Section 124.
 * For selected repositories: CI Health, Failure Trends, Release Trends, Dependency Risks.
 */
@Singleton
class CrossRepositoryIntelligence @Inject constructor() {

    data class CrossRepoSummary(
        val repoName: String,
        val ciHealth: Float,
        val failureRate: Float,
        val releaseRecency: Int, // days since last release
        val dependencyRiskCount: Int
    )

    fun analyze(repos: List<CrossRepoSummary>): List<CrossRepoSummary> {
        return repos.sortedByDescending { it.failureRate }
    }
}

/**
 * Verification-First Operations — PRD v7.0 Section 131.
 * After critical operations, verify remote state before marking success.
 */
@Singleton
class VerificationFirstOps @Inject constructor() {

    enum class VerificationType {
        REPOSITORY_CREATED, PUSH_COMPLETE, WORKFLOW_TRIGGERED,
        ARTIFACT_DOWNLOADED, RELEASE_PUBLISHED, DEPLOYMENT_STATE
    }

    data class VerificationResult(
        val type: VerificationType,
        val isVerified: Boolean,
        val message: String
    )

    fun verify(type: VerificationType, remoteCheck: () -> Boolean): VerificationResult {
        val isVerified = remoteCheck()
        return VerificationResult(
            type = type,
            isVerified = isVerified,
            message = if (isVerified) "${type.name} verified successfully"
                      else "Verification failed for ${type.name} — remote state does not match"
        )
    }
}

/**
 * Intelligent Recovery — PRD v7.0 Section 132.
 * Upload failed → Determine cause → Select recovery action.
 */
@Singleton
class IntelligentRecovery @Inject constructor() {

    data class RecoverySuggestion(
        val cause: String,
        val canResume: Boolean,
        val actionDescription: String,
        val safeToRetry: Boolean
    )

    fun suggestRecovery(error: String, operationType: String): RecoverySuggestion {
        return when {
            error.contains("network", ignoreCase = true) || error.contains("timeout", ignoreCase = true) ->
                RecoverySuggestion("Network", true, "Network was interrupted. Your operation can safely continue from where it left off.", true)
            error.contains("401", ignoreCase = true) || error.contains("auth", ignoreCase = true) ->
                RecoverySuggestion("Authentication", false, "Your GitHub authorization has expired. Please sign in again.", false)
            error.contains("403", ignoreCase = true) || error.contains("permission", ignoreCase = true) ->
                RecoverySuggestion("Permission", false, "Additional GitHub permissions are required for this operation.", false)
            error.contains("conflict", ignoreCase = true) || error.contains("409", ignoreCase = true) ->
                RecoverySuggestion("Remote Conflict", false, "The remote repository has changed. Pull latest changes before retrying.", false)
            error.contains("storage", ignoreCase = true) || error.contains("space", ignoreCase = true) ->
                RecoverySuggestion("Storage", false, "Device storage is full. Free up space and retry.", false)
            else -> RecoverySuggestion("Unknown", false, "An unexpected error occurred. Review the operation logs.", false)
        }
    }
}
