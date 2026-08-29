package com.gitofy.ai.settings

import javax.inject.Inject
import javax.inject.Singleton

/**
 * AI Settings Architecture — PRD 2 Section 92.
 *
 * Settings → AI → (Providers, Models, Routing, Privacy, Usage, Conversations, Advanced)
 *
 * User AI Preferences — PRD Section 40:
 * AI Settings ├── Default Provider ├── Default Model ├── Coding Provider
 * ├── Reasoning Provider ├── Vision Provider ├── Language Provider
 * ├── Fast Response Provider └── Fallback Strategy
 */

/**
 * User AI Preferences — PRD 2 Section 40.
 */
@Singleton
class UserAIPreferences @Inject constructor() {

    data class Preferences(
        val defaultProvider: com.gitofy.ai.credentials.AiProvider? = null,
        val defaultModel: String? = null,
        val codingProvider: com.gitofy.ai.credentials.AiProvider = com.gitofy.ai.credentials.AiProvider.OPENAI,
        val reasoningProvider: com.gitofy.ai.credentials.AiProvider = com.gitofy.ai.credentials.AiProvider.OPENAI,
        val visionProvider: com.gitofy.ai.credentials.AiProvider = com.gitofy.ai.credentials.AiProvider.GEMINI,
        val languageProvider: com.gitofy.ai.credentials.AiProvider = com.gitofy.ai.credentials.AiProvider.SARVAM,
        val fastResponseProvider: com.gitofy.ai.credentials.AiProvider = com.gitofy.ai.credentials.AiProvider.OPENROUTER,
        val fallbackStrategy: FallbackStrategy = FallbackStrategy.AUTOMATIC
    )

    enum class FallbackStrategy {
        AUTOMATIC,   // GITOFY selects fallback automatically
        MANUAL,      // User selects fallback
        PREFERRED_ONLY // No fallback — only use preferred provider
    }

    /**
     * Automatic Routing Modes — PRD Section 41.
     */
    enum class RoutingMode(val displayName: String) {
        AUTO("Auto — GITOFY selects best available"),
        MANUAL("Manual — User explicitly selects"),
        PREFERRED("Preferred Provider — User chooses with fallback")
    }

    private var preferences = Preferences()
    private var routingMode = RoutingMode.AUTO

    fun getPreferences(): Preferences = preferences
    fun setPreferences(new: Preferences) { preferences = new }

    fun getRoutingMode(): RoutingMode = routingMode
    fun setRoutingMode(mode: RoutingMode) { routingMode = mode }

    /**
     * Get preferred provider for a task type.
     */
    fun getPreferredProvider(taskType: com.gitofy.ai.model.AITaskType): com.gitofy.ai.credentials.AiProvider {
        return when (taskType) {
            com.gitofy.ai.model.AITaskType.CODE_GENERATION,
            com.gitofy.ai.model.AITaskType.CODE_REFACTORING,
            com.gitofy.ai.model.AITaskType.BUG_FIX -> preferences.codingProvider

            com.gitofy.ai.model.AITaskType.BUILD_FAILURE_ANALYSIS,
            com.gitofy.ai.model.AITaskType.ERROR_ANALYSIS,
            com.gitofy.ai.model.AITaskType.ARCHITECTURE_DESIGN,
            com.gitofy.ai.model.AITaskType.PROJECT_ANALYSIS,
            com.gitofy.ai.model.AITaskType.REPOSITORY_ANALYSIS -> preferences.reasoningProvider

            com.gitofy.ai.model.AITaskType.VISION_UI_ANALYSIS,
            com.gitofy.ai.model.AITaskType.IMAGE_ANALYSIS -> preferences.visionProvider

            com.gitofy.ai.model.AITaskType.TRANSLATION,
            com.gitofy.ai.model.AITaskType.INDIAN_LANGUAGE_ASSISTANCE -> preferences.languageProvider

            else -> preferences.defaultProvider ?: preferences.codingProvider
        }
    }
}

