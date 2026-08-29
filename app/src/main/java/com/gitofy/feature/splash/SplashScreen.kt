package com.gitofy.feature.splash

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle

/**
 * PRD §2: Unified Splash Screen — no duplicate logo.
 *
 * The Android 12+ system splash screen (installed via installSplashScreen()
 * in MainActivity) is the SOLE visual entry point. It shows the GITOFY logo
 * using the system's built-in animation.
 *
 * This composable previously rendered a SECOND full-screen custom splash with
 * its own logo Image, breathing animation, and zoom-through exit — causing
 * the duplicate-icon problem described in the PRD. That entire custom splash
 * has been removed.
 *
 * What remains is a transparent bridge that simply waits for the ViewModel to
 * resolve the destination (Home or Auth) and then navigates. No second logo,
 * no breathing animation, no zoom-through exit — the system splash handles
 * all of that. The background colour matches the Compose theme so there is no
 * white flash between the system splash and the first screen.
 *
 * Cold start and warm start are both handled: on warm start the system splash
 * is near-instant, and this composable resolves the destination just as fast.
 */
@Composable
fun SplashScreen(
    onNavigate: (String) -> Unit,
    viewModel: SplashViewModel = hiltViewModel()
) {
    val destination by viewModel.destination.collectAsStateWithLifecycle()

    // PRD §2: No artificial delay. As soon as the destination is resolved,
    // navigate immediately. The system splash has already been dismissed.
    LaunchedEffect(destination) {
        destination?.let(onNavigate)
    }

    // Transparent bridge screen — no logo, no animation.
    // The background matches the Compose theme background so there is no
    // white flash while the destination resolves.
    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background),
        contentAlignment = Alignment.Center
    ) {
        // Intentionally empty — the system splash already showed the logo.
    }
}
