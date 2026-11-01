package com.lume.app.data.library

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.ensureActive
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
import androidx.work.ExistingWorkPolicy
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lume.app.LumeApp
import com.lume.app.R
import com.lume.app.data.db.TitleMetaEntity
import com.lume.app.data.db.WATCHED_TITLES_LIMIT
import com.lume.app.domain.LibraryCheckOutcome
import com.lume.app.domain.LibraryUpdateResult
import com.lume.app.domain.LinkedChapterId
import com.lume.app.domain.dedupeChapters
import com.lume.app.domain.inReadingOrder
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
        var checked = 0
        var failed = 0
        // A cold start for this job alone would otherwise see an empty source list and do nothing.
        app.sources.awaitLoaded()

        // Only titles with the bell: checking hundreds of titles takes too long and strains the sites.
        // Until any title has one, the most recently read titles stand in.
        val bookmarks = dao.watchedBookmarks().ifEmpty { dao.recentBookmarks(WATCHED_TITLES_LIMIT) }
        setProgress(workDataOf(KEY_DONE to 0, KEY_TOTAL to bookmarks.size))
        for ((index, bookmark) in bookmarks.withIndex()) {
            if (app.sources.sourceById(bookmark.sourceId) != null) try {
                checked++
                // The main source and every linked one, merged; the title screen shows it at once.
                val (chapters, all) = app.titles.fetchChapterLists(bookmark.sourceId, bookmark.titleId)
                // New = ids never seen before (not "more chapters than last time"), one per chapter.
                // With linked sources, the copy found first is the new one.
                val fresh = app.libraryChapters.sync(bookmark.sourceId, bookmark.titleId, all)
                val prev = dao.getTitleMeta(bookmark.sourceId, bookmark.titleId)
                val last = chapters.firstOrNull()
                dao.upsertTitleMeta(
                    TitleMetaEntity(
                        sourceId = bookmark.sourceId,
                        titleId = bookmark.titleId,
                        lastChapterId = last?.id,
                        lastChapterName = last?.title,
                        lastCheckedAt = System.currentTimeMillis(),
                        knownChapterCount = chapters.size,
                        unreadNewCount = (prev?.unreadNewCount ?: 0) + fresh.size,
                    ),
                )
                val titlePrefs = if (fresh.isEmpty()) null else dao.getTitlePrefs(bookmark.sourceId, bookmark.titleId)
                if (fresh.isNotEmpty() && app.prefs.autoDownloadMode.value.downloads(titlePrefs?.autoDownload == true)) {
                    // One copy per chapter, in the team this title reads.
                    val branch = titlePrefs?.branch
                    app.downloadQueue.enqueueAll(
                        bookmark.sourceId,
                        bookmark.titleId,
                        dedupeChapters(fresh, branch).inReadingOrder(),
                        bookmark.titleName,
                        bookmark.coverUrl,
                    )
                }
                if (fresh.isNotEmpty()) {
                    val multiSource = all.any { LinkedChapterId.isLinked(it.id) }
                    results += LibraryUpdateResult(
                        sourceId = bookmark.sourceId,
                        titleId = bookmark.titleId,
                        titleName = bookmark.titleName,
                        newChapterCount = fresh.size,
                        firstOn = if (!multiSource) emptyList() else fresh
                            .map { LinkedChapterId.sourceOf(it.id, bookmark.sourceId) }
                            .distinct()
                            .map { id -> app.sources.sourceById(id)?.manifest?.name ?: id },
                    )
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                failed++
                android.util.Log.w("LibraryUpdate", "${bookmark.titleName}: ${e.message}")
            }
            currentCoroutineContext().ensureActive()
            setProgress(workDataOf(KEY_DONE to index + 1, KEY_TOTAL to bookmarks.size))
        }
        val outcome = LibraryCheckOutcome(checked, failed)
        app.prefs.setLibraryLastCheck(System.currentTimeMillis(), outcome)

        if (results.isNotEmpty()) {
            notifyUpdates(results)
        }
        // A run the user started reports its outcome at once; only the background check retries.
        val manual = inputData.getBoolean(KEY_MANUAL, false)
        return when {
            !manual && outcome.shouldRetry(runAttemptCount) -> Result.retry()
            outcome.allFailed -> Result.failure()
            else -> Result.success()
        }
    }

    private fun notifyUpdates(results: List<LibraryUpdateResult>) {
        ensureChannel()
        val title = applicationContext.getString(R.string.notif_library_updates_title)
        val text = if (results.size == 1) {
            line(results[0])
        } else {
            applicationContext.resources.getQuantityString(R.plurals.notif_library_updates_many, results.size, results.size)
        }
        val notification = NotificationCompat.Builder(applicationContext, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_stat_lume)
            .setContentTitle(title)
            .setContentText(text)
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(results.joinToString("\n") { line(it) }),
            )
            .setPriority(NotificationCompat.PRIORITY_DEFAULT)
            .setContentIntent(
                PendingIntent.getActivity(
                    applicationContext,
                    NOTIF_ID,
                    AppLinks.intentFor(applicationContext, Routes.Updates),
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

    /** "Title: +2", and for titles with linked sources "Title: +2 · first on MangaDex". */
    private fun line(result: LibraryUpdateResult): String {
        val base = applicationContext.getString(R.string.notif_library_updates_one, result.titleName, result.newChapterCount)
        if (result.firstOn.isEmpty()) return base
        return applicationContext.getString(R.string.notif_library_updates_first_on, base, result.firstOn.joinToString())
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
        const val WORK_NAME = "library_update"
        const val NOW_WORK_NAME = "library_update_now"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_MANUAL = "manual"

        /**
         * "Update now": one run right away, whatever the schedule says. A run in progress is kept.
         * No network constraint: offline, the run fails fast and records "Last check failed"
         * instead of waiting for a connection with the spinner on.
         */
        fun runNow(context: Context) {
            val request = OneTimeWorkRequestBuilder<LibraryUpdateWorker>()
                .setInputData(workDataOf(KEY_MANUAL to true))
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(NOW_WORK_NAME, ExistingWorkPolicy.KEEP, request)
        }
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
