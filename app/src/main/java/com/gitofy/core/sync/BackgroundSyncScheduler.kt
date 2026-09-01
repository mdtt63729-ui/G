package com.gitofy.core.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import com.gitofy.core.settings.SyncFrequency
import com.gitofy.worker.WorkflowSyncWorker
import java.util.concurrent.TimeUnit

object BackgroundSyncScheduler {
    private const val UNIQUE_WORK = "gitofy_background_sync"

    fun apply(context: Context, enabled: Boolean, frequency: SyncFrequency) {
        val manager = WorkManager.getInstance(context)
        if (!enabled || frequency == SyncFrequency.MANUAL) {
            manager.cancelUniqueWork(UNIQUE_WORK)
            return
        }
        val minutes = when (frequency) {
            SyncFrequency.FIFTEEN_MINUTES -> 15L
            SyncFrequency.THIRTY_MINUTES -> 30L
            SyncFrequency.HOUR -> 60L
            SyncFrequency.MANUAL -> return
        }
        val constraints = Constraints.Builder()
            .setRequiredNetworkType(NetworkType.CONNECTED)
            .build()
        val request = PeriodicWorkRequestBuilder<WorkflowSyncWorker>(minutes, TimeUnit.MINUTES)
            .setConstraints(constraints)
            .build()
        manager.enqueueUniquePeriodicWork(UNIQUE_WORK, ExistingPeriodicWorkPolicy.UPDATE, request)
    }
}
