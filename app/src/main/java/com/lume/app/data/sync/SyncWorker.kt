package com.lume.app.data.sync

import android.content.Context
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lume.app.LumeApp
import com.lume.app.data.prefs.SyncSettings
import java.util.concurrent.TimeUnit

/** Background sync (Settings → Sync): every few hours, and once soon after the app starts. */
class SyncWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = (applicationContext as? LumeApp)?.container ?: return Result.success()
        val status = app.sync.sync() ?: return Result.success()
        return if (status.error == null || runAttemptCount >= 2) Result.success() else Result.retry()
    }

    companion object {
        private const val PERIODIC = "sync_periodic"
        private const val ONCE = "sync_once"

        fun schedule(context: Context, settings: SyncSettings) {
            val wm = WorkManager.getInstance(context)
            if (!settings.ready || settings.intervalHours <= 0) {
                wm.cancelUniqueWork(PERIODIC)
                return
            }
            val request = PeriodicWorkRequestBuilder<SyncWorker>(settings.intervalHours.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints(settings))
                .build()
            wm.enqueueUniquePeriodicWork(PERIODIC, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** One sync as soon as the constraints allow (app start, coming back online). */
        fun runSoon(context: Context, settings: SyncSettings) {
            if (!settings.ready) return
            val request = OneTimeWorkRequestBuilder<SyncWorker>()
                .setConstraints(constraints(settings))
                .setInitialDelay(5, TimeUnit.SECONDS)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(ONCE, ExistingWorkPolicy.KEEP, request)
        }

        // A folder may be on the phone (Syncthing syncs it later), so only WebDAV needs the network.
        private fun constraints(settings: SyncSettings) = Constraints.Builder()
            .setRequiredNetworkType(if (settings.backend == SyncSettings.WEBDAV) NetworkType.CONNECTED else NetworkType.NOT_REQUIRED)
            .build()
    }
}
