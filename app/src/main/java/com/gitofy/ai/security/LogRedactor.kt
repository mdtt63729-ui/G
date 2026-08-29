package com.gitofy.ai.security

import java.util.regex.Pattern
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §52/§53: Log Redaction — strips sensitive credentials from logs
 * before they are sent to the AI or displayed in repair context.
 */
@Singleton
class LogRedactor @Inject constructor() {

    data class RedactionResult(
        val redactedText: String,
        val redactionCount: Int,
        val redactedTypes: Set<String>
    )

    companion object {
        private val GITHUB_PAT = Pattern.compile("gh[pousr]_[A-Za-z0-9]{36,}")
        private val GITHUB_FINE_GRAINED = Pattern.compile("github_pat_[A-Za-z0-9_]{82,}")
        private val BEARER_TOKEN = Pattern.compile("(?i)Bearer\\s+[A-Za-z0-9_\\-\\.=]{20,}")
        private val AUTH_HEADER = Pattern.compile("(?i)Authorization:\\s*[A-Za-z0-9_\\-\\.=]{20,}")
        private val AWS_ACCESS_KEY = Pattern.compile("AKIA[0-9A-Z]{16}")
        private val AWS_SECRET = Pattern.compile("(?i)aws_secret_access_key\\s*[=:]\\s*[A-Za-z0-9/+=]{40}")
        private val API_KEY = Pattern.compile("(?i)(api[_-]?key|apikey)\\s*[=:]\\s*['\"]?[A-Za-z0-9]{20,}")
        private val ACCESS_TOKEN = Pattern.compile("(?i)(access[_-]?token|secret[_-]?key|password|passwd)\\s*[=:]\\s*['\"]?[A-Za-z0-9]{20,}")
        private val ENV_SECRET = Pattern.compile("(?i)(SECRET|TOKEN|PASSWORD|CREDENTIAL|API_KEY)[_A-Z]*\\s*=\\s*['\"]?[A-Za-z0-9+/=_\\-]{16,}")
        private val PRIVATE_KEY_BLOCK = Pattern.compile("-----BEGIN (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----[\\s\\S]*?-----END (RSA |EC |DSA |OPENSSH )?PRIVATE KEY-----")
        private val LONG_TOKEN = Pattern.compile("(?i)token[_=:]\\s*[A-Za-z0-9_\\-]{40,}")
        private const val REDACTED = "[REDACTED]"
    }

    fun redact(text: String): RedactionResult {
        var result = text
        val types = mutableSetOf<String>()
        var count = 0

        fun applyPattern(pattern: Pattern, typeName: String) {
            val matcher = pattern.matcher(result)
            if (matcher.find()) {
                val matches = matcher.results().count().toInt()
                if (matches > 0) {
                    result = pattern.matcher(result).replaceAll(REDACTED)
                    types.add(typeName)
                    count += matches
                }
            }
        }

        applyPattern(GITHUB_PAT, "GitHub PAT")
        applyPattern(GITHUB_FINE_GRAINED, "GitHub Fine-grained PAT")
        applyPattern(BEARER_TOKEN, "Bearer token")
        applyPattern(AUTH_HEADER, "Authorization header")
        applyPattern(AWS_ACCESS_KEY, "AWS Access Key")
        applyPattern(AWS_SECRET, "AWS Secret Key")
        applyPattern(API_KEY, "API Key")
        applyPattern(ACCESS_TOKEN, "Access Token / Password")
        applyPattern(ENV_SECRET, "Environment Secret")
        applyPattern(PRIVATE_KEY_BLOCK, "Private Key Block")
        applyPattern(LONG_TOKEN, "Long Token")

        return RedactionResult(redactedText = result, redactionCount = count, redactedTypes = types)
    }

    fun redactText(text: String): String = redact(text).redactedText
}
