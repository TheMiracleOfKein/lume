package com.lume.app.data.backup

import android.content.Context
import android.net.Uri
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lume.app.LumeApp
import java.util.concurrent.TimeUnit

/** Automatic backups (Settings → Backups): every few days, keeping the newest few. */
class BackupWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = (applicationContext as? LumeApp)?.container ?: return Result.success()
        app.sources.awaitLoaded()
        return runCatching {
            app.backup.autoBackup(
                keep = app.prefs.autoBackupKeep.value,
                folder = app.prefs.autoBackupFolder.value?.let(Uri::parse),
            )
        }.fold(onSuccess = { Result.success() }, onFailure = { Result.retry() })
    }

    companion object {
        private const val WORK_NAME = "auto_backup"

        fun schedule(context: Context, days: Int) {
            val wm = WorkManager.getInstance(context)
            if (days <= 0) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<BackupWorker>(days.toLong(), TimeUnit.DAYS).build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }
    }
}
