package com.gitofy.feature.ai

import com.gitofy.domain.model.AIAnalysis
import com.gitofy.domain.model.AIAnalysisType
import com.gitofy.domain.model.AIConfidence
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Build Failure Analysis — PRD v5.0 Section 47.
 * Analyzes build failures and generates structured analysis:
 * Root Cause, Affected files, Likely Cause, Evidence, Recommended Fix, Confidence.
 * Must distinguish: Observed, Inferred, Suggested.
 */
@Singleton
class AIBuildFailureAnalyzer @Inject constructor(
    private val failureInspector: com.gitofy.feature.ci.CIFailureInspector,
    private val contextEngine: AIContextEngine
) {

    suspend fun analyze(
        logs: String,
        repoName: String,
        workflowName: String
    ): AIAnalysis {
        // Step 1: Deterministic pattern matching
        val deterministic = failureInspector.analyze(logs)

        // Step 2: Build AI context (with secret exclusion)
        val context = contextEngine.buildContext(
            AIContextEngine.AIContextConfig(
                repositoryScope = repoName,
                includeCode = false,
                includeDiffs = false,
                includeWorkflowLogs = true,
                includeIssues = false,
                includePRs = false,
                includeCommits = false,
                includeReleases = false,
                privacyMode = true
            ),
            mapOf("workflow_name" to workflowName, "logs" to logs)
        )

        // Step 3: Generate analysis (simulated — would call AI API in production)
        val rootCause = when (deterministic.category) {
            com.gitofy.feature.ci.CIFailureInspector.FailureCategory.COMPILATION -> "Compilation error in source code"
            com.gitofy.feature.ci.CIFailureInspector.FailureCategory.KOTLIN -> "Kotlin compiler error"
            com.gitofy.feature.ci.CIFailureInspector.FailureCategory.GRADLE -> "Gradle build configuration issue"
            com.gitofy.feature.ci.CIFailureInspector.FailureCategory.DEPENDENCY -> "Dependency resolution failure"
            com.gitofy.feature.ci.CIFailureInspector.FailureCategory.TEST -> "Test failure"
            com.gitofy.feature.ci.CIFailureInspector.FailureCategory.SIGNING -> "Signing configuration issue"
            com.gitofy.feature.ci.CIFailureInspector.FailureCategory.UNKNOWN -> "Unknown failure"
            else -> deterministic.category.name.lowercase().replace("_", " ")
        }

        val confidence = when (deterministic.category) {
            com.gitofy.feature.ci.CIFailureInspector.FailureCategory.UNKNOWN -> AIConfidence.LOW
            com.gitofy.feature.ci.CIFailureInspector.FailureCategory.COMPILATION,
            com.gitofy.feature.ci.CIFailureInspector.FailureCategory.KOTLIN,
            com.gitofy.feature.ci.CIFailureInspector.FailureCategory.TEST -> AIConfidence.HIGH
            else -> AIConfidence.MEDIUM
        }

        return AIAnalysis(
            id = java.util.UUID.randomUUID().toString(),
            type = AIAnalysisType.BUILD_FAILURE,
            rootCause = rootCause,
            evidence = deterministic.relevantLog,
            confidence = confidence,
            recommendedAction = deterministic.suggestedAction,
            isObserved = true,
            isInferred = confidence != AIConfidence.HIGH,
            isSuggested = true
        )
    }
}

/**
 * AI Log Summarization — PRD v5.0 Section 48.
 * For long logs: extract relevant sections, generate summary, identify failure and likely root cause.
 * Must not upload sensitive logs to a third-party model without explicit user consent.
 */
