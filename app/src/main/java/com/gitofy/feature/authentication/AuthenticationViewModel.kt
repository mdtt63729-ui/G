package com.gitofy.feature.authentication

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.domain.model.AuthState
import com.gitofy.domain.model.GitOFYError
import com.gitofy.domain.usecase.AuthenticateUseCase
import com.gitofy.domain.usecase.ObserveAuthStateUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

data class AuthUiState(
    val token: String = "",
    val isLoading: Boolean = false,
    val error: String? = null,
    val isAuthError: Boolean = false
)

@HiltViewModel
class AuthenticationViewModel @Inject constructor(
    private val authenticateUseCase: AuthenticateUseCase
) : ViewModel() {

    private val _uiState = MutableStateFlow(AuthUiState())
    val uiState = _uiState.asStateFlow()

    fun onTokenChange(token: String) {
        _uiState.value = _uiState.value.copy(token = token, error = null)
    }

    fun authenticate() {
        val token = _uiState.value.token.trim()
        if (token.isEmpty()) {
            _uiState.value = _uiState.value.copy(error = "Please enter your GitHub token")
            return
        }

        _uiState.value = _uiState.value.copy(isLoading = true, error = null)

        viewModelScope.launch {
            val result = authenticateUseCase(token)
            result.fold(
                onSuccess = {
                    _uiState.value = _uiState.value.copy(isLoading = false, error = null)
                },
                onFailure = { error ->
                    val message = when (error) {
                        is GitOFYError.AuthenticationRequired -> "Authentication failed. Check your token and try again."
                        is GitOFYError.InsufficientPermission -> "Insufficient permissions. Ensure your token has repo and workflow access."
                        is GitOFYError.NoNetwork -> "No internet connection. Please check your network."
                        is GitOFYError.NetworkTimeout -> "Network timeout. Please try again."
                        else -> "Authentication failed. ${error.message}"
                    }
                    _uiState.value = _uiState.value.copy(
                        isLoading = false,
                        error = message,
                        isAuthError = error is GitOFYError.AuthenticationRequired
                    )
                }
            )
        }
    }
}
