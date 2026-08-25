package com.gitofy.ai.routing

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Updated Model Registry — PRD 2 Sections 30-37.
 *
 * Added OpenAI and Sarvam AI models to the registry.
 * The provider/model architecture must permit models to change without rewriting the UI.
 */
@Singleton
class ExtendedModelRegistry @Inject constructor() {

    data class ExtendedModelRecord(
        val modelId: String,
        val provider: com.gitofy.ai.credentials.AiProvider,
        val displayName: String,
        val contextWindow: Int,
        val supportsVision: Boolean,
        val supportsStreaming: Boolean,
        val supportsTools: Boolean,
        val supportsStructuredOutput: Boolean,
        val costTier: CostTier,
        val priority: Int,
        val enabled: Boolean,
        val codingScore: Int,
        val reasoningScore: Int,
        val languageScore: Int,
        val latencyClass: LatencyClass
    ) {
        enum class CostTier { FREE, LOW_COST, MEDIUM_COST, HIGH_COST }
        enum class LatencyClass { FAST, MEDIUM, SLOW }
    }

    init {
        registerModels()
    }

    private val models = mutableListOf<ExtendedModelRecord>()

    private fun registerModels() {
        // Gemini — PRD Section 32
        register(ExtendedModelRecord("gemini-2.0-flash", com.gitofy.ai.credentials.AiProvider.GEMINI, "Gemini 2.0 Flash", 1_000_000,
            true, true, true, true, ExtendedModelRecord.CostTier.FREE, 1, true, 8, 8, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("gemini-2.5-pro", com.gitofy.ai.credentials.AiProvider.GEMINI, "Gemini 2.5 Pro", 2_000_000,
            true, true, true, true, ExtendedModelRecord.CostTier.LOW_COST, 2, true, 9, 10, 6, ExtendedModelRecord.LatencyClass.MEDIUM))

        // OpenAI — PRD Section 33
        register(ExtendedModelRecord("gpt-4o", com.gitofy.ai.credentials.AiProvider.OPENAI, "GPT-4o", 128_000,
            true, true, true, true, ExtendedModelRecord.CostTier.MEDIUM_COST, 1, true, 10, 10, 7, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("gpt-4o-mini", com.gitofy.ai.credentials.AiProvider.OPENAI, "GPT-4o mini", 128_000,
            false, true, true, true, ExtendedModelRecord.CostTier.LOW_COST, 2, true, 8, 8, 7, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("o3-mini", com.gitofy.ai.credentials.AiProvider.OPENAI, "o3-mini", 200_000,
            false, true, true, false, ExtendedModelRecord.CostTier.LOW_COST, 3, true, 9, 10, 6, ExtendedModelRecord.LatencyClass.MEDIUM))

        // NVIDIA NIM — PRD Section 34
        register(ExtendedModelRecord("nim-llama-3.1-70b", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Llama 3.1 70B (NIM)", 128_000,
            false, true, true, false, ExtendedModelRecord.CostTier.FREE, 1, true, 8, 9, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("nim-deepseek-coder-33b", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "DeepSeek Coder 33B (NIM)", 128_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 2, true, 9, 7, 4, ExtendedModelRecord.LatencyClass.FAST))

        // OpenRouter — PRD Section 35
        register(ExtendedModelRecord("openrouter-auto", com.gitofy.ai.credentials.AiProvider.OPENROUTER, "OpenRouter Auto", 128_000,
            false, true, true, true, ExtendedModelRecord.CostTier.LOW_COST, 1, true, 7, 7, 5, ExtendedModelRecord.LatencyClass.FAST))

        // OpenCode Zen — PRD Section 36
        register(ExtendedModelRecord("opencode-zen-default", com.gitofy.ai.credentials.AiProvider.OPENCODE_ZEN, "OpenCode Zen", 64_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 1, true, 7, 6, 4, ExtendedModelRecord.LatencyClass.MEDIUM))

        // Sarvam AI — PRD Section 37
        register(ExtendedModelRecord("sarvam-1", com.gitofy.ai.credentials.AiProvider.SARVAM, "Sarvam-1", 32_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 1, true, 6, 7, 10, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("sarvam-translate", com.gitofy.ai.credentials.AiProvider.SARVAM, "Sarvam Translate", 16_000,
            false, false, false, false, ExtendedModelRecord.CostTier.FREE, 2, true, 5, 5, 10, ExtendedModelRecord.LatencyClass.FAST))
    }

    fun register(model: ExtendedModelRecord) { models.add(model) }
    fun getAllModels(): List<ExtendedModelRecord> = models.toList()
    fun getModelsByProvider(provider: com.gitofy.ai.credentials.AiProvider): List<ExtendedModelRecord> =
        models.filter { it.provider == provider }

    fun getAvailableModels(): List<ExtendedModelRecord> = models.filter { it.enabled }

    /**
     * Sarvam Language Routing — PRD Section 38.
     * INDIAN_LANGUAGE → Sarvam preferred → Fallback multilingual provider.
     */
    fun getModelsForLanguageTask(): List<ExtendedModelRecord> {
        return models.filter { it.provider == com.gitofy.ai.credentials.AiProvider.SARVAM }
            .sortedBy { it.priority } +
            models.filter { it.provider != com.gitofy.ai.credentials.AiProvider.SARVAM && it.languageScore >= 6 }
            .sortedByDescending { it.languageScore }
    }

    /**
     * AI Model Fallback Matrix — PRD Section 82.
     * Task → Primary → Fallback mapping.
     */
    fun getModelsForTask(taskType: com.gitofy.ai.model.AITaskType): List<ExtendedModelRecord> {
        val available = getAvailableModels()
        return when (taskType) {
            com.gitofy.ai.model.AITaskType.CODE_GENERATION, com.gitofy.ai.model.AITaskType.CODE_REFACTORING,
            com.gitofy.ai.model.AITaskType.BUG_FIX ->
                available.sortedByDescending { it.codingScore }

            com.gitofy.ai.model.AITaskType.BUILD_FAILURE_ANALYSIS, com.gitofy.ai.model.AITaskType.ERROR_ANALYSIS,
            com.gitofy.ai.model.AITaskType.ARCHITECTURE_DESIGN, com.gitofy.ai.model.AITaskType.ARCHITECTURE_REVIEW ->
                available.sortedByDescending { it.reasoningScore }

            com.gitofy.ai.model.AITaskType.VISION_UI_ANALYSIS, com.gitofy.ai.model.AITaskType.UI_ANALYSIS,
            com.gitofy.ai.model.AITaskType.IMAGE_ANALYSIS ->
                available.filter { it.supportsVision }.sortedByDescending { it.codingScore }

            com.gitofy.ai.model.AITaskType.TRANSLATION, com.gitofy.ai.model.AITaskType.INDIAN_LANGUAGE_ASSISTANCE ->
                getModelsForLanguageTask()

            com.gitofy.ai.model.AITaskType.PROJECT_ANALYSIS, com.gitofy.ai.model.AITaskType.REPOSITORY_ANALYSIS ->
                available.sortedByDescending { it.contextWindow }

            else -> available.sortedBy { it.priority }
        }
    }
}
