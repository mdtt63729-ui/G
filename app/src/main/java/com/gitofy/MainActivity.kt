package com.gitofy

import android.os.Bundle
import android.Manifest
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.scaleIn
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.runtime.getValue
import androidx.core.splashscreen.SplashScreen.Companion.installSplashScreen
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.gitofy.core.designsystem.theme.GITOFYTheme
import com.gitofy.core.navigation.GITOFYNavHost
import com.gitofy.feature.splash.SplashViewModel
import com.gitofy.core.settings.AppSettingsViewModel
import com.gitofy.core.settings.ThemeMode
import com.gitofy.core.settings.FontFamilyOption
import androidx.compose.ui.text.font.FontFamily
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

    // FIX (startup lag): the previous version held the system splash on
    // screen for a *fixed* 3 seconds no matter how quickly the destination
    // and settings resolved (they usually resolve in well under 100ms). That
    // fixed floor is exactly what read as "app takes forever to reopen /
    // laggy restart" — every cold start paid the full 3s tax even when
    // there was nothing left to wait for. The splash is now released as
    // soon as both the destination and settings are ready, with only a
    // small minimum-visible-time floor (just enough to avoid an unpleasant
    // flash on the rare instant-resolve case) instead of an artificial wait.
    private var isContentReady = false

    /**
     * Keep the window edge-to-edge configuration stable for the entire Activity
     * lifetime. Re-applying immersive system-bar hiding from onResume/onFocus
     * causes WindowInsets to change during a minimize/restore cycle, which can
     * make Compose measure children twice and visibly move UI components.
     */
    private fun configureStableSystemBars() {
        WindowCompat.setDecorFitsSystemWindows(window, false)
        if (android.os.Build.VERSION.SDK_INT >= android.os.Build.VERSION_CODES.Q) {
            window.isNavigationBarContrastEnforced = false
        }
        WindowInsetsControllerCompat(window, window.decorView).apply {
            // Keep system bars available and transparent. Compose consumes the
            // stable insets instead of entering/leaving immersive mode on every
            // resume, preventing the reported minimize/restore layout jump.
            isAppearanceLightStatusBars = false
            isAppearanceLightNavigationBars = false
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        val splashScreen = installSplashScreen()
        splashScreen.setKeepOnScreenCondition { !isContentReady }

        super.onCreate(savedInstanceState)
        // Configure the window once. Do not reconfigure bars from onResume;
        // changing inset policy during restore is what causes visible layout
        // jumps on devices that briefly report transient system-bar insets.
        configureStableSystemBars()

        setContent {
            val notificationPermissionLauncher = androidx.activity.compose.rememberLauncherForActivityResult(
                ActivityResultContracts.RequestPermission()
            ) { }
            androidx.compose.runtime.LaunchedEffect(Unit) {
                if (android.os.Build.VERSION.SDK_INT >= 33 &&
                    androidx.core.app.NotificationManagerCompat.from(this@MainActivity).areNotificationsEnabled()) {
                    // Permission is already available; no prompt required.
                } else if (android.os.Build.VERSION.SDK_INT >= 33) {
                    notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
                }
            }
            // PRD §1.2: Obtain AppSettingsViewModel through the Compose
            // HiltViewModelFactory — not via Activity-level by viewModels().
            val appSettingsViewModel: AppSettingsViewModel = hiltViewModel()
            val startupViewModel: SplashViewModel = hiltViewModel()
            val settings by appSettingsViewModel.settings.collectAsStateWithLifecycle()
            val startupDestination by startupViewModel.destination.collectAsStateWithLifecycle()
            val systemDark = isSystemInDarkTheme()

            val darkTheme = when (settings.themeMode) {
                ThemeMode.LIGHT -> false
                ThemeMode.DARK -> true
                ThemeMode.SYSTEM -> systemDark
            }

            // FIX (startup lag): release the splash the moment we actually
            // have something to show, bounded by a small minimum-visible
            // floor for a clean handoff — not a fixed multi-second wait.
            var minTimeElapsed by remember { mutableStateOf(false) }
            LaunchedEffect(Unit) {
                kotlinx.coroutines.delay(180L)
                minTimeElapsed = true
            }
            var contentVisible by remember { mutableStateOf(false) }
            LaunchedEffect(startupDestination, minTimeElapsed) {
                if (startupDestination != null && minTimeElapsed) {
                    isContentReady = true
                    contentVisible = true
                } else if (startupDestination == null) {
                    // Safety net: never hang on splash indefinitely even if
                    // route resolution is unexpectedly slow.
                    kotlinx.coroutines.delay(3000L)
                    if (!isContentReady) {
                        isContentReady = true
                        contentVisible = true
                    }
                }
            }

            GITOFYTheme(
                darkTheme = darkTheme,
                dynamicColor = settings.dynamicColor,
                amoledMode = settings.amoledMode,
                accentColorHex = settings.accentColorHex,
                fontFamily = when (settings.fontFamily) {
                    FontFamilyOption.DEFAULT -> FontFamily.Default
                    FontFamilyOption.SERIF -> FontFamily.Serif
                    FontFamilyOption.MONOSPACE -> FontFamily.Monospace
                    FontFamilyOption.SYSTEM -> FontFamily.SansSerif
                }
            ) {
                val effectiveDestination = startupDestination
                    ?: if (isContentReady) com.gitofy.core.navigation.Routes.AUTH else null
                effectiveDestination?.let { destination ->
                    // Smooth, premium hand-off from the native splash into
                    // the first Compose screen: fade + gentle scale-up
                    // instead of a hard cut.
                    AnimatedVisibility(
                        visible = contentVisible,
                        enter = fadeIn(tween(360)) + scaleIn(
                            initialScale = 0.96f,
                            animationSpec = tween(360)
                        )
                    ) {
                        GITOFYNavHost(startDestination = destination)
                    }
                }
            }
        }
    }
}
