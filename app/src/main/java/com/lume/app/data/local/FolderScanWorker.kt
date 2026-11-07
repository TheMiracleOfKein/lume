package com.lume.app.data.local

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lume.app.LumeApp
import com.lume.app.R
import com.lume.app.data.library.LibraryUpdateWorker
import com.lume.app.ui.nav.AppLinks
import com.lume.app.ui.nav.Routes
import java.util.concurrent.TimeUnit

/** Re-scans the watched folders for new chapters, every few hours or on "Scan now". */
class FolderScanWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {
    override suspend fun doWork(): Result {
        val app = (applicationContext as? LumeApp)?.container ?: return Result.success()
        val summary = runCatching { app.folderScanner.scanAll() }.getOrElse { return Result.retry() }
        if (summary.newChapters > 0) notifyNew(summary.newChapters)
        return Result.success()
    }

    private fun notifyNew(count: Int) {
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        manager.createNotificationChannel(
            NotificationChannel(
                LibraryUpdateWorker.CHANNEL_ID,
                applicationContext.getString(R.string.notif_library_updates_channel),
                NotificationManager.IMPORTANCE_DEFAULT,
            ),
        )
        val notification = NotificationCompat.Builder(applicationContext, LibraryUpdateWorker.CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lume)
            .setContentTitle(applicationContext.getString(R.string.notif_library_updates_title))
            .setContentText(applicationContext.resources.getQuantityString(R.plurals.notif_folder_scan_new, count, count))
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    NOTIF_ID,
                    AppLinks.intentFor(applicationContext, Routes.browse("local")),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setAutoCancel(true)
            .build()
        // Lint requires the check to be in the same method as notify().
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(applicationContext, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // User revoked notification permission between check and notify.
        }
    }

    companion object {
        private const val WORK_NAME = "folder_scan"
        private const val NOW_WORK_NAME = "folder_scan_now"
        private const val NOTIF_ID = 43

        /** Every [hours] hours; 0 = off (only "Scan now"). */
        fun schedule(context: Context, hours: Int) {
            val wm = WorkManager.getInstance(context)
            if (hours <= 0) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val request = PeriodicWorkRequestBuilder<FolderScanWorker>(hours.toLong(), TimeUnit.HOURS).build()
            wm.enqueueUniquePeriodicWork(WORK_NAME, ExistingPeriodicWorkPolicy.UPDATE, request)
        }

        /** "Scan now": one run right away. A run in progress is kept. */
        fun runNow(context: Context) {
            WorkManager.getInstance(context)
                .enqueueUniqueWork(NOW_WORK_NAME, ExistingWorkPolicy.KEEP, OneTimeWorkRequestBuilder<FolderScanWorker>().build())
        }
    }
}
