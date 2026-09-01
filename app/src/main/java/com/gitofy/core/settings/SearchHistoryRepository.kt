package com.gitofy.core.settings

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

/**
 * PRD §18: Search history with DataStore persistence.
 * Max 20 entries, duplicates move to top.
 */
private val Context.searchHistoryDataStore: DataStore<Preferences> by preferencesDataStore(
    name = "gitofy_search_history"
)

@Singleton
class SearchHistoryRepository @Inject constructor(
    @ApplicationContext private val context: Context
) {
    private val key = stringPreferencesKey("search_queries")

    val history: Flow<List<String>> = context.searchHistoryDataStore.data.map { prefs ->
        prefs[key]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
    }

    suspend fun addQuery(query: String) {
        val trimmed = query.trim()
        if (trimmed.isBlank()) return
        context.searchHistoryDataStore.edit { prefs ->
            val current = prefs[key]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
            // Remove duplicate, add to front, cap at 20
            val updated = (listOf(trimmed) + current.filter { it != trimmed }).take(20)
            prefs[key] = updated.joinToString("\n")
        }
    }

    suspend fun removeQuery(query: String) {
        context.searchHistoryDataStore.edit { prefs ->
            val current = prefs[key]?.split("\n")?.filter { it.isNotBlank() } ?: emptyList()
            prefs[key] = current.filterNot { it == query }.joinToString("\n")
        }
    }

    suspend fun clearAll() {
        context.searchHistoryDataStore.edit { prefs ->
            prefs.remove(key)
        }
    }
}
