package com.lume.app.data.db

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

interface DownloadQueries {
    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    fun observeDownloads(): Flow<List<DownloadEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownload(item: DownloadEntity)

    @Query("SELECT * FROM downloads WHERE sourceId = :sourceId AND titleId = :titleId ORDER BY downloadedAt DESC")
    suspend fun downloadsForTitle(sourceId: String, titleId: String): List<DownloadEntity>

    @Query("SELECT * FROM downloads ORDER BY downloadedAt DESC")
    suspend fun allDownloads(): List<DownloadEntity>

    @Query("DELETE FROM downloads WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteDownloadsForTitle(sourceId: String, titleId: String)

    @Query("SELECT * FROM downloads WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId = :chapterId LIMIT 1")
    suspend fun getDownload(sourceId: String, titleId: String, chapterId: String): DownloadEntity?

    @Query("SELECT chapterId FROM downloads WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun downloadedChapterIds(sourceId: String, titleId: String): List<String>

    @Query("DELETE FROM downloads WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId = :chapterId")
    suspend fun deleteDownload(sourceId: String, titleId: String, chapterId: String)

    @Query("SELECT * FROM offline_title_cache WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    suspend fun getOfflineTitleCache(sourceId: String, titleId: String): OfflineTitleCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertOfflineTitleCache(item: OfflineTitleCacheEntity)

    // Download queue
    @Query("SELECT * FROM download_queue ORDER BY createdAt ASC")
    fun observeDownloadQueue(): Flow<List<DownloadQueueEntity>>

    @Query("SELECT * FROM download_queue WHERE status IN ('pending','running') ORDER BY createdAt ASC")
    suspend fun pendingDownloadQueue(): List<DownloadQueueEntity>

    @Query("SELECT * FROM download_queue WHERE id = :id LIMIT 1")
    suspend fun getDownloadQueueItem(id: Long): DownloadQueueEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDownloadQueue(item: DownloadQueueEntity): Long

    /** New row only; -1 when the chapter is already in the queue. */
    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertDownloadQueueIgnore(item: DownloadQueueEntity): Long

    @Query("UPDATE download_queue SET status = :status, progress = :progress WHERE id = :id")
    suspend fun updateDownloadQueueStatus(id: Long, status: String, progress: Float)

    /** Progress of a running row only, so a late write cannot revive a paused or failed one. */
    @Query("UPDATE download_queue SET progress = :progress WHERE id = :id AND status = 'running'")
    suspend fun updateRunningDownloadProgress(id: Long, progress: Float)

    @Query("DELETE FROM download_queue WHERE id = :id")
    suspend fun deleteDownloadQueueItem(id: Long)

    @Query("SELECT * FROM download_queue WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId = :chapterId LIMIT 1")
    suspend fun findDownloadQueueItem(sourceId: String, titleId: String, chapterId: String): DownloadQueueEntity?

    /** Rows left "running" when the process died; they go back to the queue at startup. */
    @Query("UPDATE download_queue SET status = 'pending', progress = 0 WHERE status = 'running'")
    suspend fun resetRunningDownloads(): Int

    /** Cached details of library titles (status, alternative title) without the chapter lists. */
    @Query(
        """
        SELECT o.sourceId, o.titleId, o.titleJson FROM offline_title_cache o
        JOIN bookmarks b ON b.sourceId = o.sourceId AND b.titleId = o.titleId
        """,
    )
    fun observeLibraryTitleJson(): Flow<List<TitleJsonRow>>

    @Query("SELECT * FROM download_queue WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun downloadQueueFor(sourceId: String, titleId: String): List<DownloadQueueEntity>
}
