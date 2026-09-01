package com.gitofy.ai.provider

import com.gitofy.ai.credentials.AiCredentialStore
import com.gitofy.ai.credentials.AiProvider
import com.gitofy.ai.credentials.CustomProviderConfig
import com.gitofy.ai.model.AIConfidenceLevel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONObject
import java.io.IOException
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/** Production HTTP transport shared by all first-party BYOK providers. */
@Singleton
class AiHttpTransport @Inject constructor(@com.gitofy.core.network.AiHttpClient private val client: OkHttpClient) {
    private val jsonMediaType = "application/json; charset=utf-8".toMediaType()

    suspend fun execute(
        url: String,
        headers: Map<String, String>,
        body: String
    ): HttpResult = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).post(body.toRequestBody(jsonMediaType)).apply {
            headers.forEach { (key, value) -> if (value.isNotBlank()) header(key, value) }
        }.build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                response.use {
                    val text = it.body?.string().orEmpty()
                    if (continuation.isActive) continuation.resumeWith(Result.success(HttpResult(it.code, text)))
                }
            }
        })
    }

    suspend fun stream(
        url: String,
        headers: Map<String, String>,
        body: String,
        onLine: (String) -> Unit
    ): HttpResult = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).post(body.toRequestBody(jsonMediaType)).apply {
            headers.forEach { (key, value) -> if (value.isNotBlank()) header(key, value) }
        }.build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) {
                            val bodyText = it.body?.string().orEmpty()
                            if (continuation.isActive) continuation.resumeWith(Result.success(HttpResult(it.code, bodyText)))
                            return
                        }
                        val source = it.body?.charStream()?.buffered() ?: throw IOException("Empty streaming response")
                        source.use { reader ->
                            while (true) {
                                if (!continuation.isActive) { call.cancel(); return }
                                val line = reader.readLine() ?: break
                                if (line.startsWith("data:")) {
                                    onLine(line.removePrefix("data:").trim())
                                }
                            }
                        }
                        if (continuation.isActive) continuation.resumeWith(Result.success(HttpResult(it.code, "")))
                    }
                } catch (t: Throwable) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(t))
                }
            }
        })
    }

    /**
     * Like [stream], but forwards every non-blank line as-is instead of only
     * lines prefixed with "data:". Needed for NDJSON streaming protocols
     * (e.g. Ollama's /api/chat), which emit one raw JSON object per line
     * rather than SSE-formatted "data:" frames.
     */
    suspend fun streamRaw(
        url: String,
        headers: Map<String, String>,
        body: String,
        onLine: (String) -> Unit
    ): HttpResult = kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
        val request = Request.Builder().url(url).post(body.toRequestBody(jsonMediaType)).apply {
            headers.forEach { (key, value) -> if (value.isNotBlank()) header(key, value) }
        }.build()
        val call = client.newCall(request)
        continuation.invokeOnCancellation { call.cancel() }
        call.enqueue(object : okhttp3.Callback {
            override fun onFailure(call: okhttp3.Call, e: IOException) {
                if (continuation.isActive) continuation.resumeWith(Result.failure(e))
            }
            override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                try {
                    response.use {
                        if (!it.isSuccessful) {
                            val bodyText = it.body?.string().orEmpty()
                            if (continuation.isActive) continuation.resumeWith(Result.success(HttpResult(it.code, bodyText)))
                            return
                        }
                        val source = it.body?.charStream()?.buffered() ?: throw IOException("Empty streaming response")
                        source.use { reader ->
                            while (true) {
                                if (!continuation.isActive) { call.cancel(); return }
                                val line = reader.readLine() ?: break
                                if (line.isNotBlank()) onLine(line)
                            }
                        }
                        if (continuation.isActive) continuation.resumeWith(Result.success(HttpResult(it.code, "")))
                    }
                } catch (t: Throwable) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(t))
                }
            }
        })
    }

    data class HttpResult(val code: Int, val body: String)
}

