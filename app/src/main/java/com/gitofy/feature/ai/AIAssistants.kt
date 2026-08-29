package com.gitofy.feature.ai

import com.gitofy.core.config.FeatureFlags
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Cost & Resource Controls — PRD v5.0 Section 60.
 * Settings: AI Enabled, AI Analysis, AI Log Analysis, AI PR Assistant, AI Code Assistant.
 * Users must understand when an operation may require external AI processing.
 */
@Singleton
class AICostController @Inject constructor(
    private val featureFlags: FeatureFlags
) {

    data class AICostSettings(
        val aiEnabled: Boolean,
        val aiAnalysis: Boolean,
        val aiLogAnalysis: Boolean,
        val aiPRAssistant: Boolean,
        val aiCodeAssistant: Boolean,
        val aiPrivacyMode: Boolean,
        val requiresExternalProcessing: Boolean
    )

    fun getSettings(): AICostSettings {
        return AICostSettings(
            aiEnabled = true,
            aiAnalysis = featureFlags.isEnabled(FeatureFlags.Flag.ADVANCED_LOGS),
            aiLogAnalysis = featureFlags.isEnabled(FeatureFlags.Flag.ADVANCED_LOGS),
            aiPRAssistant = true,
            aiCodeAssistant = true,
            aiPrivacyMode = true,
            requiresExternalProcessing = false // Simulated — would be true if calling external AI API
        )
    }

    fun isOperationAllowed(operation: String): Boolean {
        val settings = getSettings()
        if (!settings.aiEnabled) return false
        return when (operation) {
            "BUILD_ANALYSIS", "LOG_SUMMARY" -> settings.aiLogAnalysis
            "PR_SUMMARY", "PR_DESCRIPTION" -> settings.aiPRAssistant
            "CODE_EXPLANATION", "CODE_REVIEW" -> settings.aiCodeAssistant
            "COMMIT_MESSAGE" -> settings.aiPRAssistant
            else -> settings.aiAnalysis
        }
    }
}

/**
 * AI Privacy Mode — PRD v5.0 Section 61.
 * Provide private analysis where supported. Sensitive content must not be transmitted
 * unless necessary and authorized.
 */
@Singleton
class AIPrivacyMode @Inject constructor() {

    data class PrivacyConfig(
        val privacyModeEnabled: Boolean,
        val dataCategoriesAllowed: Set<String>,
        val requiresUserConsent: Boolean
    )

    fun getConfig(): PrivacyConfig {
        return PrivacyConfig(
            privacyModeEnabled = true,
            dataCategoriesAllowed = setOf("METADATA", "WORKFLOW_NAMES", "ERROR_MESSAGES"),
            requiresUserConsent = true
        )
    }

    fun shouldTransmit(dataCategory: String): Boolean {
        val config = getConfig()
        if (!config.privacyModeEnabled) return true
        return dataCategory in config.dataCategoriesAllowed
    }
}

/**
 * AI Code Review Assistant — PRD v5.0 Section 53.
 * Identifies: potential bugs, nullability risks, concurrency issues, security concerns,
 * performance concerns, maintainability concerns. Output must be labeled advisory.
 */
@Singleton
class AICodeReviewAssistant @Inject constructor() {

    data class CodeReview(
        val issues: List<CodeIssue>,
        val advisory: String
    )

    data class CodeIssue(
        val type: IssueType,
        val description: String,
        val severity: Severity,
        val fileLine: String?
    )

    enum class IssueType { BUG, NULLABILITY, CONCURRENCY, SECURITY, PERFORMANCE, MAINTAINABILITY }
    enum class Severity { CRITICAL, HIGH, MEDIUM, LOW }

    fun review(code: String, fileName: String): CodeReview {
        val issues = mutableListOf<CodeIssue>()

        // Basic pattern-based review (deterministic)
        if (code.contains("!!") && fileName.endsWith(".kt")) {
            issues.add(CodeIssue(IssueType.NULLABILITY, "Non-null assertion (!!) may cause NullPointerException", Severity.MEDIUM, null))
        }
        if (code.contains("runBlocking") && fileName.endsWith(".kt")) {
            issues.add(CodeIssue(IssueType.CONCURRENCY, "runBlocking can cause UI freezes", Severity.HIGH, null))
        }
        if (code.contains("http://") && !code.contains("localhost")) {
            issues.add(CodeIssue(IssueType.SECURITY, "HTTP used instead of HTTPS", Severity.HIGH, null))
        }
        if (code.contains("printStackTrace()")) {
            issues.add(CodeIssue(IssueType.PERFORMANCE, "printStackTrace() should be replaced with proper logging", Severity.LOW, null))
        }

        return CodeReview(
            issues = issues,
            advisory = "This review is advisory and based on pattern matching. It is not a substitute for human review."
        )
    }
}

/**
 * AI Code Explanation — PRD v5.0 Section 52.
 * Explains selected code: Purpose, Inputs, Outputs, Dependencies, Side effects, Potential issues.
 */
@Singleton
class AICodeExplainer @Inject constructor() {

    data class CodeExplanation(
        val purpose: String,
        val inputs: List<String>,
        val outputs: List<String>,
        val dependencies: List<String>,
        val sideEffects: List<String>,
        val potentialIssues: List<String>
    )

    fun explain(code: String, fileName: String): CodeExplanation {
        val inputs = mutableListOf<String>()
        val outputs = mutableListOf<String>()
        val deps = mutableListOf<String>()
        val sideEffects = mutableListOf<String>()

        if (fileName.endsWith(".kt")) {
            // Extract function parameters
            val funcPattern = Regex("fun\\s+\\w+\\s*\\(([^)]*)\\)")
            funcPattern.findAll(code).forEach { match ->
                inputs.addAll(match.groupValues[1].split(",").map { it.trim() }.filter { it.isNotEmpty() })
            }
            // Extract return types
            Regex("fun\\s+\\w+\\s*\\([^)]*\\)\\s*:\\s*(\\w+)").findAll(code).forEach {
                outputs.add(it.groupValues[1])
            }
            // Detect imports
            Regex("import\\s+([\\w.]+)").findAll(code).forEach {
                deps.add(it.groupValues[1])
            }
            // Detect side effects
            if (code.contains("println") || code.contains("Log.")) sideEffects.add("Logging")
            if (code.contains("File(") || code.contains("write") || code.contains("save")) sideEffects.add("File I/O")
            if (code.contains("api.") || code.contains("retrofit")) sideEffects.add("Network I/O")
        }

        return CodeExplanation(
            purpose = "This code is defined in $fileName.",
            inputs = inputs,
            outputs = outputs,
            dependencies = deps,
            sideEffects = sideEffects,
            potentialIssues = emptyList()
        )
    }
}
