package com.lume.app.data.library

import androidx.room.withTransaction
import com.lume.app.data.db.BookmarkEntity
import com.lume.app.data.db.CategoryEntity
import com.lume.app.data.db.ChapterReadEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.LumeDatabase
import com.lume.app.data.db.ProgressEntity
import com.lume.app.data.source.LibBookmark
import com.lume.app.data.source.LibBookmarkFolder
import com.lume.app.data.source.LibReadChapter
import com.lume.app.data.source.LibSocialEngine
import com.lume.app.data.source.SourceRegistry
import com.lume.app.domain.Chapter
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import java.util.concurrent.atomic.AtomicInteger
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.delay
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.withContext

/** What a MangaLib-family bookmark import did. */
data class LibBookmarkImportResult(
    val added: Int,
    val alreadyInLibrary: Int,
    /** Folders that became categories (new or existing ones with the same name). */
    val folders: Int,
    /** Titles whose read chapters on the site were marked read in Lume. */
    val progress: Int = 0,
    /** Titles with progress on the site whose chapters couldn't be loaded or matched. */
    val progressMissed: Int = 0,
)

/**
 * Brings the bookmarks of a signed-in MangaLib, HentaiLib or YaoiLib account into the library.
 * Each bookmark folder becomes a category of the same name; titles already in the library keep
 * their categories and gain the folder's. The last chapter read on the site and every chapter
 * before it are marked read; local read marks and progress are never taken away.
 */
class LibBookmarkImport(
    private val db: LumeDatabase,
    private val dao: LibraryDao,
    private val sources: SourceRegistry,
    private val libraryChapters: LibraryChapters,
    private val scope: CoroutineScope,
) {
    private val _status = MutableStateFlow<LibImportStatus?>(null)

    /**
     * The running or last finished import; null when there is nothing to show. Lives as long as
     * the process, so leaving the Sources screen neither stops the import nor hides it.
     */
    val status: StateFlow<LibImportStatus?> = _status.asStateFlow()

    /** Starts importing [sourceId] in the app scope. False when an import is already running. */
    fun start(sourceId: String, sourceName: String): Boolean {
        if (_status.value?.running == true) return false
        _status.value = LibImportStatus(sourceId, sourceName, LibImportStage.Fetching)
        scope.launch {
            val outcome = runCatching {
                import(sourceId) { stage -> _status.update { it?.copy(stage = stage) } }
            }
            if (outcome.exceptionOrNull() is CancellationException) return@launch
            _status.update {
                it?.copy(
                    stage = LibImportStage.Finished,
                    result = outcome.getOrNull(),
                    error = outcome.exceptionOrNull()?.let { e -> e.message ?: e.toString() },
                )
            }
        }
        return true
    }

    /** Hides a finished import's summary. */
    fun dismiss() {
        _status.update { if (it?.running == true) it else null }
    }

    suspend fun import(
        sourceId: String,
        onStage: (LibImportStage) -> Unit = {},
    ): LibBookmarkImportResult = withContext(Dispatchers.IO) {
        onStage(LibImportStage.Fetching)
        sources.awaitLoaded()
        // The registry hands out wrapped sources; the bookmark call lives on the engine itself.
        val engine = sources.engineById(sourceId) as? LibSocialEngine
            ?: error("Not a Lib source: $sourceId")
        val (folders, bookmarks) = engine.fetchBookmarks()
        val plan = planLibBookmarkImport(folders, bookmarks)
        onStage(LibImportStage.Adding(plan.titles.size))
        val result = apply(sourceId, plan)
        val (progress, missed) = importProgress(engine, sourceId, plan.titles, onStage)
        result.copy(progress = progress, progressMissed = missed)
    }

    private suspend fun apply(sourceId: String, plan: LibImportPlan): LibBookmarkImportResult = db.withTransaction {
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

    /** Loads the chapter list of every title read on the site, a few at a time. Returns (done, missed). */
    private suspend fun importProgress(
        engine: LibSocialEngine,
        sourceId: String,
        titles: List<LibImportTitle>,
        onStage: (LibImportStage) -> Unit,
    ): Pair<Int, Int> = coroutineScope {
        val read = titles.filter { it.lastRead != null }
        val gate = Semaphore(PROGRESS_PARALLELISM)
        val finished = AtomicInteger(0)
        onStage(LibImportStage.Progress(0, read.size))
        val outcomes = read.map { title ->
            async {
                gate.withPermit {
                    val ok = try {
                        importTitleProgress(engine, sourceId, title)
                    } catch (e: CancellationException) {
                        throw e
                    } catch (_: Exception) {
                        false
                    }
                    onStage(LibImportStage.Progress(finished.incrementAndGet(), read.size))
                    ok
                }
            }
        }.awaitAll()
        val done = outcomes.count { it }
        done to (outcomes.size - done)
    }

    private suspend fun importTitleProgress(engine: LibSocialEngine, sourceId: String, title: LibImportTitle): Boolean {
        val lastRead = title.lastRead ?: return false
        val chapters = try {
            engine.getChapters(title.titleId)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            // The site limits how fast its API may be asked; one more try after a pause.
            delay(PROGRESS_RETRY_DELAY_MS)
            engine.getChapters(title.titleId)
        }
        val target = libChaptersReadUpTo(chapters, lastRead) ?: return false
        libraryChapters.sync(sourceId, title.titleId, chapters)
        val readAt = lastRead.readAt ?: System.currentTimeMillis()
        db.withTransaction {
            val known = dao.readChapterIds(sourceId, title.titleId).toHashSet()
            val fresh = target.read.filter { it.id !in known }
            if (fresh.isNotEmpty()) {
                dao.markChaptersRead(fresh.map { ChapterReadEntity(sourceId, title.titleId, it.id, readAt) })
            }
            if (dao.getProgress(sourceId, title.titleId) == null) {
                val bookmark = dao.getBookmark(sourceId, title.titleId)
                dao.upsertProgress(
                    ProgressEntity(
                        sourceId = sourceId,
                        titleId = title.titleId,
                        titleName = bookmark?.titleName ?: title.title,
                        coverUrl = bookmark?.coverUrl ?: title.coverUrl,
                        typeLabel = bookmark?.typeLabel ?: title.typeLabel,
                        chapterId = target.last.id,
                        chapterName = target.last.title,
                        chapterUrl = target.last.url,
                        updatedAt = readAt,
                    ),
                )
            }
        }
        return true
    }

    private companion object {
        const val PROGRESS_PARALLELISM = 3
        const val PROGRESS_RETRY_DELAY_MS = 1_500L
    }
}

/** Where a bookmark import is. */
sealed interface LibImportStage {
    /** Asking the site for the account's bookmarks. */
    data object Fetching : LibImportStage

    /** Adding [titles] titles to the library. */
    data class Adding(val titles: Int) : LibImportStage

    /** Loading chapter lists to mark read chapters: [done] of [total] titles. */
    data class Progress(val done: Int, val total: Int) : LibImportStage

    data object Finished : LibImportStage
}

/** One bookmark import for the UI: its stage, then its result or error. */
data class LibImportStatus(
    val sourceId: String,
    val sourceName: String,
    val stage: LibImportStage,
    val result: LibBookmarkImportResult? = null,
    val error: String? = null,
) {
    val running: Boolean get() = stage != LibImportStage.Finished
}

/** One title to add, with every folder it sits in. */
data class LibImportTitle(
    val titleId: String,
    val title: String,
    val coverUrl: String?,
    val typeLabel: String?,
    val folderIds: Set<String>,
    /** The last chapter read on the site, if any. */
    val lastRead: LibReadChapter? = null,
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
            lastRead = entries.firstNotNullOfOrNull { it.lastRead },
        )
    }
    val used = titles.flatMapTo(HashSet()) { it.folderIds }
    return LibImportPlan(folders.filter { it.id in used }, titles)
}