abstract class BaseRealProvider(
    protected val credentials: AiCredentialStore,
    protected val transport: AiHttpTransport
) : AIProvider {
    protected suspend fun apiKey(provider: AiProvider): String =
        credentials.getCredential(provider)?.encryptedApiKey?.toString(Charsets.UTF_8)
            ?.takeIf { it.isNotBlank() }
            ?: throw ProviderHttpException(401, "${provider.displayName} API key is not configured")

    protected fun ensureSuccess(result: AiHttpTransport.HttpResult) {
        if (result.code !in 200..299) throw ProviderHttpException(result.code, extractError(result.body))
    }

    protected fun extractError(body: String): String = runCatching {
        val root = JSONObject(body)
        root.optJSONObject("error")?.let { error ->
            error.optString("message").takeIf { it.isNotBlank() } ?: error.toString()
        } ?: root.optString("message").takeIf { it.isNotBlank() } ?: body.take(500)
    }.getOrElse { body.take(500).ifBlank { "Provider request failed" } }

    protected fun normalize(content: String, tokens: Int = estimateTokens(content)) =
        AIProvider.GenerateResponse(content = content, tokensUsed = tokens, confidence = AIConfidenceLevel.HIGH)

    protected fun estimateTokens(text: String): Int = maxOf(1, text.length / 4)

    protected data class UsageMetrics(val totalTokens: Int, val reasoningTokens: Int)

    protected fun parseUsageMetrics(root: JSONObject, fallbackText: String): UsageMetrics {
        val usage = root.optJSONObject("usage")
        val total = usage?.optInt("total_tokens", -1)?.takeIf { it >= 0 }
            ?: usage?.optInt("totalTokens", -1)?.takeIf { it >= 0 }
            ?: estimateTokens(fallbackText)
        val details = usage?.optJSONObject("completion_tokens_details")
        val reasoning = details?.optInt("reasoning_tokens", -1)?.takeIf { it >= 0 }
            ?: details?.optInt("reasoningTokens", -1)?.takeIf { it >= 0 }
            ?: 0
        return UsageMetrics(total, reasoning)
    }

    protected fun parseUsage(root: JSONObject, fallbackText: String): Int =
        parseUsageMetrics(root, fallbackText).totalTokens

    // FIX: JSONObject(body) throws org.json's raw, developer-facing message
    // ("Value ... of type ... cannot be converted to JSONObject") whenever a
    // 2xx response body isn't actually a JSON object — e.g. an upstream
    // proxy/CDN returning a plain-text or HTML body on a transient fault, or
    // a truncated response. That raw exception was surfacing verbatim in the
    // chat error banner, unreadable to the user. Parse defensively here so
    // every provider gets one clear, actionable message instead.
    protected fun safeJsonObject(body: String): JSONObject {
        if (body.isBlank()) throw ProviderHttpException(0, "The provider returned an empty response.")
        return try {
            JSONObject(body)
        } catch (e: Exception) {
            throw ProviderHttpException(0, "The provider returned an unreadable response: ${body.take(200)}")
        }
    }

    protected fun parseOpenAiContent(body: String): Pair<String, Int> {
        val root = safeJsonObject(body)
        val choices = root.optJSONArray("choices")
        val message = choices?.optJSONObject(0)?.optJSONObject("message")
        val content = message?.optString("content").orEmpty()
        return content to parseUsage(root, content)
    }

    protected fun openAiBody(request: AIProvider.GenerateRequest, stream: Boolean = false, completionTokenField: String = "max_tokens"): String =
        JSONObject().apply {
            put("model", request.modelId)
            put("messages", JSONArray().apply {
                if (request.systemPrompt.isNotBlank()) put(JSONObject().put("role", "system").put("content", request.systemPrompt))
                if (request.context.isNotBlank()) put(JSONObject().put("role", "user").put("content", "Context:\n${request.context}"))
                put(JSONObject().put("role", "user").put("content", request.prompt))
            })
            put("temperature", request.temperature.toDouble())
            put(completionTokenField, request.maxOutputTokens)
            put("stream", stream)
        }.toString()

    protected suspend fun openAiGenerate(
        endpoint: String,
        provider: AiProvider,
        request: AIProvider.GenerateRequest,
        extraHeaders: Map<String, String> = emptyMap()
    ): Result<AIProvider.GenerateResponse> = runCatching {
        val key = apiKey(provider)
        val result = transport.execute(
            endpoint,
            mapOf("Authorization" to "Bearer $key", "Accept" to "application/json") + extraHeaders,
            openAiBody(request, completionTokenField = if (request.modelId.startsWith("gpt-5") || request.modelId.startsWith("o")) "max_completion_tokens" else "max_tokens")
        )
        ensureSuccess(result)
        val root = safeJsonObject(result.body)
        val choices = root.optJSONArray("choices")
        val content = choices?.optJSONObject(0)?.optJSONObject("message")?.optString("content").orEmpty()
        val usage = parseUsageMetrics(root, content)
        if (content.isBlank()) throw ProviderHttpException(result.code, "Provider returned an empty response")
        AIProvider.GenerateResponse(content, tokensUsed = usage.totalTokens, reasoningTokens = usage.reasoningTokens, confidence = AIConfidenceLevel.HIGH)
    }

    protected suspend fun openAiStream(
        endpoint: String,
        provider: AiProvider,
        request: AIProvider.GenerateRequest,
        onChunk: (String) -> Unit,
        extraHeaders: Map<String, String> = emptyMap()
    ): Result<AIProvider.GenerateResponse> = runCatching {
        val key = apiKey(provider)
        val full = StringBuilder()
        var reasoningTokens = 0
        var reportedTotalTokens = 0
        val result = transport.stream(
            endpoint,
            mapOf("Authorization" to "Bearer $key", "Accept" to "text/event-stream", "Cache-Control" to "no-cache") + extraHeaders,
            openAiBody(request, stream = true, completionTokenField = if (request.modelId.startsWith("gpt-5") || request.modelId.startsWith("o")) "max_completion_tokens" else "max_tokens")
        ) { data ->
            if (data != "[DONE]") runCatching {
                val json = JSONObject(data)
                val delta = json.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
                if (delta.isNotEmpty()) {
                    full.append(delta)
                    onChunk(delta)
                }
                val usage = json.optJSONObject("usage")
                if (usage != null) {
                    reportedTotalTokens = usage.optInt("total_tokens", usage.optInt("totalTokens", reportedTotalTokens))
                    val details = usage.optJSONObject("completion_tokens_details")
                    reasoningTokens = details?.optInt("reasoning_tokens", details?.optInt("reasoningTokens", reasoningTokens) ?: reasoningTokens) ?: reasoningTokens
                }
            }
        }
        ensureSuccess(result)
        val content = full.toString()
        if (content.isBlank()) throw ProviderHttpException(result.code, "Provider returned an empty stream")
        AIProvider.GenerateResponse(content, tokensUsed = if (reportedTotalTokens > 0) reportedTotalTokens else estimateTokens(content), reasoningTokens = reasoningTokens, confidence = AIConfidenceLevel.HIGH)
    }

    override suspend fun healthCheck(): AIProvider.HealthStatus {
        val started = System.currentTimeMillis()
        return runCatching {
            // A minimal generation request verifies both credentials and model transport.
            val model = defaultHealthModel()
            val result = generate(AIProvider.GenerateRequest("Return only: OK", "", model, "You are a connection test.", maxOutputTokens = 8, temperature = 0f))
            if (result.isSuccess) AIProvider.HealthStatus(true, System.currentTimeMillis() - started, null)
            else AIProvider.HealthStatus(false, System.currentTimeMillis() - started, result.exceptionOrNull()?.message)
        }.getOrElse { AIProvider.HealthStatus(false, System.currentTimeMillis() - started, it.message) }
    }

    protected abstract fun defaultHealthModel(): String
}

