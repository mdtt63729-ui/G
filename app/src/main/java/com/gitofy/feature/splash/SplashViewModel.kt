package com.gitofy.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.navigation.Routes
import com.gitofy.domain.usecase.CheckStoredCredentialsUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val checkStoredCredentials: CheckStoredCredentialsUseCase
) : ViewModel() {

    private val _destination = MutableStateFlow<String?>(null)
    val destination = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            // Native Android 12+ splash is the visual entry point. Do not add
            // an artificial delay here; resolve the first destination ASAP.
            _destination.value = if (checkStoredCredentials()) Routes.HOME else Routes.AUTH
        }
    }
}
