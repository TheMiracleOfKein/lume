package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.snapshotFlow
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lume.app.AppContainer
import com.lume.app.data.db.CategoryEntity
import com.lume.app.data.db.ProgressEntity
import com.lume.app.data.db.DownloadQueueEntity
import com.lume.app.data.db.TitleJsonRow
import com.lume.app.data.db.UpdateRow
import com.lume.app.data.library.LibraryUpdateWorker
import com.lume.app.data.library.parseCategoryIds
import com.lume.app.domain.Chapter
import com.lume.app.domain.LibraryItem
import com.lume.app.domain.LibrarySettings
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.isCompletedStatus
import com.lume.app.domain.queryLibrary
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import androidx.work.WorkInfo
import androidx.work.WorkManager

class HistoryViewModel(app: AppContainer) : ViewModel() {
    val history: StateFlow<List<ProgressEntity>> =
        app.dao.observeHistory().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
}

/** Status and alternative title from the cached details, for filters and search. */
private data class TitleInfo(val altTitle: String?, val completed: Boolean)

class LibraryViewModel(private val app: AppContainer) : ViewModel() {
    private val json = Json { ignoreUnknownKeys = true }
    private val dao = app.dao

    val categories: StateFlow<List<CategoryEntity>> =
        dao.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val settings: StateFlow<LibrarySettings> = app.prefs.librarySettings
    val online: StateFlow<Boolean> = app.network.online

    private val titleInfo = dao.observeLibraryTitleJson().map { rows ->
        rows.associate { row: TitleJsonRow ->
            val details = runCatching { json.decodeFromString<TitleDetails>(row.titleJson) }.getOrNull()
            "${row.sourceId}\u0000${row.titleId}" to TitleInfo(details?.altTitle, isCompletedStatus(details?.status))
        }
    }

    /** Cached details plus the number of linked sources per title. */
    private val titleExtras = combine(titleInfo, dao.observeLinkedSourceCounts()) { info, links ->
        info to links.associate { "${it.sourceId}\u0000${it.titleId}" to it.linked }
    }

