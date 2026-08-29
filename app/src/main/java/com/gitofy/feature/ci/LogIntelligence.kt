package com.gitofy.feature.ci

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Log Intelligence — PRD v4.5 Section 38.
 * Provides search, error-only mode, warning-only mode, failure jump,
 * stack trace detection, Gradle/Kotlin error grouping.
 */
@Singleton
class LogIntelligence @Inject constructor() {

    data class LogEntry(
        val lineNumber: Int,
        val content: String,
        val level: LogLevel,
        val isStackTrace: Boolean
    )

    enum class LogLevel { ERROR, WARNING, INFO, DEBUG, UNKNOWN }

    data class LogAnalysis(
        val entries: List<LogEntry>,
        val errorCount: Int,
        val warningCount: Int,
        val stackTraces: List<String>,
        val gradleErrors: List<String>,
        val kotlinErrors: List<String>,
        val firstFailureLine: Int?
    )

    private val errorPattern = Regex("(?i)^.*(error|failed|failure|exception|fatal).*\$", RegexOption.MULTILINE)
    private val warningPattern = Regex("(?i)^.*(warning|warn|deprecated).*\$", RegexOption.MULTILINE)
    private val stackTracePattern = Regex("(?i)(at\\s+[\\w.$]+\\([^)]*\\)|Caused by:|Suppressed:)")
    private val gradleErrorPattern = Regex("(?i)(BUILD FAILED|Could not|Task .* failed|Execution failed)")
    private val kotlinErrorPattern = Regex("(?i)(e: .*\\.kt:|kt\\d+|Unresolved reference)")
    private val failurePattern = Regex("(?i)(failure|failed|error|exception)")

    fun analyze(rawLogs: String): LogAnalysis {
        val lines = rawLogs.lines()
        val entries = lines.mapIndexed { index, line ->
            val level = when {
                errorPattern.containsMatchIn(line) -> LogLevel.ERROR
                warningPattern.containsMatchIn(line) -> LogLevel.WARNING
                else -> LogLevel.UNKNOWN
            }
            val isStackTrace = stackTracePattern.containsMatchIn(line)
            LogEntry(index + 1, line, level, isStackTrace)
        }

        val errorCount = entries.count { it.level == LogLevel.ERROR }
        val warningCount = entries.count { it.level == LogLevel.WARNING }
        val stackTraces = lines.filter { stackTracePattern.containsMatchIn(it) }.take(20)
        val gradleErrors = lines.filter { gradleErrorPattern.containsMatchIn(it) }.take(20)
        val kotlinErrors = lines.filter { kotlinErrorPattern.containsMatchIn(it) }.take(20)
        val firstFailure = entries.indexOfFirst { failurePattern.containsMatchIn(it.content) }

        return LogAnalysis(entries, errorCount, warningCount, stackTraces, gradleErrors, kotlinErrors,
            if (firstFailure >= 0) firstFailure + 1 else null)
    }

    fun filterErrors(entries: List<LogEntry>): List<LogEntry> = entries.filter { it.level == LogLevel.ERROR }
    fun filterWarnings(entries: List<LogEntry>): List<LogEntry> = entries.filter { it.level == LogLevel.WARNING }
    fun search(entries: List<LogEntry>, query: String): List<LogEntry> =
        entries.filter { it.content.contains(query, ignoreCase = true) }
}
