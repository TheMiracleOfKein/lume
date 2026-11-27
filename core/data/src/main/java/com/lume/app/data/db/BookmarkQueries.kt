package com.lume.app.data.db

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

interface BookmarkQueries {
    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    fun observeBookmarks(): Flow<List<BookmarkEntity>>

    /** Library titles of one [BookmarkEntity.mediaKind] (the active mode). */
    @Query("SELECT * FROM bookmarks WHERE mediaKind = :mediaKind ORDER BY createdAt DESC")
    fun observeBookmarksOfKind(mediaKind: String): Flow<List<BookmarkEntity>>

    @Query("SELECT * FROM bookmarks ORDER BY createdAt DESC")
    suspend fun allBookmarks(): List<BookmarkEntity>

    @Query("SELECT EXISTS(SELECT 1 FROM bookmarks WHERE sourceId = :sourceId AND titleId = :titleId)")
    fun observeIsBookmarked(sourceId: String, titleId: String): Flow<Boolean>

    @Query("SELECT * FROM bookmarks WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    suspend fun getBookmark(sourceId: String, titleId: String): BookmarkEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertBookmark(item: BookmarkEntity)

    /** Stores a library title's genres; does nothing for titles outside the library or when unchanged. */
    @Query("UPDATE bookmarks SET genresJson = :genresJson WHERE sourceId = :sourceId AND titleId = :titleId AND genresJson != :genresJson")
    suspend fun setBookmarkGenres(sourceId: String, titleId: String, genresJson: String)

    @Query("DELETE FROM bookmarks WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteBookmark(sourceId: String, titleId: String)

    /** Library titles with the bell on; the ones update checks look at. */
    @Query(
        """
        SELECT b.* FROM bookmarks b JOIN title_prefs p ON p.sourceId = b.sourceId AND p.titleId = b.titleId
        WHERE p.autoDownload = 1 ORDER BY b.createdAt DESC
        """,
    )
    suspend fun watchedBookmarks(): List<BookmarkEntity>

    @Query(
        """
        SELECT COUNT(*) FROM bookmarks b JOIN title_prefs p ON p.sourceId = b.sourceId AND p.titleId = b.titleId
        WHERE p.autoDownload = 1
        """,
    )
    fun observeWatchedCount(): Flow<Int>

    /** The [limit] library titles read (or, unread, added) most recently. */
    @Query(
        """
        SELECT b.* FROM bookmarks b LEFT JOIN reading_progress r ON r.sourceId = b.sourceId AND r.titleId = b.titleId
        ORDER BY COALESCE(r.updatedAt, b.createdAt) DESC LIMIT :limit
        """,
    )
    suspend fun recentBookmarks(limit: Int): List<BookmarkEntity>
}

interface LibraryChapterQueries {
    // Library chapters
    @Query("SELECT * FROM library_chapters WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun libraryChapters(sourceId: String, titleId: String): List<LibraryChapterEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertLibraryChapters(items: List<LibraryChapterEntity>)

    @Query("DELETE FROM library_chapters WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId IN (:chapterIds)")
    suspend fun deleteLibraryChapters(sourceId: String, titleId: String, chapterIds: List<String>)

    @Query("DELETE FROM library_chapters WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteLibraryChaptersForTitle(sourceId: String, titleId: String)

    @Query(
        """
        SELECT c.sourceId, c.titleId, c.chapterId, c.name AS chapterName, c.discoveredAt,
               b.titleName, b.coverUrl,
               EXISTS(SELECT 1 FROM library_chapters o
                      JOIN chapter_read r ON r.sourceId = o.sourceId AND r.titleId = o.titleId
                           AND r.chapterId = o.chapterId
                      WHERE o.sourceId = c.sourceId AND o.titleId = c.titleId
                           AND o.dedupeKey = c.dedupeKey) AS read
        FROM library_chapters c
        JOIN bookmarks b ON b.sourceId = c.sourceId AND b.titleId = c.titleId
        WHERE c.discoveredAt IS NOT NULL
        ORDER BY c.discoveredAt DESC, c.sourceOrder ASC
        LIMIT :limit
        """,
    )
    fun observeUpdates(limit: Int): Flow<List<UpdateRow>>

    /**
     * Unread = chapters (one per [LibraryChapterEntity.dedupeKey], so a chapter several teams
     * translated counts once) with no read copy.
     *
     * One pass over the chapters, grouped, instead of correlated subqueries per title: the old
     * `NOT IN` form re-ran a subquery for every chapter and took many seconds on a big library.
     */
    @Query(
        """
        SELECT b.sourceId, b.titleId,
            COALESCE(t.total, 0) AS total,
            COALESCE(t.unread, 0) AS unread,
            COALESCE(d.downloaded, 0) AS downloaded,
            p.updatedAt AS lastReadAt,
            t.latestFoundAt AS latestFoundAt
        FROM bookmarks b
        LEFT JOIN (
            SELECT k.sourceId, k.titleId, COUNT(*) AS total, SUM(k.isRead = 0) AS unread,
                MAX(k.foundAt) AS latestFoundAt
            FROM (
                SELECT c.sourceId, c.titleId, MAX(r.chapterId IS NOT NULL) AS isRead,
                    MAX(c.discoveredAt) AS foundAt
                FROM library_chapters c
                LEFT JOIN chapter_read r ON r.sourceId = c.sourceId AND r.titleId = c.titleId
                    AND r.chapterId = c.chapterId
                GROUP BY c.sourceId, c.titleId, c.dedupeKey
            ) k
            GROUP BY k.sourceId, k.titleId
        ) t ON t.sourceId = b.sourceId AND t.titleId = b.titleId
        LEFT JOIN (
            SELECT sourceId, titleId, COUNT(*) AS downloaded FROM downloads GROUP BY sourceId, titleId
        ) d ON d.sourceId = b.sourceId AND d.titleId = b.titleId
        LEFT JOIN reading_progress p ON p.sourceId = b.sourceId AND p.titleId = b.titleId
        """,
    )
    fun observeLibraryStats(): Flow<List<LibraryTitleStats>>
}