class OpenAiProvider @Inject constructor(c: AiCredentialStore, t: AiHttpTransport) : BaseRealProvider(c, t) {
    override val providerId = "openai"
    override val displayName = "OpenAI"
    override suspend fun generate(request: AIProvider.GenerateRequest) = openAiGenerate("https://api.openai.com/v1/chat/completions", AiProvider.OPENAI, request)
    override suspend fun stream(request: AIProvider.GenerateRequest, onChunk: (String) -> Unit) = openAiStream("https://api.openai.com/v1/chat/completions", AiProvider.OPENAI, request, onChunk)
    override fun defaultHealthModel() = "gpt-5-mini"
}

class NvidiaNimProvider @Inject constructor(c: AiCredentialStore, t: AiHttpTransport) : BaseRealProvider(c, t) {
    override val providerId = "nvidia_nim"
    override val displayName = "NVIDIA NIM"
    override suspend fun generate(request: AIProvider.GenerateRequest) = openAiGenerate("https://integrate.api.nvidia.com/v1/chat/completions", AiProvider.NVIDIA_NIM, request)
    override suspend fun stream(request: AIProvider.GenerateRequest, onChunk: (String) -> Unit) = openAiStream("https://integrate.api.nvidia.com/v1/chat/completions", AiProvider.NVIDIA_NIM, request, onChunk)
    override fun defaultHealthModel() = "nvidia/llama-3.3-nemotron-super-49b-v1"
}

