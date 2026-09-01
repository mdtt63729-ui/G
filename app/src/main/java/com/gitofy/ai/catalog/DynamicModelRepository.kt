package com.gitofy.ai.catalog

import com.gitofy.ai.credentials.AiProvider
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.data.local.dao.SyncMetadataDao
import com.gitofy.data.local.entity.SyncMetadataEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.TimeUnit
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Dynamic multi-provider model discovery/cache layer.
 *
 * Providers are queried in parallel when credentials/endpoints are configured:
 * OpenRouter, NVIDIA NIM, Gemini and Ollama. Successful results replace only
 * that provider's cached registry; failed providers retain their last cache.
 * The UI can therefore start from Room cache and converge to the live registry.
 */
@Singleton
class DynamicModelRepository @Inject constructor(
    private val secureStorage: SecureCredentialStorage,
    private val syncMetadataDao: SyncMetadataDao,
    @com.gitofy.core.network.AiHttpClient private val client: OkHttpClient
) {
    companion object {
        private const val CACHE_KEY = "dynamic_ai_model_registry_v1"
        private const val OPENROUTER_MODELS = "https://openrouter.ai/api/v1/models"
        private const val NVIDIA_MODELS = "https://integrate.api.nvidia.com/v1/models"
        private const val GEMINI_MODELS = "https://generativelanguage.googleapis.com/v1beta/models"
    }

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val _models = MutableStateFlow<List<AIModelDefinition>>(emptyList())
    val models: StateFlow<List<AIModelDefinition>> = _models.asStateFlow()

    private var cachedByProvider: MutableMap<AiProvider, List<AIModelDefinition>> = mutableMapOf()

    init {
        scope.launch { loadCache() }
    }

    /** Load cached models immediately, then refresh all enabled providers in parallel. */
    suspend fun refreshModels(): List<AIModelDefinition> {
        loadCache()
        val configured = configuredProviderJobs()
        if (configured.isEmpty()) return _models.value

        val results = configured.map { (provider, job) ->
            scope.async {
                provider to runCatching { job() }.getOrNull()
            }
        }.awaitAll()

        var changed = false
        results.forEach { (provider, discovered) ->
            if (discovered != null) {
                cachedByProvider[provider] = discovered.distinctBy { it.id }
                changed = true
            }
        }
        if (changed) persistCache()
        publish()
        return _models.value
    }

    fun refreshInBackground() {
        scope.launch { runCatching { refreshModels() } }
    }

    private suspend fun loadCache() {
        val row = runCatching { syncMetadataDao.get(CACHE_KEY) }.getOrNull() ?: return
        val body = row.cachedBody ?: return
        runCatching {
            val root = JSONObject(body)
            val providerObject = root.optJSONObject("providers") ?: return@runCatching
            val map = mutableMapOf<AiProvider, List<AIModelDefinition>>()
            providerObject.keys().forEach { providerName ->
                val provider = AiProvider.entries.firstOrNull { it.name == providerName } ?: return@forEach
                val array = providerObject.optJSONArray(providerName) ?: return@forEach
                map[provider] = buildList {
                    for (i in 0 until array.length()) {
                        val o = array.optJSONObject(i) ?: continue
                        add(o.toDefinition(provider))
                    }
                }
            }
            cachedByProvider = map
            publish()
        }
    }

    private suspend fun persistCache() {
        val providers = JSONObject()
        cachedByProvider.forEach { (provider, list) ->
            providers.put(provider.name, JSONArray().apply { list.forEach { put(it.toJson()) } })
        }
        val root = JSONObject().put("version", 1).put("updatedAt", System.currentTimeMillis()).put("providers", providers)
        runCatching {
            syncMetadataDao.upsert(SyncMetadataEntity(
                key = CACHE_KEY,
                lastSyncTime = System.currentTimeMillis(),
                cachedBody = root.toString()
            ))
        }
    }

    private fun publish() {
        _models.value = cachedByProvider.values.flatten().distinctBy { it.uniqueKey }
    }

    private fun configuredProviderJobs(): List<Pair<AiProvider, suspend () -> List<AIModelDefinition>>> = buildList {
        if (secureStorage.getAiKey(AiProvider.OPENROUTER.name).orEmpty().isNotBlank()) {
            add(AiProvider.OPENROUTER to { fetchOpenRouter() })
        }
        if (secureStorage.getAiKey(AiProvider.NVIDIA_NIM.name).orEmpty().isNotBlank()) {
            add(AiProvider.NVIDIA_NIM to { fetchNvidia() })
        }
        if (secureStorage.getAiKey(AiProvider.GEMINI.name).orEmpty().isNotBlank()) {
            add(AiProvider.GEMINI to { fetchGemini() })
        }
        val ollamaEndpoints = secureStorage.getProviderInstances()
            .filter { it.definitionId.equals("ollama", true) && it.isEnabled }
            .map { it.endpoint.trim().trimEnd('/') }
            .filter { it.isNotBlank() }
            .distinct()
        if (ollamaEndpoints.isNotEmpty()) {
            add(AiProvider.OLLAMA to { fetchOllama(ollamaEndpoints.first()) })
        }
    }

    private suspend fun fetchOpenRouter(): List<AIModelDefinition> {
        val key = secureStorage.getAiKey(AiProvider.OPENROUTER.name) ?: return emptyList()
        val root = getJson(OPENROUTER_MODELS, mapOf(
            "Authorization" to "Bearer $key",
            "HTTP-Referer" to "https://github.com/gitofy",
            "X-Title" to "GITOFY"
        ))
        val data = root.optJSONArray("data") ?: JSONArray()
        return buildList {
            for (i in 0 until data.length()) {
                val o = data.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                val pricing = o.optJSONObject("pricing")
                val prompt = pricing?.optString("prompt").orEmpty()
                val completion = pricing?.optString("completion").orEmpty()
                val free = id.endsWith(":free") || (prompt.toDoubleOrNull() == 0.0 && completion.toDoubleOrNull() == 0.0)
                if (!free) continue
                add(AIModelDefinition(
                    id = id,
                    provider = AiProvider.OPENROUTER,
                    displayName = o.optString("name").ifBlank { id.substringAfterLast('/') },
                    costTier = CostTier.FREE,
                    contextWindow = o.optInt("context_length", 4096).coerceAtLeast(1),
                    supportsImage = o.optJSONObject("architecture")?.optJSONArray("input_modalities")?.containsValue("image") == true,
                    supportsTools = o.optJSONArray("supported_parameters")?.containsValue("tools") == true,
                    supportsStreaming = true,
                    codingScore = 7,
                    reasoningScore = 7,
                    languageScore = 6,
                    endpointType = EndpointType.OPENAI_COMPATIBLE,
                    status = ModelStatus.ACTIVE
                ))
            }
        }
    }

    private suspend fun fetchNvidia(): List<AIModelDefinition> {
        val key = secureStorage.getAiKey(AiProvider.NVIDIA_NIM.name) ?: return emptyList()
        val root = getJson(NVIDIA_MODELS, mapOf("Authorization" to "Bearer $key"))
        val data = root.optJSONArray("data") ?: JSONArray()
        return buildList {
            for (i in 0 until data.length()) {
                val o = data.optJSONObject(i) ?: continue
                val id = o.optString("id").takeIf { it.isNotBlank() } ?: continue
                val modalities = o.optJSONObject("architecture")?.optJSONArray("input_modalities")
                add(AIModelDefinition(
                    id = id,
                    provider = AiProvider.NVIDIA_NIM,
                    displayName = o.optString("name").ifBlank { id.substringAfterLast('/') },
                    costTier = CostTier.FREE,
                    contextWindow = o.optInt("context_length", 4096).coerceAtLeast(1),
                    supportsImage = modalities?.containsValue("image") == true,
                    supportsAudio = modalities?.containsValue("audio") == true,
                    supportsVideo = modalities?.containsValue("video") == true,
                    supportsTools = o.optJSONArray("supported_parameters")?.containsValue("tools") == true,
                    supportsStreaming = true,
                    codingScore = 7,
                    reasoningScore = 7,
                    languageScore = 6,
                    endpointType = EndpointType.OPENAI_COMPATIBLE,
                    status = ModelStatus.ACTIVE
                ))
            }
        }
    }

    private suspend fun fetchGemini(): List<AIModelDefinition> {
        val key = secureStorage.getAiKey(AiProvider.GEMINI.name) ?: return emptyList()
        val root = getJson("$GEMINI_MODELS?key=${java.net.URLEncoder.encode(key, "UTF-8")}")
        val data = root.optJSONArray("models") ?: JSONArray()
        return buildList {
            for (i in 0 until data.length()) {
                val o = data.optJSONObject(i) ?: continue
                val methods = o.optJSONArray("supportedGenerationMethods") ?: JSONArray()
                if (!methods.containsValue("generateContent")) continue
                val rawName = o.optString("name")
                val id = rawName.removePrefix("models/").takeIf { it.isNotBlank() } ?: continue
                add(AIModelDefinition(
                    id = id,
                    provider = AiProvider.GEMINI,
                    displayName = o.optString("displayName").ifBlank { id },
                    costTier = CostTier.FREE,
                    contextWindow = o.optInt("inputTokenLimit", 4096).coerceAtLeast(1),
                    supportsImage = true,
                    supportsStreaming = true,
                    supportsTools = methods.containsValue("generateContent"),
                    codingScore = 7,
                    reasoningScore = 7,
                    languageScore = 7,
                    endpointType = EndpointType.GEMINI,
                    status = ModelStatus.ACTIVE
                ))
            }
        }
    }

    private suspend fun fetchOllama(endpoint: String): List<AIModelDefinition> {
        val root = getJson("$endpoint/api/tags")
        val data = root.optJSONArray("models") ?: JSONArray()
        return buildList {
            for (i in 0 until data.length()) {
                val o = data.optJSONObject(i) ?: continue
                val id = o.optString("name").takeIf { it.isNotBlank() } ?: continue
                add(AIModelDefinition(
                    id = id,
                    provider = AiProvider.OLLAMA,
                    displayName = id,
                    costTier = CostTier.FREE,
                    contextWindow = 4096,
                    supportsStreaming = true,
                    codingScore = 7,
                    reasoningScore = 7,
                    languageScore = 6,
                    endpointType = EndpointType.OPENAI_COMPATIBLE,
                    status = ModelStatus.ACTIVE
                ))
            }
        }
    }

    private suspend fun getJson(url: String, headers: Map<String, String> = emptyMap()): JSONObject {
        val request = Request.Builder().url(url).get().apply {
            headers.forEach { (key, value) -> header(key, value) }
        }.build()
        return kotlinx.coroutines.suspendCancellableCoroutine { continuation ->
            val call = client.newCall(request)
            continuation.invokeOnCancellation { call.cancel() }
            call.enqueue(object : okhttp3.Callback {
                override fun onFailure(call: okhttp3.Call, e: java.io.IOException) {
                    if (continuation.isActive) continuation.resumeWith(Result.failure(e))
                }
                override fun onResponse(call: okhttp3.Call, response: okhttp3.Response) {
                    response.use {
                        val body = it.body?.string().orEmpty()
                        if (!it.isSuccessful) {
                            if (continuation.isActive) continuation.resumeWith(Result.failure(IllegalStateException("HTTP ${it.code}: ${body.take(300)}")))
                            return
                        }
                        try {
                            if (continuation.isActive) continuation.resumeWith(Result.success(JSONObject(body)))
                        } catch (t: Throwable) {
                            if (continuation.isActive) continuation.resumeWith(Result.failure(t))
                        }
                    }
                }
            })
        }
    }

    private fun JSONObject.toDefinition(provider: AiProvider): AIModelDefinition = AIModelDefinition(
        id = optString("id"),
        provider = provider,
        displayName = optString("displayName").ifBlank { optString("name").ifBlank { optString("id") } },
        costTier = CostTier.valueOf(optString("costTier", CostTier.FREE.name)),
        contextWindow = optInt("contextWindow", 4096),
        supportsText = optBoolean("supportsText", true),
        supportsImage = optBoolean("supportsImage"),
        supportsAudio = optBoolean("supportsAudio"),
        supportsVideo = optBoolean("supportsVideo"),
        supportsTools = optBoolean("supportsTools"),
        supportsStreaming = optBoolean("supportsStreaming", true),
        supportsStructuredOutput = optBoolean("supportsStructuredOutput"),
        codingScore = optInt("codingScore", 7),
        reasoningScore = optInt("reasoningScore", 7),
        languageScore = optInt("languageScore", 6),
        latencyClass = runCatching { LatencyClass.valueOf(optString("latencyClass", LatencyClass.MEDIUM.name)) }.getOrDefault(LatencyClass.MEDIUM),
        endpointType = runCatching { EndpointType.valueOf(optString("endpointType", EndpointType.OPENAI_COMPATIBLE.name)) }.getOrDefault(EndpointType.OPENAI_COMPATIBLE),
        status = runCatching { ModelStatus.valueOf(optString("status", ModelStatus.ACTIVE.name)) }.getOrDefault(ModelStatus.ACTIVE)
    )

    private fun AIModelDefinition.toJson() = JSONObject().apply {
        put("id", id); put("displayName", displayName); put("costTier", costTier.name); put("contextWindow", contextWindow)
        put("supportsText", supportsText); put("supportsImage", supportsImage); put("supportsAudio", supportsAudio); put("supportsVideo", supportsVideo)
        put("supportsTools", supportsTools); put("supportsStreaming", supportsStreaming); put("supportsStructuredOutput", supportsStructuredOutput)
        put("codingScore", codingScore); put("reasoningScore", reasoningScore); put("languageScore", languageScore)
        put("latencyClass", latencyClass.name); put("endpointType", endpointType.name); put("status", status.name)
    }

    private fun JSONArray.containsValue(value: String): Boolean {
        for (i in 0 until length()) if (optString(i) == value) return true
        return false
    }
}
