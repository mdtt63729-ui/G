package com.gitofy.ai.routing

import javax.inject.Inject
import javax.inject.Singleton

/**
 * Updated Model Registry — PRD 2 Sections 30-37.
 *
 * Model IDs MUST match what each provider's API expects:
 * - NVIDIA NIM: include vendor prefix (nvidia/..., meta/..., deepseek-ai/...)
 * - Sarvam: sarvam-105b (NOT sarvam-1)
 * - OpenRouter: include full slug (deepseek/deepseek-v4-flash-latest)
 *
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
        enum class CostTier { FREE, FREE_CREDIT, LOW_COST, MEDIUM_COST, HIGH_COST }
        enum class LatencyClass { FAST, MEDIUM, SLOW }
    }

    init {
        registerModels()
    }

    private val models = mutableListOf<ExtendedModelRecord>()

    private fun registerModels() {
        // Gemini — PRD Section 32
        register(ExtendedModelRecord("gemini-3.5-flash", com.gitofy.ai.credentials.AiProvider.GEMINI, "Gemini 3.5 Flash", 1_000_000,
            true, true, true, true, ExtendedModelRecord.CostTier.FREE, 1, true, 10, 9, 6, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("gemini-3.1-flash", com.gitofy.ai.credentials.AiProvider.GEMINI, "Gemini 3.1 Flash", 1_000_000,
            true, true, true, true, ExtendedModelRecord.CostTier.FREE, 2, true, 9, 8, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("gemini-3.6-flash", com.gitofy.ai.credentials.AiProvider.GEMINI, "Gemini 3.6 Flash", 1_000_000,
            true, true, true, true, ExtendedModelRecord.CostTier.FREE, 3, true, 9, 8, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("gemini-2.5-pro", com.gitofy.ai.credentials.AiProvider.GEMINI, "Gemini 2.5 Pro", 2_000_000,
            true, true, true, true, ExtendedModelRecord.CostTier.FREE, 4, true, 9, 10, 6, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("gemini-3.5-flash-lite", com.gitofy.ai.credentials.AiProvider.GEMINI, "Gemini 3.5 Flash-Lite", 1_000_000,
            true, true, true, true, ExtendedModelRecord.CostTier.FREE, 5, true, 7, 6, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("gemini-3.1-flash-lite", com.gitofy.ai.credentials.AiProvider.GEMINI, "Gemini 3.1 Flash-Lite", 1_000_000,
            true, true, true, true, ExtendedModelRecord.CostTier.FREE, 6, true, 7, 6, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("gemma-4-31b-it", com.gitofy.ai.credentials.AiProvider.GEMINI, "Gemma 4 31B IT", 128_000,
            true, true, false, false, ExtendedModelRecord.CostTier.FREE, 7, true, 7, 7, 6, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("gemma-4-26b-a4b-it", com.gitofy.ai.credentials.AiProvider.GEMINI, "Gemma 4 26B A4B IT", 128_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 8, true, 7, 6, 6, ExtendedModelRecord.LatencyClass.FAST))

        // OpenAI — PRD Section 33
        register(ExtendedModelRecord("gpt-4o", com.gitofy.ai.credentials.AiProvider.OPENAI, "GPT-4o", 128_000,
            true, true, true, true, ExtendedModelRecord.CostTier.MEDIUM_COST, 1, true, 10, 10, 7, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("gpt-4o-mini", com.gitofy.ai.credentials.AiProvider.OPENAI, "GPT-4o mini", 128_000,
            false, true, true, true, ExtendedModelRecord.CostTier.LOW_COST, 2, true, 8, 8, 7, ExtendedModelRecord.LatencyClass.FAST))

        // NVIDIA NIM — PRD Section 34
        // FIX: Model IDs now include vendor prefix (nvidia/..., meta/..., deepseek-ai/...)
        register(ExtendedModelRecord("deepseek-ai/deepseek-v4-flash-0731", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "DeepSeek V4 Flash 0731", 1_000_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 1, true, 10, 9, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("nvidia/nemotron-3.5-lightning-30b-a3b", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Nemotron 3.5 Lightning 30B", 256_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 2, true, 8, 7, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("nvidia/nemotron-3-ultra-550b-a55b", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Nemotron 3 Ultra 550B", 256_000,
            false, true, true, false, ExtendedModelRecord.CostTier.FREE, 3, true, 9, 9, 5, ExtendedModelRecord.LatencyClass.SLOW))
        register(ExtendedModelRecord("nvidia/nemotron-3-super-120b-a12b", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Nemotron 3 Super 120B", 256_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 4, true, 8, 8, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("meta/llama-3.1-70b-instruct", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Llama 3.1 70B Instruct", 128_000,
            false, true, true, false, ExtendedModelRecord.CostTier.FREE, 5, true, 8, 9, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("meta/llama-3.1-8b-instruct", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Llama 3.1 8B Instruct", 128_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 6, true, 7, 6, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("meta/muse-glimmer-30b", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Muse Glimmer 30B", 128_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 7, true, 7, 6, 5, ExtendedModelRecord.LatencyClass.MEDIUM))

        register(ExtendedModelRecord("deepseek-ai/deepseek-v4-pro-0813", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "DeepSeek V4 Pro 0813", 128000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 10, 9, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("moonshotai/kimi-k3", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Kimi K3", 128000,
            true, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 9, 9, 6, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("nvidia/ising-calibration-1.5-31b", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Ising Calibration 1.5 31B", 256000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 6, 7, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("nvidia/cosmos3-nano", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Cosmos 3 Nano", 128000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 5, 6, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("nvidia/cosmos3-nano-reasoner", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Cosmos 3 Nano Reasoner", 128000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 6, 9, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("nvidia/synthetic-video-detector", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Synthetic Video Detector", 128000,
            true, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 5, 7, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("nvidia/active-speaker-detection", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Active Speaker Detection", 128000,
            true, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 5, 6, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("nvidia/ising-calibration-1-35b-a3b", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Ising Calibration 1 35B A3B", 128000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 6, 7, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("nvidia/nemotron-voicechat", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Nemotron VoiceChat", 128000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 7, 8, 6, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("nvidia/cosmos-transfer2.5-2b", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Cosmos Transfer 2.5 2B", 128000,
            true, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 5, 6, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("nvidia/riva-translate-4b-instruct-v1_1", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Riva Translate 4B Instruct v1.1", 128000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 5, 5, 9, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("nvidia/streampetr", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "StreamPETR", 128000,
            true, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 5, 7, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("nvidia/llama-3.1-nemotron-safety-guard-8b-v3", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Llama 3.1 Nemotron Safety Guard 8B v3", 128000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 5, 7, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("openai/gpt-oss-20b", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "GPT OSS 20B", 131072,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 8, 8, 6, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("openai/gpt-oss-120b", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "GPT OSS 120B", 131072,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 9, 9, 6, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("meta/llama-guard-4-12b", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Llama Guard 4 12B", 128000,
            true, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 5, 8, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("nvidia/cosmos-transfer1-7b", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Cosmos Transfer 1 7B", 128000,
            true, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 5, 6, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("nvidia/background-noise-removal", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Background Noise Removal", 128000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 5, 6, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("mistralai/mistral-nemotron", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Mistral Nemotron", 128000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 8, 8, 6, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("nvidia/magpie-tts-zeroshot", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Magpie TTS ZeroShot", 128000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 5, 6, 8, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("nvidia/sparsedrive", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "SparseDrive", 128000,
            true, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 5, 7, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("nvidia/bevformer", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "BEVFormer", 128000,
            true, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 5, 7, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("nvidia/studio-voice", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Studio Voice", 128000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 5, 7, 8, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("meta/llama-3.2-11b-vision-instruct", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Llama 3.2 11B Vision Instruct", 128000,
            true, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 8, 7, 6, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("meta/llama-3.2-90b-vision-instruct", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "Llama 3.2 90B Vision Instruct", 256000,
            true, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 9, 8, 6, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("google/paligemma", com.gitofy.ai.credentials.AiProvider.NVIDIA_NIM, "PaliGemma", 128000,
            true, true, false, false, ExtendedModelRecord.CostTier.FREE, 50, true, 7, 7, 7, ExtendedModelRecord.LatencyClass.MEDIUM))

        // OpenRouter — PRD Section 35
        // FIX: Model IDs now include full vendor slug (deepseek/deepseek-v4-flash-latest)
        register(ExtendedModelRecord("deepseek/deepseek-v4-flash-latest", com.gitofy.ai.credentials.AiProvider.OPENROUTER, "DeepSeek V4 Flash Latest", 1_000_000,
            false, true, true, false, ExtendedModelRecord.CostTier.LOW_COST, 1, true, 10, 9, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("deepseek/deepseek-v4-flash-0731", com.gitofy.ai.credentials.AiProvider.OPENROUTER, "DeepSeek V4 Flash 0731", 1_310_720,
            false, true, true, false, ExtendedModelRecord.CostTier.LOW_COST, 2, true, 10, 9, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("qwen/qwen3.8-flash", com.gitofy.ai.credentials.AiProvider.OPENROUTER, "Qwen3.8 Flash", 1_000_000,
            true, true, true, false, ExtendedModelRecord.CostTier.LOW_COST, 3, true, 9, 8, 6, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("qwen/qwen3.7-flash", com.gitofy.ai.credentials.AiProvider.OPENROUTER, "Qwen3.7 Flash", 1_000_000,
            false, true, true, false, ExtendedModelRecord.CostTier.LOW_COST, 4, true, 9, 8, 6, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("cohere/north-mini-code:free", com.gitofy.ai.credentials.AiProvider.OPENROUTER, "Cohere North Mini Code Free", 256_000,
            false, true, true, false, ExtendedModelRecord.CostTier.FREE, 5, true, 8, 7, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("openai/gpt-4o-mini", com.gitofy.ai.credentials.AiProvider.OPENROUTER, "GPT-4o mini", 128_000,
            false, true, true, true, ExtendedModelRecord.CostTier.LOW_COST, 6, true, 8, 8, 7, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("meta-llama/llama-3.1-70b-instruct", com.gitofy.ai.credentials.AiProvider.OPENROUTER, "Llama 3.1 70B Instruct", 128_000,
            false, true, true, false, ExtendedModelRecord.CostTier.LOW_COST, 7, true, 8, 9, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("z-ai/glm-5.3-flash", com.gitofy.ai.credentials.AiProvider.OPENROUTER, "GLM-5.3 Flash", 512_000,
            false, true, true, false, ExtendedModelRecord.CostTier.LOW_COST, 8, true, 8, 8, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("minimax/minimax-m3:free", com.gitofy.ai.credentials.AiProvider.OPENROUTER, "MiniMax M3 Free", 256_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 9, true, 7, 7, 6, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("xiaomi/mimo-v2.5:free", com.gitofy.ai.credentials.AiProvider.OPENROUTER, "MiMo V2.5 Free", 256_000,
            false, true, true, false, ExtendedModelRecord.CostTier.FREE, 10, true, 9, 8, 6, ExtendedModelRecord.LatencyClass.FAST))

        // OpenCode Zen — PRD Section 36
        // FIX: Correct endpoint is opencode.ai/zen/v1 (NOT api.opencodezen.com)
        register(ExtendedModelRecord("big-pickle", com.gitofy.ai.credentials.AiProvider.OPENCODE_ZEN, "Big Pickle", 256_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 1, true, 9, 8, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("deepseek-v4-flash", com.gitofy.ai.credentials.AiProvider.OPENCODE_ZEN, "DeepSeek V4 Flash", 256_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 2, true, 9, 8, 5, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("deepseek-v4-pro", com.gitofy.ai.credentials.AiProvider.OPENCODE_ZEN, "DeepSeek V4 Pro", 256_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 3, true, 9, 9, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("glm-5.2", com.gitofy.ai.credentials.AiProvider.OPENCODE_ZEN, "GLM 5.2", 512_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 4, true, 8, 8, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("minimax-m3", com.gitofy.ai.credentials.AiProvider.OPENCODE_ZEN, "MiniMax M3", 256_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 5, true, 7, 7, 6, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("mimo-v2.5-free", com.gitofy.ai.credentials.AiProvider.OPENCODE_ZEN, "MiMo-V2.5 Free", 256_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 6, true, 8, 7, 6, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("nemotron-3-ultra-free", com.gitofy.ai.credentials.AiProvider.OPENCODE_ZEN, "Nemotron 3 Ultra Free", 256_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 7, true, 9, 9, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("nemotron-3.5-lightning-free", com.gitofy.ai.credentials.AiProvider.OPENCODE_ZEN, "Nemotron 3.5 Lightning Free", 256_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE, 8, true, 8, 7, 5, ExtendedModelRecord.LatencyClass.FAST))

        // Sarvam AI — PRD Section 37
        // FIX: Model ID is sarvam-105b (NOT sarvam-1, NOT sarvam-105b-chat)
        register(ExtendedModelRecord("sarvam-105b", com.gitofy.ai.credentials.AiProvider.SARVAM, "Sarvam 105B Chat", 128_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE_CREDIT, 1, true, 7, 8, 10, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("sarvam-105b-conversations", com.gitofy.ai.credentials.AiProvider.SARVAM, "Sarvam 105B Conversations", 128_000,
            false, true, false, false, ExtendedModelRecord.CostTier.FREE_CREDIT, 2, true, 6, 7, 10, ExtendedModelRecord.LatencyClass.FAST))
        register(ExtendedModelRecord("glm5.2", com.gitofy.ai.credentials.AiProvider.SARVAM, "GLM-5.2 (Sarvam)", 512_000,
            false, true, true, false, ExtendedModelRecord.CostTier.FREE_CREDIT, 3, true, 8, 8, 5, ExtendedModelRecord.LatencyClass.MEDIUM))
        register(ExtendedModelRecord("gemma4", com.gitofy.ai.credentials.AiProvider.SARVAM, "Gemma 4 31B (Sarvam)", 128_000,
            true, true, true, false, ExtendedModelRecord.CostTier.FREE_CREDIT, 4, true, 7, 7, 8, ExtendedModelRecord.LatencyClass.MEDIUM))
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
