package com.lume.app.data.db

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface LibraryDao {
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

    @Query("DELETE FROM reading_progress")
    suspend fun clearAllProgress()

    @Query("DELETE FROM reading_progress WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun clearProgress(sourceId: String, titleId: String)

    @Query("SELECT * FROM reading_progress ORDER BY updatedAt DESC LIMIT :limit")
    fun observeHistoryLimit(limit: Int): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM reading_progress WHERE mediaKind = :mediaKind ORDER BY updatedAt DESC LIMIT :limit")
    fun observeHistoryLimitOfKind(mediaKind: String, limit: Int): Flow<List<ProgressEntity>>

    @Query("SELECT * FROM source_packs ORDER BY installedAt ASC")
    suspend fun allSourcePacks(): List<SourcePackEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSourcePack(item: SourcePackEntity)

    @Query("DELETE FROM source_packs WHERE id = :id")
    suspend fun deleteSourcePack(id: String)

    // Categories
    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    fun observeCategories(): Flow<List<CategoryEntity>>

    @Query("SELECT * FROM categories ORDER BY sortOrder ASC, id ASC")
    suspend fun allCategories(): List<CategoryEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertCategory(item: CategoryEntity): Long

    @Query("DELETE FROM categories WHERE id = :id")
    suspend fun deleteCategory(id: Long)

    // Title meta
    @Query("SELECT * FROM title_meta")
    fun observeTitleMeta(): Flow<List<TitleMetaEntity>>

    @Query("SELECT * FROM title_meta WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    suspend fun getTitleMeta(sourceId: String, titleId: String): TitleMetaEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTitleMeta(item: TitleMetaEntity)

    // Title prefs
    @Query("SELECT * FROM title_prefs WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    suspend fun getTitlePrefs(sourceId: String, titleId: String): TitlePrefsEntity?

    @Query("SELECT * FROM title_prefs")
    suspend fun allTitlePrefs(): List<TitlePrefsEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTitlePrefs(item: TitlePrefsEntity)

    // Translation glossary (rejected suggestions are left out: they only stop the same proposal)
    @Query("SELECT * FROM glossary_terms WHERE sourceId = :sourceId AND titleId = :titleId AND status != 'rejected' ORDER BY source COLLATE NOCASE")
    fun glossary(sourceId: String, titleId: String): Flow<List<GlossaryTermEntity>>

    @Query("SELECT * FROM glossary_terms WHERE sourceId = :sourceId AND titleId = :titleId AND status != 'rejected' ORDER BY source COLLATE NOCASE")
    suspend fun glossaryOnce(sourceId: String, titleId: String): List<GlossaryTermEntity>

    /** Every source spelling of the title's glossary, rejected ones included, for skipping known proposals. */
    @Query("SELECT source FROM glossary_terms WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun glossarySources(sourceId: String, titleId: String): List<String>

    @Query("UPDATE glossary_terms SET status = :status, updatedAt = :now WHERE sourceId = :sourceId AND titleId = :titleId AND source = :source")
    suspend fun setGlossaryStatus(sourceId: String, titleId: String, source: String, status: String, now: Long)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertGlossaryTermsIfNew(terms: List<GlossaryTermEntity>)

    @Query("SELECT * FROM glossary_terms")
    suspend fun allGlossaryTerms(): List<GlossaryTermEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertGlossaryTerm(term: GlossaryTermEntity)

    @Query("DELETE FROM glossary_terms WHERE sourceId = :sourceId AND titleId = :titleId AND source = :source")
    suspend fun deleteGlossaryTerm(sourceId: String, titleId: String, source: String)

    // Story text index (plain text per block, original language)
    @Query("SELECT EXISTS(SELECT 1 FROM chapter_text WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId = :chapterId)")
    suspend fun isChapterIndexed(sourceId: String, titleId: String, chapterId: String): Boolean

    @Query("SELECT DISTINCT chapterId FROM chapter_text WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun indexedChapterIds(sourceId: String, titleId: String): List<String>

    @Query("DELETE FROM chapter_text WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId = :chapterId")
    suspend fun deleteChapterText(sourceId: String, titleId: String, chapterId: String)

    @Insert
    suspend fun insertChapterText(rows: List<ChapterTextEntity>)

    /** Replaces the chapter's rows, so indexing it again leaves one copy. */
    @Transaction
    suspend fun replaceChapterText(sourceId: String, titleId: String, chapterId: String, rows: List<ChapterTextEntity>) {
        deleteChapterText(sourceId, titleId, chapterId)
        if (rows.isNotEmpty()) insertChapterText(rows)
    }

    @Query("DELETE FROM chapter_text WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteTitleText(sourceId: String, titleId: String)

    @Query("DELETE FROM chapter_text")
    suspend fun deleteAllText()

    @Query("SELECT COUNT(*) AS blocks, COALESCE(SUM(LENGTH(text)), 0) AS chars FROM chapter_text")
    suspend fun textIndexSize(): TextIndexSize

    /** A chapter's indexed text, block by block in order. */
    @Query("SELECT block, text FROM chapter_text WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId = :chapterId ORDER BY block")
    suspend fun chapterBlocks(sourceId: String, titleId: String, chapterId: String): List<BlockText>

    // AI chapter digests (rebuildable, not backed up)
    @Query("SELECT * FROM chapter_digests WHERE sourceId = :sourceId AND titleId = :titleId AND lang = :lang AND chapterId IN (:chapterIds)")
    suspend fun digests(sourceId: String, titleId: String, lang: String, chapterIds: List<String>): List<ChapterDigestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDigest(digest: ChapterDigestEntity)

    @Query("DELETE FROM chapter_digests")
    suspend fun deleteAllDigests()

    @Query("SELECT COUNT(*) FROM chapter_digests")
    suspend fun digestCount(): Int

    /** Digests of [chapterIds] in any language (a summary can read one in another language). */
    @Query("SELECT * FROM chapter_digests WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId IN (:chapterIds)")
    suspend fun digestsAnyLang(sourceId: String, titleId: String, chapterIds: List<String>): List<ChapterDigestEntity>

    /** Digests of the title's chapters in any language. */
    @Query("SELECT * FROM chapter_digests WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun titleDigestsAnyLang(sourceId: String, titleId: String): List<ChapterDigestEntity>

    /** Digests of the title's chapters in [lang], any chapter. */
    @Query("SELECT * FROM chapter_digests WHERE sourceId = :sourceId AND titleId = :titleId AND lang = :lang")
    suspend fun titleDigests(sourceId: String, titleId: String, lang: String): List<ChapterDigestEntity>

    @Query("SELECT * FROM chapter_digests")
    suspend fun allDigests(): List<ChapterDigestEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertDigests(digests: List<ChapterDigestEntity>)

    // Codex facts (AI story notes: what each chapter shows about a name)
    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertFacts(facts: List<CodexFactEntity>)

    @Query("SELECT * FROM codex_facts WHERE sourceId = :sourceId AND titleId = :titleId AND nameKey IN (:keys)")
    suspend fun factsFor(sourceId: String, titleId: String, keys: List<String>): List<CodexFactEntity>

    /** Facts of [fromKey] become facts of [toKey] (entries merged or renamed); a chapter [toKey] already has keeps its own. */
    @Query("UPDATE OR IGNORE codex_facts SET nameKey = :toKey WHERE sourceId = :sourceId AND titleId = :titleId AND nameKey = :fromKey")
    suspend fun moveFacts(sourceId: String, titleId: String, fromKey: String, toKey: String)

    @Query("SELECT * FROM codex_facts")
    suspend fun allFacts(): List<CodexFactEntity>

    /** Chapter ids of the title that have a digest in any language. */
    @Query("SELECT DISTINCT chapterId FROM chapter_digests WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun digestedChapters(sourceId: String, titleId: String): List<String>

    // Codex relations (AI-proposed: from story notes, `notes:` origin, or from a summary, `llm:` origin)
    @Query(
        "SELECT * FROM codex_relations WHERE sourceId = :sourceId AND titleId = :titleId " +
            "AND (fromName = :name COLLATE NOCASE OR toName = :name COLLATE NOCASE) ORDER BY createdAt",
    )
    suspend fun relationsOf(sourceId: String, titleId: String, name: String): List<CodexRelationEntity>

    /** Relations from [fromName] that a summary wrote; those from story notes stay. */
    @Query(
        "DELETE FROM codex_relations WHERE sourceId = :sourceId AND titleId = :titleId AND fromName = :fromName COLLATE NOCASE " +
            "AND origin NOT LIKE 'notes:%'",
    )
    suspend fun deleteRelationsFrom(sourceId: String, titleId: String, fromName: String)

    @Insert
    suspend fun insertRelations(relations: List<CodexRelationEntity>)

    @Query("SELECT * FROM codex_relations WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun titleRelations(sourceId: String, titleId: String): List<CodexRelationEntity>

    @Query("SELECT * FROM codex_relations")
    suspend fun allRelations(): List<CodexRelationEntity>

    /** The relations [fromName]'s summary wrote, replaced by [relations] (a new summary sees the story anew). */
    @Transaction
    suspend fun replaceRelationsFrom(sourceId: String, titleId: String, fromName: String, relations: List<CodexRelationEntity>) {
        deleteRelationsFrom(sourceId, titleId, fromName)
        if (relations.isNotEmpty()) insertRelations(relations)
    }

    @Query("DELETE FROM codex_relations WHERE id = :id")
    suspend fun deleteRelation(id: Long)

    /** Per chapter: first and last block containing [query] (an FTS4 expression) and how many blocks do. */
    @Query(
        "SELECT t.chapterId AS chapterId, MIN(t.block) AS firstBlock, MAX(t.block) AS lastBlock, COUNT(*) AS hits " +
            "FROM chapter_text t JOIN chapter_text_fts f ON f.rowid = t.id " +
            "WHERE f.text MATCH :query AND t.sourceId = :sourceId AND t.titleId = :titleId GROUP BY t.chapterId",
    )
    suspend fun mentionsByChapter(sourceId: String, titleId: String, query: String): List<ChapterMention>

    /** The last [limit] blocks of the chapter before [beforeBlock] that contain [query], newest first. */
    @Query(
        "SELECT t.block AS block, t.text AS text " +
            "FROM chapter_text t JOIN chapter_text_fts f ON f.rowid = t.id " +
            "WHERE f.text MATCH :query AND t.sourceId = :sourceId AND t.titleId = :titleId " +
            "AND t.chapterId = :chapterId AND t.block < :beforeBlock ORDER BY t.block DESC LIMIT :limit",
    )
    suspend fun matchingBlocksBefore(
        sourceId: String,
        titleId: String,
        chapterId: String,
        query: String,
        beforeBlock: Int,
        limit: Int,
    ): List<BlockText>

    @Query("SELECT text FROM chapter_text WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId = :chapterId AND block = :block")
    suspend fun blockText(sourceId: String, titleId: String, chapterId: String, block: Int): String?

    /** Blocks of the chapter from [fromBlock] to [toBlock] inclusive, in order. */
    @Query(
        "SELECT block, text FROM chapter_text WHERE sourceId = :sourceId AND titleId = :titleId " +
            "AND chapterId = :chapterId AND block BETWEEN :fromBlock AND :toBlock ORDER BY block",
    )
    suspend fun blocksInRange(sourceId: String, titleId: String, chapterId: String, fromBlock: Int, toBlock: Int): List<BlockText>

    /** The chapter's last [n] blocks, in reading order. */
    @Query(
        "SELECT * FROM (SELECT block, text FROM chapter_text WHERE sourceId = :sourceId AND titleId = :titleId " +
            "AND chapterId = :chapterId ORDER BY block DESC LIMIT :n) ORDER BY block",
    )
    suspend fun chapterTail(sourceId: String, titleId: String, chapterId: String, n: Int): List<BlockText>

    // Codex
    // Ignored entries (AI entries the reader removed) are left out; they only stop the name coming back.
    @Transaction
    @Query("SELECT * FROM codex_entries WHERE sourceId = :sourceId AND titleId = :titleId AND origin != 'ignored' ORDER BY name COLLATE NOCASE")
    fun codexFor(sourceId: String, titleId: String): Flow<List<CodexEntryWithAliases>>

    @Transaction
    @Query("SELECT * FROM codex_entries WHERE sourceId = :sourceId AND titleId = :titleId AND origin != 'ignored' ORDER BY name COLLATE NOCASE")
    suspend fun codexOnce(sourceId: String, titleId: String): List<CodexEntryWithAliases>

    /** Every entry of the title, ignored ones included, for adding names found by AI. */
    @Transaction
    @Query("SELECT * FROM codex_entries WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun codexWithIgnored(sourceId: String, titleId: String): List<CodexEntryWithAliases>

    @Query("SELECT * FROM codex_entries")
    suspend fun allCodexEntries(): List<CodexEntryEntity>

    @Query("SELECT * FROM codex_aliases")
    suspend fun allCodexAliases(): List<CodexAliasEntity>

    @Insert
    suspend fun insertCodexEntry(entry: CodexEntryEntity): Long

    @Update
    suspend fun updateCodexEntry(entry: CodexEntryEntity)

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertCodexAliases(aliases: List<CodexAliasEntity>)

    @Query("DELETE FROM codex_aliases WHERE entryId = :entryId")
    suspend fun deleteCodexAliases(entryId: Long)

    @Query("DELETE FROM codex_entries WHERE id = :id")
    suspend fun deleteCodexEntry(id: Long)

    /** Inserts the entry (id 0) or updates it, and makes [aliases] its aliases. Returns the entry id. */
    @Transaction
    suspend fun saveCodexEntry(entry: CodexEntryEntity, aliases: List<String>): Long {
        val id = if (entry.id == 0L) insertCodexEntry(entry) else entry.id.also { updateCodexEntry(entry) }
        deleteCodexAliases(id)
        insertCodexAliases(aliases.map { it.trim() }.filter { it.isNotEmpty() }.distinct().map { CodexAliasEntity(id, it) })
        return id
    }

    // Tracker links
    @Query("SELECT * FROM tracker_links WHERE sourceId = :sourceId AND titleId = :titleId")
    fun observeTrackerLinks(sourceId: String, titleId: String): Flow<List<TrackerLinkEntity>>

    @Query("SELECT * FROM tracker_links WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun getTrackerLinks(sourceId: String, titleId: String): List<TrackerLinkEntity>

    @Query("SELECT * FROM tracker_links")
    suspend fun allTrackerLinks(): List<TrackerLinkEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTrackerLink(item: TrackerLinkEntity)

    @Query("DELETE FROM tracker_links WHERE sourceId = :sourceId AND titleId = :titleId AND tracker = :tracker")
    suspend fun deleteTrackerLink(sourceId: String, titleId: String, tracker: String)

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

    @Query("DELETE FROM download_queue WHERE status = 'done'")
    suspend fun clearDoneDownloadQueue()

    /** Rows left "running" when the process died; they go back to the queue at startup. */
    @Query("UPDATE download_queue SET status = 'pending', progress = 0 WHERE status = 'running'")
    suspend fun resetRunningDownloads(): Int

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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun markChaptersRead(items: List<ChapterReadEntity>)

    @Query("DELETE FROM chapter_read WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId IN (:chapterIds)")
    suspend fun markChaptersUnread(sourceId: String, titleId: String, chapterIds: List<String>)

    @Query("DELETE FROM chapter_progress WHERE sourceId = :sourceId AND titleId = :titleId AND chapterId IN (:chapterIds)")
    suspend fun deleteChapterProgress(sourceId: String, titleId: String, chapterIds: List<String>)

    @Query("SELECT DISTINCT sourceId, titleId FROM tracker_links")
    fun observeTrackedTitles(): Flow<List<TitleKey>>

    @Query("SELECT * FROM title_prefs WHERE sourceId = :sourceId AND titleId = :titleId LIMIT 1")
    fun observeTitlePrefs(sourceId: String, titleId: String): Flow<TitlePrefsEntity?>

    @Query("UPDATE categories SET name = :name WHERE id = :id")
    suspend fun renameCategory(id: Long, name: String)

    @Query("UPDATE categories SET sortOrder = :sortOrder WHERE id = :id")
    suspend fun setCategoryOrder(id: Long, sortOrder: Int)

    @Query("SELECT * FROM offline_title_cache")
    suspend fun allOfflineTitleCache(): List<OfflineTitleCacheEntity>

    /** Cached details of library titles (status, alternative title) without the chapter lists. */
    @Query(
        """
        SELECT o.sourceId, o.titleId, o.titleJson FROM offline_title_cache o
        JOIN bookmarks b ON b.sourceId = o.sourceId AND b.titleId = o.titleId
        """,
    )
    fun observeLibraryTitleJson(): Flow<List<TitleJsonRow>>

    @Query("SELECT DISTINCT chapterId FROM page_bookmarks WHERE sourceId = :sourceId AND titleId = :titleId")
    fun observePageBookmarkChapterIds(sourceId: String, titleId: String): Flow<List<String>>

    // Title sources
    @Query("SELECT * FROM title_sources WHERE sourceId = :sourceId AND titleId = :titleId ORDER BY priority ASC, addedAt ASC")
    fun observeTitleSources(sourceId: String, titleId: String): Flow<List<TitleSourceEntity>>

    @Query("SELECT * FROM title_sources WHERE sourceId = :sourceId AND titleId = :titleId ORDER BY priority ASC, addedAt ASC")
    suspend fun titleSources(sourceId: String, titleId: String): List<TitleSourceEntity>

    @Query("SELECT * FROM title_sources")
    suspend fun allTitleSources(): List<TitleSourceEntity>

    /** Every linked source of every title (search results mark titles saved from another source). */
    @Query("SELECT * FROM title_sources WHERE state = 'linked'")
    fun observeLinkedTitleSources(): Flow<List<TitleSourceEntity>>

    /** Linked sources per title that has any (for the library's sources badge). */
    @Query("SELECT sourceId, titleId, COUNT(*) AS linked FROM title_sources WHERE state = 'linked' GROUP BY sourceId, titleId")
    fun observeLinkedSourceCounts(): Flow<List<LinkedSourceCount>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTitleSource(item: TitleSourceEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTitleSources(items: List<TitleSourceEntity>)

    @Query("DELETE FROM title_sources WHERE sourceId = :sourceId AND titleId = :titleId AND linkSourceId = :linkSourceId AND linkTitleId = :linkTitleId")
    suspend fun deleteTitleSource(sourceId: String, titleId: String, linkSourceId: String, linkTitleId: String)

    @Query("DELETE FROM title_sources WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteTitleSources(sourceId: String, titleId: String)

    @Query("SELECT * FROM chapter_read WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun chapterReadFor(sourceId: String, titleId: String): List<ChapterReadEntity>

    @Query("DELETE FROM chapter_read WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteChapterReadFor(sourceId: String, titleId: String)

    @Query("SELECT * FROM chapter_progress WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun chapterProgressFor(sourceId: String, titleId: String): List<ChapterProgressEntity>

    @Query("DELETE FROM chapter_progress WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteChapterProgressFor(sourceId: String, titleId: String)

    @Query("SELECT * FROM page_bookmarks WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun pageBookmarksFor(sourceId: String, titleId: String): List<PageBookmarkEntity>

    @Query("UPDATE page_bookmarks SET sourceId = :newSourceId, titleId = :newTitleId, chapterId = :chapterId WHERE id = :id")
    suspend fun movePageBookmark(id: Long, newSourceId: String, newTitleId: String, chapterId: String)

    @Query("DELETE FROM title_meta WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteTitleMeta(sourceId: String, titleId: String)

    @Query("DELETE FROM title_prefs WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteTitlePrefs(sourceId: String, titleId: String)

    @Query("DELETE FROM tracker_links WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun deleteTrackerLinksFor(sourceId: String, titleId: String)

    @Query("SELECT * FROM download_queue WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun downloadQueueFor(sourceId: String, titleId: String): List<DownloadQueueEntity>

    @Query("SELECT * FROM watched_folders ORDER BY addedAt")
    fun observeWatchedFolders(): Flow<List<WatchedFolderEntity>>

    @Query("SELECT * FROM watched_folders ORDER BY addedAt")
    suspend fun watchedFolders(): List<WatchedFolderEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertWatchedFolder(folder: WatchedFolderEntity)

    @Query("DELETE FROM watched_folders WHERE treeUri = :treeUri")
    suspend fun deleteWatchedFolder(treeUri: String)

    @Query("UPDATE watched_folders SET available = :available WHERE treeUri = :treeUri")
    suspend fun setWatchedFolderAvailable(treeUri: String, available: Boolean)

    @Query("UPDATE watched_folders SET lastScanAt = :at WHERE treeUri = :treeUri")
    suspend fun setWatchedFolderScanned(treeUri: String, at: Long)

    @Query("SELECT * FROM imported_files WHERE treeUri = :treeUri")
    suspend fun importedFilesFor(treeUri: String): List<ImportedFileEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertImportedFile(file: ImportedFileEntity)

    @Query("DELETE FROM imported_files WHERE treeUri = :treeUri")
    suspend fun deleteImportedFilesFor(treeUri: String)

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

data class TitleKey(val sourceId: String, val titleId: String)

data class LinkedSourceCount(val sourceId: String, val titleId: String, val linked: Int)

data class TitleJsonRow(val sourceId: String, val titleId: String, val titleJson: String)

