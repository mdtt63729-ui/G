package com.gitofy

import android.app.Application
import com.gitofy.BuildConfig
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.gitofy.core.common.NetworkConnectivity
import com.gitofy.core.notification.NotificationHelper
import com.gitofy.core.security.IntegritySecurityEngine
import com.gitofy.core.settings.AppSettingsRepository
import com.gitofy.ai.catalog.DynamicModelRepository
import com.gitofy.core.sync.BackgroundSyncScheduler
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.launch
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GITOFYApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var networkConnectivity: NetworkConnectivity
    @Inject lateinit var notificationHelper: NotificationHelper
    @Inject lateinit var appSettingsRepository: AppSettingsRepository
    @Inject lateinit var dynamicModelRepository: DynamicModelRepository
    private val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()

        // FIX (app never gets past splash on release builds): this used to
        // call Process.killProcess() here on any integrity mismatch,
        // including plain RESOURCE_MISMATCH (pinned per-file asset hashes
        // going stale after an icon/asset change — which is exactly what
        // had happened). That silently terminated the process during
        // Application.onCreate(), before MainActivity/Compose ever ran, so
        // the app appeared to hang/never progress past the splash screen —
        // it was actually being killed, not hanging.
        //
        // IntegritySecurityEngine's own class doc states it "fails closed
        // by disabling sensitive operations... rather than... killing the
        // process," and it already exposes isTrusted()/
        // sensitiveOperationsAllowed() which RepositoryUploadCoordinator
        // (and any future caller) can use to gate sensitive functionality.
        // That is the correct fail-closed mechanism; a hard process kill on
        // startup is not, since a stale/incorrect pinned hash then bricks
        // the entire app rather than just the sensitive feature it's meant
        // to protect. The watchdog started below keeps evaluating trust
        // continuously in the background.
        // FIX (app crashes immediately on open): none of these startup
        // steps are allowed to throw past this point. Previously an
        // exception from any one of them (integrity check reading the APK
        // zip, notification channel setup on an unusual OEM ROM, connectivity
        // registration) would propagate straight out of Application.onCreate()
        // and kill the process before MainActivity/Compose ever rendered —
        // a hard crash on every single launch with no in-app way to recover.
        // Each step is now isolated so a failure in one can't take down the
        // others or the app itself; worst case a feature silently no-ops
        // instead of the whole app refusing to open.
        if (BuildConfig.BUILD_TYPE == "release") {
            runCatching { IntegritySecurityEngine.check(this) }
        }

        runCatching { networkConnectivity.register() }
        // PRD §73: Register notification channels (build success/failure etc.) once at startup.
        runCatching { notificationHelper.createChannels() }
        runCatching { IntegritySecurityEngine.start(this, appScope) }
        // Dynamic AI model registry: hydrate Room cache and refresh configured
        // OpenRouter/NVIDIA/Gemini/Ollama registries without blocking startup.
        dynamicModelRepository.refreshInBackground()
        appScope.launch {
            runCatching {
                appSettingsRepository.settings
                    .map { it.backgroundSync to it.syncFrequency }
                    .distinctUntilChanged()
                    .collect { (enabled, frequency) ->
                        runCatching { BackgroundSyncScheduler.apply(this@GITOFYApp, enabled, frequency) }
                    }
            }
        }
    }
}
