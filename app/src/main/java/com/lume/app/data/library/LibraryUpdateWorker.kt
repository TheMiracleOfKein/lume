package com.lume.app.data.library

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
import androidx.work.Constraints
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import com.lume.app.LumeApp
import com.lume.app.R
import com.lume.app.data.db.TitleMetaEntity
import com.lume.app.domain.LibraryUpdateResult
import com.lume.app.ui.nav.AppLinks
import com.lume.app.ui.nav.Routes
import java.util.concurrent.TimeUnit

class LibraryUpdateWorker(
    appContext: Context,
    params: WorkerParameters,
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val app = (applicationContext as? LumeApp)?.container ?: return Result.success()
        val dao = app.dao
        val results = mutableListOf<LibraryUpdateResult>()
        // A cold start for this job alone would otherwise see an empty source list and do nothing.
        app.sources.awaitLoaded()

        for (bookmark in dao.allBookmarks()) {
            val source = app.sources.sourceById(bookmark.sourceId) ?: continue
            runCatching {
                val chapters = source.getChapters(bookmark.titleId)
                // The title screen shows the cached list first; keep it current.
                app.titles.updateChapters(bookmark.sourceId, bookmark.titleId, chapters)
                val count = chapters.size
                val last = chapters.firstOrNull()
                val prev = dao.getTitleMeta(bookmark.sourceId, bookmark.titleId)
                val prevCount = prev?.knownChapterCount ?: count
                val newCount = if (prev == null) 0 else (count - prevCount).coerceAtLeast(0)
                dao.upsertTitleMeta(
                    TitleMetaEntity(
                        sourceId = bookmark.sourceId,
                        titleId = bookmark.titleId,
                        lastChapterId = last?.id,
                        lastChapterName = last?.title,
                        lastCheckedAt = System.currentTimeMillis(),
                        knownChapterCount = count,
                        unreadNewCount = (prev?.unreadNewCount ?: 0) + newCount,
                    ),
                )
                if (newCount > 0) {
                    results += LibraryUpdateResult(
                        sourceId = bookmark.sourceId,
                        titleId = bookmark.titleId,
                        titleName = bookmark.titleName,
                        newChapterCount = newCount,
                    )
                }
            }
        }

        if (results.isNotEmpty()) {
            notifyUpdates(results)
        }
        return Result.success()
    }

    private fun notifyUpdates(results: List<LibraryUpdateResult>) {
        ensureChannel()
        val title = applicationContext.getString(R.string.notif_library_updates_title)
        val text = if (results.size == 1) {
            applicationContext.getString(
                R.string.notif_library_updates_one,
                results[0].titleName,
                results[0].newChapterCount,
            )
        } else {
            applicationContext.getString(R.string.notif_library_updates_many, results.size)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_lume)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    results.joinToString("\n") {
                        applicationContext.getString(
                            R.string.notif_library_updates_one,
                            it.titleName,
                            it.newChapterCount,
                        )
                    },
                ),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    NOTIF_ID,
                    AppLinks.intentFor(applicationContext, Routes.Library),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .setAutoCancel(true)
            .build()
        // Lint requires the check to be in the same method as notify().
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(
                applicationContext,
                Manifest.permission.POST_NOTIFICATIONS,
            ) != PackageManager.PERMISSION_GRANTED
        ) {
            return
        }
        try {
            NotificationManagerCompat.from(applicationContext).notify(NOTIF_ID, notification)
        } catch (_: SecurityException) {
            // User revoked notification permission between check and notify.
        }
    }

    private fun ensureChannel() {
        val manager = applicationContext.getSystemService(NotificationManager::class.java) ?: return
        val channel = NotificationChannel(
            CHANNEL_ID,
            applicationContext.getString(R.string.notif_library_updates_channel),
            NotificationManager.IMPORTANCE_DEFAULT,
        )
        manager.createNotificationChannel(channel)
    }

    companion object {
        private const val WORK_NAME = "library_update"
        private const val CHANNEL_ID = "library_updates"
        private const val NOTIF_ID = 42

        fun schedule(context: Context, enabled: Boolean, intervalHours: Int = 6, wifiOnly: Boolean = false) {
            val wm = WorkManager.getInstance(context)
            if (!enabled) {
                wm.cancelUniqueWork(WORK_NAME)
                return
            }
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(if (wifiOnly) NetworkType.UNMETERED else NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<LibraryUpdateWorker>(intervalHours.toLong(), TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            wm.enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.UPDATE,
                request,
            )
        }
    }
}
