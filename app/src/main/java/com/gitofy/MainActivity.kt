package com.gitofy

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.theme.GITOFYTheme
import com.gitofy.core.navigation.GITOFYNavHost
import com.gitofy.core.settings.AppSettingsViewModel
import com.gitofy.core.settings.ThemeMode
import dagger.hilt.android.AndroidEntryPoint

/**
 * PRD §1.2 — Hilt-compatible ViewModel lifecycle architecture.
 *
 * Previously AppSettingsViewModel was obtained via `by viewModels()` directly
 * on the Activity. While this works with @AndroidEntryPoint in many setups,
 * it can trigger the Hilt validation error:
 *   "Injection of an @HiltViewModel class is prohibited"
 * when the HiltViewModelFactory is not properly wired through the
 * ViewModelProvider. The fix is to NOT hold the ViewModel as an Activity
 * field at all — instead, obtain it inside setContent via the standard
 * `hiltViewModel()` composable function, which goes through the proper
 * HiltViewModelFactory → ViewModelProvider → @HiltViewModel chain:
 *
 *   Hilt → ViewModelProvider / hiltViewModel() → @HiltViewModel
 *
 * This ensures the generated Hilt code is used as-is (never manually edited)
 * and the ViewModel lifecycle is correctly scoped to the composition.
 */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    // PRD FIX (splash white-flash): true once the first real (non-default)
    // frame of themed Compose content has been laid out. The system splash
    // is kept on screen via setKeepOnScreenCondition until this flips —
    // previously the splash's return value was discarded entirely, so it
    // dismissed itself as soon as the Activity drew ANY frame (even before
    // settings/theme resolved), leaving a one-frame gap of the window's
    // default background — the reported "white flash" before the GitHub
    // token login page.
    private var isContentReady = false

    private fun hideSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        WindowInsetsControllerCompat(window, window.decorView).apply {
            hide(WindowInsetsCompat.Type.systemBars())
            systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
        window.navigationBarColor = android.graphics.Color.TRANSPARENT
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) hideSystemBars()
    }

    override fun onResume() {
        super.onResume()
        hideSystemBars()
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !isContentReady }

        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        hideSystemBars()

        setContent {
            // PRD §1.2: Obtain AppSettingsViewModel through the Compose
            // HiltViewModelFactory — not via Activity-level by viewModels().
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val settings by appSettingsViewModel.settings.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()

            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }

            // Settings has emitted at least once (its real, persisted value —
            // not just the StateFlow's construction-time default) and the
            // theme below is about to compose with final colors. Only now is
            // it safe to let the system splash hand off to themed content.
            LaunchedEffect(settings) {
                isContentReady = true
            }

            GITOFYTheme(
                darkTheme = darkTheme,
                dynamicColor = false,
                amoledMode = settings.amoledMode,
                accentColorHex = settings.accentColorHex
            ) {
                GITOFYNavHost()
            }
        }
    }
}
