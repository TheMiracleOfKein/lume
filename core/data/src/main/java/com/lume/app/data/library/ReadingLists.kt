package com.lume.app.data.library

import android.content.Context
import android.net.Uri
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.source.SourceRegistry
import com.lume.app.domain.LibraryItem
import com.lume.app.domain.ReadingList
import com.lume.app.domain.ReadingListEntry
import com.lume.app.domain.ReadingListRow
import com.lume.app.domain.ReadingListState
import com.lume.app.domain.encodeReadingList
import com.lume.app.domain.parseReadingList
import com.lume.app.domain.planReadingList
import com.lume.app.domain.readingListFileName
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.withContext
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Reading lists: library titles written to a file to share, and lists from other people read
 * back and added to the library. See [ReadingList] for what a list holds.
 */
class ReadingLists(
    private val context: Context,
    private val dao: LibraryDao,
    private val sources: SourceRegistry,
    private val library: LibraryRepository,
) {
    private val _opened = MutableStateFlow<ReadingList?>(null)

    /** The list being looked at on the reading list screen (opened from a file or a share). */
    val opened: StateFlow<ReadingList?> = _opened.asStateFlow()

    fun build(name: String, items: List<LibraryItem>): ReadingList = ReadingList(
        name = name,
        createdAt = System.currentTimeMillis(),
        titles = items.map { item ->
            val source = sources.sourceById(item.sourceId)
            ReadingListEntry(
                sourceId = item.sourceId,
                titleId = item.titleId,
                title = item.title,
                coverUrl = item.coverUrl,
                typeLabel = item.typeLabel,
                url = source?.let { runCatching { it.webUrl(item.titleId) }.getOrNull() },
                sourceName = source?.manifest?.name,
            )
        },
    )

    fun fileName(list: ReadingList): String =
        readingListFileName(list.name, SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date(list.createdAt)))

    /** Writes [list] to the cache for the share sheet; the share provider serves `cache/shared/`. */
    suspend fun writeShareFile(list: ReadingList): File = withContext(Dispatchers.IO) {
        val dir = File(context.cacheDir, SHARED_DIR).apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        File(dir, fileName(list)).apply { writeText(encodeReadingList(list)) }
    }

    suspend fun writeTo(uri: Uri, list: ReadingList) = withContext(Dispatchers.IO) {
        val out = context.contentResolver.openOutputStream(uri, "wt") ?: error("Can't write $uri")
        out.bufferedWriter().use { it.write(encodeReadingList(list)) }
    }

    /** Reads a list from a file and keeps it for the reading list screen. */
    suspend fun open(uri: Uri): ReadingList {
        val raw = withContext(Dispatchers.IO) {
            context.contentResolver.openInputStream(uri)?.use { input ->
                // A reading list is small; anything far bigger is some other file.
                val out = java.io.ByteArrayOutputStream()
                val buffer = ByteArray(8 * 1024)
                while (out.size() < MAX_BYTES) {
                    val n = input.read(buffer)
                    if (n < 0) break
                    out.write(buffer, 0, n)
                }
                out.toByteArray().decodeToString()
            } ?: error("Can't read $uri")
        }
        return parseReadingList(raw).also { _opened.value = it }
    }

    fun close() {
        _opened.value = null
    }

    suspend fun plan(list: ReadingList): List<ReadingListRow> {
        sources.awaitLoaded()
        val inLibrary = dao.allBookmarks().mapTo(HashSet()) { it.sourceId to it.titleId }
        return planReadingList(
            list,
            inLibrary = { source, title -> (source to title) in inLibrary },
            hasSource = { sources.sourceById(it) != null },
            resolveLink = sources::resolveLink,
        )
    }

    /** Adds the [rows] that are new, into [categoryId] when one is picked. Returns how many were added. */
    suspend fun add(rows: List<ReadingListRow>, categoryId: Long?): Int {
        val adding = rows.filter { it.state == ReadingListState.New }
        val discover = adding.size <= DISCOVER_UP_TO
        for (row in adding) {
            library.add(row.sourceId, row.titleId, row.entry.title, row.entry.coverUrl, row.entry.typeLabel, discover)
        }
        if (categoryId != null && adding.isNotEmpty()) {
            library.setCategories(adding.map { it.sourceId to it.titleId }, setOf(categoryId))
        }
        return adding.size
    }

    companion object {
        const val SHARED_DIR = "shared"
        const val MIME = "application/json"
        private const val MAX_BYTES = 4 * 1024 * 1024
        /** Short lists still get tracker linking and "Also on …" for every title. */
        private const val DISCOVER_UP_TO = 10
    }
}