@Singleton
class AILogSummarizer @Inject constructor(
    private val logIntelligence: com.gitofy.feature.ci.LogIntelligence
) {

    data class LogSummary(
        val summary: String,
        val errorCount: Int,
        val warningCount: Int,
        val firstFailureLine: Int?,
        val likelyRootCause: String?,
        val relevantSections: List<String>
    )

    fun summarize(rawLogs: String): LogSummary {
        val analysis = logIntelligence.analyze(rawLogs)

        val summary = buildString {
            append("Log contains ${analysis.errorCount} errors and ${analysis.warningCount} warnings. ")
            if (analysis.gradleErrors.isNotEmpty()) {
                append("Gradle errors detected: ${analysis.gradleErrors.first().take(100)}. ")
            }
            if (analysis.kotlinErrors.isNotEmpty()) {
                append("Kotlin errors detected: ${analysis.kotlinErrors.first().take(100)}. ")
            }
            if (analysis.firstFailureLine != null) {
                append("First failure at line ${analysis.firstFailureLine}.")
            }
        }

        return LogSummary(
            summary = summary,
            errorCount = analysis.errorCount,
            warningCount = analysis.warningCount,
            firstFailureLine = analysis.firstFailureLine,
            likelyRootCause = analysis.gradleErrors.firstOrNull() ?: analysis.kotlinErrors.firstOrNull(),
            relevantSections = (analysis.gradleErrors + analysis.kotlinErrors).distinct().take(5)
        )
    }
}

/**
 * AI Commit Message Generator — PRD v5.0 Section 50.
 * Generates commit message from actual changes. User must approve before commit.
 */
@Singleton
class AICommitMessageGenerator @Inject constructor() {

    fun generate(addedFiles: List<String>, modifiedFiles: List<String>, deletedFiles: List<String>): String {
        val parts = mutableListOf<String>()
        if (addedFiles.isNotEmpty()) parts.add("add ${addedFiles.size} file(s)")
        if (modifiedFiles.isNotEmpty()) parts.add("update ${modifiedFiles.size} file(s)")
        if (deletedFiles.isNotEmpty()) parts.add("remove ${deletedFiles.size} file(s)")

        val action = when {
            addedFiles.isNotEmpty() && modifiedFiles.isEmpty() && deletedFiles.isEmpty() -> "feat: add ${addedFiles.first().substringAfterLast('/')}"
            modifiedFiles.isNotEmpty() && addedFiles.isEmpty() && deletedFiles.isEmpty() -> "fix: update ${modifiedFiles.first().substringAfterLast('/')}"
            deletedFiles.isNotEmpty() && addedFiles.isEmpty() && modifiedFiles.isEmpty() -> "chore: remove ${deletedFiles.first().substringAfterLast('/')}"
            else -> "chore: ${parts.joinToString(", ")}"
        }

        return action
    }
}

/**
 * AI PR Summary — PRD v5.0 Section 49.
 * Generate: Summary, Changed areas, Risk areas, Testing status, Potential regressions, Suggested review focus.
 */
@Singleton
class AIPRSummaryGenerator @Inject constructor() {

    data class PRSummary(
        val summary: String,
        val changedAreas: List<String>,
        val riskAreas: List<String>,
        val testingStatus: String,
        val potentialRegressions: List<String>,
        val suggestedReviewFocus: String
    )

    fun generate(
        changedFiles: List<String>,
        additions: Int,
        deletions: Int,
        ciStatus: String?
    ): PRSummary {
        val changedAreas = changedFiles.map { it.substringBeforeLast("/").takeLast(30) }.distinct()
        val riskAreas = changedFiles.filter { file ->
            file.contains("build.gradle") || file.contains("AndroidManifest") ||
            file.contains("security/", ignoreCase = true) || file.contains("auth", ignoreCase = true)
        }

        return PRSummary(
            summary = "This PR changes ${changedFiles.size} files (+$additions -$deletions).",
            changedAreas = changedAreas,
            riskAreas = riskAreas,
            testingStatus = ciStatus ?: "Unknown",
            potentialRegressions = riskAreas,
            suggestedReviewFocus = if (riskAreas.isNotEmpty())
                "Focus review on: ${riskAreas.joinToString { it.substringAfterLast("/") }}"
            else "No high-risk files detected."
        )
    }
}
