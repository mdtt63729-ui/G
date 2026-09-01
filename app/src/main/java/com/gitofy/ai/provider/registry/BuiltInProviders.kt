package com.gitofy.ai.provider.registry

/**
 * Static built-in provider definitions — shared between ProviderRegistryData
 * and ApiProviderClient so the client can look up protocol info without a
 * runtime dependency on the @Singleton registry.
 *
 * Keeping this in a plain object avoids circular DI and keeps the data
 * consistent — there is exactly ONE list of definitions in the codebase.
 */
object BuiltInProviders {

    val all: List<ProviderDefinition> = buildList {

        add(ProviderDefinition(
            id = "gemini",
            displayName = "Google Gemini",
            description = "Complex coding, long-context analysis, multimodal understanding",
            defaultEndpoint = "https://generativelanguage.googleapis.com/v1beta",
            authType = AuthType.API_KEY_QUERY,
            protocol = ProviderProtocol.GEMINI,
            defaultModels = listOf("gemini-3.5-flash", "gemini-3.1-flash", "gemini-3-flash", "gemma-4-26b-a4b-it"),
            capabilities = setOf(
                ProviderCapability.TEXT, ProviderCapability.VISION,
                ProviderCapability.STREAMING, ProviderCapability.TOOLS,
                ProviderCapability.FUNCTION_CALLING, ProviderCapability.MODEL_LISTING
            ),
            docsUrl = "https://ai.google.dev/docs",
            privacyUrl = "https://policies.google.com/privacy"
        ))

        add(ProviderDefinition(
            id = "openrouter",
            displayName = "OpenRouter",
            description = "Model diversity, provider fallback, cost-aware routing",
            defaultEndpoint = "https://openrouter.ai/api/v1",
            authType = AuthType.BEARER_TOKEN,
            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf(
                "nvidia/nemotron-3-ultra-550b-a55b:free",
                "minimax/minimax-m3:free",
                "poolside/laguna-s-2.1:free",
                "nvidia/nemotron-3.5-lightning:free",
                "nvidia/nemotron-3-super-120b-a12b:free",
                "inclusionai/ling-3.0-flash-fin:free",
                "minimax/minimax-m2.7:free",
                "thinkingmachines/inkling:free",
                "dots-studio/dots-3-note-preview:free",
                "cohere/north-mini-code:free",
                "poolside/laguna-xs-2.1:free",
                "thinkingmachines/inkling-small:free",
                "nvidia/nemotron-3-nano-omni-30b-a3b-reasoning:free",
                "z-ai/glm-5.2:free",
                "liquid/lfm-2.5-2.6b:free",
                "google/gemma-4-31b-it:free",
                "google/gemma-4-26b-a4b-it:free",
                "deepseek/deepseek-v4-flash-latest"
            ),
            capabilities = setOf(
                ProviderCapability.TEXT, ProviderCapability.STREAMING,
                ProviderCapability.TOOLS, ProviderCapability.MODEL_LISTING
            ),
            docsUrl = "https://openrouter.ai/docs",
            privacyUrl = "https://openrouter.ai/privacy"
        ))

        add(ProviderDefinition(
            id = "openai",
            displayName = "OpenAI",
            description = "Coding, reasoning, code review, structured output, agentic tasks",
            defaultEndpoint = "https://api.openai.com/v1",
            authType = AuthType.BEARER_TOKEN,
            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf("gpt-5", "gpt-5-mini", "o4-mini"),
            capabilities = setOf(
                ProviderCapability.TEXT, ProviderCapability.VISION,
                ProviderCapability.STREAMING, ProviderCapability.TOOLS,
                ProviderCapability.FUNCTION_CALLING, ProviderCapability.MODEL_LISTING
            ),
            docsUrl = "https://platform.openai.com/docs",
            privacyUrl = "https://openai.com/policies/privacy"
        ))

        add(ProviderDefinition(
            id = "anthropic",
            displayName = "Anthropic",
            description = "Claude models — safe, helpful coding and reasoning",
            defaultEndpoint = "https://api.anthropic.com/v1",
            authType = AuthType.BEARER_TOKEN,
            authHeaderName = "x-api-key",
            protocol = ProviderProtocol.ANTHROPIC,
            defaultModels = listOf("claude-sonnet-4-20250514", "claude-opus-4-20250514"),
            capabilities = setOf(
                ProviderCapability.TEXT, ProviderCapability.VISION,
                ProviderCapability.STREAMING, ProviderCapability.TOOLS,
                ProviderCapability.REASONING
            ),
            docsUrl = "https://docs.anthropic.com",
            privacyUrl = "https://www.anthropic.com/privacy"
        ))

        add(ProviderDefinition(
            id = "deepseek",
            displayName = "DeepSeek",
            description = "Cost-effective coding and reasoning models",
            defaultEndpoint = "https://api.deepseek.com/v1",
            authType = AuthType.BEARER_TOKEN,
            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf("deepseek-chat", "deepseek-reasoner"),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.STREAMING, ProviderCapability.MODEL_LISTING),
            docsUrl = "https://api-docs.deepseek.com",
            privacyUrl = "https://www.deepseek.com/privacy"
        ))

