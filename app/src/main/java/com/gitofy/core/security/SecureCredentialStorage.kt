package com.gitofy.core.security

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import androidx.security.crypto.EncryptedSharedPreferences
import androidx.security.crypto.MasterKey
import org.json.JSONArray
import org.json.JSONObject
import java.security.KeyStore
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec
import android.util.Base64
import dagger.hilt.android.qualifiers.ApplicationContext
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Secure credential storage using Android Keystore + EncryptedSharedPreferences.
 * PRD 8.1: No plaintext credential storage anywhere.
 */
@Singleton
class SecureCredentialStorage @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val masterKey: MasterKey by lazy {
        MasterKey.Builder(context)
            .setKeyScheme(MasterKey.KeyScheme.AES256_GCM)
            .build()
    }

    private val encryptedPrefs by lazy {
        EncryptedSharedPreferences.create(
            context,
            "encrypted_prefs",
            masterKey,
            EncryptedSharedPreferences.PrefKeyEncryptionScheme.AES256_SIV,
            EncryptedSharedPreferences.PrefValueEncryptionScheme.AES256_GCM
        )
    }

    fun saveToken(token: String) {
        encryptedPrefs.edit().putString(KEY_GITHUB_TOKEN, token).apply()
    }

    fun getToken(): String? {
        return encryptedPrefs.getString(KEY_GITHUB_TOKEN, null)
    }

    fun clearToken() {
        encryptedPrefs.edit().remove(KEY_GITHUB_TOKEN).apply()
    }

    fun hasToken(): Boolean {
        return encryptedPrefs.contains(KEY_GITHUB_TOKEN)
    }

    fun saveUserData(login: String, avatarUrl: String) {
        encryptedPrefs.edit()
            .putString(KEY_USER_LOGIN, login)
            .putString(KEY_USER_AVATAR, avatarUrl)
            .apply()
    }

    fun getUserLogin(): String? = encryptedPrefs.getString(KEY_USER_LOGIN, null)
    fun getUserAvatar(): String? = encryptedPrefs.getString(KEY_USER_AVATAR, null)

    fun clearAll() {
        encryptedPrefs.edit().clear().apply()
    }

    // PRD §56-57: AI Provider credential persistent storage
    fun saveAiKey(provider: String, apiKey: String) {
        // Clipboard pastes and password-manager entries can carry a trailing
        // newline/space. Store one canonical representation so the exact same
        // credential is used by Settings, model discovery and Chat.
        val normalized = apiKey.trim()
        if (normalized.isBlank()) {
            encryptedPrefs.edit().remove("ai_key_$provider").apply()
        } else {
            encryptedPrefs.edit().putString("ai_key_$provider", normalized).apply()
        }
    }

    fun getAiKey(provider: String): String? =
        encryptedPrefs.getString("ai_key_$provider", null)?.trim()?.takeIf { it.isNotBlank() }

    fun hasAiKey(provider: String): Boolean = getAiKey(provider) != null

    fun removeAiKey(provider: String) {
        encryptedPrefs.edit().remove("ai_key_$provider").apply()
    }

    /** Encrypted non-secret metadata associated with an AI provider credential. */
    fun saveAiCredentialMetadata(key: String, value: String) {
        encryptedPrefs.edit().putString(key, value).apply()
    }

    fun getAiCredentialMetadata(key: String): String? =
        encryptedPrefs.getString(key, null)

    fun removeAiCredentialMetadata(key: String) {
        encryptedPrefs.edit().remove(key).apply()
    }

    fun getAllAiKeys(): Map<String, String> {
        val result = mutableMapOf<String, String>()
        encryptedPrefs.all.forEach { (key, value) ->
            if (key.startsWith("ai_key_") && value is String) {
                result[key.removePrefix("ai_key_")] = value
            }
        }
        return result
    }

    // PRD §59: Selected model persistence per provider
    fun saveSelectedModel(provider: String, modelId: String) {
        encryptedPrefs.edit().putString("selected_model_$provider", modelId).apply()
    }

    fun getSelectedModel(provider: String): String? =
        encryptedPrefs.getString("selected_model_$provider", null)

    fun removeSelectedModel(provider: String) {
        encryptedPrefs.edit().remove("selected_model_$provider").apply()
    }

    // FIX: chat used to restore "the current model" by scanning every
    // AiProvider in fixed enum declaration order and stopping at the first
    // one that had ANY saved model — not the one the user most recently
    // picked. That meant re-selecting a different model (e.g. a Gemini or
    // OpenRouter model) could silently snap back to an older saved
    // selection for whichever provider happens to be declared earlier in
    // the enum (in practice, often a DeepSeek model under NVIDIA NIM),
    // regardless of what was just chosen. This single pointer records which
    // provider (and, for instance-derived models, which instance) is the
    // actual current pick, so restoration reflects the real last choice.
    fun saveActiveModelSelection(providerKey: String) {
        encryptedPrefs.edit().putString("active_selected_model_provider_key", providerKey).apply()
    }

    fun getActiveModelSelection(): String? =
        encryptedPrefs.getString("active_selected_model_provider_key", null)

    // ── PRD §10: Provider Instance persistence ────────────────────────────
    //
    // Stores the user's configured provider instances (definitionId, endpoint,
    // selected model, enabled flag, etc.) as encrypted JSON.  The actual API
    // key is stored separately via saveAiKey so credential reset is independent
    // from settings reset (PRD §29).

    // Provider instances can contain custom authorization headers. Keep the
    // complete instance payload in the same Keystore-backed store as API keys.
    private val providerInstancePrefs get() = encryptedPrefs

    fun saveProviderInstances(instances: List<com.gitofy.ai.provider.registry.ProviderInstance>) {
        val json = JSONArray()
        for (inst in instances) {
            val obj = JSONObject()
            obj.put("instanceId", inst.instanceId)
            obj.put("definitionId", inst.definitionId)
            obj.put("displayName", inst.displayName)
            obj.put("endpoint", inst.endpoint)
            obj.put("apiKeyHint", inst.apiKeyHint)
            inst.selectedModel?.let { obj.put("selectedModel", it) }
            obj.put("isEnabled", inst.isEnabled)
            obj.put("isDefault", inst.isDefault)
            obj.put("isCustom", inst.isCustom)
            obj.put("createdAt", inst.createdAt)
            if (inst.customHeaders.isNotEmpty()) {
                val headers = JSONObject()
                inst.customHeaders.forEach { (k, v) -> headers.put(k, v) }
                obj.put("customHeaders", headers)
            }
            json.put(obj)
        }
        providerInstancePrefs.edit().putString("instances", json.toString()).apply()
    }

    fun getProviderInstances(): List<com.gitofy.ai.provider.registry.ProviderInstance> {
        val jsonStr = providerInstancePrefs.getString("instances", null) ?: return emptyList()
        return try {
            val json = JSONArray(jsonStr)
            val list = mutableListOf<com.gitofy.ai.provider.registry.ProviderInstance>()
            for (i in 0 until json.length()) {
                val obj = json.getJSONObject(i)
                val headers = mutableMapOf<String, String>()
                obj.optJSONObject("customHeaders")?.let { h ->
                    h.keys().forEach { key -> headers[key] = h.getString(key) }
                }
                list.add(
                    com.gitofy.ai.provider.registry.ProviderInstance(
                        instanceId = obj.getString("instanceId"),
                        definitionId = obj.getString("definitionId"),
                        displayName = obj.getString("displayName"),
                        endpoint = obj.getString("endpoint"),
                        apiKeyHint = obj.optString("apiKeyHint", ""),
                        selectedModel = obj.optString("selectedModel").takeIf { it.isNotBlank() },
                        isEnabled = obj.optBoolean("isEnabled", true),
                        isDefault = obj.optBoolean("isDefault", false),
                        isCustom = obj.optBoolean("isCustom", false),
                        customHeaders = headers,
                        createdAt = obj.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    // ── Cached model lists per provider (PRD §17 — cache model discovery) ──

    fun saveCachedModels(providerId: String, models: List<String>) {
        val json = JSONArray()
        models.forEach { json.put(it) }
        encryptedPrefs.edit()
            .putString("cached_models_$providerId", json.toString())
            .apply()
    }

    fun getCachedModels(providerId: String): List<String> {
        val jsonStr = encryptedPrefs.getString("cached_models_$providerId", null)
            ?: return emptyList()
        return try {
            val json = JSONArray(jsonStr)
            (0 until json.length()).map { json.getString(it) }
        } catch (e: Exception) {
            emptyList()
        }
    }

    fun clearCachedModels() {
        val keys = encryptedPrefs.all.keys.filter { it.startsWith("cached_models_") }
        val editor = encryptedPrefs.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
    }

    // PRD §26: Clear stored AI credentials (separate from settings reset)
    fun clearAllAiKeys() {
        val keys = encryptedPrefs.all.keys.filter { it.startsWith("ai_key_") }
        val editor = encryptedPrefs.edit()
        keys.forEach { editor.remove(it) }
        editor.apply()
    }

    // PRD PHASE 20: Chat conversation persistence
    private val chatPrefs = context.getSharedPreferences("gitofy_chat_history", android.content.Context.MODE_PRIVATE)

    fun saveChatConversations(conversations: List<com.gitofy.feature.ai.ChatConversation>) {
        val json = JSONArray()
        for (conv in conversations) {
            val convJson = JSONObject()
            convJson.put("id", conv.id)
            convJson.put("title", conv.title)
            convJson.put("createdAt", conv.createdAt)
            val msgsJson = JSONArray()
            for (msg in conv.messages) {
                val msgJson = JSONObject()
                msgJson.put("id", msg.id)
                msgJson.put("role", msg.role.name)
                msgJson.put("content", msg.content)
                msg.timestamp?.let { msgJson.put("timestamp", it) }
                msgsJson.put(msgJson)
            }
            convJson.put("messages", msgsJson)
            json.put(convJson)
        }
        chatPrefs.edit().putString("conversations", json.toString()).apply()
    }

    fun getChatConversations(): List<com.gitofy.feature.ai.ChatConversation> {
        val jsonStr = chatPrefs.getString("conversations", null) ?: return emptyList()
        return try {
            val json = JSONArray(jsonStr)
            val list = mutableListOf<com.gitofy.feature.ai.ChatConversation>()
            for (i in 0 until json.length()) {
                val convJson = json.getJSONObject(i)
                val msgsJson = convJson.optJSONArray("messages") ?: JSONArray()
                val msgs = mutableListOf<com.gitofy.feature.ai.ChatMessage>()
                for (j in 0 until msgsJson.length()) {
                    val msgJson = msgsJson.getJSONObject(j)
                    msgs.add(
                        com.gitofy.feature.ai.ChatMessage(
                            id = msgJson.getString("id"),
                            role = com.gitofy.feature.ai.ChatRole.valueOf(msgJson.getString("role")),
                            content = msgJson.getString("content"),
                            timestamp = if (msgJson.has("timestamp")) msgJson.getString("timestamp") else null
                        )
                    )
                }
                list.add(
                    com.gitofy.feature.ai.ChatConversation(
                        id = convJson.getString("id"),
                        title = convJson.getString("title"),
                        messages = msgs,
                        createdAt = convJson.optLong("createdAt", System.currentTimeMillis())
                    )
                )
            }
            list
        } catch (e: Exception) {
            emptyList()
        }
    }

    companion object {
        private const val KEY_GITHUB_TOKEN = "github_token"
        private const val KEY_USER_LOGIN = "user_login"
        private const val KEY_USER_AVATAR = "user_avatar"
    }
}
