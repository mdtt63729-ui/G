package com.gitofy.feature.workflows.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.domain.model.GitOFYError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class LogsUiState(
    val logs: String = "",
    val isLoading: Boolean = false,
    val error: String? = null
)

@HiltViewModel
class LogsViewModel @Inject constructor(
    private val apiService: GitHubApiService,
    private val secureStorage: SecureCredentialStorage
) : ViewModel() {

    private val _uiState = MutableStateFlow(LogsUiState())
    val uiState = _uiState.asStateFlow()

    fun loadLogs(owner: String, repo: String, jobId: Long) {
        _uiState.update { it.copy(isLoading = true, error = null) }

        viewModelScope.launch {
            try {
                val token = secureStorage.getToken()
                    ?: run {
                        _uiState.update { it.copy(isLoading = false, error = "Authentication required") }
                        return@launch
                    }

                // PRD FIX: this was a blocking OkHttp call made directly on the
                // ViewModel's (Main) coroutine dispatcher. Android throws
                // NetworkOnMainThreadException for ANY blocking network I/O on
                // the main thread — that exception was being silently caught
                // below and shown as a generic "Failed to load logs" error,
                // which is why logs never appeared even after a successful
                // build. Moving the actual network call onto Dispatchers.IO
                // fixes this.
                withContext(Dispatchers.IO) {
                    // Individual job logs: GitHub redirects to a plain-text log
                    // blob (not a zip — the zip is only for whole-run logs).
                    val client = okhttp3.OkHttpClient.Builder()
                        .followRedirects(true)
                        .build()

                    val request = okhttp3.Request.Builder()
                        .url("https://api.github.com/repos/$owner/$repo/actions/jobs/$jobId/logs")
                        .header("Authorization", "Bearer $token")
                        .header("Accept", "application/vnd.github+json")
                        .build()

                    client.newCall(request).execute().use { response ->
                        if (response.isSuccessful) {
                            val body = response.body?.string() ?: ""
                            _uiState.update { it.copy(logs = body, isLoading = false) }
                        } else {
                            _uiState.update {
                                it.copy(isLoading = false, error = "Failed to load logs: ${response.code}")
                            }
                        }
                    }
                }
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load logs") }
            }
        }
    }
}
