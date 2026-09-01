package com.gitofy.ai.provider.registry

/**
 * PRD §10 — Provider Registry: Centralized provider definition system.
 *
 * A ProviderDefinition describes *what* a provider is (its static metadata).
 * A ProviderInstance is the user's configured copy of that provider (API key,
 * endpoint override, selected model, etc.).
 *
 * This separation is MANDATORY per the PRD — definitions are never mutated by
 * the user; instances are what the user creates, edits and deletes.
 */

/** How the provider authenticates requests. */
enum class AuthType {
    BEARER_TOKEN,   // Authorization: Bearer <key>
    API_KEY_QUERY,  // ?key=<key>
    CUSTOM_HEADER, // e.g. api-subscription-key
    NONE            // local providers (Ollama, LM Studio)
}

/** What a provider can do — PRD §42. */
enum class ProviderCapability {
    TEXT,
    VISION,
    STREAMING,
    TOOLS,
    FUNCTION_CALLING,
    REASONING,
    EMBEDDINGS,
    MODEL_LISTING
}

/** Protocol/compatibility family — determines which API client to use. */
enum class ProviderProtocol {
    GEMINI,
    OPENAI_COMPATIBLE,
    ANTHROPIC,
    COHERE,
    LOCAL_OLLAMA,
    LOCAL_LM_STUDIO
}

/**
 * Static definition of an AI provider — PRD §10.
 *
 * @param id               Stable unique identifier (e.g. "gemini", "openrouter").
 * @param displayName      Human-readable name.
 * @param description      Short description shown in the add-provider sheet.
 * @param defaultEndpoint  Base API URL for built-in providers.
 * @param authType         How the API key is sent.
 * @param authHeaderName   When [authType] is CUSTOM_HEADER, the header name.
 * @param protocol         Which API protocol / client implementation to use.
 * @param defaultModels     Built-in model IDs known to work.
 * @param capabilities      Feature flags — PRD §42.
 * @param docsUrl           Documentation URL.
 * @param privacyUrl        Privacy policy URL.
 * @param isBuiltIn         True for providers shipped in the registry.
 * @param supportsCustomEndpoint  Whether the user can override the endpoint.
 */
data class ProviderDefinition(
    val id: String,
    val displayName: String,
    val description: String,
    val defaultEndpoint: String,
    val authType: AuthType,
    val authHeaderName: String? = null,
    val protocol: ProviderProtocol,
    val defaultModels: List<String> = emptyList(),
    val capabilities: Set<ProviderCapability> = emptySet(),
    val docsUrl: String = "",
    val privacyUrl: String = "",
    val isBuiltIn: Boolean = true,
    val supportsCustomEndpoint: Boolean = false
) {
    val supportsModelListing: Boolean
        get() = ProviderCapability.MODEL_LISTING in capabilities
}

/**
 * A user-configured instance of a provider — PRD §10.
 *
 * The [apiKey] is kept in [com.gitofy.core.security.SecureCredentialStorage]
 * and only its hint is stored here for UI display.  [definitionId] maps back
 * to the [ProviderDefinition] this instance was created from.
 */
data class ProviderInstance(
    val instanceId: String,
    val definitionId: String,
    val displayName: String,
    val endpoint: String,
    val apiKeyHint: String,
    val selectedModel: String? = null,
    val isEnabled: Boolean = true,
    val isDefault: Boolean = false,
    val isCustom: Boolean = false,
    val customHeaders: Map<String, String> = emptyMap(),
    val createdAt: Long = System.currentTimeMillis()
)
