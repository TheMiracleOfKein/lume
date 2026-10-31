package com.lume.app.data.library

import androidx.room.withTransaction
import com.lume.app.data.db.BookmarkEntity
import com.lume.app.data.db.CategoryEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.LumeDatabase
import com.lume.app.data.source.LibBookmark
import com.lume.app.data.source.LibBookmarkFolder
import com.lume.app.data.source.LibSocialEngine
import com.lume.app.data.source.SourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/** What a MangaLib-family bookmark import did. */
data class LibBookmarkImportResult(
    val added: Int,
    val alreadyInLibrary: Int,
    /** Folders that became categories (new or existing ones with the same name). */
    val folders: Int,
)

/**
 * Brings the bookmarks of a signed-in MangaLib, HentaiLib or YaoiLib account into the library.
 * Each bookmark folder becomes a category of the same name; titles already in the library keep
 * their categories and gain the folder's.
 */
class LibBookmarkImport(
    private val db: LumeDatabase,
    private val dao: LibraryDao,
    private val sources: SourceRegistry,
) {
    suspend fun import(sourceId: String): LibBookmarkImportResult = withContext(Dispatchers.IO) {
        sources.awaitLoaded()
        // The registry hands out wrapped sources; the bookmark call lives on the engine itself.
        val engine = sources.engineById(sourceId) as? LibSocialEngine
            ?: error("Not a Lib source: $sourceId")
        val (folders, bookmarks) = engine.fetchBookmarks()
        apply(sourceId, folders, bookmarks)
    }

    private suspend fun apply(
        sourceId: String,
        folders: List<LibBookmarkFolder>,
        bookmarks: List<LibBookmark>,
    ): LibBookmarkImportResult = db.withTransaction {
        val plan = planLibBookmarkImport(folders, bookmarks)
        val existing = dao.allCategories().toMutableList()
        val categoryByFolder = HashMap<String, Long>()
        for (folder in plan.folders) {
            val found = existing.firstOrNull { it.name.equals(folder.name, ignoreCase = true) }
            val id = found?.id ?: dao.upsertCategory(
                CategoryEntity(name = folder.name, sortOrder = (existing.maxOfOrNull { it.sortOrder } ?: -1) + 1),
            ).also { newId -> existing += CategoryEntity(id = newId, name = folder.name, sortOrder = existing.size) }
            categoryByFolder[folder.id] = id
        }
        var added = 0
        var already = 0
        for (title in plan.titles) {
            val categories = title.folderIds.mapNotNull { categoryByFolder[it] }.toSet()
            val current = dao.getBookmark(sourceId, title.titleId)
            if (current != null) {
                already++
                val merged = parseCategoryIds(current.categoryIdsJson) + categories
                dao.upsertBookmark(current.copy(categoryIdsJson = encodeCategoryIds(merged)))
            } else {
                added++
                dao.upsertBookmark(
                    BookmarkEntity(
                        sourceId = sourceId,
                        titleId = title.titleId,
                        titleName = title.title,
                        coverUrl = title.coverUrl,
                        typeLabel = title.typeLabel,
                        categoryIdsJson = encodeCategoryIds(categories),
                    ),
                )
            }
        }
        LibBookmarkImportResult(added, already, categoryByFolder.size)
    }
}

/** One title to add, with every folder it sits in. */
data class LibImportTitle(
    val titleId: String,
    val title: String,
    val coverUrl: String?,
    val typeLabel: String?,
    val folderIds: Set<String>,
)

data class LibImportPlan(val folders: List<LibBookmarkFolder>, val titles: List<LibImportTitle>)

/**
 * Groups [bookmarks] by title (a title may sit in several folders) and keeps only the folders
 * that hold something, so empty site folders don't turn into empty categories.
 */
internal fun planLibBookmarkImport(folders: List<LibBookmarkFolder>, bookmarks: List<LibBookmark>): LibImportPlan {
    val titles = bookmarks.groupBy { it.titleId }.map { (id, entries) ->
        val first = entries.first()
        LibImportTitle(
            titleId = id,
            title = first.title,
            coverUrl = entries.firstNotNullOfOrNull { it.coverUrl },
            typeLabel = entries.firstNotNullOfOrNull { it.typeLabel },
            folderIds = entries.mapNotNullTo(LinkedHashSet()) { it.folderId },
        )
    }
    val used = titles.flatMapTo(HashSet()) { it.folderIds }
    return LibImportPlan(folders.filter { it.id in used }, titles)
}
