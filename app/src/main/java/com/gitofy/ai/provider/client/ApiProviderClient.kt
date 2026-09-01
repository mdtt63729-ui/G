package com.gitofy.ai.provider.client

import com.gitofy.ai.provider.registry.AuthType
import com.gitofy.ai.provider.registry.ProviderDefinition
import com.gitofy.ai.provider.registry.ProviderInstance
import com.gitofy.ai.provider.registry.ProviderProtocol
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §41 — API Client Architecture.
 *
 * Abstraction over provider-specific networking so Settings UI never has
 * provider-specific HTTP code.  Uses a generic OpenAI-compatible client for
 * the majority of providers and delegates to specialised methods only where
 * the protocol genuinely differs (Gemini, local Ollama, etc.).
 *
 * All operations execute on Dispatchers.IO (PRD §35) and NEVER expose the API
 * key in results or errors (PRD §14, §43).
 */

/** Normalized test-connection result — PRD §15. */
sealed class ApiTestResult {
    data object Idle : ApiTestResult()
    data object Testing : ApiTestResult()
    data class Success(val message: String, val modelCount: Int? = null) : ApiTestResult()
    data class Failed(val message: String) : ApiTestResult()
}

/** Model discovered from a provider's model-listing endpoint — PRD §17. */
data class DiscoveredModel(
    val id: String,
    val displayName: String
)

@Singleton
class ApiProviderClient @Inject constructor() {

    /**
     * Test the connection to a provider instance — PRD §15.
     *
     * Sends a minimal request, validates the response, and returns a
     * user-friendly result.  The API key is never included in the result.
     */
    fun testConnection(instance: ProviderInstance, apiKey: String): ApiTestResult {
        return try {
            val definition = STATIC_DEFINITION_MAP[instance.definitionId]
                ?: return ApiTestResult.Failed("Unknown provider type")

            when (definition.protocol) {
                ProviderProtocol.GEMINI -> testGemini(instance, apiKey)
                ProviderProtocol.OPENAI_COMPATIBLE -> {
                    if (instance.definitionId.equals("sarvam", ignoreCase = true)) {
                        testSarvam(instance, apiKey)
                    } else {
                        testOpenAiCompatible(instance, apiKey, definition)
                    }
                }
                ProviderProtocol.ANTHROPIC -> testAnthropic(instance, apiKey)
                ProviderProtocol.COHERE -> testCohere(instance, apiKey)
                ProviderProtocol.LOCAL_OLLAMA -> testLocal(
                    instance.endpoint.ifBlank { "http://localhost:11434" },
                    "/api/tags"
                )
                ProviderProtocol.LOCAL_LM_STUDIO -> testLocal(
                    instance.endpoint.ifBlank { "http://localhost:1234/v1" },
                    "/models"
                )
            }
        } catch (e: Exception) {
            ApiTestResult.Failed(e.message ?: "Connection failed")
        }
    }

