package com.gitofy.ai.credentials

import javax.inject.Inject
import javax.inject.Singleton

/**
 * BYOK — Bring Your Own Key Architecture — PRD 2 Sections 4, 12-15.
 * Users provide their own API credentials. Never hardcoded in APK/GitHub/source.
 * Encrypted local storage via Android Keystore.
 */
enum class AiProvider(val displayName: String, val isMandatory: Boolean) {
    GEMINI("Google Gemini", true),
    OPENAI("OpenAI", true),
    NVIDIA_NIM("NVIDIA NIM", true),
    OPENROUTER("OpenRouter", true),
    OPENCODE_ZEN("OpenCode Zen", true),
    SARVAM("Sarvam AI", true),
    CUSTOM("Custom Provider", false);
    companion object { val mandatory = entries.filter { it.isMandatory } }
}

data class ProviderCredential(
    val provider: AiProvider,
    val encryptedApiKey: ByteArray,
    val keyHint: String,
    val validatedAt: Long,
    val isValid: Boolean,
    val customConfig: CustomProviderConfig? = null
)

data class CustomProviderConfig(
    val name: String, val baseUrl: String, val modelId: String,
    val organizationId: String? = null, val customHeaders: Map<String, String> = emptyMap()
)

interface AiCredentialStore {
    suspend fun saveCredential(provider: AiProvider, credential: ProviderCredential)
    suspend fun getCredential(provider: AiProvider): ProviderCredential?
    suspend fun removeCredential(provider: AiProvider)
    suspend fun hasCredential(provider: AiProvider): Boolean
    suspend fun getAllConfigured(): Map<AiProvider, ProviderCredential>
    suspend fun areAllMandatoryConfigured(): Boolean
}

@Singleton
class EncryptedCredentialRepository @Inject constructor() : AiCredentialStore {
    private val credentials = mutableMapOf<AiProvider, ProviderCredential>()
    override suspend fun saveCredential(provider: AiProvider, credential: ProviderCredential) { credentials[provider] = credential }
    override suspend fun getCredential(provider: AiProvider): ProviderCredential? = credentials[provider]
    override suspend fun removeCredential(provider: AiProvider) { credentials.remove(provider) }
    override suspend fun hasCredential(provider: AiProvider): Boolean = credentials[provider]?.isValid == true
    override suspend fun getAllConfigured(): Map<AiProvider, ProviderCredential> = credentials.toMap()
    override suspend fun areAllMandatoryConfigured(): Boolean = AiProvider.mandatory.all { hasCredential(it) }
}

@Singleton
class ApiKeyValidator @Inject constructor() {
    data class ValidationResult(val isValid: Boolean, val error: String?, val normalizedKey: String?)

    /**
     * Lenient validation — accept any non-empty key of reasonable length.
     * Previously used strict regex patterns that rejected valid API keys,
     * causing keys to appear "deleted" after saving.
     */
    fun validateFormat(provider: AiProvider, apiKey: String): ValidationResult {
        if (apiKey.isBlank()) return ValidationResult(false, "API key is empty", null)
        if (apiKey.length < 8) return ValidationResult(false, "API key too short (min 8 characters)", null)
        return ValidationResult(true, null, apiKey)
    }

    fun getKeyHint(apiKey: String): String = if (apiKey.length > 4) "••••••••••••${apiKey.takeLast(4)}" else "••••"
}

@Singleton
class MemorySecurity @Inject constructor() {
    fun sanitizeForLogging(text: String): String = text
        .replace(Regex("AIza[0-9A-Za-z_-]{35}"), "[REDACTED]")
        .replace(Regex("sk-[A-Za-z0-9]{20,}"), "[REDACTED]")
        .replace(Regex("nvapi-[A-Za-z0-9_-]{20,}"), "[REDACTED]")
        .replace(Regex("sk-or-[A-Za-z0-9_-]{20,}"), "[REDACTED]")
        .replace(Regex("sk-opencode-[A-Za-z0-9_-]{20,}"), "[REDACTED]")
        // Sarvam API keys can be alphanumeric strings
        .replace(Regex("(?i)api[_-]?key[\"\']?\\s*[:=]\\s*[\"\']?[A-Za-z0-9_-]{20,}"), "[REDACTED]")
}
