package com.gitofy.feature.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.GitOFYError
import com.gitofy.domain.usecase.AuthenticateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import javax.inject.Inject

enum class AuthenticationStatus { Idle, Validating, Success, Error }

data class AuthUiState(
    val token: String = "",
    val status: AuthenticationStatus = AuthenticationStatus.Idle,
    val error: String? = null,
    val isAuthError: Boolean = false
) {
    val isLoading: Boolean get() = status == AuthenticationStatus.Validating
}

@HiltViewModel
class AuthenticationViewModel @Inject constructor(
    private val authenticateUseCase: AuthenticateUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun onTokenChange(token: String) {
        _uiState.update {
            it.copy(
                token = token,
                status = AuthenticationStatus.Idle,
                error = null,
                isAuthError = false
            )
        }
    }

    fun authenticate() {
        val token = _uiState.value.token.trim()
        if (token.isEmpty()) {
            _uiState.update {
                it.copy(status = AuthenticationStatus.Error, error = "Enter your GitHub token.")
            }
            return
        }

        if (token.length < 20) {
            _uiState.update {
                it.copy(status = AuthenticationStatus.Error, error = "That token looks incomplete. Check it and try again.")
            }
            return
        }

        _uiState.update { it.copy(status = AuthenticationStatus.Validating, error = null) }

        viewModelScope.launch {
            val result = authenticateUseCase(token)
            result.fold(
                onSuccess = {
                    // Persisted authentication is complete; do not retain the raw
                    // credential in the UI state after the hand-off succeeds.
                    _uiState.update {
                        it.copy(
                            token = "",
                            status = AuthenticationStatus.Success,
                            error = null
                        )
                    }
                },
                onFailure = { error ->
                    val message = when (error) {
                        is GitOFYError.AuthenticationRequired ->
                            "Invalid token. Check the token and try again."
                        is GitOFYError.InsufficientPermission ->
                            "This token does not have the permissions GITOFY needs."
                        is GitOFYError.NoNetwork ->
                            "No network connection. Check your connection and retry."
                        is GitOFYError.NetworkTimeout ->
                            "The request timed out. Please retry."
                        is GitOFYError.GitHubApiError ->
                            if (error.code >= 500) "GitHub is temporarily unavailable. Please retry."
                            else "GitHub rejected the request. Check your token and permissions."
                        else ->
                            "Authentication failed. Please retry."
                    }
                    _uiState.update {
                        it.copy(
                            status = AuthenticationStatus.Error,
                            error = message,
                            isAuthError = error is GitOFYError.AuthenticationRequired
                        )
                    }
                }
            )
        }
    }

    fun clearError() {
        _uiState.update { it.copy(error = null, status = AuthenticationStatus.Idle) }
    }
}
