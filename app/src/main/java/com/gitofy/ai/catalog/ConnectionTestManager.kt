package com.gitofy.ai.catalog

import com.gitofy.ai.credentials.AiProvider
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Provider Connection Testing — PRD §19-25.
 *
 * Each provider has a dedicated tester that sends a minimal request
 * to verify: authentication + endpoint + model availability.
 *
 * Test request: "Return only: OK" — no repository context, no source code,
 * no GitHub token, no conversation history, no large payload (PRD §21).
 *
 * All exceptions are caught at the provider boundary — UI never handles raw
 * exceptions (PRD §24).
 *
 * IMPORTANT FIXES:
 * - Sarvam: Uses api-subscription-key header (NOT Bearer auth)
 * - Sarvam v2 endpoint for open-source models (glm5.2, gemma4)
 * - OpenCode Zen: Correct URL is opencode.ai/zen/v1 (NOT api.opencodezen.com)
 * - NVIDIA NIM: Model IDs include vendor prefix (nvidia/..., meta/..., deepseek-ai/...)
 */

/**
 * Normalized connection test result — PRD §22, §27.
 * UI never receives provider-specific raw error JSON.
 */
sealed class ConnectionTestResult {
    data object Idle : ConnectionTestResult()
    data object Testing : ConnectionTestResult()
    data class Success(val message: String, val modelName: String) : ConnectionTestResult()
    data class InvalidKey(val message: String) : ConnectionTestResult()
    data object Unauthorized : ConnectionTestResult()
    data object RateLimited : ConnectionTestResult()
    data object ModelUnavailable : ConnectionTestResult()
    data object NetworkError : ConnectionTestResult()
    data object ServerError : ConnectionTestResult()
    data class Unknown(val message: String) : ConnectionTestResult()

    companion object {
        fun failure(message: String): ConnectionTestResult =
            if (message.contains("401") || message.contains("403")) InvalidKey(message)
            else if (message.contains("429")) RateLimited
            else if (message.contains("404")) ModelUnavailable
            else if (message.contains("502") || message.contains("503") || message.contains("504")) ServerError
            else if (message.contains("timeout", ignoreCase = true) || message.contains("network", ignoreCase = true)) NetworkError
            else Unknown(message)
    }
}

/**
 * Normalized provider error — PRD §27.
 */
sealed class ProviderError {
    data object InvalidApiKey : ProviderError()
    data object RateLimited : ProviderError()
    data object ModelNotFound : ProviderError()
    data object NetworkUnavailable : ProviderError()
    data object Timeout : ProviderError()
    data object ServerUnavailable : ProviderError()
    data class Unknown(val message: String) : ProviderError()
}

/**
 * Per-provider connection tester interface — PRD §20.
 */
interface ProviderConnectionTester {
    suspend fun test(apiKey: String, model: AIModelDefinition): ConnectionTestResult
}

@Singleton
class ConnectionTestManager @Inject constructor() : ProviderConnectionTester {

    override suspend fun test(apiKey: String, model: AIModelDefinition): ConnectionTestResult {
        return try {
            // Route based on the model's endpointType, not just the provider
            when (model.endpointType) {
                EndpointType.GEMINI -> testGemini(apiKey, model)
                EndpointType.SARVAM_V1 -> testSarvam(
                    apiKey, model, "https://api.sarvam.ai/v1/chat/completions"
                )
                EndpointType.SARVAM_V2 -> testSarvam(
                    apiKey, model, "https://api.sarvam.ai/v2/chat/completions"
                )
                EndpointType.OPENAI_COMPATIBLE -> {
                    val endpoint = when (model.provider) {
                        AiProvider.OPENAI -> "https://api.openai.com/v1/chat/completions"
                        AiProvider.NVIDIA_NIM -> "https://integrate.api.nvidia.com/v1/chat/completions"
                        AiProvider.OPENROUTER -> "https://openrouter.ai/api/v1/chat/completions"
                        // FIX: OpenCode Zen uses opencode.ai/zen/v1, NOT api.opencodezen.com
                        AiProvider.OPENCODE_ZEN -> "https://opencode.ai/zen/v1/chat/completions"
                        else -> return ConnectionTestResult.Unknown("Provider ${model.provider} not supported for testing")
                    }
                    testOpenAiCompatible(apiKey, model, endpoint)
                }
                EndpointType.CUSTOM -> ConnectionTestResult.Unknown("Custom provider not configured for testing")
            }
        } catch (e: Exception) {
            // PRD §24: All exceptions caught at provider boundary, never thrown to UI
            ConnectionTestResult.failure(e.message ?: "Unknown error")
        }
    }