    /** Every library title with its counts, before category, search, filters and sorting; null until the first database answer. */
    private val items: StateFlow<List<LibraryItem>?> = combine(
        dao.observeBookmarks(),
        dao.observeLibraryStats(),
        dao.observeTitleMeta(),
        dao.observeTrackedTitles(),
        titleExtras,
    ) { bookmarks, stats, meta, tracked, (info, linked) ->
        val statsByKey = stats.associateBy { "${it.sourceId}\u0000${it.titleId}" }
        val newKeys = meta.filter { it.unreadNewCount > 0 }.mapTo(HashSet()) { "${it.sourceId}\u0000${it.titleId}" }
        val trackedKeys = tracked.mapTo(HashSet()) { "${it.sourceId}\u0000${it.titleId}" }
        bookmarks.map { b ->
            val key = "${b.sourceId}\u0000${b.titleId}"
            val s = statsByKey[key]
            LibraryItem(
                sourceId = b.sourceId,
                titleId = b.titleId,
                title = b.titleName,
                altTitle = info[key]?.altTitle,
                coverUrl = b.coverUrl,
                typeLabel = b.typeLabel,
                addedAt = b.createdAt,
                categoryIds = parseCategoryIds(b.categoryIdsJson),
                totalChapters = s?.total ?: 0,
                unread = s?.unread ?: 0,
                downloaded = s?.downloaded ?: 0,
                lastReadAt = s?.lastReadAt,
                latestChapterAt = s?.latestFoundAt,
                tracking = key in trackedKeys,
                completed = info[key]?.completed == true,
                hasNew = key in newKeys,
                sources = 1 + (linked[key] ?: 0),
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Selected category tab; null = all. */
    var categoryId by mutableStateOf<Long?>(null)

    var searchOpen by mutableStateOf(false)
        private set
    var search by mutableStateOf("")

    /** What the screen shows. Offline, only titles with downloads. */
    val visible: StateFlow<List<LibraryItem>> = combine(
        items, settings, snapshotFlow { categoryId }, snapshotFlow { search }, online,
    ) { loadedItems, s, category, query, isOnline ->
        val all = loadedItems.orEmpty()
        val base = if (isOnline) all else all.filter { it.downloaded > 0 }
        queryLibrary(base, s, category, query)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** False until the first database answer, so an empty library isn't flashed on start. */
    val loaded: StateFlow<Boolean> = items.map { it != null }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

    init {
        // A deleted category can't stay selected.
        viewModelScope.launch {
            categories.collect { list -> if (categoryId != null && list.none { it.id == categoryId }) categoryId = null }
        }
    }

    fun openSearch() {
        searchOpen = true
    }

    fun closeSearch() {
        searchOpen = false
        search = ""
    }

    fun updateSettings(transform: (LibrarySettings) -> LibrarySettings) {
        viewModelScope.launch { app.prefs.setLibrarySettings(transform(settings.value)) }
    }

    // ---- Selection ----------------------------------------------------------------------------

    var selected by mutableStateOf<Set<String>>(emptySet())
        private set
    private var anchorKey: String? = null

    val selecting: Boolean get() = selected.isNotEmpty()

    fun toggle(item: LibraryItem) {
        selected = if (item.key in selected) selected - item.key else selected + item.key
        anchorKey = item.key
    }

    /** Long-press while selecting: everything between the last tapped title and this one. */
    fun selectRange(item: LibraryItem) {
        val list = visible.value
        val from = list.indexOfFirst { it.key == anchorKey }
        val to = list.indexOfFirst { it.key == item.key }
        if (from < 0 || to < 0) return toggle(item)
        selected = selected + list.subList(minOf(from, to), maxOf(from, to) + 1).map { it.key }
        anchorKey = item.key
    }

    fun selectAll() {
        selected = selected + visible.value.map { it.key }
    }

    fun invertSelection() {
        selected = visible.value.map { it.key }.filterNot { it in selected }.toSet()
    }

    fun clearSelection() {
        selected = emptySet()
        anchorKey = null
    }

    private fun selectedItems(): List<LibraryItem> = items.value.orEmpty().filter { it.key in selected }

    /** Categories every selected title already has (the dialog starts from these). */
    fun commonCategories(): Set<Long> =
        selectedItems().map { it.categoryIds }.reduceOrNull { a, b -> a intersect b }.orEmpty()

    private fun bulk(action: suspend (LibraryItem) -> Unit) {
        val targets = selectedItems()
        clearSelection()
        viewModelScope.launch { targets.forEach { action(it) } }
    }

    fun setCategories(ids: Set<Long>) {
        val targets = selectedItems().map { it.sourceId to it.titleId }
        clearSelection()
        viewModelScope.launch { app.library.setCategories(targets, ids) }
    }

    fun markRead() = bulk { app.library.markTitleRead(it.sourceId, it.titleId) }

    fun markUnread() = bulk { app.library.markTitleUnread(it.sourceId, it.titleId) }

    fun downloadUnread() = bulk { item ->
        app.downloadQueue.enqueueAll(
            sourceId = item.sourceId,
            titleId = item.titleId,
            chapters = app.library.unreadChapters(item.sourceId, item.titleId),
            titleName = item.title,
            coverUrl = item.coverUrl,
        )
    }

    fun removeFromLibrary() = bulk { app.library.remove(it.sourceId, it.titleId) }

    // ---- Reading lists ------------------------------------------------------------------------

    /** What a reading list made now holds: the selected titles, else everything on screen. */
    fun exportItems(): List<LibraryItem> = if (selecting) selectedItems() else visible.value

    /** The open category's name, or [fallback] on "All". */
    fun exportName(fallback: String): String = categories.value.firstOrNull { it.id == categoryId }?.name ?: fallback

    fun listFileName(name: String, items: List<LibraryItem>): String =
        app.readingLists.fileName(app.readingLists.build(name, items))

    fun shareList(name: String, items: List<LibraryItem>, onFile: (java.io.File) -> Unit) {
        viewModelScope.launch {
            runCatching { app.readingLists.writeShareFile(app.readingLists.build(name, items)) }.onSuccess(onFile)
        }
    }

    fun saveList(uri: android.net.Uri, name: String, items: List<LibraryItem>, onDone: (Boolean) -> Unit) {
        viewModelScope.launch {
            onDone(runCatching { app.readingLists.writeTo(uri, app.readingLists.build(name, items)) }.isSuccess)
        }
    }
}

class CategoriesViewModel(private val app: AppContainer) : ViewModel() {
    val categories: StateFlow<List<CategoryEntity>> =
        app.dao.observeCategories().stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())
    val defaultCategoryId: StateFlow<Long?> = app.prefs.defaultCategoryId

    fun create(name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { app.library.createCategory(name) }
    }

    fun rename(id: Long, name: String) {
        if (name.isBlank()) return
        viewModelScope.launch { app.library.renameCategory(id, name) }
    }

    fun delete(id: Long) {
        viewModelScope.launch { app.library.deleteCategory(id) }
    }

    fun move(id: Long, delta: Int) {
        viewModelScope.launch { app.library.moveCategory(id, delta) }
    }

    fun setDefault(id: Long?) {
        viewModelScope.launch { app.prefs.setDefaultCategoryId(id) }
    }
}

class UpdatesViewModel(private val app: AppContainer) : ViewModel() {
    val updates: StateFlow<List<UpdateRow>> =
        app.dao.observeUpdates(limit = 500).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    val lastCheckedAt: StateFlow<Long> = app.prefs.libraryLastCheckedAt

    /** `sourceId\u0000titleId\u0000chapterId` of downloaded chapters. */
    val downloaded: StateFlow<Set<String>> = app.dao.observeDownloads()
        .map { rows -> rows.mapTo(HashSet()) { "${it.sourceId}\u0000${it.titleId}\u0000${it.chapterId}" } }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    val queued: StateFlow<Set<String>> = app.downloadQueue.items
        .map { rows ->
            rows.filter { it.status == DownloadQueueEntity.STATUS_PENDING || it.status == DownloadQueueEntity.STATUS_RUNNING }
                .mapTo(HashSet()) { "${it.sourceId}\u0000${it.titleId}\u0000${it.chapterId}" }
        }
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptySet())

    /** Progress of a running check (done, total), or null when none runs. */
    val checking: StateFlow<Pair<Int, Int>?> = combine(
        WorkManager.getInstance(app.app).getWorkInfosForUniqueWorkFlow(LibraryUpdateWorker.NOW_WORK_NAME),
        WorkManager.getInstance(app.app).getWorkInfosForUniqueWorkFlow(LibraryUpdateWorker.WORK_NAME),
    ) { now, periodic ->
        val running = (now + periodic).firstOrNull { it.state == WorkInfo.State.RUNNING }
        val queuedNow = now.any { it.state == WorkInfo.State.ENQUEUED }
        when {
            running != null -> running.progress.getInt(LibraryUpdateWorker.KEY_DONE, 0) to
                running.progress.getInt(LibraryUpdateWorker.KEY_TOTAL, 0)
            queuedNow -> 0 to 0
            else -> null
        }
    }.stateIn(viewModelScope, SharingStarted.Eagerly, null)

    /** Library titles with the bell: the ones a check looks at. */
    val watchedCount: StateFlow<Int> = app.dao.observeWatchedCount()
        .stateIn(viewModelScope, SharingStarted.Eagerly, 0)

    fun updateNow() = LibraryUpdateWorker.runNow(app.app)

    fun download(row: UpdateRow) {
        app.downloadQueue.enqueue(
            sourceId = row.sourceId,
            titleId = row.titleId,
            chapter = Chapter(id = row.chapterId, title = row.chapterName, url = row.chapterId),
            titleName = row.titleName,
            coverUrl = row.coverUrl,
        )
    }

    fun setRead(row: UpdateRow, read: Boolean) {
        viewModelScope.launch {
            if (read) app.library.markRead(row.sourceId, row.titleId, listOf(row.chapterId))
            else app.library.markUnread(row.sourceId, row.titleId, listOf(row.chapterId))
        }
    }
}
