package com.gitofy.core.network

import com.gitofy.core.logging.GITOFYLogger
import okhttp3.Interceptor
import okhttp3.Response
import okhttp3.logging.HttpLoggingInterceptor

/**
 * Sensitive header/body redacting logger.
 * PRD 8.2: Sensitive HTTP headers redacted from logs.
 * PRD 8.1: No token in logs, analytics, or exception messages.
 */
class RedactingLogger : HttpLoggingInterceptor.Logger {
    private val sensitiveHeaders = setOf(
        "authorization", "cookie", "set-cookie", "x-github-token", "token"
    )

    override fun log(message: String) {
        val redacted = redactMessage(message)
        GITOFYLogger.d(redacted, tag = "Network")
    }

    private fun redactMessage(message: String): String {
        var result = message
        // Redact header lines
        sensitiveHeaders.forEach { header ->
            val regex = Regex("(?i)$header\\s*:\\s*.+", RegexOption.IGNORE_CASE)
            result = regex.replace(result) { "${it.value.substringBefore(":")}: [REDACTED]" }
        }
        // Redact token patterns
        result = result.replace(Regex("ghp_[A-Za-z0-9]{36,}"), "ghp_[REDACTED]")
        result = result.replace(Regex("github_pat_[A-Za-z0-9_]{82,}"), "github_pat_[REDACTED]")
        result = result.replace(Regex("(?i)Bearer\\s+[A-Za-z0-9_\\-]+"), "Bearer [REDACTED]")
        return result
    }
}
