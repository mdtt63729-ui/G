package com.gitofy.data.repository

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.longPreferencesKey
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.gitofy.core.logging.GITOFYLogger
import com.gitofy.data.remote.dto.Repository
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.first
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import javax.inject.Inject
import javax.inject.Singleton

private val Context.exploreCacheDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gitofy_explore_cache"
)

/**
 * PRD §13/§14 — Explore page persistence.
 *
 * Required architecture:
 *   Network -> Explore Repository -> Local Cache -> Explore UI
 *
 * Caches the last successful Explore search (query + result list) to local
 * disk so the screen can render cached content immediately on the next
 * visit/recreation instead of the old "content disappears -> skeleton ->
 * content" cycle. [ExploreViewModel] renders this cache first and only then
 * kicks off a silent background refresh; a skeleton is only shown when
 * there is genuinely nothing cached yet (PRD §14).
 */
@Singleton
class ExploreCacheStore @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val json = Json {
        ignoreUnknownKeys = true
        encodeDefaults = true
        explicitNulls = false
    }

    private object Keys {
        val QUERY = stringPreferencesKey("last_query")
        val RESULTS = stringPreferencesKey("last_results_json")
        val CACHED_AT = longPreferencesKey("cached_at")
    }

    data class CachedExplore(
        val query: String,
        val results: List<Repository>,
        val cachedAtMillis: Long
    )

    /** Returns the last cached search, or null if there's nothing usable cached. */
    suspend fun load(): CachedExplore? {
        return try {
            val prefs = context.exploreCacheDataStore.data.first()
            val resultsJson = prefs[Keys.RESULTS] ?: return null
            val query = prefs[Keys.QUERY] ?: return null
            val cachedAt = prefs[Keys.CACHED_AT] ?: 0L
            val results = json.decodeFromString<List<Repository>>(resultsJson)
            if (results.isEmpty()) null else CachedExplore(query, results, cachedAt)
        } catch (e: Exception) {
            GITOFYLogger.w("ExploreCacheStore.load failed: ${e.message}")
            null
        }
    }

    /** Persists a successful search result so the next visit can render it instantly. */
    suspend fun save(query: String, results: List<Repository>) {
        if (results.isEmpty()) return
        runCatching {
            context.exploreCacheDataStore.edit { prefs ->
                prefs[Keys.QUERY] = query
                prefs[Keys.RESULTS] = json.encodeToString(results)
                prefs[Keys.CACHED_AT] = System.currentTimeMillis()
            }
        }.onFailure { GITOFYLogger.w("ExploreCacheStore.save failed: ${it.message}") }
    }
}
