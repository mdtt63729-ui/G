package com.gitofy.ai.provider.client

import com.gitofy.ai.provider.AiHttpTransport
import com.gitofy.ai.provider.ProviderHttpException
import com.gitofy.ai.provider.registry.ProviderInstance
import com.gitofy.ai.provider.registry.ProviderProtocol
import org.json.JSONArray
import org.json.JSONObject
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Universal AI Provider Support — PRD §5.2 (Gateway routing).
 *
 * Chat-completion client for provider INSTANCES that Gito AI's curated
 * AIModelCatalog has no hand-picked models for (Anthropic, DeepSeek,
 * Mistral, Groq, xAI, Together, Fireworks, Cerebras, Cohere, Perplexity,
 * HuggingFace, Ollama, LM Studio, and Custom endpoints).
 *
 * Routes purely by [ProviderProtocol] — never by a per-provider hardcoded
 * branch — reusing the exact request shapes already proven in
 * [com.gitofy.ai.provider.client.ApiProviderClient]'s testConnection/
 * discoverModels. This is what closes the gap described in the PRD: a
 * validated key for any of the 20 providers now works in chat, not just the
 * 6 that had curated catalog entries.
 */
data class InstanceChatRequest(
    val instance: ProviderInstance,
    val apiKey: String,
    val modelId: String,
    val protocol: ProviderProtocol,
    val systemPrompt: String,
    val userPrompt: String
)

data class InstanceChatResponse(val content: String, val tokensUsed: Int)

