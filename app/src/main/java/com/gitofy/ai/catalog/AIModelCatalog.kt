package com.gitofy.ai.catalog

import com.gitofy.ai.credentials.AiProvider
import javax.inject.Inject
import javax.inject.Singleton

/**
 * AIModelCatalog — Single Source of Truth for all AI models (PRD §3, §17).
 *
 * Static catalog is the offline baseline. DynamicModelRepository overlays live provider
 * registries at startup and falls back to this catalog/cache when offline.
 *
 * Model identity key = provider + ":" + modelId (PRD §4)
 * Free model classification: FREE (truly $0), FREE_CREDIT (free credits), PAID (PRD §9)
 *
 * Catalog Version: 2026.09.01
 * Last Verified: 2026-09-01
 *
 * IMPORTANT: Model IDs must EXACTLY match what each provider's API expects.
 * - Gemini: model IDs from generativelanguage.googleapis.com (e.g., gemini-3.5-flash)
 * - Sarvam: model IDs from api.sarvam.ai (e.g., sarvam-105b) — v1 endpoint
 * - Sarvam open-source (glm5.2, gemma4): v2 endpoint
 * - NVIDIA NIM: model IDs from integrate.api.nvidia.com (e.g., nvidia/nemotron-3.5-lightning-30b-a3b)
 * - OpenRouter: full slug including vendor (e.g., deepseek/deepseek-v4-flash-latest)
 * - OpenCode Zen: model IDs from opencode.ai/zen/v1 (e.g., big-pickle)
 */

// ── Enums ──────────────────────────────────────────────────────────────────

enum class CostTier { FREE, FREE_CREDIT, LOW_COST, MEDIUM_COST, HIGH_COST }
enum class LatencyClass { FAST, MEDIUM, SLOW }
enum class ModelStatus { ACTIVE, DEPRECATED, PREVIEW }
enum class EndpointType { GEMINI, OPENAI_COMPATIBLE, SARVAM_V1, SARVAM_V2, CUSTOM }

/**
 * Canonical model definition (PRD §3).
 * Every model's identity = provider.name + ":" + id
 */
data class AIModelDefinition(
    val id: String,
    val provider: AiProvider,
    val displayName: String,
    val costTier: CostTier,
    val contextWindow: Int,
    val supportsText: Boolean = true,
    val supportsImage: Boolean = false,
    val supportsAudio: Boolean = false,
    val supportsVideo: Boolean = false,
    val supportsTools: Boolean = false,
    val supportsStreaming: Boolean = true,
    val supportsStructuredOutput: Boolean = false,
    val codingScore: Int = 5,       // 1-10
    val reasoningScore: Int = 5,   // 1-10
    val languageScore: Int = 5,     // 1-10
    val latencyClass: LatencyClass = LatencyClass.MEDIUM,
    val endpointType: EndpointType = EndpointType.OPENAI_COMPATIBLE,
    val status: ModelStatus = ModelStatus.ACTIVE
) {
    /** Stable UI key — provider:modelId, never collides across providers (PRD §4) */
    val uniqueKey: String get() = "${provider.name}:$id"
}

// ── Catalog ────────────────────────────────────────────────────────────────

@Singleton
class AIModelCatalog @Inject constructor() {

    companion object {
        const val CATALOG_VERSION = "2026.09.01-nvidia-nim"
    }