    /**
     * Fetch available models from a provider — PRD §17.
     * Returns an empty list if the provider does not support model listing.
     */
    fun discoverModels(instance: ProviderInstance, apiKey: String): List<DiscoveredModel> {
        return try {
            val definition = STATIC_DEFINITION_MAP[instance.definitionId]
                ?: return emptyList()

            when (definition.protocol) {
                ProviderProtocol.GEMINI -> discoverGeminiModels(instance, apiKey)
                ProviderProtocol.OPENAI_COMPATIBLE -> discoverOpenAiModels(instance, apiKey, definition)
                ProviderProtocol.ANTHROPIC -> emptyList() // Anthropic has no public model-list endpoint
                ProviderProtocol.COHERE -> emptyList()
                ProviderProtocol.LOCAL_OLLAMA -> discoverLocal(
                    instance.endpoint.ifBlank { "http://localhost:11434" },
                    "/api/tags", "models"
                )
                ProviderProtocol.LOCAL_LM_STUDIO -> discoverLocal(
                    instance.endpoint.ifBlank { "http://localhost:1234/v1" },
                    "/models", "data"
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Test implementations ──────────────────────────────────────────────

    private fun testGemini(instance: ProviderInstance, apiKey: String): ApiTestResult {
        val endpoint = "${instance.endpoint.ifBlank { "https://generativelanguage.googleapis.com/v1beta" }.trimEnd('/')}/models?key=$apiKey"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        return parseModelsResponse(conn) { body ->
            val count = JSONObject(body).optJSONArray("models")?.length()
            ApiTestResult.Success("Connection successful", count)
        }
    }

    private fun testOpenAiCompatible(
        instance: ProviderInstance,
        apiKey: String,
        definition: ProviderDefinition
    ): ApiTestResult {
        val base = instance.endpoint.ifBlank { definition.defaultEndpoint }.trimEnd('/')
        val conn = (URL("$base/models").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            applyAuthentication(definition, apiKey)
            if (instance.definitionId == "openrouter") {
                setRequestProperty("HTTP-Referer", "https://github.com/gitofy")
                setRequestProperty("X-Title", "GITOFY")
            }
            instance.customHeaders.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) setRequestProperty(key, value)
            }
        }
        return parseModelsResponse(conn) { body ->
            val count = JSONObject(body).optJSONArray("data")?.length()
            ApiTestResult.Success("Connection successful", count)
        }
    }

    /**
     * Sarvam does not expose an OpenAI-style /models endpoint for this
     * connection flow. Test the actual chat endpoint and use Sarvam's required
     * api-subscription-key header.
     */
    private fun testSarvam(instance: ProviderInstance, apiKey: String): ApiTestResult {
        val model = instance.selectedModel?.takeIf { it.isNotBlank() } ?: "sarvam-105b"
        val version = if (model.equals("glm5.2", true) || model.equals("gemma4", true)) "v2" else "v1"
        val base = instance.endpoint
            .ifBlank { "https://api.sarvam.ai" }
            .trimEnd('/')
            .removeSuffix("/v1")
            .removeSuffix("/v2")
        val conn = (URL("$base/$version/chat/completions").openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            connectTimeout = 10_000
            readTimeout = 20_000
            doOutput = true
            setRequestProperty("Content-Type", "application/json")
            setRequestProperty("api-subscription-key", apiKey)
            instance.customHeaders.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) setRequestProperty(key, value)
            }
        }

