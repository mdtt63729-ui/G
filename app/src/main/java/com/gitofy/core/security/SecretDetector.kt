package com.gitofy.core.security

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secret Detection Before Push — PRD v3.0 Section 29.
 * Scans project files for obvious credential patterns before pushing to GitHub.
 *
 * Detects:
 * - Private keys (PEM blocks)
 * - Cloud credentials (AWS, GCP, Azure)
 * - API keys
 * - Access tokens
 * - .env files containing secrets
 * - Service-account JSON files
 *
 * Never uploads detected secret contents to analytics.
 */
@Singleton
class SecretDetector @Inject constructor() {

    data class DetectionResult(
        val hasSecrets: Boolean,
        val findings: List<SecretFinding>
    )

    data class SecretFinding(
        val filePath: String,
        val type: SecretType,
        val linePreview: String
    )

    enum class SecretType {
        PRIVATE_KEY,
        AWS_CREDENTIALS,
        GCP_CREDENTIALS,
        AZURE_CREDENTIALS,
        API_KEY,
        ACCESS_TOKEN,
        ENV_SECRETS,
        SERVICE_ACCOUNT,
        GOOGLE_SERVICES_JSON
    }

    // Patterns to detect (file-level)
    private val suspiciousFiles = setOf(
        ".env", "credentials.json", "service-account.json",
        "google-services.json", "agconnect-services.json"
    )

    // Content patterns
    private val contentPatterns = listOf(
        SecretType.PRIVATE_KEY to Regex("-----BEGIN (RSA |EC |DSA )?PRIVATE KEY-----"),
        SecretType.AWS_CREDENTIALS to Regex("(?i)(aws_access_key_id|aws_secret_access_key)\\s*[=:]"),
        SecretType.GCP_CREDENTIALS to Regex("(?i)(type\\s*:\\s*service_account|private_key_id)"),
        SecretType.AZURE_CREDENTIALS to Regex("(?i)(azure_subscription_id|azure_client_secret)\\s*[=:]"),
        SecretType.API_KEY to Regex("(?i)(api[_-]?key|apikey)\\s*[=:]\\s*['\"]?[A-Za-z0-9]{20,}"),
        SecretType.ACCESS_TOKEN to Regex("(?i)(access[_-]?token|secret[_-]?key)\\s*[=:]\\s*['\"]?[A-Za-z0-9]{20,}"),
        SecretType.GOOGLE_SERVICES_JSON to Regex("(?i)\"project_info\".*\"firebase\"")
    )

    /**
     * Scan a project directory for potential secrets.
     */
    fun scan(projectDir: File): DetectionResult {
        val findings = mutableListOf<SecretFinding>()

        projectDir.walkTopDown()
            .filter { it.isFile }
            .filter { !it.absolutePath.contains("/.git/") }
            .filter { it.length() < 1_000_000 } // Skip large files
            .forEach { file ->
                val relativePath = file.absolutePath.removePrefix(projectDir.absolutePath)

                // Check suspicious filenames
                if (file.name.lowercase() in suspiciousFiles) {
                    findings.add(SecretFinding(
                        filePath = relativePath,
                        type = when (file.name.lowercase()) {
                            ".env" -> SecretType.ENV_SECRETS
                            "google-services.json" -> SecretType.GOOGLE_SERVICES_JSON
                            "service-account.json", "credentials.json" -> SecretType.SERVICE_ACCOUNT
                            else -> SecretType.API_KEY
                        },
                        linePreview = "Suspicious file detected"
                    ))
                    return@forEach
                }

                // Check file contents
                try {
                    val content = file.readText()
                    contentPatterns.forEach { (type, pattern) ->
                        if (pattern.containsMatchIn(content)) {
                            val match = pattern.find(content)
                            val preview = match?.value?.take(50)?.let { "$it..." } ?: "Pattern detected"
                            findings.add(SecretFinding(
                                filePath = relativePath,
                                type = type,
                                linePreview = preview
                            ))
                        }
                    }
                } catch (e: Exception) {
                    // Skip unreadable files
                }
            }

        return DetectionResult(
            hasSecrets = findings.isNotEmpty(),
            findings = findings
        )
    }
}
