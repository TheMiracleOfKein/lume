package com.lume.app.data.db

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

interface ProgressQueries {
    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC")
    fun observeHistory(): Flow<List<ProgressEntity>>

    /** History of one [ProgressEntity.mediaKind] (the active mode). */
    @Query("SELECT * FROM reading_progress WHERE mediaKind = :mediaKind ORDER BY updatedAt DESC")
    fun observeHistoryOfKind(mediaKind: String): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC")
    suspend fun allProgress(): List<ProgressEntity>

    @Query("SELECT * FROM reading_progress WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    suspend fun getProgress(sourceId: String, titleId: String): ProgressEntity?

    @Query("SELECT * FROM reading_progress WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    fun observeProgress(sourceId: String, titleId: String): Flow<ProgressEntity?>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertProgress(item: ProgressEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markChapterRead(item: ChapterReadEntity)

    @Query("SELECT chapterId FROM chapter_read WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun readChapterIds(sourceId: String, titleId: String): List<String>

    @Query("SELECT chapterId FROM chapter_read WHERE sourceId = :sourceId AND titleId = :titleId")
    fun observeReadChapterIds(sourceId: String, titleId: String): Flow<List<String>>

    @Query("SELECT * FROM chapter_read")
    suspend fun allChapterRead(): List<ChapterReadEntity>

    // Per-chapter progress
    @Query("SELECT * FROM chapter_progress WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId = :chapterId LIMIT 1")
    suspend fun getChapterProgress(sourceId: String, titleId: String, chapterId: String): ChapterProgressEntity?

    @Query("SELECT * FROM chapter_progress WHERE sourceId = :sourceId AND titleId = :titleId")
    fun observeChapterProgress(sourceId: String, titleId: String): Flow<List<ChapterProgressEntity>>

    @Query("SELECT * FROM chapter_progress")
    suspend fun allChapterProgress(): List<ChapterProgressEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertChapterProgress(item: ChapterProgressEntity)

    @Query("DELETE FROM reading_progress")
    suspend fun clearAllProgress()

    @Query("DELETE FROM reading_progress WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun clearProgress(sourceId: String, titleId: String)

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC LIMIT :limit")
    fun observeHistoryLimit(limit: Int): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM reading_progress WHERE mediaKind = :mediaKind ORDER BY updatedAt DESC LIMIT :limit")
    fun observeHistoryLimitOfKind(mediaKind: String, limit: Int): Flow<List<ProgressEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markChaptersRead(items: List<ChapterReadEntity>)

    @Query("DELETE FROM chapter_read WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId IN (:chapterIds)")
    suspend fun markChaptersUnread(sourceId: String, titleId: String, chapterIds: List<String>)

    @Query("DELETE FROM chapter_progress WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId IN (:chapterIds)")
    suspend fun deleteChapterProgress(sourceId: String, titleId: String, chapterIds: List<String>)

    @Query("SELECT * FROM chapter_read WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun chapterReadFor(sourceId: String, titleId: String): List<ChapterReadEntity>

    @Query("DELETE FROM chapter_read WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteChapterReadFor(sourceId: String, titleId: String)

    @Query("SELECT * FROM chapter_progress WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun chapterProgressFor(sourceId: String, titleId: String): List<ChapterProgressEntity>

    @Query("DELETE FROM chapter_progress WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteChapterProgressFor(sourceId: String, titleId: String)
}

interface PageBookmarkQueries {
    // Page bookmarks
    @Query("SELECT * FROM page_bookmarks WHERE sourceId = :sourceId AND titleId = :titleId ORDER BY createdAt DESC")
    fun observePageBookmarks(sourceId: String, titleId: String): Flow<List<PageBookmarkEntity>>

    @Query("SELECT * FROM page_bookmarks ORDER BY createdAt ASC")
    suspend fun allPageBookmarks(): List<PageBookmarkEntity>

    @Insert
    suspend fun insertPageBookmark(item: PageBookmarkEntity): Long

    @Query("UPDATE page_bookmarks SET note = :note WHERE id = :id")
    suspend fun updatePageBookmarkNote(id: Long, note: String?)

    @Query("DELETE FROM page_bookmarks WHERE id = :id")
    suspend fun deletePageBookmark(id: Long)

    @Query("SELECT DISTINCT chapterId FROM page_bookmarks WHERE sourceId = :sourceId AND titleId = :titleId")
    fun observePageBookmarkChapterIds(sourceId: String, titleId: String): Flow<List<String>>

    @Query("SELECT * FROM page_bookmarks WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun pageBookmarksFor(sourceId: String, titleId: String): List<PageBookmarkEntity>

    @Query("UPDATE page_bookmarks SET sourceId = :newSourceId, titleId = :newTitleId, chapterId = :chapterId WHERE id = :id")
    suspend fun movePageBookmark(id: Long, newSourceId: String, newTitleId: String, chapterId: String)
}

interface SessionQueries {
    @Insert
    suspend fun insertSession(item: ReadingSessionEntity)

    @Insert
    suspend fun insertSessions(items: List<ReadingSessionEntity>)

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt DESC")
    fun observeSessions(): Flow<List<ReadingSessionEntity>>

    @Query("SELECT * FROM reading_sessions ORDER BY startedAt")
    suspend fun allSessions(): List<ReadingSessionEntity>

    /**
     * Read marks of chapters actually read in this app (they have a reading session). Marks from
     * imports, sync, trackers or "mark as read" would otherwise flood the statistics.
     */
    @Query(
        """SELECT r.sourceId, r.titleId, r.readAt FROM chapter_read r
        WHERE EXISTS (SELECT 1 FROM reading_sessions s
            WHERE s.sourceId = r.sourceId AND s.titleId = r.titleId AND s.chapterId = r.chapterId)""",
    )
    fun observeChapterReadTimes(): Flow<List<ChapterReadTime>>
}