class OllamaProvider @Inject constructor(
    c: AiCredentialStore,
    t: AiHttpTransport,
    private val secureStorage: com.gitofy.core.security.SecureCredentialStorage
) : BaseRealProvider(c, t) {
    override val providerId = "ollama"
    override val displayName = "Ollama"

    private fun endpoint(): String = secureStorage.getProviderInstances()
        .firstOrNull { it.definitionId.equals("ollama", ignoreCase = true) }
        ?.endpoint
        ?.trim()
        ?.trimEnd('/')
        ?.takeIf { it.isNotBlank() }
        ?: "http://10.0.2.2:11434"

    override suspend fun generate(request: AIProvider.GenerateRequest): Result<AIProvider.GenerateResponse> = runCatching {
        val body = JSONObject().apply {
            put("model", request.modelId)
            put("messages", JSONArray().apply {
                if (request.systemPrompt.isNotBlank()) put(JSONObject().put("role", "system").put("content", request.systemPrompt))
                put(JSONObject().put("role", "user").put("content", request.prompt))
            })
            put("stream", false)
        }.toString()
        val result = transport.execute("${endpoint()}/api/chat", mapOf("Content-Type" to "application/json", "Accept" to "application/json"), body)
        ensureSuccess(result)
        val json = safeJsonObject(result.body)
        val content = json.optJSONObject("message")?.optString("content").orEmpty()
        if (content.isBlank()) throw ProviderHttpException(result.code, "Ollama returned an empty response")
        AIProvider.GenerateResponse(content = content, tokensUsed = estimateTokens(content))
    }

    override suspend fun stream(request: AIProvider.GenerateRequest, onChunk: (String) -> Unit): Result<AIProvider.GenerateResponse> = runCatching {
        val body = JSONObject().apply {
            put("model", request.modelId)
            put("messages", JSONArray().apply {
                if (request.systemPrompt.isNotBlank()) put(JSONObject().put("role", "system").put("content", request.systemPrompt))
                put(JSONObject().put("role", "user").put("content", request.prompt))
            })
            put("stream", true)
        }.toString()
        val full = StringBuilder()
        val result = transport.streamRaw("${endpoint()}/api/chat", mapOf("Content-Type" to "application/json", "Accept" to "application/x-ndjson"), body) { line ->
            runCatching {
                val text = JSONObject(line).optJSONObject("message")?.optString("content").orEmpty()
                if (text.isNotEmpty()) { full.append(text); onChunk(text) }
            }
        }
        ensureSuccess(result)
        if (full.isBlank()) throw ProviderHttpException(result.code, "Ollama returned an empty stream")
        AIProvider.GenerateResponse(content = full.toString(), tokensUsed = estimateTokens(full.toString()))
    }

    override fun defaultHealthModel() = "llama3.2"

    override suspend fun healthCheck(): AIProvider.HealthStatus = runCatching {
        val start = System.currentTimeMillis()
        val result = transport.execute("${endpoint()}/api/tags", emptyMap(), "{}")
        AIProvider.HealthStatus(result.code in 200..299, System.currentTimeMillis() - start, if (result.code in 200..299) null else "HTTP ${result.code}")
    }.getOrElse { AIProvider.HealthStatus(false, null, it.message ?: "Ollama unavailable") }
}