        add(ProviderDefinition(
            id = "mistral",
            displayName = "Mistral AI",
            description = "Efficient European AI — coding and multilingual tasks",
            defaultEndpoint = "https://api.mistral.ai/v1",
            authType = AuthType.BEARER_TOKEN,
            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf("mistral-large-latest", "codestral-latest"),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.STREAMING, ProviderCapability.TOOLS, ProviderCapability.MODEL_LISTING),
            docsUrl = "https://docs.mistral.ai",
            privacyUrl = "https://mistral.ai/privacy"
        ))

        add(ProviderDefinition(
            id = "groq",
            displayName = "Groq",
            description = "Ultra-fast inference for open-source models",
            defaultEndpoint = "https://api.groq.com/openai/v1",
            authType = AuthType.BEARER_TOKEN,
            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf("llama-3.3-70b-versatile", "llama-3.1-8b-instant"),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.STREAMING, ProviderCapability.MODEL_LISTING),
            docsUrl = "https://console.groq.com/docs",
            privacyUrl = "https://groq.com/privacy"
        ))

        add(ProviderDefinition(
            id = "xai",
            displayName = "xAI",
            description = "Grok models — real-time knowledge and coding",
            defaultEndpoint = "https://api.x.ai/v1",
            authType = AuthType.BEARER_TOKEN,
            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf("grok-3", "grok-3-mini"),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.STREAMING, ProviderCapability.MODEL_LISTING),
            docsUrl = "https://docs.x.ai",
            privacyUrl = "https://x.ai/privacy"
        ))

        add(ProviderDefinition(
            id = "together",
            displayName = "Together AI",
            description = "Open-source model hosting and fine-tuning",
            defaultEndpoint = "https://api.together.xyz/v1",
            authType = AuthType.BEARER_TOKEN,
            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf("meta-llama/Llama-3.3-70B-Instruct-Turbo"),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.STREAMING, ProviderCapability.MODEL_LISTING),
            docsUrl = "https://docs.together.ai",
            privacyUrl = "https://www.together.ai/privacy"
        ))

        add(ProviderDefinition(
            id = "fireworks",
            displayName = "Fireworks AI",
            description = "Fast inference for open-source models",
            defaultEndpoint = "https://api.fireworks.ai/inference/v1",
            authType = AuthType.BEARER_TOKEN,
            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf("accounts/fireworks/models/llama-v3p3-70b-instruct"),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.STREAMING, ProviderCapability.MODEL_LISTING),
            docsUrl = "https://docs.fireworks.ai",
            privacyUrl = "https://fireworks.ai/privacy"
        ))

        add(ProviderDefinition(
            id = "cerebras",
            displayName = "Cerebras",
            description = "Ultra-fast inference with wafer-scale engines",
            defaultEndpoint = "https://api.cerebras.ai/v1",
            authType = AuthType.BEARER_TOKEN,
            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf("llama-3.3-70b"),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.STREAMING, ProviderCapability.MODEL_LISTING),
            docsUrl = "https://docs.cerebras.ai",
            privacyUrl = "https://www.cerebras.ai/privacy"
        ))

        add(ProviderDefinition(
            id = "cohere",
            displayName = "Cohere",
            description = "Command R models — enterprise-grade RAG and reasoning",
            defaultEndpoint = "https://api.cohere.com/v2",
            authType = AuthType.BEARER_TOKEN,
            protocol = ProviderProtocol.COHERE,
            defaultModels = listOf("command-r-plus", "command-r"),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.STREAMING, ProviderCapability.TOOLS, ProviderCapability.EMBEDDINGS, ProviderCapability.MODEL_LISTING),
            docsUrl = "https://docs.cohere.com",
            privacyUrl = "https://cohere.com/privacy"
        ))

        add(ProviderDefinition(
            id = "perplexity",
            displayName = "Perplexity",
            description = "Online models with real-time web search",
            defaultEndpoint = "https://api.perplexity.ai",
            authType = AuthType.BEARER_TOKEN,
            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf("sonar-pro", "sonar"),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.STREAMING),
            docsUrl = "https://docs.perplexity.ai",
            privacyUrl = "https://www.perplexity.ai/privacy"
        ))

        add(ProviderDefinition(
            id = "huggingface",
            displayName = "Hugging Face",
            description = "Inference API for 100k+ open-source models",
            defaultEndpoint = "https://api-inference.huggingface.co",
            authType = AuthType.BEARER_TOKEN,
            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf("meta-llama/Llama-3.3-70B-Instruct"),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.EMBEDDINGS),
            docsUrl = "https://huggingface.co/docs/api-inference",
            privacyUrl = "https://huggingface.co/privacy"
        ))

        add(ProviderDefinition(
            id = "nvidia_nim",
            displayName = "NVIDIA NIM",
            description = "High-performance inference for enterprise workloads",
            defaultEndpoint = "https://integrate.api.nvidia.com/v1",
            authType = AuthType.BEARER_TOKEN,
            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf("nvidia/nemotron-3.5-lightning-30b-a3b", "meta/llama-3.3-70b-instruct"),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.STREAMING, ProviderCapability.MODEL_LISTING),
            docsUrl = "https://docs.nvidia.com/nim",
            privacyUrl = "https://www.nvidia.com/en-us/about-nvidia/privacy-policy"
        ))

        add(ProviderDefinition(
            id = "ollama",
            displayName = "Ollama",
            description = "Run models locally on your device — no API key needed",
            defaultEndpoint = "http://localhost:11434",
            authType = AuthType.NONE,
            protocol = ProviderProtocol.LOCAL_OLLAMA,
            defaultModels = listOf("llama3.3", "qwen2.5-coder"),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.STREAMING, ProviderCapability.MODEL_LISTING),
            docsUrl = "https://ollama.com",
            privacyUrl = "https://ollama.com/privacy",
            supportsCustomEndpoint = true
        ))

        add(ProviderDefinition(
            id = "lm_studio",
            displayName = "LM Studio",
            description = "Local OpenAI-compatible server for GGUF models",
            defaultEndpoint = "http://localhost:1234/v1",
            authType = AuthType.NONE,
            protocol = ProviderProtocol.LOCAL_LM_STUDIO,
            defaultModels = emptyList(),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.STREAMING, ProviderCapability.MODEL_LISTING),
            docsUrl = "https://lmstudio.ai/docs",
            privacyUrl = "https://lmstudio.ai/privacy",
            supportsCustomEndpoint = true
        ))

        add(ProviderDefinition(
            id = "sarvam",
            displayName = "Sarvam AI",
            description = "Indian-language assistance, translation, language understanding",
            defaultEndpoint = "https://api.sarvam.ai",
            authType = AuthType.CUSTOM_HEADER,
            authHeaderName = "api-subscription-key",
            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf("sarvam-105b", "glm5.2", "gemma4"),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.STREAMING, ProviderCapability.MODEL_LISTING),
            docsUrl = "https://sarvam.ai/docs",
            privacyUrl = "https://sarvam.ai/privacy"
        ))

        add(ProviderDefinition(
            id = "opencode_zen",
            displayName = "OpenCode Zen",
            description = "Coding, agentic coding, code repair, build-error reasoning",
            defaultEndpoint = "https://opencode.ai/zen/v1",
            authType = AuthType.BEARER_TOKEN,
            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
            defaultModels = listOf("big-pickle"),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.STREAMING, ProviderCapability.MODEL_LISTING),
            docsUrl = "https://opencode.ai/docs",
            privacyUrl = "https://opencode.ai/privacy"
        ))

        // Custom / OpenAI-compatible — PRD §12 (always available)
        add(ProviderDefinition(
            id = "custom",
            displayName = "Custom Provider",
            description = "Connect any OpenAI-compatible endpoint",
            defaultEndpoint = "",
            authType = AuthType.BEARER_TOKEN,
            protocol = ProviderProtocol.OPENAI_COMPATIBLE,
            defaultModels = emptyList(),
            capabilities = setOf(ProviderCapability.TEXT, ProviderCapability.STREAMING, ProviderCapability.MODEL_LISTING),
            isBuiltIn = true,
            supportsCustomEndpoint = true
        ))
    }
}
