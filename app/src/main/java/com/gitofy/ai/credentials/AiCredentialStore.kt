package com.gitofy.ai.credentials

import com.gitofy.core.security.SecureCredentialStorage
import org.json.JSONObject
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
    OLLAMA("Ollama", false),
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
class EncryptedCredentialRepository @Inject constructor(
    private val secureStorage: SecureCredentialStorage
) : AiCredentialStore {

    private fun metaKey(provider: AiProvider) = "ai_credential_meta_${provider.name}"

    override suspend fun saveCredential(provider: AiProvider, credential: ProviderCredential) {
        // The secret itself is stored by SecureCredentialStorage (Keystore-backed).
        // Only non-secret metadata/configuration is serialized here.
        val apiKey = credential.encryptedApiKey.toString(Charsets.UTF_8).trim()
        secureStorage.saveAiKey(provider.name, apiKey)

        val meta = JSONObject().apply {
            put("keyHint", credential.keyHint)
            put("validatedAt", credential.validatedAt)
            put("isValid", credential.isValid)
            credential.customConfig?.let { config ->
                put("customName", config.name)
                put("customBaseUrl", config.baseUrl)
                put("customModelId", config.modelId)
                config.organizationId?.let { put("organizationId", it) }
                val headers = JSONObject()
                config.customHeaders.forEach { (k, v) -> headers.put(k, v) }
                put("customHeaders", headers)
            }
        }
        secureStorage.saveAiCredentialMetadata(metaKey(provider), meta.toString())
    }

    override suspend fun getCredential(provider: AiProvider): ProviderCredential? {
        val key = secureStorage.getAiKey(provider.name) ?: return null
        val rawMeta = secureStorage.getAiCredentialMetadata(metaKey(provider))

        // Compatibility/migration path for credentials created by older
        // builds that stored the API key but not the metadata companion record.
        // A present key is still a usable credential for all curated providers;
        // the provider-side request/test remains the authority on whether the
        // key is actually accepted.
        if (rawMeta.isNullOrBlank()) {
            return ProviderCredential(
                provider = provider,
                encryptedApiKey = key.toByteArray(Charsets.UTF_8),
                keyHint = if (key.length > 4) "••••••••••••${key.takeLast(4)}" else "••••",
                validatedAt = 0L,
                isValid = true,
                customConfig = null
            )
        }

        return runCatching {
            val meta = JSONObject(rawMeta)
            val custom = if (meta.has("customBaseUrl")) {
                val headers = mutableMapOf<String, String>()
                meta.optJSONObject("customHeaders")?.let { obj ->
                    obj.keys().forEach { k -> headers[k] = obj.getString(k) }
                }
                CustomProviderConfig(
                    name = meta.optString("customName", provider.displayName),
                    baseUrl = meta.getString("customBaseUrl"),
                    modelId = meta.getString("customModelId"),
                    organizationId = meta.optString("organizationId").takeIf { it.isNotBlank() },
                    customHeaders = headers
                )
            } else null
            ProviderCredential(
                provider = provider,
                encryptedApiKey = key.trim().toByteArray(Charsets.UTF_8),
                keyHint = meta.optString("keyHint", "••••"),
                validatedAt = meta.optLong("validatedAt", 0L),
                isValid = meta.optBoolean("isValid", false),
                customConfig = custom
            )
        }.getOrNull()
    }

    override suspend fun removeCredential(provider: AiProvider) {
        secureStorage.removeAiKey(provider.name)
        secureStorage.removeAiCredentialMetadata(metaKey(provider))
    }

    override suspend fun hasCredential(provider: AiProvider): Boolean =
        getCredential(provider)?.isValid == true

    override suspend fun getAllConfigured(): Map<AiProvider, ProviderCredential> =
        AiProvider.entries.mapNotNull { provider ->
            getCredential(provider)?.let { provider to it }
        }.toMap()

    override suspend fun areAllMandatoryConfigured(): Boolean =
        AiProvider.mandatory.all { hasCredential(it) }
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
