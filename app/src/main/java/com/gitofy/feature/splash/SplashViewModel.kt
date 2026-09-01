package com.gitofy.feature.splash

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gitofy.core.navigation.Routes
import com.gitofy.domain.usecase.CheckStoredCredentialsUseCase
import com.gitofy.feature.onboarding.OnboardingRepository
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import javax.inject.Inject

@HiltViewModel
class SplashViewModel @Inject constructor(
    private val checkStoredCredentials: CheckStoredCredentialsUseCase,
    private val onboardingRepository: OnboardingRepository
) : ViewModel() {

    private val _destination = MutableStateFlow<String?>(null)
    val destination = _destination.asStateFlow()

    init {
        viewModelScope.launch {
            // FIX (splash hang): The previous version ran
            // checkStoredCredentials() — which touches EncryptedSharedPreferences
            // (Keystore-backed, synchronous disk + crypto I/O) — on the Main
            // dispatcher inside a non-cancellable blocking call. When the
            // Keystore was slow/corrupted on first cold launch, hasToken()
            // blocked the main thread, which in turn froze the 5-second safety
            // Handler and the LaunchedEffect that flips isContentReady. Result:
            // the app sat on the splash screen indefinitely.
            //
            // Both blocking reads are now moved onto Dispatchers.IO and each is
            // bounded by withTimeoutOrNull. If either exceeds the bound (or
            // throws), we fall back to the safe AUTH route so the app is always
            // usable and the splash never hangs.

            val onboarding = withTimeoutOrNull(SPLASH_RESOLVE_TIMEOUT_MS) {
                runCatching { withContext(Dispatchers.IO) { onboardingRepository.state.first() } }.getOrNull()
            }
            val hasToken = withTimeoutOrNull(SPLASH_RESOLVE_TIMEOUT_MS) {
                runCatching { withContext(Dispatchers.IO) { checkStoredCredentials() } }.getOrNull()
            } ?: false

            _destination.value = when {
                onboarding == null -> Routes.AUTH
                !onboarding.isCompleted && onboarding.currentStep > 0 -> Routes.ONBOARDING
                !onboarding.isCompleted -> Routes.INTRODUCTION
                hasToken -> Routes.HOME
                else -> Routes.AUTH
            }
        }
    }

    private companion object {
        // Generous bound: long enough for a cold DataStore / Keystore read,
        // short enough that the user never sees a stuck splash for more than
        // ~3 seconds.
        const val SPLASH_RESOLVE_TIMEOUT_MS = 3000L
    }
}
