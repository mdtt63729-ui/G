package com.gitofy.feature.settings.github

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.network.GitHubApiService
import com.gitofy.core.security.PermissionPreflight
import com.gitofy.core.security.SecureCredentialStorage
import com.gitofy.data.remote.dto.GitHubUser
import com.gitofy.data.remote.dto.OrgDto
import com.gitofy.data.remote.dto.RateLimitInfo
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import javax.inject.Inject

data class GitHubSettingsUiState(
    val isLoading: Boolean = false,
    val isConnected: Boolean = false,
    val user: GitHubUser? = null,
    val organizations: List<OrgDto> = emptyList(),
    val rateLimit: RateLimitInfo? = null,
    val permissions: List<PermissionPreflight.PermissionResult> = emptyList(),
    val error: String? = null,
    val lastRefreshAt: Long? = null
)

@HiltViewModel
class GitHubSettingsViewModel @Inject constructor(
    private val api: GitHubApiService,
    private val secureStorage: SecureCredentialStorage,
    private val permissionPreflight: PermissionPreflight
) : ViewModel() {

    private val _uiState = MutableStateFlow(
        GitHubSettingsUiState(isConnected = secureStorage.hasToken())
    )
    val uiState: StateFlow<GitHubSettingsUiState> = _uiState.asStateFlow()

    init {
        refresh()
    }

    fun refresh() {
        val token = secureStorage.getToken()
        if (token.isNullOrBlank()) {
            _uiState.value = GitHubSettingsUiState(isConnected = false, error = "Connect a GitHub token to load account settings.")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, isConnected = true, error = null)
        viewModelScope.launch {
            runCatching {
                withContext(Dispatchers.IO) {
                    val userResponse = api.getAuthenticatedUser()
                    val user = userResponse.body()
                        ?: throw IllegalStateException("GitHub account could not be loaded (${userResponse.code()}).")
                    val orgs = api.listOrganizations().body().orEmpty()
                    val rateResponse = api.getRateLimit()
                    val rateBody = rateResponse.body()
                    val rate = rateBody?.resources?.core ?: rateBody?.rate
                    val permissions = permissionPreflight.checkPermissions(token)
                    Triple(user, orgs, Pair(rate, permissions))
                }
            }.onSuccess { (user, orgs, details) ->
                _uiState.value = GitHubSettingsUiState(
                    isLoading = false,
                    isConnected = true,
                    user = user,
                    organizations = orgs,
                    rateLimit = details.first,
                    permissions = details.second,
                    lastRefreshAt = System.currentTimeMillis()
                )
                secureStorage.saveUserData(user.login, user.avatarUrl)
            }.onFailure { error ->
                _uiState.value = _uiState.value.copy(
                    isLoading = false,
                    isConnected = true,
                    error = error.message ?: "Unable to load GitHub settings."
                )
            }
        }
    }
}