class OpenRouterProvider @Inject constructor(c: AiCredentialStore, t: AiHttpTransport) : BaseRealProvider(c, t) {
    override val providerId = "openrouter"
    override val displayName = "OpenRouter"
    override suspend fun generate(request: AIProvider.GenerateRequest) = openAiGenerate("https://openrouter.ai/api/v1/chat/completions", AiProvider.OPENROUTER, request, mapOf("HTTP-Referer" to "https://github.com/gitofy", "X-Title" to "GITOFY"))
    override suspend fun stream(request: AIProvider.GenerateRequest, onChunk: (String) -> Unit) = openAiStream("https://openrouter.ai/api/v1/chat/completions", AiProvider.OPENROUTER, request, onChunk, mapOf("HTTP-Referer" to "https://github.com/gitofy", "X-Title" to "GITOFY"))
    override fun defaultHealthModel() = "deepseek/deepseek-chat"
}

class OpenCodeZenProvider @Inject constructor(c: AiCredentialStore, t: AiHttpTransport) : BaseRealProvider(c, t) {
    override val providerId = "opencode_zen"
    override val displayName = "OpenCode Zen"
    override suspend fun generate(request: AIProvider.GenerateRequest) = openAiGenerate("https://opencode.ai/zen/v1/chat/completions", AiProvider.OPENCODE_ZEN, request)
    override suspend fun stream(request: AIProvider.GenerateRequest, onChunk: (String) -> Unit) = openAiStream("https://opencode.ai/zen/v1/chat/completions", AiProvider.OPENCODE_ZEN, request, onChunk)
    override fun defaultHealthModel() = "deepseek-v3.1"
}

class SarvamProvider @Inject constructor(c: AiCredentialStore, t: AiHttpTransport) : BaseRealProvider(c, t) {
    override val providerId = "sarvam"
    override val displayName = "Sarvam AI"

    enum class IndianLanguage(val displayName: String, val bcp47Code: String) {
        BENGALI("Bengali", "bn-IN"), HINDI("Hindi", "hi-IN"), TAMIL("Tamil", "ta-IN"), TELUGU("Telugu", "te-IN"),
        MARATHI("Marathi", "mr-IN"), GUJARATI("Gujarati", "gu-IN"), PUNJABI("Punjabi", "pa-IN"), KANNADA("Kannada", "kn-IN"), MALAYALAM("Malayalam", "ml-IN")
    }

    fun detectLanguage(text: String): IndianLanguage? = when {
        text.any { it.code in 0x0980..0x09FF } -> IndianLanguage.BENGALI
        text.any { it.code in 0x0900..0x097F } -> IndianLanguage.HINDI
        text.any { it.code in 0x0B80..0x0BFF } -> IndianLanguage.TAMIL
        text.any { it.code in 0x0C00..0x0C7F } -> IndianLanguage.TELUGU
        text.any { it.code in 0x0A80..0x0AFF } -> IndianLanguage.GUJARATI
        text.any { it.code in 0x0A00..0x0A7F } -> IndianLanguage.PUNJABI
        text.any { it.code in 0x0C80..0x0CFF } -> IndianLanguage.KANNADA
        text.any { it.code in 0x0D00..0x0D7F } -> IndianLanguage.MALAYALAM
        else -> null
    }

