package com.gitofy.feature.onboarding

import android.content.Context
import androidx.datastore.core.DataStore
import androidx.datastore.preferences.core.Preferences
import androidx.datastore.preferences.core.booleanPreferencesKey
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.intPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import javax.inject.Inject
import javax.inject.Singleton

private val Context.onboardingStore: DataStore<Preferences> by preferencesDataStore(name = "gitofy_onboarding")

data class OnboardingState(
    val isCompleted: Boolean = false,
    val currentStep: Int = 0,
    val githubConnected: Boolean = false,
    val repositoryConfigured: Boolean = false,
    val apiProviderConfigured: Boolean = false,
    val backgroundSyncEnabled: Boolean = true,
    val appearanceConfigured: Boolean = false
)

@Singleton
class OnboardingRepository @Inject constructor(@ApplicationContext private val context: Context) {
    private object Keys {
        val COMPLETED = booleanPreferencesKey("completed")
        val STEP = intPreferencesKey("step")
        val GITHUB = booleanPreferencesKey("github_connected")
        val REPOSITORY = booleanPreferencesKey("repository_configured")
        val AI = booleanPreferencesKey("ai_configured")
        val SYNC = booleanPreferencesKey("sync_enabled")
        val APPEARANCE = booleanPreferencesKey("appearance_configured")
    }

    val state: Flow<OnboardingState> = context.onboardingStore.data.map { p ->
        OnboardingState(
            isCompleted = p[Keys.COMPLETED] ?: false,
            currentStep = (p[Keys.STEP] ?: 0).coerceIn(0, 5),
            githubConnected = p[Keys.GITHUB] ?: false,
            repositoryConfigured = p[Keys.REPOSITORY] ?: false,
            apiProviderConfigured = p[Keys.AI] ?: false,
            backgroundSyncEnabled = p[Keys.SYNC] ?: true,
            appearanceConfigured = p[Keys.APPEARANCE] ?: false
        )
    }

    suspend fun setStep(step: Int) = context.onboardingStore.edit { it[Keys.STEP] = step.coerceIn(0, 5) }
    suspend fun setGithubConnected(value: Boolean) = context.onboardingStore.edit { it[Keys.GITHUB] = value }
    suspend fun setRepositoryConfigured(value: Boolean) = context.onboardingStore.edit { it[Keys.REPOSITORY] = value }
    suspend fun setAiProviderConfigured(value: Boolean) = context.onboardingStore.edit { it[Keys.AI] = value }
    suspend fun setBackgroundSync(value: Boolean) = context.onboardingStore.edit { it[Keys.SYNC] = value }
    suspend fun setAppearanceConfigured(value: Boolean) = context.onboardingStore.edit { it[Keys.APPEARANCE] = value }
    suspend fun complete() = context.onboardingStore.edit { it[Keys.COMPLETED] = true; it[Keys.STEP] = 5 }
    suspend fun skipAndComplete() = context.onboardingStore.edit { it[Keys.COMPLETED] = true; it[Keys.STEP] = 5 }
}