    private val models: List<AIModelDefinition> = buildList {
        // ═══════════════════════════════════════════════════════════════════
        // Google Gemini — All models from user's list
        // API: generativelanguage.googleapis.com/v1beta/models/{id}:generateContent
        // Auth: ?key=API_KEY (query param, NOT Bearer header)
        // ═══════════════════════════════════════════════════════════════════

        // --- Best Coding Models ---
        add(AIModelDefinition(
            id = "gemini-3.5-flash", provider = AiProvider.GEMINI,
            displayName = "Gemini 3.5 Flash", costTier = CostTier.FREE,
            contextWindow = 1_000_000,
            supportsImage = true, supportsVideo = true, supportsAudio = true,
            supportsTools = true, supportsStreaming = true, supportsStructuredOutput = true,
            codingScore = 10, reasoningScore = 9, languageScore = 6,
            latencyClass = LatencyClass.FAST,
            endpointType = EndpointType.GEMINI, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "gemini-3.1-flash", provider = AiProvider.GEMINI,
            displayName = "Gemini 3.1 Flash", costTier = CostTier.FREE,
            contextWindow = 1_000_000,
            supportsImage = true, supportsVideo = true, supportsAudio = true,
            supportsTools = true, supportsStreaming = true, supportsStructuredOutput = true,
            codingScore = 9, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            endpointType = EndpointType.GEMINI, status = ModelStatus.PREVIEW
        ))
        add(AIModelDefinition(
            id = "gemini-3-flash", provider = AiProvider.GEMINI,
            displayName = "Gemini 3 Flash", costTier = CostTier.FREE,
            contextWindow = 1_000_000,
            supportsImage = true, supportsVideo = true, supportsAudio = true,
            supportsTools = true, supportsStreaming = true, supportsStructuredOutput = true,
            codingScore = 8, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            endpointType = EndpointType.GEMINI, status = ModelStatus.PREVIEW
        ))

        // --- Other Gemini Models from Image ---
        add(AIModelDefinition(
            id = "gemma-4-26b-a4b-it", provider = AiProvider.GEMINI,
            displayName = "Gemma 4 26B A4B IT", costTier = CostTier.FREE,
            contextWindow = 128_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 6, languageScore = 6,
            latencyClass = LatencyClass.FAST,
            endpointType = EndpointType.GEMINI, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "gemma-4-31b-it", provider = AiProvider.GEMINI,
            displayName = "Gemma 4 31B IT", costTier = CostTier.FREE,
            contextWindow = 128_000,
            supportsImage = true,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 7, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM,
            endpointType = EndpointType.GEMINI, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "gemini-3.1-flash-lite-preview", provider = AiProvider.GEMINI,
            displayName = "Gemini 3.1 Flash Lite Preview", costTier = CostTier.FREE,
            contextWindow = 1_000_000,
            supportsImage = true, supportsTools = true,
            supportsStreaming = true, supportsStructuredOutput = true,
            codingScore = 7, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            endpointType = EndpointType.GEMINI, status = ModelStatus.PREVIEW
        ))
        add(AIModelDefinition(
            id = "gemini-3.1-flash-lite", provider = AiProvider.GEMINI,
            displayName = "Gemini 3.1 Flash-Lite", costTier = CostTier.FREE,
            contextWindow = 1_000_000,
            supportsImage = true, supportsVideo = true, supportsAudio = true,
            supportsTools = true, supportsStreaming = true, supportsStructuredOutput = true,
            codingScore = 7, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            endpointType = EndpointType.GEMINI, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "gemini-3.5-flash-lite", provider = AiProvider.GEMINI,
            displayName = "Gemini 3.5 Flash-Lite", costTier = CostTier.FREE,
            contextWindow = 1_000_000,
            supportsImage = true, supportsTools = true,
            supportsStreaming = true, supportsStructuredOutput = true,
            codingScore = 7, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            endpointType = EndpointType.GEMINI, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "gemini-3.6-flash", provider = AiProvider.GEMINI,
            displayName = "Gemini 3.6 Flash", costTier = CostTier.FREE,
            contextWindow = 1_000_000,
            supportsImage = true, supportsVideo = true, supportsAudio = true,
            supportsTools = true, supportsStreaming = true, supportsStructuredOutput = true,
            codingScore = 9, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            endpointType = EndpointType.GEMINI, status = ModelStatus.ACTIVE
        ))

        // --- Existing models kept for backward compat ---
        add(AIModelDefinition(
            id = "gemini-2.5-pro", provider = AiProvider.GEMINI,
            displayName = "Gemini 2.5 Pro", costTier = CostTier.FREE,
            contextWindow = 2_000_000,
            supportsImage = true, supportsTools = true,
            supportsStreaming = true, supportsStructuredOutput = true,
            codingScore = 9, reasoningScore = 10, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM,
            endpointType = EndpointType.GEMINI, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "gemini-3.7-flash", provider = AiProvider.GEMINI,
            displayName = "Gemini 3.7 Flash", costTier = CostTier.FREE,
            contextWindow = 1_000_000,
            supportsImage = true, supportsTools = true,
            supportsStreaming = true, supportsStructuredOutput = true,
            codingScore = 9, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            endpointType = EndpointType.GEMINI, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "gemini-2.5-flash", provider = AiProvider.GEMINI,
            displayName = "Gemini 2.5 Flash", costTier = CostTier.FREE,
            contextWindow = 1_000_000,
            supportsImage = true, supportsTools = true,
            supportsStreaming = true, supportsStructuredOutput = true,
            codingScore = 8, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            endpointType = EndpointType.GEMINI, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "gemini-2.5-flash-lite", provider = AiProvider.GEMINI,
            displayName = "Gemini 2.5 Flash-Lite", costTier = CostTier.FREE,
            contextWindow = 1_000_000,
            supportsImage = false, supportsTools = true,
            supportsStreaming = true, supportsStructuredOutput = true,
            codingScore = 7, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            endpointType = EndpointType.GEMINI, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "gemma-4-27b-it", provider = AiProvider.GEMINI,
            displayName = "Gemma 4 27B IT", costTier = CostTier.FREE,
            contextWindow = 128_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 6, languageScore = 6,
            latencyClass = LatencyClass.FAST,
            endpointType = EndpointType.GEMINI, status = ModelStatus.ACTIVE
        ))

        // ═══════════════════════════════════════════════════════════════════
        // OpenRouter — All models from user's list
        // API: openrouter.ai/api/v1/chat/completions (OpenAI-compatible)
        // Auth: Bearer API_KEY
        // Model IDs include vendor prefix (e.g., deepseek/deepseek-v4-flash-latest)
        // :free suffix = free models
        // ═══════════════════════════════════════════════════════════════════

        // --- Best Coding Models ---
        add(AIModelDefinition(
            id = "deepseek/deepseek-v4-flash-latest", provider = AiProvider.OPENROUTER,
            displayName = "DeepSeek V4 Flash Latest", costTier = CostTier.LOW_COST,
            contextWindow = 1_000_000,
            supportsStreaming = true, supportsTools = true,
            codingScore = 10, reasoningScore = 9, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "deepseek/deepseek-v4-flash-0731", provider = AiProvider.OPENROUTER,
            displayName = "DeepSeek V4 Flash 0731", costTier = CostTier.LOW_COST,
            contextWindow = 1_310_720,
            supportsStreaming = true, supportsTools = true,
            codingScore = 10, reasoningScore = 9, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "qwen/qwen3.8-flash", provider = AiProvider.OPENROUTER,
            displayName = "Qwen3.8 Flash", costTier = CostTier.LOW_COST,
            contextWindow = 1_000_000,
            supportsImage = true, supportsStreaming = true, supportsTools = true,
            codingScore = 9, reasoningScore = 8, languageScore = 6,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "qwen/qwen3.7-flash", provider = AiProvider.OPENROUTER,
            displayName = "Qwen3.7 Flash", costTier = CostTier.LOW_COST,
            contextWindow = 1_000_000,
            supportsStreaming = true, supportsTools = true,
            codingScore = 9, reasoningScore = 8, languageScore = 6,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "cohere/north-mini-code:free", provider = AiProvider.OPENROUTER,
            displayName = "Cohere North Mini Code Free", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsTools = true, supportsStreaming = true,
            codingScore = 8, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "openai/gpt-4o-mini", provider = AiProvider.OPENROUTER,
            displayName = "GPT-4o mini (OpenRouter)", costTier = CostTier.LOW_COST,
            contextWindow = 128_000,
            supportsTools = true, supportsStreaming = true, supportsStructuredOutput = true,
            codingScore = 8, reasoningScore = 8, languageScore = 7,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "meta-llama/llama-3.1-70b-instruct", provider = AiProvider.OPENROUTER,
            displayName = "Llama 3.1 70B Instruct", costTier = CostTier.LOW_COST,
            contextWindow = 128_000,
            supportsTools = true, supportsStreaming = true,
            codingScore = 8, reasoningScore = 9, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))

        // --- Other OpenRouter Models from User's List ---
        add(AIModelDefinition(
            id = "inclusionai/ling-3.0-flash-fin:free", provider = AiProvider.OPENROUTER,
            displayName = "Ling 3.0 Flash Fin Free", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "z-ai/glm-5.3-flash", provider = AiProvider.OPENROUTER,
            displayName = "GLM-5.3 Flash", costTier = CostTier.LOW_COST,
            contextWindow = 512_000,
            supportsStreaming = true, supportsTools = true,
            codingScore = 8, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "tencent/hy-mt2-30b-a3b", provider = AiProvider.OPENROUTER,
            displayName = "Tencent HY-MT2 30B A3B", costTier = CostTier.LOW_COST,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 7, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "tencent/hy-mt2-1.8b", provider = AiProvider.OPENROUTER,
            displayName = "Tencent HY-MT2 1.8B", costTier = CostTier.FREE,
            contextWindow = 128_000,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 5, languageScore = 6,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "tencent/hy-mt2-7b", provider = AiProvider.OPENROUTER,
            displayName = "Tencent HY-MT2 7B", costTier = CostTier.FREE,
            contextWindow = 128_000,
            supportsStreaming = true,
            codingScore = 6, reasoningScore = 6, languageScore = 6,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "dots-studio/dots-3-note-preview:free", provider = AiProvider.OPENROUTER,
            displayName = "Dots 3 Note Preview Free", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 6, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.PREVIEW
        ))
        add(AIModelDefinition(
            id = "nvidia/nemotron-3.5-lightning", provider = AiProvider.OPENROUTER,
            displayName = "Nemotron 3.5 Lightning", costTier = CostTier.LOW_COST,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "meta/muse-glimmer-30b", provider = AiProvider.OPENROUTER,
            displayName = "Muse Glimmer 30B", costTier = CostTier.LOW_COST,
            contextWindow = 128_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "inclusionai/ling-3.0-flash", provider = AiProvider.OPENROUTER,
            displayName = "Ling 3.0 Flash", costTier = CostTier.LOW_COST,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "poolside/laguna-s-2.1", provider = AiProvider.OPENROUTER,
            displayName = "Poolside Laguna S 2.1", costTier = CostTier.MEDIUM_COST,
            contextWindow = 64_000,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "poolside/laguna-xs-2.1", provider = AiProvider.OPENROUTER,
            displayName = "Poolside Laguna XS 2.1", costTier = CostTier.LOW_COST,
            contextWindow = 64_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nex-agi/nex-n2-mini", provider = AiProvider.OPENROUTER,
            displayName = "Nex N2 Mini", costTier = CostTier.LOW_COST,
            contextWindow = 128_000,
            supportsStreaming = true,
            codingScore = 6, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/nemotron-3.5-content-safety:free", provider = AiProvider.OPENROUTER,
            displayName = "Nemotron 3.5 Content Safety Free", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 5, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "minimax/minimax-m3:free", provider = AiProvider.OPENROUTER,
            displayName = "MiniMax M3 Free", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 7, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "stepfun/step-3.7-flash", provider = AiProvider.OPENROUTER,
            displayName = "Step 3.7 Flash", costTier = CostTier.LOW_COST,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "perceptron/perceptron-mk1", provider = AiProvider.OPENROUTER,
            displayName = "Perceptron MK1", costTier = CostTier.LOW_COST,
            contextWindow = 128_000,
            supportsStreaming = true,
            codingScore = 6, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "ibm-granite/granite-4.1-8b", provider = AiProvider.OPENROUTER,
            displayName = "IBM Granite 4.1 8B", costTier = CostTier.FREE,
            contextWindow = 128_000,
            supportsStreaming = true,
            codingScore = 6, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "xiaomi/mimo-v2.5", provider = AiProvider.OPENROUTER,
            displayName = "MiMo V2.5", costTier = CostTier.LOW_COST,
            contextWindow = 256_000,
            supportsTools = true, supportsStreaming = true,
            codingScore = 8, reasoningScore = 7, languageScore = 6,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/nemotron-3-super-120b-a12b", provider = AiProvider.OPENROUTER,
            displayName = "Nemotron 3 Super 120B A12B", costTier = CostTier.LOW_COST,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "qwen/qwen3.5-9b", provider = AiProvider.OPENROUTER,
            displayName = "Qwen3.5 9B", costTier = CostTier.FREE,
            contextWindow = 128_000,
            supportsStreaming = true,
            codingScore = 6, reasoningScore = 6, languageScore = 6,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "inception/mercury-2", provider = AiProvider.OPENROUTER,
            displayName = "Inception Mercury 2", costTier = CostTier.MEDIUM_COST,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "bytedance-seed/seed-2.0-mini", provider = AiProvider.OPENROUTER,
            displayName = "ByteDance Seed 2.0 Mini", costTier = CostTier.LOW_COST,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 7, languageScore = 6,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "meta-llama/llama-3.1-8b-instruct", provider = AiProvider.OPENROUTER,
            displayName = "Llama 3.1 8B Instruct", costTier = CostTier.FREE,
            contextWindow = 128_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "openai/gpt-4o-mini-2024-07-18", provider = AiProvider.OPENROUTER,
            displayName = "GPT-4o mini 2024-07-18", costTier = CostTier.LOW_COST,
            contextWindow = 128_000,
            supportsTools = true, supportsStreaming = true, supportsStructuredOutput = true,
            codingScore = 8, reasoningScore = 8, languageScore = 7,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))

        // --- Previously curated free models (kept for compatibility) ---
        add(AIModelDefinition(
            id = "xiaomi/mimo-v2.5:free", provider = AiProvider.OPENROUTER,
            displayName = "MiMo V2.5 Free", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsTools = true, supportsStreaming = true,
            codingScore = 9, reasoningScore = 8, languageScore = 6,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "deepseek/deepseek-v4-flash:free", provider = AiProvider.OPENROUTER,
            displayName = "DeepSeek V4 Flash Free", costTier = CostTier.FREE,
            contextWindow = 1_000_000,
            supportsStreaming = true,
            codingScore = 9, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/nemotron-3-ultra-550b-a55b:free", provider = AiProvider.OPENROUTER,
            displayName = "Nemotron 3 Ultra Free", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsTools = true, supportsStreaming = true,
            codingScore = 9, reasoningScore = 9, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "z-ai/glm-5.2:free", provider = AiProvider.OPENROUTER,
            displayName = "GLM-5.2 Free", costTier = CostTier.FREE,
            contextWindow = 512_000,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "tencent/hy3:free", provider = AiProvider.OPENROUTER,
            displayName = "Hy3 Free", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsTools = true, supportsStreaming = true,
            codingScore = 8, reasoningScore = 7, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/nemotron-3.5-lightning-30b-a3b:free", provider = AiProvider.OPENROUTER,
            displayName = "Nemotron 3.5 Lightning Free", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))

        // --- OpenRouter Programming / Free Model Expansion ---
        // Exact OpenRouter slugs supplied for the Programming model catalog.
        // Chat-capable models are configured for streaming; embeddings,
        // reranking, safety and TTS models are catalogued with their native
        // capability flags so they are not misrepresented as chat models.
        add(AIModelDefinition(
            id = "poolside/laguna-s-2.1:free", provider = AiProvider.OPENROUTER,
            displayName = "Poolside Laguna S 2.1 Free", costTier = CostTier.FREE,
            contextWindow = 256_000, supportsStreaming = true,
            codingScore = 9, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/nemotron-3.5-lightning:free", provider = AiProvider.OPENROUTER,
            displayName = "NVIDIA Nemotron 3.5 Lightning Free", costTier = CostTier.FREE,
            contextWindow = 256_000, supportsStreaming = true,
            codingScore = 9, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.FAST, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "minimax/minimax-m2.7:free", provider = AiProvider.OPENROUTER,
            displayName = "MiniMax M2.7 Free", costTier = CostTier.FREE,
            contextWindow = 256_000, supportsStreaming = true,
            codingScore = 9, reasoningScore = 9, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "thinkingmachines/inkling:free", provider = AiProvider.OPENROUTER,
            displayName = "Thinking Machines Inkling Free", costTier = CostTier.FREE,
            contextWindow = 256_000, supportsStreaming = true,
            codingScore = 9, reasoningScore = 8, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "poolside/laguna-xs-2.1:free", provider = AiProvider.OPENROUTER,
            displayName = "Poolside Laguna XS 2.1 Free", costTier = CostTier.FREE,
            contextWindow = 256_000, supportsStreaming = true,
            codingScore = 8, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.FAST, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "thinkingmachines/inkling-small:free", provider = AiProvider.OPENROUTER,
            displayName = "Thinking Machines Inkling Small Free", costTier = CostTier.FREE,
            contextWindow = 128_000, supportsStreaming = true,
            codingScore = 8, reasoningScore = 7, languageScore = 6,
            latencyClass = LatencyClass.FAST, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free", provider = AiProvider.OPENROUTER,
            displayName = "Nemotron 3 Nano Omni 30B A3B Reasoning Free", costTier = CostTier.FREE,
            contextWindow = 256_000, supportsImage = true, supportsStreaming = true,
            codingScore = 8, reasoningScore = 9, languageScore = 5,
            latencyClass = LatencyClass.FAST, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "liquid/lfm-2.5-2.6b:free", provider = AiProvider.OPENROUTER,
            displayName = "Liquid LFM 2.5 2.6B Free", costTier = CostTier.FREE,
            contextWindow = 32_000, supportsStreaming = true,
            codingScore = 7, reasoningScore = 6, languageScore = 6,
            latencyClass = LatencyClass.FAST, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/llama-nemotron-rerank-vl-1b-v2:free", provider = AiProvider.OPENROUTER,
            displayName = "Llama Nemotron Rerank VL 1B V2 Free", costTier = CostTier.FREE,
            contextWindow = 32_000, supportsText = false, supportsImage = true,
            supportsStreaming = false, codingScore = 1, reasoningScore = 1, languageScore = 1,
            latencyClass = LatencyClass.FAST, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "google/gemma-4-31b-it:free", provider = AiProvider.OPENROUTER,
            displayName = "Google Gemma 4 31B IT Free", costTier = CostTier.FREE,
            contextWindow = 128_000, supportsStreaming = true,
            codingScore = 7, reasoningScore = 7, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/nemotron-3-embed-1b:free", provider = AiProvider.OPENROUTER,
            displayName = "Nemotron 3 Embed 1B Free", costTier = CostTier.FREE,
            contextWindow = 8_192, supportsText = false, supportsStreaming = false,
            codingScore = 1, reasoningScore = 1, languageScore = 1,
            latencyClass = LatencyClass.FAST, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/llama-nemotron-embed-vl-1b-v2:free", provider = AiProvider.OPENROUTER,
            displayName = "Llama Nemotron Embed VL 1B V2 Free", costTier = CostTier.FREE,
            contextWindow = 8_192, supportsText = false, supportsImage = true,
            supportsStreaming = false, codingScore = 1, reasoningScore = 1, languageScore = 1,
            latencyClass = LatencyClass.FAST, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "google/gemma-4-26b-a4b-it:free", provider = AiProvider.OPENROUTER,
            displayName = "Google Gemma 4 26B A4B IT Free", costTier = CostTier.FREE,
            contextWindow = 128_000, supportsStreaming = true,
            codingScore = 7, reasoningScore = 6, languageScore = 6,
            latencyClass = LatencyClass.FAST, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "liquid/lfm-2.5-embedding-350m:free", provider = AiProvider.OPENROUTER,
            displayName = "Liquid LFM 2.5 Embedding 350M Free", costTier = CostTier.FREE,
            contextWindow = 8_192, supportsText = false, supportsStreaming = false,
            codingScore = 1, reasoningScore = 1, languageScore = 1,
            latencyClass = LatencyClass.FAST, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "fish-audio/s2.1-pro-free:free", provider = AiProvider.OPENROUTER,
            displayName = "Fish Audio S2.1 Pro Free", costTier = CostTier.FREE,
            contextWindow = 8_192, supportsText = false, supportsAudio = true,
            supportsStreaming = true, codingScore = 1, reasoningScore = 1, languageScore = 1,
            latencyClass = LatencyClass.FAST, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "deepgram/flux-tts:free", provider = AiProvider.OPENROUTER,
            displayName = "Deepgram Flux TTS Free", costTier = CostTier.FREE,
            contextWindow = 8_192, supportsText = false, supportsAudio = true,
            supportsStreaming = true, codingScore = 1, reasoningScore = 1, languageScore = 1,
            latencyClass = LatencyClass.FAST, status = ModelStatus.ACTIVE
        ))

        // ═══════════════════════════════════════════════════════════════════
        // OpenCode Zen — Free Models
        // API: opencode.ai/zen/v1/chat/completions (OpenAI-compatible)
        // Auth: Bearer API_KEY
        // ═══════════════════════════════════════════════════════════════════
        add(AIModelDefinition(
            id = "big-pickle", provider = AiProvider.OPENCODE_ZEN,
            displayName = "Big Pickle", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 9, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "mimo-v2.5-free", provider = AiProvider.OPENCODE_ZEN,
            displayName = "MiMo-V2.5 Free", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 7, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "hy3-free", provider = AiProvider.OPENCODE_ZEN,
            displayName = "Hy3 Free", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsTools = true, supportsStreaming = true,
            codingScore = 8, reasoningScore = 7, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nemotron-3-ultra-free", provider = AiProvider.OPENCODE_ZEN,
            displayName = "Nemotron 3 Ultra Free", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 9, reasoningScore = 9, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nemotron-3.5-lightning-free", provider = AiProvider.OPENCODE_ZEN,
            displayName = "Nemotron 3.5 Lightning Free", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "muse-spark-1.2-contributor-free", provider = AiProvider.OPENCODE_ZEN,
            displayName = "Muse Spark 1.2 Contributor Free", costTier = CostTier.FREE,
            contextWindow = 128_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "deepseek-v4-pro", provider = AiProvider.OPENCODE_ZEN,
            displayName = "DeepSeek V4 Pro", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 9, reasoningScore = 9, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "deepseek-v4-flash", provider = AiProvider.OPENCODE_ZEN,
            displayName = "DeepSeek V4 Flash", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 9, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "glm-5.2", provider = AiProvider.OPENCODE_ZEN,
            displayName = "GLM 5.2", costTier = CostTier.FREE,
            contextWindow = 512_000,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "minimax-m3", provider = AiProvider.OPENCODE_ZEN,
            displayName = "MiniMax M3", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 7, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))

        // ═══════════════════════════════════════════════════════════════════
        // NVIDIA NIM — Free Endpoints
        // API: integrate.api.nvidia.com/v1/chat/completions (OpenAI-compatible)
        // Auth: Bearer API_KEY (nvapi-...)
        // Model IDs MUST include vendor prefix (e.g., nvidia/nemotron-3.5-lightning-30b-a3b)
        // ═══════════════════════════════════════════════════════════════════
        add(AIModelDefinition(
            id = "deepseek-ai/deepseek-v4-flash-0731", provider = AiProvider.NVIDIA_NIM,
            displayName = "DeepSeek V4 Flash 0731", costTier = CostTier.FREE,
            contextWindow = 1_000_000,
            supportsStreaming = true,
            codingScore = 10, reasoningScore = 9, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/nemotron-3.5-lightning-30b-a3b", provider = AiProvider.NVIDIA_NIM,
            displayName = "Nemotron 3.5 Lightning 30B", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "meta/muse-glimmer-30b", provider = AiProvider.NVIDIA_NIM,
            displayName = "Muse Glimmer 30B", costTier = CostTier.FREE,
            contextWindow = 128_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/riva-translate-4b-instruct-v2", provider = AiProvider.NVIDIA_NIM,
            displayName = "Riva Translate 4B", costTier = CostTier.FREE,
            contextWindow = 32_000,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 5, languageScore = 8,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "poolside/laguna-xs-2.1", provider = AiProvider.NVIDIA_NIM,
            displayName = "Laguna XS 2.1", costTier = CostTier.FREE,
            contextWindow = 64_000,
            supportsStreaming = true,
            codingScore = 6, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "z-ai/glm-5.2", provider = AiProvider.NVIDIA_NIM,
            displayName = "GLM-5.2", costTier = CostTier.FREE,
            contextWindow = 512_000,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "minimaxai/minimax-m3", provider = AiProvider.NVIDIA_NIM,
            displayName = "MiniMax M3", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 7, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/nemotron-3-ultra-550b-a55b", provider = AiProvider.NVIDIA_NIM,
            displayName = "Nemotron 3 Ultra 550B", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsTools = true, supportsStreaming = true,
            codingScore = 9, reasoningScore = 9, languageScore = 5,
            latencyClass = LatencyClass.SLOW,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/nemotron-3-super-120b-a12b", provider = AiProvider.NVIDIA_NIM,
            displayName = "Nemotron 3 Super 120B", costTier = CostTier.FREE,
            contextWindow = 256_000,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "meta/llama-3.1-8b-instruct", provider = AiProvider.NVIDIA_NIM,
            displayName = "Llama 3.1 8B Instruct", costTier = CostTier.FREE,
            contextWindow = 128_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "meta/llama-3.1-70b-instruct", provider = AiProvider.NVIDIA_NIM,
            displayName = "Llama 3.1 70B Instruct", costTier = CostTier.FREE,
            contextWindow = 128_000,
            supportsTools = true, supportsStreaming = true,
            codingScore = 8, reasoningScore = 9, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))

        add(AIModelDefinition(
            id = "deepseek-ai/deepseek-v4-pro-0813", provider = AiProvider.NVIDIA_NIM,
            displayName = "DeepSeek V4 Pro 0813", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = false, supportsAudio = false, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 10, reasoningScore = 9, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "moonshotai/kimi-k3", provider = AiProvider.NVIDIA_NIM,
            displayName = "Kimi K3", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = true, supportsAudio = false, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 9, reasoningScore = 9, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/ising-calibration-1.5-31b", provider = AiProvider.NVIDIA_NIM,
            displayName = "Ising Calibration 1.5 31B", costTier = CostTier.FREE,
            contextWindow = 256000,
            supportsImage = false, supportsAudio = false, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 6, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/cosmos3-nano", provider = AiProvider.NVIDIA_NIM,
            displayName = "Cosmos 3 Nano", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = false, supportsAudio = true, supportsVideo = true,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/cosmos3-nano-reasoner", provider = AiProvider.NVIDIA_NIM,
            displayName = "Cosmos 3 Nano Reasoner", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = false, supportsAudio = true, supportsVideo = true,
            supportsStreaming = true,
            codingScore = 6, reasoningScore = 9, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/synthetic-video-detector", provider = AiProvider.NVIDIA_NIM,
            displayName = "Synthetic Video Detector", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = true, supportsAudio = false, supportsVideo = true,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/active-speaker-detection", provider = AiProvider.NVIDIA_NIM,
            displayName = "Active Speaker Detection", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = true, supportsAudio = true, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/ising-calibration-1-35b-a3b", provider = AiProvider.NVIDIA_NIM,
            displayName = "Ising Calibration 1 35B A3B", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = false, supportsAudio = false, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 6, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/nemotron-voicechat", provider = AiProvider.NVIDIA_NIM,
            displayName = "Nemotron VoiceChat", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = false, supportsAudio = true, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 8, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/cosmos-transfer2.5-2b", provider = AiProvider.NVIDIA_NIM,
            displayName = "Cosmos Transfer 2.5 2B", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = true, supportsAudio = true, supportsVideo = true,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/riva-translate-4b-instruct-v1_1", provider = AiProvider.NVIDIA_NIM,
            displayName = "Riva Translate 4B Instruct v1.1", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = false, supportsAudio = true, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 5, languageScore = 9,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/streampetr", provider = AiProvider.NVIDIA_NIM,
            displayName = "StreamPETR", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = true, supportsAudio = false, supportsVideo = true,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/llama-3.1-nemotron-safety-guard-8b-v3", provider = AiProvider.NVIDIA_NIM,
            displayName = "Llama 3.1 Nemotron Safety Guard 8B v3", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = false, supportsAudio = false, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "openai/gpt-oss-20b", provider = AiProvider.NVIDIA_NIM,
            displayName = "GPT OSS 20B", costTier = CostTier.FREE,
            contextWindow = 131072,
            supportsImage = false, supportsAudio = false, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 8, languageScore = 6,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "openai/gpt-oss-120b", provider = AiProvider.NVIDIA_NIM,
            displayName = "GPT OSS 120B", costTier = CostTier.FREE,
            contextWindow = 131072,
            supportsImage = false, supportsAudio = false, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 9, reasoningScore = 9, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "meta/llama-guard-4-12b", provider = AiProvider.NVIDIA_NIM,
            displayName = "Llama Guard 4 12B", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = true, supportsAudio = false, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/cosmos-transfer1-7b", provider = AiProvider.NVIDIA_NIM,
            displayName = "Cosmos Transfer 1 7B", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = true, supportsAudio = true, supportsVideo = true,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/background-noise-removal", provider = AiProvider.NVIDIA_NIM,
            displayName = "Background Noise Removal", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = false, supportsAudio = true, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 6, languageScore = 5,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "mistralai/mistral-nemotron", provider = AiProvider.NVIDIA_NIM,
            displayName = "Mistral Nemotron", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = false, supportsAudio = false, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 8, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/magpie-tts-zeroshot", provider = AiProvider.NVIDIA_NIM,
            displayName = "Magpie TTS ZeroShot", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = false, supportsAudio = true, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 6, languageScore = 8,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/sparsedrive", provider = AiProvider.NVIDIA_NIM,
            displayName = "SparseDrive", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = true, supportsAudio = false, supportsVideo = true,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/bevformer", provider = AiProvider.NVIDIA_NIM,
            displayName = "BEVFormer", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = true, supportsAudio = false, supportsVideo = true,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 7, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "nvidia/studio-voice", provider = AiProvider.NVIDIA_NIM,
            displayName = "Studio Voice", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = false, supportsAudio = true, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 5, reasoningScore = 7, languageScore = 8,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "meta/llama-3.2-11b-vision-instruct", provider = AiProvider.NVIDIA_NIM,
            displayName = "Llama 3.2 11B Vision Instruct", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = true, supportsAudio = false, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 7, languageScore = 6,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "meta/llama-3.2-90b-vision-instruct", provider = AiProvider.NVIDIA_NIM,
            displayName = "Llama 3.2 90B Vision Instruct", costTier = CostTier.FREE,
            contextWindow = 256000,
            supportsImage = true, supportsAudio = false, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 9, reasoningScore = 8, languageScore = 6,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "google/paligemma", provider = AiProvider.NVIDIA_NIM,
            displayName = "PaliGemma", costTier = CostTier.FREE,
            contextWindow = 128000,
            supportsImage = true, supportsAudio = false, supportsVideo = false,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 7, languageScore = 7,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))

        // ═══════════════════════════════════════════════════════════════════
        // Sarvam AI — Free Credits Only (PRD §14)
        // Sarvam gives free credits to new users but models are NOT $0.
        // API v1: api.sarvam.ai/v1/chat/completions — serves sarvam-105b
        //   Auth: Bearer API_KEY (or api-subscription-key header)
        // API v2: api.sarvam.ai/v2/chat/completions — serves sarvam-105b, glm5.2, gemma4
        //   Auth: api-subscription-key header
        // ═══════════════════════════════════════════════════════════════════
        add(AIModelDefinition(
            id = "sarvam-105b", provider = AiProvider.SARVAM,
            displayName = "Sarvam 105B Chat", costTier = CostTier.FREE_CREDIT,
            contextWindow = 128_000,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 8, languageScore = 10,
            latencyClass = LatencyClass.FAST,
            endpointType = EndpointType.SARVAM_V1, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "sarvam-105b-conversations", provider = AiProvider.SARVAM,
            displayName = "Sarvam 105B Conversations", costTier = CostTier.FREE_CREDIT,
            contextWindow = 128_000,
            supportsStreaming = true,
            codingScore = 6, reasoningScore = 7, languageScore = 10,
            latencyClass = LatencyClass.FAST,
            endpointType = EndpointType.SARVAM_V1, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "glm5.2", provider = AiProvider.SARVAM,
            displayName = "GLM-5.2 (Sarvam)", costTier = CostTier.FREE_CREDIT,
            contextWindow = 512_000,
            supportsTools = true,
            supportsStreaming = true,
            codingScore = 8, reasoningScore = 8, languageScore = 5,
            latencyClass = LatencyClass.MEDIUM,
            endpointType = EndpointType.SARVAM_V2, status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "gemma4", provider = AiProvider.SARVAM,
            displayName = "Gemma 4 31B (Sarvam)", costTier = CostTier.FREE_CREDIT,
            contextWindow = 128_000,
            supportsImage = true,
            supportsTools = true,
            supportsStreaming = true,
            codingScore = 7, reasoningScore = 7, languageScore = 8,
            latencyClass = LatencyClass.MEDIUM,
            endpointType = EndpointType.SARVAM_V2, status = ModelStatus.ACTIVE
        ))

        // ═══════════════════════════════════════════════════════════════════
        // OpenAI — No Free API Models (PRD §15)
        // ═══════════════════════════════════════════════════════════════════
        add(AIModelDefinition(
            id = "gpt-4o", provider = AiProvider.OPENAI,
            displayName = "GPT-4o", costTier = CostTier.MEDIUM_COST,
            contextWindow = 128_000,
            supportsImage = true, supportsTools = true,
            supportsStreaming = true, supportsStructuredOutput = true,
            codingScore = 10, reasoningScore = 10, languageScore = 7,
            latencyClass = LatencyClass.MEDIUM,
            status = ModelStatus.ACTIVE
        ))
        add(AIModelDefinition(
            id = "gpt-4o-mini", provider = AiProvider.OPENAI,
            displayName = "GPT-4o mini", costTier = CostTier.LOW_COST,
            contextWindow = 128_000,
            supportsTools = true,
            supportsStreaming = true, supportsStructuredOutput = true,
            codingScore = 8, reasoningScore = 8, languageScore = 7,
            latencyClass = LatencyClass.FAST,
            status = ModelStatus.ACTIVE
        ))
    }