    private fun testGemini(apiKey: String, model: AIModelDefinition): ConnectionTestResult {
        val endpoint = "https://generativelanguage.googleapis.com/v1beta/models/${model.id}:generateContent?key=$apiKey"
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000  // PRD §53: 10s connect
        connection.readTimeout = 15000     // PRD §53: 15s read
        connection.setRequestProperty("Content-Type", "application/json")
        connection.doOutput = true

        // Minimal test prompt (PRD §21)
        val requestBody = JSONObject().apply {
            put("contents", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("parts", org.json.JSONArray().apply {
                        put(JSONObject().apply { put("text", "Return only: OK") })
                    })
                })
            })
            put("generationConfig", JSONObject().apply { put("maxOutputTokens", 10) })
        }

        connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }

        val responseCode = connection.responseCode
        val responseBody = try {
            connection.inputStream?.bufferedReader()?.use { it.readText() }
        } catch (e: Exception) {
            connection.errorStream?.bufferedReader()?.use { it.readText() } ?: ""
        }
        connection.disconnect()

        return when (responseCode) {
            in 200..299 -> ConnectionTestResult.Success(
                "Connection successful",
                "${model.displayName} responded successfully"
            )
            401, 403 -> ConnectionTestResult.InvalidKey("API key rejected. Check your Gemini API key.")
            404 -> ConnectionTestResult.ModelUnavailable
            429 -> ConnectionTestResult.RateLimited
            in 500..599 -> ConnectionTestResult.ServerError
            else -> ConnectionTestResult.Unknown("HTTP $responseCode")
        }
    }

    /**
     * Sarvam connection test — uses api-subscription-key header, NOT Bearer auth.
     * This was a critical bug: Sarvam API rejects Bearer auth.
     */
    private fun testSarvam(
        apiKey: String, model: AIModelDefinition, endpoint: String
    ): ConnectionTestResult {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.setRequestProperty("Content-Type", "application/json")
        // FIX: Sarvam uses api-subscription-key header, NOT Bearer
        connection.setRequestProperty("api-subscription-key", apiKey)
        connection.doOutput = true

        val requestBody = JSONObject().apply {
            put("model", model.id)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Return only: OK")
                })
            })
            put("max_tokens", 10)
        }

        connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }

        val responseCode = connection.responseCode
        connection.disconnect()

        return when (responseCode) {
            in 200..299 -> ConnectionTestResult.Success(
                "Connection successful",
                "${model.displayName} responded successfully"
            )
            401, 403 -> ConnectionTestResult.InvalidKey("API key rejected. Check your Sarvam AI API key.")
            404 -> ConnectionTestResult.ModelUnavailable
            429 -> ConnectionTestResult.RateLimited
            in 500..599 -> ConnectionTestResult.ServerError
            else -> ConnectionTestResult.Unknown("HTTP $responseCode")
        }
    }

    private fun testOpenAiCompatible(
        apiKey: String, model: AIModelDefinition, endpoint: String
    ): ConnectionTestResult {
        val connection = URL(endpoint).openConnection() as HttpURLConnection
        connection.requestMethod = "POST"
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.setRequestProperty("Content-Type", "application/json")
        connection.setRequestProperty("Authorization", "Bearer $apiKey")
        // OpenRouter benefits from HTTP-Referer and X-Title headers
        if (model.provider == AiProvider.OPENROUTER) {
            connection.setRequestProperty("HTTP-Referer", "https://github.com/gitofy")
            connection.setRequestProperty("X-Title", "GITOFY")
        }
        connection.doOutput = true

        // Minimal test prompt (PRD §21)
        val requestBody = JSONObject().apply {
            put("model", model.id)
            put("messages", org.json.JSONArray().apply {
                put(JSONObject().apply {
                    put("role", "user")
                    put("content", "Return only: OK")
                })
            })
            put("max_tokens", 10)
        }

        connection.outputStream.use { it.write(requestBody.toString().toByteArray()) }

        val responseCode = connection.responseCode
        connection.disconnect()

        return when (responseCode) {
            in 200..299 -> ConnectionTestResult.Success(
                "Connection successful",
                "${model.displayName} responded successfully"
            )
            401, 403 -> ConnectionTestResult.InvalidKey("API key rejected. Check your ${model.provider.displayName} API key.")
            404 -> ConnectionTestResult.ModelUnavailable
            429 -> ConnectionTestResult.RateLimited
            in 500..599 -> ConnectionTestResult.ServerError
            else -> ConnectionTestResult.Unknown("HTTP $responseCode")
        }
    }
}