/**
 * AI Privacy Controls — PRD 2 Section 53.
 *
 * Settings: AI Privacy
 * ├── Allow Source Code to AI
 * ├── Allow Project Files
 * ├── Exclude Secret Files
 * ├── Confirm Before Large Upload
 * └── Clear AI Session Data
 *
 * Default: Exclude obvious secret/credential files.
 */
@Singleton
class AIPrivacyControls @Inject constructor() {

    data class PrivacySettings(
        val allowSourceCodeToAI: Boolean = true,
        val allowProjectFiles: Boolean = true,
        val excludeSecretFiles: Boolean = true,
        val confirmBeforeLargeUpload: Boolean = true,
        val largeUploadThresholdChars: Int = 10_000
    )

    private var settings = PrivacySettings()

    fun getSettings(): PrivacySettings = settings
    fun setSettings(new: PrivacySettings) { settings = new }

    /**
     * AI Source Exclusion — PRD Section 89.
     * Default exclusions: local.properties, .env, *.pem, *.key, credentials.*, service-account*.json, secrets.*
     * Users can modify exclusions.
     */
    private val defaultExcludedPatterns = listOf(
        Regex("local\\.properties", RegexOption.IGNORE_CASE),
        Regex("\\.env(\\..*)?", RegexOption.IGNORE_CASE),
        Regex(".*\\.pem", RegexOption.IGNORE_CASE),
        Regex(".*\\.key", RegexOption.IGNORE_CASE),
        Regex("credentials\\..*", RegexOption.IGNORE_CASE),
        Regex("service-account.*\\.json", RegexOption.IGNORE_CASE),
        Regex("secrets\\..*", RegexOption.IGNORE_CASE),
        Regex(".*\\.keystore", RegexOption.IGNORE_CASE),
        Regex(".*\\.jks", RegexOption.IGNORE_CASE),
        Regex("google-services\\.json", RegexOption.IGNORE_CASE),
        Regex(".*api[_-]?key.*", RegexOption.IGNORE_CASE),
        Regex(".*token.*", RegexOption.IGNORE_CASE)
    )

    private var customExclusions = mutableListOf<Regex>()

    fun isFileExcluded(filePath: String): Boolean {
        if (!settings.excludeSecretFiles) return false
        val allPatterns = defaultExcludedPatterns + customExclusions
        return allPatterns.any { it.matches(filePath.substringAfterLast('/')) || it.containsMatchIn(filePath) }
    }

    fun addCustomExclusion(pattern: String) {
        customExclusions.add(Regex(pattern, RegexOption.IGNORE_CASE))
    }

    fun removeCustomExclusion(pattern: String) {
        customExclusions.removeAll { it.pattern == pattern }
    }

    fun getDefaultExclusions(): List<String> = defaultExcludedPatterns.map { it.pattern }
    fun getCustomExclusions(): List<String> = customExclusions.map { it.pattern }

    /**
     * Sensitive Code Protection — PRD Section 52.
     * Before sending project files to AI: warn user, identify provider,
     * allow project-level AI access control, exclude sensitive files, never send API keys.
     */
    fun shouldWarnBeforeUpload(contextSize: Int, provider: String): Boolean {
        if (!settings.allowSourceCodeToAI) return true
        if (settings.confirmBeforeLargeUpload && contextSize > settings.largeUploadThresholdChars) return true
        return false
    }

    /**
     * AI Context Permissions — PRD Section 88.
     * Allow: Current file only, Selected files, Current module, Entire project.
     * Default: Minimum necessary context.
     */
    enum class ContextPermission(val displayName: String) {
        CURRENT_FILE("Current file only"),
        SELECTED_FILES("Selected files"),
        CURRENT_MODULE("Current module"),
        ENTIRE_PROJECT("Entire project")
    }

    private var contextPermission = ContextPermission.CURRENT_FILE

    fun getContextPermission(): ContextPermission = contextPermission
    fun setContextPermission(permission: ContextPermission) { contextPermission = permission }
}

/**
 * AI Conversation Storage — PRD 2 Section 91.
 * If enabled: Encrypt sensitive conversation history, allow delete, allow clear all.
 * Do not include API credentials. Do not sync private AI conversations to GitHub.
 */
@Singleton
class AIConversationStorage @Inject constructor() {

