package com.lume.app.data.story

import com.lume.app.AppContainer
import com.lume.app.data.db.ChapterTextEntity
import com.lume.app.data.novel.NovelBlock
import com.lume.app.data.novel.NovelBlocks
import com.lume.app.data.novel.plainText
import com.lume.app.ui.screens.LoadedNovelChapter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * The plain text of read and downloaded chapters, stored per block so a name can be found
 * offline (see [StoryLookup]). Always the original language: the reader's translation is not stored.
 */
class StoryIndex(private val app: AppContainer) {
    private val dao = app.dao

    /**
     * Stores [lc]'s text unless the index is off, incognito is on or the chapter is already
     * there. Returns whether it was stored.
     */
    suspend fun index(sourceId: String, titleId: String, lc: LoadedNovelChapter): Boolean {
        if (!app.prefs.storyIndex.value || app.prefs.incognito.value) return false
        if (dao.isChapterIndexed(sourceId, titleId, lc.chapter.id)) return false
        return store(sourceId, titleId, lc.chapter.id, lc.original ?: lc.blocks)
    }

    /**
     * Indexes the chapters among [chapterIds] that are downloaded but not indexed yet.
     * Returns how many were added. Does nothing while the index is off.
     */
    suspend fun backfillDownloaded(sourceId: String, titleId: String, chapterIds: Collection<String>): Int {
        if (!app.prefs.storyIndex.value || chapterIds.isEmpty()) return 0
        val wanted = chapterIds.toSet()
        val indexed = dao.indexedChapterIds(sourceId, titleId).toSet()
        val todo = dao.downloadsForTitle(sourceId, titleId).map { it.chapterId }.filter { it in wanted && it !in indexed }
        var added = 0
        for (chapterId in todo) {
            val text = app.downloads.localText(sourceId, titleId, chapterId) ?: continue
            val blocks = withContext(Dispatchers.Default) { NovelBlocks.parse(text.html, null) }
            if (store(sourceId, titleId, chapterId, blocks)) added++
        }
        return added
    }

    suspend fun clearTitle(sourceId: String, titleId: String) = dao.deleteTitleText(sourceId, titleId)

    suspend fun clearAll() = dao.deleteAllText()

    private suspend fun store(sourceId: String, titleId: String, chapterId: String, blocks: List<NovelBlock>): Boolean {
        val rows = chapterTextRows(sourceId, titleId, chapterId, blocks)
        if (rows.isEmpty()) return false
        dao.replaceChapterText(sourceId, titleId, chapterId, rows)
        return true
    }
}

/**
 * One row per text block with its text. [ChapterTextEntity.block] is the block's index in
 * [blocks], so images, dividers and blank blocks leave gaps instead of shifting later blocks.
 */
internal fun chapterTextRows(sourceId: String, titleId: String, chapterId: String, blocks: List<NovelBlock>): List<ChapterTextEntity> =
    blocks.mapIndexedNotNull { index, block ->
        val text = when (block) {
            is NovelBlock.Paragraph -> block.spans.plainText
            is NovelBlock.Heading -> block.spans.plainText
            else -> null
        }?.trim()
        if (text.isNullOrEmpty()) null else ChapterTextEntity(sourceId = sourceId, titleId = titleId, chapterId = chapterId, block = index, text = text)
    }