    /** All models (including deprecated/preview) — use sparingly */
    fun getAllModels(): List<AIModelDefinition> = models

    /** All active models (excludes deprecated) — PRD §5.1 */
    fun getActiveModels(): List<AIModelDefinition> =
        models.filter { it.status == ModelStatus.ACTIVE }

    /** All FREE models ($0 inference only) — PRD §9 Category A */
    fun getFreeModels(): List<AIModelDefinition> =
        getActiveModels().filter { it.costTier == CostTier.FREE }

    /**
     * All models visible in model picker (FREE + FREE_CREDIT + LOW_COST, excludes PAID)
     * Shows all non-paid models so users can see what's available.
     */
    fun getPickerModels(): List<AIModelDefinition> =
        getActiveModels().filter {
            it.costTier == CostTier.FREE ||
            it.costTier == CostTier.FREE_CREDIT ||
            it.costTier == CostTier.LOW_COST
        }

    /**
     * Models for a specific provider (active only)
     */
    fun getModelsByProvider(provider: AiProvider): List<AIModelDefinition> =
        getActiveModels().filter { it.provider == provider }

    /**
     * Models for a specific provider that should appear in the picker.
     * Excludes HIGH_COST and MEDIUM_COST models.
     */
    fun getPickerModelsByProvider(provider: AiProvider): List<AIModelDefinition> =
        getModelsByProvider(provider).filter {
            it.costTier == CostTier.FREE ||
            it.costTier == CostTier.FREE_CREDIT ||
            it.costTier == CostTier.LOW_COST
        }