    data class ConversationStorageSettings(
        val isEnabled: Boolean = false,
        val encryptHistory: Boolean = true,
        val maxStoredConversations: Int = 50,
        val autoDeleteAfterDays: Int = 30
    )

    private var settings = ConversationStorageSettings()

    fun getSettings(): ConversationStorageSettings = settings
    fun setSettings(new: ConversationStorageSettings) { settings = new }

    fun canStoreConversations(): Boolean = settings.isEnabled

    /**
     * AI Request Audit — PRD Section 90.
     * Store local non-sensitive metadata: Timestamp, Provider, Model, Task type,
     * Latency, Success/failure, Fallback used.
     * Do NOT store: API key, Full prompt, Full source code (unless conversation history enabled).
     */
    data class AuditEntry(
        val timestamp: Long,
        val provider: String,
        val model: String,
        val taskType: String,
        val latencyMs: Long,
        val success: Boolean,
        val fallbackUsed: Boolean
    )

    private val auditLog = mutableListOf<AuditEntry>()

    fun recordAudit(entry: AuditEntry) { auditLog.add(entry) }
    fun getAuditLog(): List<AuditEntry> = auditLog.sortedByDescending { it.timestamp }
    fun clearAuditLog() { auditLog.clear() }
}

/**
 * Provider Terms / Privacy — PRD 2 Section 86.
 * For each provider, provide: Provider, Privacy/Terms link, API documentation link.
 */
object ProviderInfo {

    data class ProviderDetails(
        val provider: com.gitofy.ai.credentials.AiProvider,
        val displayName: String,
        val apiDocsUrl: String,
        val privacyUrl: String,
        val description: String
    )

    val allProviders = mapOf(
        com.gitofy.ai.credentials.AiProvider.GEMINI to ProviderDetails(
            com.gitofy.ai.credentials.AiProvider.GEMINI, "Google Gemini",
            "https://ai.google.dev/docs", "https://policies.google.com/privacy",
            "Complex coding, long-context analysis, multimodal understanding, UI screenshot analysis"
        ),
        com.gitofy.ai.credentials.AiProvider.OPENAI to ProviderDetails(
            com.gitofy.ai.credentials.AiProvider.OPENAI, "OpenAI",
            "https://platform.openai.com/docs", "https://openai.com/policies/privacy",
            "Coding, reasoning, code review, bug fixing, structured output, agentic tasks"
        ),
        com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM to ProviderDetails(
            com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "NVIDIA NIM",
            "https://docs.nvidia.com/nim/", "https://www.nvidia.com/en-us/about-nvidia/privacy-policy/",
            "High-performance inference, coding, reasoning, agentic workloads"
        ),
        com.gitofy.ai.credentials.AiProvider.OPENROUTER to ProviderDetails(
            com.gitofy.ai.credentials.AiProvider.OPENROUTER, "OpenRouter",
            "https://openrouter.ai/docs", "https://openrouter.ai/privacy",
            "Model diversity, provider fallback, cost-aware routing, additional coding models"
        ),
        com.gitofy.ai.credentials.AiProvider.OPENCODE_ZEN to ProviderDetails(
            com.gitofy.ai.credentials.AiProvider.OPENCODE_ZEN, "OpenCode Zen",
            "https://opencode.ai/docs", "https://opencode.ai/privacy",
            "Coding, agentic coding, code repair, terminal/build-error reasoning"
        ),
        com.gitofy.ai.credentials.AiProvider.SARVAM to ProviderDetails(
            com.gitofy.ai.credentials.AiProvider.SARVAM, "Sarvam AI",
            "https://sarvam.ai/docs", "https://sarvam.ai/privacy",
            "Indian-language assistance, Bengali/Hindi, translation, language understanding"
        ),
        com.gitofy.ai.credentials.AiProvider.CUSTOM to ProviderDetails(
            com.gitofy.ai.credentials.AiProvider.CUSTOM, "Custom Provider",
            "", "",
            "Connect your own OpenAI-compatible or compatible AI endpoint"
        )
    )

    fun getProviderInfo(provider: com.gitofy.ai.credentials.AiProvider): ProviderDetails? =
        allProviders[provider]
}
