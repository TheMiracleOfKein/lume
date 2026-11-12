package com.lume.app.data.library

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingWorkPolicy
import androidx.work.ForegroundInfo
import androidx.work.NetworkType
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.OutOfQuotaPolicy
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import androidx.work.workDataOf
import com.lume.app.LumeApp
import com.lume.app.R
import com.lume.app.ui.nav.AppLinks
import com.lume.app.ui.nav.Routes
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.launch

/**
 * Imports a Lib site's bookmarks as expedited background work, so the import goes on when the
 * app is closed and starts again if Android kills the process. The stage is published in
 * [LibBookmarkImport.status] (the Sources screen's card), in this job's progress data
 * ([KEY_STAGE], [KEY_DONE], [KEY_TOTAL]) and in an ongoing notification.
 */
class LibBookmarkImportWorker(appContext: Context, params: WorkerParameters) : CoroutineWorker(appContext, params) {

    private val sourceName: String get() = inputData.getString(KEY_SOURCE_NAME).orEmpty()

    override suspend fun doWork(): Result {
        val app = (applicationContext as? LumeApp)?.container ?: return Result.success()
        val sourceId = inputData.getString(KEY_SOURCE_ID) ?: return Result.failure()
        ensureChannel(applicationContext)
        val import = app.libBookmarkImport
        var error: String? = null
        coroutineScope {
            val updates = launch {
                import.status.collect { status ->
                    val stage = status?.stage ?: return@collect
                    val counts = stage.counts()
                    setProgress(workDataOf(KEY_STAGE to stage.javaClass.simpleName, KEY_DONE to (counts?.first ?: 0), KEY_TOTAL to (counts?.second ?: 0)))
                    // Starting a foreground job from the background is not always allowed; the import still runs.
                    runCatching { setForeground(foregroundInfo(stage)) }
                }
            }
            error = import.run(sourceId, sourceName)
            updates.cancel()
        }
        return if (error == null) Result.success() else Result.failure(workDataOf(KEY_ERROR to error))
    }

    override suspend fun getForegroundInfo(): ForegroundInfo = foregroundInfo(LibImportStage.Fetching)

    private fun foregroundInfo(stage: LibImportStage): ForegroundInfo {
        val ctx = applicationContext
        val counts = stage.counts()
        val text = when (stage) {
            LibImportStage.Fetching, LibImportStage.Finished -> ctx.getString(R.string.source_bookmarks_stage_fetching)
            is LibImportStage.Adding -> ctx.getString(R.string.source_bookmarks_stage_adding, stage.titles)
            is LibImportStage.Progress ->
                ctx.resources.getQuantityString(R.plurals.source_bookmarks_stage_progress, stage.total, stage.done, stage.total)
        }
        val notification = NotificationCompat.Builder(ctx, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download)
            .setContentTitle(ctx.getString(R.string.source_bookmarks_card_title, sourceName))
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setOngoing(true)
            .setSilent(true)
            .setProgress(100, counts?.let { libImportPercent(it.first, it.second) } ?: 0, counts == null)
            .setContentIntent(
                PendingIntent.getActivity(
                    ctx,
                    NOTIF_ID,
                    AppLinks.intentFor(ctx, Routes.Sources),
                    PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
                ),
            )
            .build()
        return if (Build.VERSION.SDK_INT >= 29) {
            ForegroundInfo(NOTIF_ID, notification, ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            ForegroundInfo(NOTIF_ID, notification)
        }
    }

    companion object {
        const val KEY_SOURCE_ID = "source_id"
        const val KEY_SOURCE_NAME = "source_name"
        const val KEY_STAGE = "stage"
        const val KEY_DONE = "done"
        const val KEY_TOTAL = "total"
        const val KEY_ERROR = "error"
        private const val CHANNEL_ID = "bookmark_import"
        private const val NOTIF_ID = 45

        /** The unique work name of [sourceId]'s import. */
        fun workName(sourceId: String) = "lib_bookmark_import:$sourceId"

        /** Queues the import; one that is already queued or running for the source is kept. */
        fun enqueue(context: Context, sourceId: String, sourceName: String) {
            val request = OneTimeWorkRequestBuilder<LibBookmarkImportWorker>()
                .setInputData(workDataOf(KEY_SOURCE_ID to sourceId, KEY_SOURCE_NAME to sourceName))
                .setConstraints(Constraints.Builder().setRequiredNetworkType(NetworkType.CONNECTED).build())
                .setExpedited(OutOfQuotaPolicy.RUN_AS_NON_EXPEDITED_WORK_REQUEST)
                .build()
            WorkManager.getInstance(context).enqueueUniqueWork(workName(sourceId), ExistingWorkPolicy.KEEP, request)
        }

        private fun ensureChannel(context: Context) {
            val manager = context.getSystemService(NotificationManager::class.java) ?: return
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, context.getString(R.string.notif_bookmark_import_channel), NotificationManager.IMPORTANCE_LOW),
            )
        }
    }
}