@Singleton
class InstanceChatClient @Inject constructor(
    private val transport: AiHttpTransport
) {

    suspend fun stream(
        request: InstanceChatRequest,
        onChunk: (String) -> Unit
    ): Result<InstanceChatResponse> = runCatching {
        when (request.protocol) {
            ProviderProtocol.OPENAI_COMPATIBLE -> openAiCompatibleStream(request, onChunk)
            ProviderProtocol.LOCAL_LM_STUDIO -> openAiCompatibleStream(request, onChunk) // LM Studio speaks OpenAI-compatible SSE
            ProviderProtocol.ANTHROPIC -> anthropicStream(request, onChunk)
            ProviderProtocol.COHERE -> cohereStream(request, onChunk)
            ProviderProtocol.LOCAL_OLLAMA -> ollamaStream(request, onChunk)
            ProviderProtocol.GEMINI -> throw ProviderHttpException(400, "Gemini instances use the built-in Gemini provider")
        }
    }

    // ── OPENAI_COMPATIBLE — DeepSeek, Mistral, Groq, xAI, Together, ────────
    //    Fireworks, Cerebras, Perplexity, HuggingFace (TGI), Custom,
    //    and LM Studio (also OpenAI-compatible on the wire) ────────────────
    private suspend fun openAiCompatibleStream(
        request: InstanceChatRequest,
        onChunk: (String) -> Unit
    ): InstanceChatResponse {
        val base = request.instance.endpoint.ifBlank { "https://api.openai.com/v1" }.trimEnd('/')
        val url = if (base.endsWith("/chat/completions")) base else "$base/chat/completions"
        val body = JSONObject().apply {
            put("model", request.modelId)
            put("messages", JSONArray().apply {
                if (request.systemPrompt.isNotBlank()) put(JSONObject().put("role", "system").put("content", request.systemPrompt))
                put(JSONObject().put("role", "user").put("content", request.userPrompt))
            })
            put("temperature", 0.7)
            put("max_tokens", 4000)
            put("stream", true)
        }.toString()
        val headers = mapOf(
            "Authorization" to "Bearer ${request.apiKey}",
            "Accept" to "text/event-stream",
            "Cache-Control" to "no-cache"
        ) + request.instance.customHeaders

        val full = StringBuilder()
        val result = transport.stream(url, headers, body) { data ->
            if (data != "[DONE]") runCatching {
                val delta = JSONObject(data).optJSONArray("choices")
                    ?.optJSONObject(0)?.optJSONObject("delta")?.optString("content").orEmpty()
                if (delta.isNotEmpty()) { full.append(delta); onChunk(delta) }
            }
        }
        if (result.code !in 200..299) throw ProviderHttpException(result.code, extractError(result.body))
        if (full.isBlank()) throw ProviderHttpException(result.code, "${request.instance.displayName} returned an empty stream")
        return InstanceChatResponse(full.toString(), estimateTokens(full.toString()))
    }

    // ── ANTHROPIC ────────────────────────────────────────────────────────
    private suspend fun anthropicStream(
        request: InstanceChatRequest,
        onChunk: (String) -> Unit
    ): InstanceChatResponse {
        val base = request.instance.endpoint.ifBlank { "https://api.anthropic.com/v1" }.trimEnd('/')
        val url = if (base.endsWith("/messages")) base else "$base/messages"
        val body = JSONObject().apply {
            put("model", request.modelId)
            put("max_tokens", 4000)
            if (request.systemPrompt.isNotBlank()) put("system", request.systemPrompt)
            put("messages", JSONArray().put(JSONObject().put("role", "user").put("content", request.userPrompt)))
            put("stream", true)
        }.toString()
        val headers = mapOf(
            "x-api-key" to request.apiKey,
            "anthropic-version" to "2023-06-01",
            "Accept" to "text/event-stream"
        ) + request.instance.customHeaders

        val full = StringBuilder()
        val result = transport.stream(url, headers, body) { data ->
            runCatching {
                val json = JSONObject(data)
                if (json.optString("type") == "content_block_delta") {
                    val text = json.optJSONObject("delta")?.optString("text").orEmpty()
                    if (text.isNotEmpty()) { full.append(text); onChunk(text) }
                }
            }
        }
        if (result.code !in 200..299) throw ProviderHttpException(result.code, extractError(result.body))
        if (full.isBlank()) throw ProviderHttpException(result.code, "Anthropic returned an empty stream")
        return InstanceChatResponse(full.toString(), estimateTokens(full.toString()))
    }

    // ── COHERE ───────────────────────────────────────────────────────────
    private suspend fun cohereStream(
        request: InstanceChatRequest,
        onChunk: (String) -> Unit
    ): InstanceChatResponse {
        val base = request.instance.endpoint.ifBlank { "https://api.cohere.com/v2" }.trimEnd('/')
        val url = if (base.endsWith("/chat")) base else "$base/chat"
        val messages = JSONArray().apply {
            if (request.systemPrompt.isNotBlank()) put(JSONObject().put("role", "system").put("content", request.systemPrompt))
            put(JSONObject().put("role", "user").put("content", request.userPrompt))
        }
        val body = JSONObject().apply {
            put("model", request.modelId)
            put("messages", messages)
            put("stream", true)
        }.toString()
        val headers = mapOf(
            "Authorization" to "Bearer ${request.apiKey}",
            "Accept" to "text/event-stream"
        ) + request.instance.customHeaders

        val full = StringBuilder()
        val result = transport.stream(url, headers, body) { data ->
            runCatching {
                val json = JSONObject(data)
                if (json.optString("type") == "content-delta") {
                    val text = json.optJSONObject("delta")
                        ?.optJSONObject("message")
                        ?.optJSONObject("content")
                        ?.optString("text").orEmpty()
                    if (text.isNotEmpty()) { full.append(text); onChunk(text) }
                }
            }
        }
        if (result.code !in 200..299) throw ProviderHttpException(result.code, extractError(result.body))
        if (full.isBlank()) throw ProviderHttpException(result.code, "Cohere returned an empty stream")
        return InstanceChatResponse(full.toString(), estimateTokens(full.toString()))
    }

    // ── LOCAL_OLLAMA — no API key; NDJSON, not SSE ──────────────────────
    private suspend fun ollamaStream(
        request: InstanceChatRequest,
        onChunk: (String) -> Unit
    ): InstanceChatResponse {
        val base = request.instance.endpoint.ifBlank { "http://localhost:11434" }.trimEnd('/')
        val url = "$base/api/chat"
        val messages = JSONArray().apply {
            if (request.systemPrompt.isNotBlank()) put(JSONObject().put("role", "system").put("content", request.systemPrompt))
            put(JSONObject().put("role", "user").put("content", request.userPrompt))
        }
        val body = JSONObject().apply {
            put("model", request.modelId)
            put("messages", messages)
            put("stream", true)
        }.toString()

        val full = StringBuilder()
        val result = transport.streamRaw(url, request.instance.customHeaders, body) { line ->
            runCatching {
                val json = JSONObject(line)
                val text = json.optJSONObject("message")?.optString("content").orEmpty()
                if (text.isNotEmpty()) { full.append(text); onChunk(text) }
            }
        }
        if (result.code !in 200..299) throw ProviderHttpException(result.code, extractError(result.body))
        if (full.isBlank()) throw ProviderHttpException(result.code, "Ollama returned an empty stream — is it running with the model pulled?")
        return InstanceChatResponse(full.toString(), estimateTokens(full.toString()))
    }

    private fun extractError(body: String): String = runCatching {
        val root = JSONObject(body)
        root.optJSONObject("error")?.let { error ->
            error.optString("message").takeIf { it.isNotBlank() } ?: error.toString()
        } ?: root.optString("message").takeIf { it.isNotBlank() } ?: body.take(500)
    }.getOrElse { body.take(500).ifBlank { "Provider request failed" } }

    private fun estimateTokens(text: String): Int = maxOf(1, text.length / 4)
}
