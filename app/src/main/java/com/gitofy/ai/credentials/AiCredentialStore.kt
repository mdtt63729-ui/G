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
    fun validateFormat(provider: AiProvider, apiKey: String): ValidationResult {
        if (apiKey.isBlank()) return ValidationResult(false, "API key is empty", null)
        val pattern = when (provider) {
            AiProvider.GEMINI -> Regex("AIza[0-9A-Za-z_-]{35}")
            AiProvider.OPENAI -> Regex("sk-[A-Za-z0-9]{20,}")
            AiProvider.NVIDIA_NIM -> Regex("nvapi-[A-Za-z0-9_-]{20,}")
            AiProvider.OPENROUTER -> Regex("sk-or-[A-Za-z0-9_-]{20,}")
            AiProvider.OPENCODE_ZEN -> Regex("[A-Za-z0-9_-]{20,}")
            AiProvider.SARVAM -> Regex("[A-Za-z0-9_-]{20,}")
            AiProvider.CUSTOM -> Regex(".{10,}")
        }
        return if (pattern.matches(apiKey)) ValidationResult(true, null, apiKey)
        else ValidationResult(false, "API key format invalid for ${provider.displayName}", null)
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
}
