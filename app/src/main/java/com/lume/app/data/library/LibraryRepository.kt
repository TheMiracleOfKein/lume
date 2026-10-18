package com.lume.app.data.library

import androidx.room.withTransaction
import com.lume.app.data.db.BookmarkEntity
import com.lume.app.data.db.CategoryEntity
import com.lume.app.data.db.ChapterReadEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.LumeDatabase
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.data.title.TitleRepository
import com.lume.app.data.title.TitleSourceRepository
import com.lume.app.data.tracker.TrackerSync
import com.lume.app.data.tracker.chapterProgressNumber
import com.lume.app.domain.Chapter
import com.lume.app.domain.dedupeChapters
import com.lume.app.domain.dedupeKey
import com.lume.app.domain.inReadingOrder
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull

/**
 * Library membership, categories and read marks: the writes several screens share (title,
 * reader, library selection, chapter list, updates).
 */
class LibraryRepository(
    private val db: LumeDatabase,
    private val dao: LibraryDao,
    private val prefs: AppPreferences,
    private val titles: TitleRepository,
    private val libraryChapters: LibraryChapters,
    private val trackerSync: TrackerSync,
    private val titleSources: TitleSourceRepository,
    private val appScope: CoroutineScope,
) {
    // ---- Membership -------------------------------------------------------------------------

    /** Adds a title, into the default category if one is set. */
    suspend fun add(sourceId: String, titleId: String, titleName: String, coverUrl: String?, typeLabel: String?) {
        val defaultCategory = prefs.defaultCategoryId.value
            ?.takeIf { id -> dao.allCategories().any { it.id == id } }
        dao.upsertBookmark(
            BookmarkEntity(
                sourceId = sourceId,
                titleId = titleId,
                titleName = titleName,
                coverUrl = coverUrl,
                typeLabel = typeLabel,
                categoryIdsJson = encodeCategoryIds(setOfNotNull(defaultCategory)),
            ),
        )
        // Know the chapters right away: unread counts work and the next check only reports new ones.
        val known = titles.cached(sourceId, titleId)
        known?.chapters?.let { libraryChapters.sync(sourceId, titleId, it) }
        // Signed-in trackers link the title when they find it under the same name.
        val names = listOfNotNull(titleName, known?.details?.altTitle)
        appScope.launch(Dispatchers.IO) { runCatching { trackerSync.autoLink(sourceId, titleId, names) } }
        // "Also on …": look for the title on the other sources.
        appScope.launch(Dispatchers.IO) { runCatching { titleSources.suggest(sourceId, titleId) } }
    }

    suspend fun remove(sourceId: String, titleId: String) {
        dao.deleteBookmark(sourceId, titleId)
        libraryChapters.forget(sourceId, titleId)
    }

    suspend fun setCategories(titles: List<Pair<String, String>>, categoryIds: Set<Long>) {
        db.withTransaction {
            for ((sourceId, titleId) in titles) {
                val bookmark = dao.getBookmark(sourceId, titleId) ?: continue
                dao.upsertBookmark(bookmark.copy(categoryIdsJson = encodeCategoryIds(categoryIds)))
            }
        }
    }

    // ---- Categories -------------------------------------------------------------------------

    suspend fun createCategory(name: String) {
        val order = (dao.allCategories().maxOfOrNull { it.sortOrder } ?: -1) + 1
        dao.upsertCategory(CategoryEntity(name = name.trim(), sortOrder = order))
    }

    suspend fun renameCategory(id: Long, name: String) = dao.renameCategory(id, name.trim())

    /** Titles stay in the library; they just lose this category. */
    suspend fun deleteCategory(id: Long) {
        db.withTransaction {
            for (bookmark in dao.allBookmarks()) {
                val ids = parseCategoryIds(bookmark.categoryIdsJson)
                if (id in ids) dao.upsertBookmark(bookmark.copy(categoryIdsJson = encodeCategoryIds(ids - id)))
            }
            dao.deleteCategory(id)
        }
        if (prefs.defaultCategoryId.value == id) prefs.setDefaultCategoryId(null)
    }

    /** Moves a category one place up ([delta] = -1) or down (+1). */
    suspend fun moveCategory(id: Long, delta: Int) {
        db.withTransaction {
            val list = dao.allCategories().toMutableList()
            val from = list.indexOfFirst { it.id == id }
            val to = from + delta
            if (from < 0 || to !in list.indices) return@withTransaction
            list.add(to, list.removeAt(from))
            list.forEachIndexed { index, cat -> if (cat.sortOrder != index) dao.setCategoryOrder(cat.id, index) }
        }
    }

    // ---- Read marks -------------------------------------------------------------------------

    /**
     * Marks chapters read (their saved spot is dropped, so they open from the top) and tells the
     * linked trackers about the furthest one. Trackers never move backwards.
     */
    suspend fun markRead(sourceId: String, titleId: String, chapterIds: Collection<String>) {
        if (chapterIds.isEmpty()) return
        val ids = chapterIds.toList()
        val now = System.currentTimeMillis()
        db.withTransaction {
            dao.markChaptersRead(ids.map { ChapterReadEntity(sourceId, titleId, it, now) })
            dao.deleteChapterProgress(sourceId, titleId, ids)
        }
        val chapters = titles.cached(sourceId, titleId)?.chapters.orEmpty()
        val furthest = ids.maxByOrNull { chapterProgressNumber(chapters, it) } ?: return
        appScope.launch(Dispatchers.IO) {
            withContext(NonCancellable) {
                runCatching { trackerSync.pushProgress(sourceId, titleId, chapters, furthest) }
            }
        }
    }

    suspend fun markUnread(sourceId: String, titleId: String, chapterIds: Collection<String>) {
        if (chapterIds.isEmpty()) return
        val ids = chapterIds.toList()
        db.withTransaction {
            dao.markChaptersUnread(sourceId, titleId, ids)
            dao.deleteChapterProgress(sourceId, titleId, ids)
        }
    }

    /** Every known chapter of the title (all teams' copies). */
    suspend fun markTitleRead(sourceId: String, titleId: String) {
        val ids = titles.cached(sourceId, titleId)?.chapters?.map { it.id }
            ?: dao.libraryChapters(sourceId, titleId).map { it.chapterId }
        markRead(sourceId, titleId, ids)
    }

    suspend fun markTitleUnread(sourceId: String, titleId: String) {
        markUnread(sourceId, titleId, dao.readChapterIds(sourceId, titleId))
    }

    /**
     * Chapters not read yet, oldest first, one per chapter (the title's preferred team), as the
     * chapter list shows them.
     */
    suspend fun unreadChapters(sourceId: String, titleId: String): List<Chapter> {
        val chapters = titles.cached(sourceId, titleId)?.chapters.orEmpty()
        if (chapters.isEmpty()) return emptyList()
        val readIds = dao.readChapterIds(sourceId, titleId).toSet()
        val readKeys = chapters.filter { it.id in readIds }.mapTo(HashSet()) { dedupeKey(it) }
        val titlePrefs = dao.getTitlePrefs(sourceId, titleId)
        val list = if (titlePrefs?.showDuplicates == true) chapters else dedupeChapters(chapters, titlePrefs?.branch)
        return list.filter { dedupeKey(it) !in readKeys }.inReadingOrder()
    }
}

fun parseCategoryIds(json: String): Set<Long> = runCatching {
    Json.parseToJsonElement(json).jsonArray.mapNotNull {
        it.jsonPrimitive.longOrNull ?: it.jsonPrimitive.contentOrNull?.toLongOrNull()
    }.toSet()
}.getOrDefault(emptySet())

fun encodeCategoryIds(ids: Set<Long>): String =
    buildJsonArray { ids.sorted().forEach { add(JsonPrimitive(it)) } }.toString()