        val body = JSONObject().apply {
            put("model", model)
            put("messages", org.json.JSONArray().put(
                JSONObject().put("role", "user").put("content", "Return only: OK")
            ))
            put("max_tokens", 10)
        }.toString()
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }

        return try {
            val code = conn.responseCode
            when (code) {
                in 200..299 -> ApiTestResult.Success("Connection successful")
                401, 403 -> ApiTestResult.Failed("Invalid Sarvam API key or unauthorized key.")
                429 -> ApiTestResult.Failed("Rate limited. Please try again later.")
                404 -> ApiTestResult.Failed("Sarvam model or endpoint is unavailable.")
                in 500..599 -> ApiTestResult.Failed("Sarvam server error. Please try again later.")
                else -> ApiTestResult.Failed("Sarvam connection failed (HTTP $code).")
            }
        } finally {
            conn.disconnect()
        }
    }

    private fun HttpURLConnection.applyAuthentication(
        definition: ProviderDefinition,
        apiKey: String
    ) {
        when (definition.authType) {
            AuthType.BEARER_TOKEN -> setRequestProperty("Authorization", "Bearer $apiKey")
            AuthType.CUSTOM_HEADER -> {
                val header = definition.authHeaderName?.takeIf { it.isNotBlank() }
                    ?: "api-key"
                setRequestProperty(header, apiKey)
            }
            AuthType.API_KEY_QUERY, AuthType.NONE -> Unit
        }
    }

    private fun testAnthropic(instance: ProviderInstance, apiKey: String): ApiTestResult {
        // Anthropic uses x-api-key + anthropic-version headers
        val base = instance.endpoint.ifBlank { "https://api.anthropic.com/v1" }
        val conn = (URL("$base/models").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("x-api-key", apiKey)
            setRequestProperty("anthropic-version", "2023-06-01")
        }
        val code = conn.responseCode
        conn.disconnect()
        return when (code) {
            in 200..299 -> ApiTestResult.Success("Connection successful")
            401, 403 -> ApiTestResult.Failed("Invalid API key. Check your Anthropic API key.")
            429 -> ApiTestResult.Failed("Rate limited. Please try again later.")
            in 500..599 -> ApiTestResult.Failed("Server error. Please try again later.")
            else -> ApiTestResult.Failed("HTTP $code")
        }
    }

    private fun testCohere(instance: ProviderInstance, apiKey: String): ApiTestResult {
        val base = instance.endpoint.ifBlank { "https://api.cohere.com/v2" }
        val conn = (URL("$base/models").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            setRequestProperty("Authorization", "Bearer $apiKey")
        }
        return parseModelsResponse(conn) { ApiTestResult.Success("Connection successful") }
    }

    private fun testLocal(baseUrl: String, path: String): ApiTestResult {
        val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 10_000
        }
        val code = conn.responseCode
        conn.disconnect()
        return if (code in 200..299) {
            ApiTestResult.Success("Connection successful")
        } else {
            ApiTestResult.Failed("Could not reach local server. Is it running?")
        }
    }

    private fun parseModelsResponse(
        conn: HttpURLConnection,
        onSuccess: (String) -> ApiTestResult
    ): ApiTestResult {
        return try {
            val code = conn.responseCode
            val body = try {
                conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
            } catch (e: Exception) {
                conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
            }
            conn.disconnect()
            when (code) {
                in 200..299 -> onSuccess(body)
                401, 403 -> ApiTestResult.Failed("Invalid API key.")
                429 -> ApiTestResult.Failed("Rate limited. Please try again later.")
                in 500..599 -> ApiTestResult.Failed("Server error. Please try again later.")
                else -> {
                    val msg = try {
                        JSONObject(body).optString("error", "HTTP $code")
                    } catch (e: Exception) {
                        "HTTP $code"
                    }
                    ApiTestResult.Failed(msg)
                }
            }
        } finally {
            conn.disconnect()
        }
    }

    // ── Model discovery implementations ────────────────────────────────────

    private fun discoverGeminiModels(
        instance: ProviderInstance, apiKey: String
    ): List<DiscoveredModel> {
        val endpoint = "${instance.endpoint.ifBlank { "https://generativelanguage.googleapis.com/v1beta" }.trimEnd('/')}/models?key=$apiKey"
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
        }
        val body = try {
            conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        conn.disconnect()
        val models = JSONObject(body).optJSONArray("models") ?: return emptyList()
        return (0 until models.length()).map { i ->
            val m = models.getJSONObject(i)
            val id = m.optString("name", "").removePrefix("models/")
            DiscoveredModel(id, m.optString("displayName", id))
        }
    }

    private fun discoverOpenAiModels(
        instance: ProviderInstance,
        apiKey: String,
        definition: ProviderDefinition
    ): List<DiscoveredModel> {
        val base = instance.endpoint.ifBlank { definition.defaultEndpoint }.trimEnd('/')
        val conn = (URL("$base/models").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 10_000
            readTimeout = 15_000
            applyAuthentication(definition, apiKey)
            if (instance.definitionId == "openrouter") {
                setRequestProperty("HTTP-Referer", "https://github.com/gitofy")
                setRequestProperty("X-Title", "GITOFY")
            }
            instance.customHeaders.forEach { (key, value) ->
                if (key.isNotBlank() && value.isNotBlank()) setRequestProperty(key, value)
            }
        }
        val body = try {
            conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            conn.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        conn.disconnect()
        val data = JSONObject(body).optJSONArray("data") ?: return emptyList()
        return (0 until data.length()).map { i ->
            val m = data.getJSONObject(i)
            val id = m.optString("id", "")
            DiscoveredModel(id, id)
        }
    }

    private fun discoverLocal(baseUrl: String, path: String, arrayKey: String): List<DiscoveredModel> {
        val conn = (URL("$baseUrl$path").openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 5_000
            readTimeout = 10_000
        }
        val body = try {
            conn.inputStream?.bufferedReader()?.use { it.readText() } ?: ""
        } catch (e: Exception) {
            return emptyList()
        }
        conn.disconnect()
        val array = JSONObject(body).optJSONArray(arrayKey) ?: return emptyList()
        return (0 until array.length()).map { i ->
            val item = array.optJSONObject(i) ?: return@map null
            val id = item.optString("name", item.optString("id", ""))
            if (id.isBlank()) null else DiscoveredModel(id, id)
        }.filterNotNull()
    }

    companion object {
        // Static protocol lookup — no DI needed, avoids circular deps.
        private val STATIC_DEFINITION_MAP: Map<String, ProviderDefinition> by lazy {
            com.gitofy.ai.provider.registry.BuiltInProviders.all.associateBy { it.id }
        }
    }
}