/** The chapter to continue from and every chapter (all teams' copies) up to and including it. */
internal data class LibReadTarget(val last: Chapter, val read: List<Chapter>)

/**
 * Finds the chapter a site bookmark names in [chapters] (newest first, as `getChapters` gives
 * them) by volume and number. When the volume doesn't match, a number that occurs only once
 * still counts. Null when the chapter isn't in the list.
 */
internal fun libChaptersReadUpTo(chapters: List<Chapter>, lastRead: LibReadChapter): LibReadTarget? {
    if (chapters.isEmpty()) return null
    fun key(c: Chapter) = libVolumeKey(c.volume) to libNumberKey(c.number)
    val keys = chapters.asReversed().map(::key).distinct()
    val wanted = libVolumeKey(lastRead.volume) to libNumberKey(lastRead.number)
    var index = keys.indexOf(wanted)
    if (index < 0) {
        val sameNumber = keys.withIndex().filter { it.value.second == wanted.second }
        index = sameNumber.singleOrNull()?.index ?: return null
    }
    val readKeys = keys.subList(0, index + 1).toHashSet()
    val copies = chapters.filter { key(it) == keys[index] }
    val last = copies.firstOrNull { lastRead.branchId != null && it.branchId == lastRead.branchId } ?: copies.first()
    return LibReadTarget(last, chapters.filter { key(it) in readKeys })
}

private fun libNumberKey(raw: String?): String {
    val s = raw?.trim()?.replace(',', '.').orEmpty()
    return s.toBigDecimalOrNull()?.stripTrailingZeros()?.toPlainString() ?: s
}

private fun libVolumeKey(raw: String?): String = libNumberKey(raw?.takeIf { it != "null" })
