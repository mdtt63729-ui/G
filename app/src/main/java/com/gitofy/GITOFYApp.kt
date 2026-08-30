package com.gitofy

import android.app.Application
import androidx.hilt.work.HiltWorkerFactory
import androidx.work.Configuration
import com.gitofy.core.common.NetworkConnectivity
import com.gitofy.core.notification.NotificationHelper
import dagger.hilt.android.HiltAndroidApp
import javax.inject.Inject

@HiltAndroidApp
class GITOFYApp : Application(), Configuration.Provider {

    @Inject lateinit var workerFactory: HiltWorkerFactory
    @Inject lateinit var networkConnectivity: NetworkConnectivity
    @Inject lateinit var notificationHelper: NotificationHelper

    override val workManagerConfiguration: Configuration
        get() = Configuration.Builder()
            .setWorkerFactory(workerFactory)
            .build()

    override fun onCreate() {
        super.onCreate()
        networkConnectivity.register()
        // PRD §73: Register notification channels (build success/failure etc.) once at startup.
        notificationHelper.createChannels()
    }
}