    private fun endpoint(request: AIProvider.GenerateRequest): String =
        if (request.modelId.equals("glm5.2", true) || request.modelId.equals("gemma4", true))
            "https://api.sarvam.ai/v2/chat/completions"
        else "https://api.sarvam.ai/v1/chat/completions"

    private fun headers(key: String) = mapOf("api-subscription-key" to key, "Accept" to "application/json")

    override suspend fun generate(request: AIProvider.GenerateRequest): Result<AIProvider.GenerateResponse> = runCatching {
        val key = apiKey(AiProvider.SARVAM)
        val result = transport.execute(endpoint(request), headers(key), openAiBody(request))
        ensureSuccess(result)
        val (content, tokens) = parseOpenAiContent(result.body)
        if (content.isBlank()) throw ProviderHttpException(result.code, "Sarvam returned an empty response")
        AIProvider.GenerateResponse(content, tokensUsed = tokens, confidence = AIConfidenceLevel.HIGH)
    }

    override suspend fun stream(request: AIProvider.GenerateRequest, onChunk: (String) -> Unit): Result<AIProvider.GenerateResponse> = runCatching {
        val key = apiKey(AiProvider.SARVAM)
        val full = StringBuilder()
        val result = transport.stream(endpoint(request), headers(key) + ("Accept" to "text/event-stream"), openAiBody(request, true)) { data ->
            if (data != "[DONE]") runCatching {
                val delta = JSONObject(data).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
                if (delta.isNotEmpty()) { full.append(delta); onChunk(delta) }
            }
        }
        ensureSuccess(result)
        if (full.isBlank()) throw ProviderHttpException(result.code, "Sarvam returned an empty stream")
        normalize(full.toString())
    }

    override fun defaultHealthModel() = "sarvam-105b"
}

class GeminiProvider @Inject constructor(c: AiCredentialStore, t: AiHttpTransport) : BaseRealProvider(c, t) {
    override val providerId = "gemini"
    override val displayName = "Gemini"

    private fun body(request: AIProvider.GenerateRequest): String = JSONObject().apply {
        if (request.systemPrompt.isNotBlank()) put("systemInstruction", JSONObject().put("parts", JSONArray().put(JSONObject().put("text", request.systemPrompt))))
        val user = buildString {
            if (request.context.isNotBlank()) append("Context:\n").append(request.context).append("\n\n")
            append(request.prompt)
        }
        put("contents", JSONArray().put(JSONObject().put("role", "user").put("parts", JSONArray().put(JSONObject().put("text", user)))))
        put("generationConfig", JSONObject().put("temperature", request.temperature.toDouble()).put("maxOutputTokens", request.maxOutputTokens))
    }.toString()

    private fun endpoint(request: AIProvider.GenerateRequest, stream: Boolean): String =
        "https://generativelanguage.googleapis.com/v1beta/models/${request.modelId}:${if (stream) "streamGenerateContent" else "generateContent"}?alt=sse"

    private fun parseGemini(body: String): Pair<String, Int> {
        val root = safeJsonObject(body)
        val candidates = root.optJSONArray("candidates")
        val parts = candidates?.optJSONObject(0)?.optJSONObject("content")?.optJSONArray("parts")
        val text = parts?.let { (0 until it.length()).joinToString("") { i -> it.optJSONObject(i)?.optString("text").orEmpty() } }.orEmpty()
        val tokens = root.optJSONObject("usageMetadata")?.optInt("totalTokenCount", -1)?.takeIf { it >= 0 } ?: estimateTokens(text)
        return text to tokens
    }

    override suspend fun generate(request: AIProvider.GenerateRequest): Result<AIProvider.GenerateResponse> = runCatching {
        val key = apiKey(AiProvider.GEMINI)
        val url = endpoint(request, false)
        val result = transport.execute(url, mapOf("Content-Type" to "application/json", "x-goog-api-key" to key), body(request))
        ensureSuccess(result)
        val (content, tokens) = parseGemini(result.body)
        if (content.isBlank()) throw ProviderHttpException(result.code, "Gemini returned an empty response")
        AIProvider.GenerateResponse(content, tokensUsed = tokens, confidence = AIConfidenceLevel.HIGH)
    }

