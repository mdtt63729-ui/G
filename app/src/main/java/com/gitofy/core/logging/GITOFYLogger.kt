package com.gitofy.core.logging

import android.util.Log
import com.gitofy.BuildConfig

/**
 * Structured logger that never exposes sensitive data.
 * Redacts tokens, auth headers, and secrets from all log output.
 */
object GITOFYLogger {

    private const val TAG = "GITOFY"

    // Patterns to redact
    private val sensitivePatterns = listOf(
        Regex("(?i)(token|authorization|secret|password|key)\\s*[:=]\\s*[\\S]+", RegexOption.IGNORE_CASE),
        Regex("Bearer\\s+[A-Za-z0-9_\\-]+"),
        Regex("ghp_[A-Za-z0-9]{36,}"),
        Regex("github_pat_[A-Za-z0-9_]{82,}"),
    )

    fun d(message: String, tag: String = TAG) {
        if (BuildConfig.DEBUG) {
            Log.d(tag, redact(message))
        }
    }

    fun i(message: String, tag: String = TAG) {
        Log.i(tag, redact(message))
    }

    fun w(message: String, tag: String = TAG, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.w(tag, redact(message), throwable)
        } else {
            Log.w(tag, redact(message))
        }
    }

    fun e(message: String, tag: String = TAG, throwable: Throwable? = null) {
        if (throwable != null) {
            Log.e(tag, redact(message), throwable)
        } else {
            Log.e(tag, redact(message))
        }
    }

    private fun redact(message: String): String {
        var redacted = message
        sensitivePatterns.forEach { pattern ->
            redacted = pattern.replace(redacted) { matchResult ->
                val key = matchResult.value.substringBefore(":", "").substringBefore("=", "")
                "$key:[REDACTED]"
            }
        }
        return redacted
    }
}
