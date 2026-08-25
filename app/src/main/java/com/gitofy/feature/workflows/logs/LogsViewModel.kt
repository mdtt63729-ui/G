package com.gitofy.feature.workflows.logs

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.domain.model.GitOFYError
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
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

                // Fetch logs — GitHub returns a redirect to a zip file
                val client = okhttp3.OkHttpClient.Builder()
                    .followRedirects(true)
                    .build()

                val request = okhttp3.Request.Builder()
                    .url("${com.gitofy.core.network.NetworkModule.let { "https://api.github.com" }}/repos/$owner/$repo/actions/jobs/$jobId/logs")
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
            } catch (e: Exception) {
                _uiState.update { it.copy(isLoading = false, error = e.message ?: "Failed to load logs") }
            }
        }
    }
}
