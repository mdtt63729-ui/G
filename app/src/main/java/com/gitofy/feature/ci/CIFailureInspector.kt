package com.gitofy.feature.ci

import javax.inject.Inject
import javax.inject.Singleton

/**
 * CI Failure Inspector — PRD v4.5 Section 36-37.
 * Deterministic pattern matching for common build failures.
 * Uses deterministic pattern matching before AI is introduced (v5.0).
 */
@Singleton
class CIFailureInspector @Inject constructor() {

    data class FailureAnalysis(
        val category: FailureCategory,
        val pattern: String,
        val relevantLog: String,
        val suggestedAction: String
    )

    enum class FailureCategory {
        COMPILATION, DEPENDENCY, GRADLE, KOTLIN, JAVA, ANDROID_SDK,
        LINT, TEST, NETWORK, PERMISSION, SIGNING, PACKAGING, RESOURCE, CONFIGURATION, UNKNOWN
    }

    private val patterns = listOf(
        FailureCategory.COMPILATION to Regex("(?i)(compilation error|e: .*\\.kt:|error:.*cannot find symbol)") to "Check for syntax errors or missing imports in the affected file",
        FailureCategory.KOTLIN to Regex("(?i)(kt\\d+|kotlin compiler|unresolved reference)") to "Verify Kotlin version compatibility and import statements",
        FailureCategory.GRADLE to Regex("(?i)(gradle build failed|could not resolve|dependency resolution failed)") to "Check Gradle version and dependency configuration",
        FailureCategory.DEPENDENCY to Regex("(?i)(could not find|dependency.*not found|version.*conflict)") to "Verify dependency coordinates and versions in build.gradle",
        FailureCategory.ANDROID_SDK to Regex("(?i)(android sdk|compileSdk|minSdk|targetSdk)") to "Check Android SDK installation and build.gradle SDK versions",
        FailureCategory.LINT to Regex("(?i)(lint.*failed|lint.*error|lintVital)") to "Review lint warnings and fix or suppress as needed",
        FailureCategory.TEST to Regex("(?i)(test.*failed|tests.*failed|assertion.*failed|junit)") to "Review failed test assertions and fix the test or the code",
        FailureCategory.SIGNING to Regex("(?i)(signing|keystore|signature)") to "Check signing configuration and keystore credentials",
        FailureCategory.PACKAGING to Regex("(?i)(packaging|duplicate.*class|merge.*failed)") to "Check for duplicate dependencies or packaging conflicts",
        FailureCategory.NETWORK to Regex("(?i)(network|timeout|connection.*refused|unreachable)") to "Check network connectivity and retry",
        FailureCategory.PERMISSION to Regex("(?i)(permission.*denied|403|forbidden|unauthorized)") to "Verify GitHub token permissions",
        FailureCategory.RESOURCE to Regex("(?i)(resource.*not found|aapt|resource.*linking)") to "Check resource files for missing or invalid references",
        FailureCategory.CONFIGURATION to Regex("(?i)(configuration|invalid.*yaml|malformed|parse.*error)") to "Review configuration file syntax"
    )

    fun analyze(logs: String): FailureAnalysis {
        for ((categoryWithPattern, suggestion) in patterns) {
            val (category, pattern) = categoryWithPattern
            val match = pattern.find(logs)
            if (match != null) {
                val relevantLine = logs.lines().find { pattern.containsMatchIn(it) } ?: match.value
                return FailureAnalysis(
                    category = category,
                    pattern = match.value,
                    relevantLog = relevantLine.take(200),
                    suggestedAction = suggestion
                )
            }
        }
        return FailureAnalysis(
            category = FailureCategory.UNKNOWN,
            pattern = "Unknown failure pattern",
            relevantLog = logs.take(200),
            suggestedAction = "Review the full logs for more details"
        )
    }
}
