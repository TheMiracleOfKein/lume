package com.lume.app.data.downloads

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lume.app.LumeApp
import com.lume.app.R
import com.lume.app.data.db.DownloadQueueEntity
import com.lume.app.ui.nav.AppLinks
import com.lume.app.ui.nav.Routes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * Runs the download queue as a foreground job: a notification shows what is downloading and
 * offers Pause. WorkManager starts the job again if Android stops the app mid-download.
 */
class DownloadWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = (applicationContext as? LumeApp)?.container ?: return Result.success()
        val queue = app.downloadQueue
        ensureChannel(applicationContext)
        // Starting a foreground job from the background is not always allowed (Android 12+);
        // the downloads still run, only without the ongoing notification.
        runCatching { setForeground(foregroundInfo(queue.items.value)) }
        coroutineScope {
            val updates = launch {
                queue.items.collectLatest { items ->
                    runCatching { setForeground(foregroundInfo(items)) }
                }
            }
            // Rows queued while the last pass was finishing would otherwise wait for the next start.
            do {
                queue.runAll()
            } while (!app.prefs.downloadsPaused.value && queue.hasWaiting())
            updates.cancel()
        }
        val failed = queue.items.value.count { it.status == DownloadQueueEntity.STATUS_ERROR }
        if (failed > 0 && !app.prefs.downloadsPaused.value) notifyFailed(failed)
        return Result.success()
    }

    override suspend fun getForegroundInfo(): ForegroundInfo {
        val items = (applicationContext as? LumeApp)?.container?.downloadQueue?.items?.value.orEmpty()
        return foregroundInfo(items)
    }

    private fun foregroundInfo(items: List<DownloadQueueEntity>): ForegroundInfo {
        val running = items.filter { it.status == DownloadQueueEntity.STATUS_RUNNING }
        val left = items.count { it.status == DownloadQueueEntity.STATUS_PENDING || it.status == DownloadQueueEntity.STATUS_RUNNING }
        val ctx = applicationContext
        val title = ctx.resources.getQuantityString(R.plurals.notif_downloads_title, left.coerceAtLeast(1), left.coerceAtLeast(1))
        val text = running.joinToString(" · ") { "${it.titleName} — ${it.chapterName}" }
        val progress = running.firstOrNull()?.progress ?: 0f
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(title)
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(100, (progress * 100).toInt(), running.isEmpty())
            .setContentIntent(openDownloads(ctx))
            .addAction(0, ctx.getString(R.string.downloads_pause), actionIntent(ctx, ACTION_PAUSE))
            .addAction(0, ctx.getString(R.string.downloads_cancel_all), actionIntent(ctx, ACTION_CANCEL_ALL))
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    private fun notifyFailed(count: Int) {
        val ctx = applicationContext
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lume)
            .setContentTitle(ctx.resources.getQuantityString(R.plurals.notif_downloads_failed, count, count))
            .setContentText(ctx.getString(R.string.notif_downloads_failed_text))
            .setContentIntent(openDownloads(ctx))
            .setAutoCancel(true)
            .build()
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(ctx, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(ctx).notify(FAILED_NOTIF_ID, notification)
        } catch (_: SecurityException) {
        }
    }

    companion object {
        private const val WORK_NAME = "downloads"
        private const val CHANNEL_ID = "downloads"
        private const val NOTIF_ID = 43
        private const val FAILED_NOTIF_ID = 44
        const val ACTION_PAUSE = "com.lume.app.downloads.PAUSE"
        const val ACTION_CANCEL_ALL = "com.lume.app.downloads.CANCEL_ALL"

        fun start(context: Context) {
            val request = OneTimeWorkRequestBuilder<DownloadWorker>().build()
            // A running job picks up new rows by itself; only start one when none runs.
            WorkManager.getInstance(context).enqueueUniqueWork(WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_downloads_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }

        private fun openDownloads(context: Context): PendingIntent = PendingIntent.getActivity(
            context,
            NOTIF_ID,
            AppLinks.intentFor(context, Routes.Downloads),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        private fun actionIntent(context: Context, action: String): PendingIntent = PendingIntent.getBroadcast(
            context,
            action.hashCode(),
            Intent(context, DownloadActionReceiver::class.java).setAction(action),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
    }
}

/** Buttons of the download notification. */
class DownloadActionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val queue = (context.applicationContext as? LumeApp)?.container?.downloadQueue ?: return
        when (intent.action) {
            DownloadWorker.ACTION_PAUSE -> queue.pause()
            DownloadWorker.ACTION_CANCEL_ALL -> queue.cancelAll()
        }
    }
}
