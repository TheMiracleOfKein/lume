package com.lume.app.data.db

import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

interface StoryTextQueries {
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
}

interface DigestQueries {
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

    /** Chapter ids of the title that have a digest in any language. */
    @Query("SELECT DISTINCT chapterId FROM chapter_digests WHERE sourceId = :sourceId AND titleId = :titleId")
    suspend fun digestedChapters(sourceId: String, titleId: String): List<String>
}

interface CodexFactQueries {
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
}

interface CodexRelationQueries {
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
}

interface CodexQueries {
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
}
