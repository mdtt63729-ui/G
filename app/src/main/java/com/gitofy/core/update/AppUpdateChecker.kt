package com.gitofy.core.update

import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.network.safeApiCall
import com.gitofy.data.remote.dto.Release
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import okhttp3.OkHttpClient
import okhttp3.Request
import javax.inject.Inject
import javax.inject.Singleton

/**
 * In-App Self-Updater — PRD Addendum: App Upgrade & Maintenance Strategy.
 * Automatic checking against releases/latest API on launch.
 * Non-blocking prompt for updating to the newest APK directly within the app.
 */
@Singleton
class AppUpdateChecker @Inject constructor(
    private val apiService: GitHubApiService,
    private val okHttpClient: OkHttpClient
) {
    private val _updateState = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val updateState = _updateState.asStateFlow()

    /**
     * Check for updates via GitHub Releases API.
     * PRD Addendum: Automatic checking on launch.
     */
    suspend fun checkForUpdate(
        repoOwner: String = "GITOFY",
        repoName: String = "GITOFY",
        currentVersion: String
    ) {
        _updateState.value = UpdateState.Checking

        // Use raw API call since GitHubApiService doesn't have releases endpoint yet
        try {
            val request = Request.Builder()
                .url("https://api.github.com/repos/$repoOwner/$repoName/releases/latest")
                .header("Accept", "application/vnd.github+json")
                .build()

            okHttpClient.newCall(request).execute().use { response ->
                if (response.isSuccessful) {
                    val body = response.body?.string()
                    // Parse release info
                    val latestVersion = body?.let { parseVersion(it) }
                    val downloadUrl = body?.let { parseDownloadUrl(it) }

                    if (latestVersion != null && isNewerVersion(latestVersion, currentVersion)) {
                        _updateState.value = UpdateState.UpdateAvailable(
                            version = latestVersion,
                            downloadUrl = downloadUrl ?: "",
                            releaseNotes = parseReleaseNotes(body)
                        )
                    } else {
                        _updateState.value = UpdateState.UpToDate
                    }
                } else {
                    _updateState.value = UpdateState.Error("Failed to check: ${response.code}")
                }
            }
        } catch (e: Exception) {
            _updateState.value = UpdateState.Error(e.message ?: "Unknown error")
        }
    }

    fun reset() {
        _updateState.value = UpdateState.Idle
    }

    private fun parseVersion(json: String): String? {
        val regex = """"tag_name"\s*:\s*"([^"]+)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun parseDownloadUrl(json: String): String? {
        val regex = """"browser_download_url"\s*:\s*"([^"]+\.apk)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)
    }

    private fun parseReleaseNotes(json: String): String {
        val regex = """"body"\s*:\s*"([^"]*)"""".toRegex()
        return regex.find(json)?.groupValues?.get(1)?.replace("\\n", "\n") ?: ""
    }

    private fun isNewerVersion(remote: String, local: String): Boolean {
        val remoteParts = remote.removePrefix("v").split(".").map { it.toIntOrNull() ?: 0 }
        val localParts = local.split(".").map { it.toIntOrNull() ?: 0 }
        for (i in 0 until maxOf(remoteParts.size, localParts.size)) {
            val r = remoteParts.getOrElse(i) { 0 }
            val l = localParts.getOrElse(i) { 0 }
            if (r > l) return true
            if (r < l) return false
        }
        return false
    }
}

sealed class UpdateState {
    data object Idle : UpdateState()
    data object Checking : UpdateState()
    data object UpToDate : UpdateState()
    data class UpdateAvailable(
        val version: String,
        val downloadUrl: String,
        val releaseNotes: String
    ) : UpdateState()
    data class Error(val message: String) : UpdateState()
}