    override suspend fun stream(request: AIProvider.GenerateRequest, onChunk: (String) -> Unit): Result<AIProvider.GenerateResponse> = runCatching {
        val key = apiKey(AiProvider.GEMINI)
        val full = StringBuilder()
        val url = endpoint(request, true)
        val result = transport.stream(url, mapOf("Content-Type" to "application/json", "Accept" to "text/event-stream", "x-goog-api-key" to key), body(request)) { data ->
            if (data.isNotBlank()) runCatching {
                val (text, _) = parseGemini(data)
                if (text.isNotEmpty()) { full.append(text); onChunk(text) }
            }
        }
        ensureSuccess(result)
        if (full.isBlank()) throw ProviderHttpException(result.code, "Gemini returned an empty stream")
        normalize(full.toString())
    }

    override fun defaultHealthModel() = "gemini-2.5-flash"
}

class CustomProvider @Inject constructor(c: AiCredentialStore, t: AiHttpTransport) : BaseRealProvider(c, t) {
    override val providerId = "custom"
    override val displayName = "Custom Provider"

    private suspend fun config(): Pair<String, CustomProviderConfig> {
        val credential = credentials.getCredential(AiProvider.CUSTOM)
            ?: throw ProviderHttpException(401, "Custom provider is not configured")
        val cfg = credential.customConfig
            ?: throw ProviderHttpException(400, "Custom provider configuration is incomplete")
        return credential.encryptedApiKey.toString(Charsets.UTF_8) to cfg
    }

    private fun chatEndpoint(base: String): String = base.trimEnd('/').let { if (it.endsWith("/chat/completions")) it else "$it/chat/completions" }

    override suspend fun generate(request: AIProvider.GenerateRequest): Result<AIProvider.GenerateResponse> = runCatching {
        val (key, cfg) = config()
        val headers = buildMap {
            put("Accept", "application/json")
            when (cfg.name.lowercase()) { else -> put("Authorization", "Bearer $key") }
            putAll(cfg.customHeaders)
        }
        val result = transport.execute(chatEndpoint(cfg.baseUrl), headers, openAiBody(request.copy(modelId = cfg.modelId)))
        ensureSuccess(result)
        val (content, tokens) = parseOpenAiContent(result.body)
        if (content.isBlank()) throw ProviderHttpException(result.code, "Custom provider returned an empty response")
        AIProvider.GenerateResponse(content, tokensUsed = tokens, confidence = AIConfidenceLevel.HIGH)
    }

    override suspend fun stream(request: AIProvider.GenerateRequest, onChunk: (String) -> Unit): Result<AIProvider.GenerateResponse> = runCatching {
        val (key, cfg) = config()
        if (!cfg.customHeaders.containsKey("Authorization")) cfg.customHeaders.toMutableMap().apply { put("Authorization", "Bearer $key") }
        val headers = mapOf("Authorization" to "Bearer $key", "Accept" to "text/event-stream") + cfg.customHeaders
        val full = StringBuilder()
        val result = transport.stream(chatEndpoint(cfg.baseUrl), headers, openAiBody(request.copy(modelId = cfg.modelId), true)) { data ->
            if (data != "[DONE]") runCatching {
                val delta = JSONObject(data).optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
                if (delta.isNotEmpty()) { full.append(delta); onChunk(delta) }
            }
        }
        ensureSuccess(result)
        if (full.isBlank()) throw ProviderHttpException(result.code, "Custom provider returned an empty stream")
        normalize(full.toString())
    }

    override fun defaultHealthModel() = "custom"
}

class ProviderHttpException(val statusCode: Int, message: String) : IOException("$statusCode: $message")
