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
import com.lume.app.data.db.TitleJsonRow
import com.lume.app.data.library.parseCategoryIds
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

    /** Every library title with its counts, before category, search, filters and sorting. */
    private val items: StateFlow<List<LibraryItem>> = combine(
        dao.observeBookmarks(),
        dao.observeLibraryStats(),
        dao.observeTitleMeta(),
        dao.observeTrackedTitles(),
        titleInfo,
    ) { bookmarks, stats, meta, tracked, info ->
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
            )
        }
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Selected category tab; null = all. */
    var categoryId by mutableStateOf<Long?>(null)

    var searchOpen by mutableStateOf(false)
        private set
    var search by mutableStateOf("")

    /** What the screen shows. Offline, only titles with downloads. */
    val visible: StateFlow<List<LibraryItem>> = combine(
        items, settings, snapshotFlow { categoryId }, snapshotFlow { search }, online,
    ) { all, s, category, query, isOnline ->
        val base = if (isOnline) all else all.filter { it.downloaded > 0 }
        queryLibrary(base, s, category, query)
    }.flowOn(Dispatchers.Default).stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** False until the first database answer, so an empty library isn't flashed on start. */
    val loaded: StateFlow<Boolean> = items.map { true }.stateIn(viewModelScope, SharingStarted.Eagerly, false)

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

    private fun selectedItems(): List<LibraryItem> = items.value.filter { it.key in selected }

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