    /** Free models for a specific provider */
    fun getFreeModelsByProvider(provider: AiProvider): List<AIModelDefinition> =
        getModelsByProvider(provider).filter { it.costTier == CostTier.FREE }

    /**
     * Get picker models filtered to only providers the user has configured.
     * This is the key method for the "only show configured provider models" requirement.
     */
    fun getPickerModelsForConfiguredProviders(configuredProviders: Set<AiProvider>): List<AIModelDefinition> =
        getPickerModels().filter { it.provider in configuredProviders }

    /** Find a model by provider + modelId — never throws (PRD §6) */
    fun findModel(provider: AiProvider, modelId: String): AIModelDefinition? =
        models.find { it.provider == provider && it.id == modelId }

    /** Find a model by its unique key (provider:modelId) — never throws */
    fun findByUniqueKey(key: String): AIModelDefinition? {
        val parts = key.split(":", limit = 2)
        if (parts.size != 2) return null
        val provider = AiProvider.entries.find { it.name == parts[0] } ?: return null
        return findModel(provider, parts[1])
    }

    /**
     * Default model selection priority (PRD §30):
     * 1. Connected FREE model
     * 2. Connected model of any cost tier
     *
     * FIX: this used to fall back to "first curated FREE model" / "first
     * available model" from the ENTIRE catalog — i.e. it could silently
     * auto-select a model from a provider the user never added a key for
     * (in practice, often a DeepSeek model, since it sorts early and is
     * FREE-tier). The user would then see errors like "<model id> is not
     * a valid model ID" from a provider they never chose. Auto-selection
     * must never cross into an unconnected provider — if nothing is
     * connected, return null so the UI asks the user to pick/configure a
     * model instead of silently guessing one.
     */
    fun getDefaultModel(connectedProviders: Set<AiProvider>): AIModelDefinition? {
        if (connectedProviders.isEmpty()) return null

        // 1. Connected FREE model
        getFreeModels().firstOrNull { it.provider in connectedProviders }?.let { return it }

        // 2. Any connected model (still restricted to connected providers)
        return getActiveModels().firstOrNull { it.provider in connectedProviders }
    }

    /**
     * Lightweight catalog validation (PRD §18).
     * Returns list of validation warnings — never crashes the app.
     */
    fun validate(): List<String> {
        val warnings = mutableListOf<String>()
        val seenKeys = mutableSetOf<String>()

        for (model in models) {
            if (model.uniqueKey in seenKeys) {
                warnings.add("Duplicate model key: ${model.uniqueKey}")
            }
            seenKeys.add(model.uniqueKey)

            if (model.id.isBlank()) {
                warnings.add("Blank model ID for provider ${model.provider}")
            }
            if (model.displayName.isBlank()) {
                warnings.add("Blank display name for ${model.uniqueKey}")
            }
            if (model.contextWindow <= 0) {
                warnings.add("Invalid context window for ${model.uniqueKey}: ${model.contextWindow}")
            }
            if (model.codingScore !in 1..10) {
                warnings.add("Invalid coding score for ${model.uniqueKey}: ${model.codingScore}")
            }
            if (model.reasoningScore !in 1..10) {
                warnings.add("Invalid reasoning score for ${model.uniqueKey}: ${model.reasoningScore}")
            }
        }
        return warnings
    }
}
