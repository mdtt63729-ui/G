package com.gitofy.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.navigation.Routes
import com.gitofy.domain.usecase.CheckStoredCredentialsUseCase
import com.gitofy.domain.usecase.ObserveAuthStateUseCase
import com.gitofy.domain.model.AuthState
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val checkStoredCredentials: CheckStoredCredentialsUseCase,
    private val observeAuthState: ObserveAuthStateUseCase
) : ViewModel() {

    private val _destination = MutableStateFlow<String?>(null)
    val destination = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            delay(800) // Brief splash display
            val hasCredentials = checkStoredCredentials()
            _destination.value = if (hasCredentials) Routes.HOME else Routes.AUTH
        }
    }
}
