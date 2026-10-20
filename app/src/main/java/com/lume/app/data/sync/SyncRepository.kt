package com.lume.app.data.sync

import android.content.Context
import android.net.Uri
import android.os.Build
import androidx.room.withTransaction
import com.lume.app.R
import com.lume.app.data.backup.ChapterProgressDto
import com.lume.app.data.backup.ChapterReadDto
import com.lume.app.data.backup.PageBookmarkDto
import com.lume.app.data.backup.ProgressEntityDto
import com.lume.app.data.backup.SourcePackDto
import com.lume.app.data.backup.TitlePrefsDto
import com.lume.app.data.backup.TitleSourceDto
import com.lume.app.data.backup.TrackerLinkDto
import com.lume.app.data.db.BookmarkEntity
import com.lume.app.data.db.CategoryEntity
import com.lume.app.data.db.ChapterProgressEntity
import com.lume.app.data.db.ChapterReadEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.LumeDatabase
import com.lume.app.data.db.PageBookmarkEntity
import com.lume.app.data.db.ProgressEntity
import com.lume.app.data.db.SourcePackEntity
import com.lume.app.data.db.TitlePrefsEntity
import com.lume.app.data.db.TitleSourceEntity
import com.lume.app.data.db.TrackerLinkEntity
import com.lume.app.data.library.LibraryRepository
import com.lume.app.data.library.encodeCategoryIds
import com.lume.app.data.library.parseCategoryIds
import com.lume.app.data.locale.appString
import com.lume.app.data.network.AppHttp
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.data.prefs.SyncSettings
import com.lume.app.data.prefs.SyncStatus
import com.lume.app.data.source.SourceRegistry
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.KSerializer
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonElement
import java.io.File
import java.util.UUID
import kotlin.random.Random

/** A library entry as it syncs: categories by name, because their ids differ between devices. */
@Serializable
data class LibrarySyncDto(
    val sourceId: String,
    val titleId: String,
    val titleName: String,
    val coverUrl: String? = null,
    val typeLabel: String? = null,
    val createdAt: Long = 0L,
    val categories: List<String> = emptyList(),
)

@Serializable
data class CategorySyncDto(val name: String, val sortOrder: Int = 0)

/**
 * Sync between devices without an account: every device keeps one file in a shared place (a
 * folder or a WebDAV server), reads the others' files and merges them with [SyncMerge].
 *
 * Library, categories, reading positions, read marks, page bookmarks, linked sources, tracker
 * links, per-title reader settings and installed packs sync. Sign-ins, downloads and app settings
 * don't.
 */
class SyncRepository(
    private val context: Context,
    private val database: LumeDatabase,
    private val dao: LibraryDao,
    private val prefs: AppPreferences,
    private val library: LibraryRepository,
    private val sources: SourceRegistry,
    private val http: AppHttp,
) {
    private val mutex = Mutex()
    private val json = SyncFile.json

    private val _running = MutableStateFlow(false)
    val running: StateFlow<Boolean> = _running.asStateFlow()

    /** Kept out of every backup and device transfer, so two devices never share an id. */
    private val stateFile get() = File(context.noBackupFilesDir, "sync_state.json")
    private val idFile get() = File(context.noBackupFilesDir, "sync_device_id")

    val deviceId: String by lazy {
        idFile.takeIf { it.exists() }?.readText()?.trim()?.takeIf { it.isNotEmpty() }
            ?: UUID.randomUUID().toString().also { idFile.writeText(it) }
    }

    private val ownFileName get() = "$PREFIX$deviceId$SUFFIX"

    /** One sync; the result also goes to [AppPreferences.syncStatus]. Does nothing when sync is off. */
    suspend fun sync(): SyncStatus? = mutex.withLock {
        val settings = prefs.syncSettings.value
        if (!settings.ready) return@withLock null
        _running.value = true
        val status = try {
            withContext(Dispatchers.IO) { runSync(settings) }
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            android.util.Log.w("Sync", "Sync failed", e)
            prefs.syncStatus.value.copy(error = describe(e))
        } finally {
            _running.value = false
        }
        prefs.setSyncStatus(status)
        status
    }

    private suspend fun runSync(settings: SyncSettings): SyncStatus {
        sources.awaitLoaded()
        val backend = backendFor(settings)
        val now = System.currentTimeMillis()
        val local = readLocal()
        val databaseId = databaseMarker()
        val saved = loadSaved()
        var previous = saved?.state().orEmpty()
        // A database created since the last sync lost its records; they are not removals.
        if (SyncMerge.databaseRecreated(saved?.databaseId ?: 0, databaseId)) previous = emptyMap()
        val own = SyncMerge.stamp(local, previous, now, bootstrap = previous.isEmpty(), device = deviceId)

        val passphrase = settings.passphrase.ifEmpty { null }
        // A file that can't be read with this passphrase (damaged, or from another setup) is skipped
        // as long as some other device's file decoded; only when none did, the passphrase is the problem.
        var passphraseError: SyncPassphraseException? = null
        val others = backend.list()
            .filter { it.startsWith(PREFIX) && it.endsWith(SUFFIX) && it != ownFileName }
            .mapNotNull { name ->
                val bytes = backend.read(name)
                val file = runCatching { json.decodeFromString(SyncFile.serializer(), SyncCodec.decode(bytes, passphrase)) }
                    .onFailure { if (it is SyncPassphraseException) passphraseError = it }
                    .getOrNull()
                file?.takeIf { it.deviceId != deviceId && it.format <= SyncFile.FORMAT }
            }
        passphraseError?.let { if (others.isEmpty()) throw it }

        val merged = SyncMerge.merge(own, others.map { it.state() }, now)
        val changes = SyncMerge.changes(local, merged)
        val failed = if (changes.isNotEmpty()) apply(changes) else emptyList()
        // A record that could not be applied stays as this device had it, so the next sync does not
        // take it for a removal: its previous entry, or none if it had none.
        val kept = if (failed.isEmpty()) merged else merged.mapValues { (name, entries) ->
            val failedKeys = failed.filter { it.first == name }.map { it.second }.toSet()
            if (failedKeys.isEmpty()) entries
            else entries.filterKeys { it !in failedKeys } + failedKeys.mapNotNull { key -> own[name]?.get(key)?.let { key to it } }
        }.filterValues { it.isNotEmpty() }

        val out = SyncFile(
            deviceId = deviceId,
            deviceName = Build.MODEL.orEmpty(),
            writtenAt = now,
            databaseId = databaseId,
            collections = kept.mapValues { (_, entries) -> entries.values.sortedBy { it.key } },
        )
        val text = json.encodeToString(SyncFile.serializer(), out)
        backend.write(ownFileName, SyncCodec.encode(text, passphrase))
        stateFile.writeText(text)
        return SyncStatus(lastAt = now, error = null, devices = others.size, changes = changes.size)
    }

    private fun backendFor(settings: SyncSettings): SyncBackend = when (settings.backend) {
        SyncSettings.FOLDER -> FolderSyncBackend(context, Uri.parse(settings.folder))
        else -> WebDavSyncBackend(http.okHttp, settings.webDavUrl, settings.webDavUser, settings.webDavPassword)
    }

    private fun loadSaved(): SyncFile? = runCatching {
        if (!stateFile.exists()) return null
        json.decodeFromString(SyncFile.serializer(), stateFile.readText())
    }.getOrNull()

    /**
     * An id stored in the database file itself (SQLite's `application_id`, which Room leaves alone),
     * so a recreated database is told apart from an emptied library. Set on first use.
     */
    private fun databaseMarker(): Int {
        val db = database.openHelper.writableDatabase
        val current = db.query("PRAGMA application_id").use { if (it.moveToFirst()) it.getInt(0) else 0 }
        if (current != 0) return current
        val id = Random.nextInt(1, Int.MAX_VALUE)
        db.execSQL("PRAGMA application_id = $id")
        return id
    }

    private fun SyncFile.state(): SyncState = collections.mapValues { (_, list) -> list.associateBy { it.key } }

    private fun describe(e: Exception): String = when (e) {
        is SyncPassphraseException -> appString(if (e.missing) R.string.sync_error_passphrase_missing else R.string.sync_error_passphrase_wrong)
        is SyncBackendException -> e.message.orEmpty()
        is java.net.UnknownHostException, is java.net.ConnectException, is java.net.SocketTimeoutException ->
            appString(R.string.sync_error_offline)
        else -> e.message ?: e.javaClass.simpleName
    }

    // ---- Local records --------------------------------------------------------------------

    private fun <T> encode(serializer: KSerializer<T>, value: T): JsonElement = json.encodeToJsonElement(serializer, value)

    private fun <T> decode(serializer: KSerializer<T>, data: JsonElement): T = json.decodeFromJsonElement(serializer, data)

    private suspend fun readLocal(): Map<String, Map<String, JsonElement>> {
        val categories = dao.allCategories()
        val nameById = categories.associate { it.id to it.name }
        return mapOf(
            CATEGORIES to categories.associate {
                it.name.lowercase() to encode(CategorySyncDto.serializer(), CategorySyncDto(it.name, it.sortOrder))
            },
            SyncMerge.LIBRARY to dao.allBookmarks().associate {
                key(it.sourceId, it.titleId) to encode(
                    LibrarySyncDto.serializer(),
                    LibrarySyncDto(
                        sourceId = it.sourceId,
                        titleId = it.titleId,
                        titleName = it.titleName,
                        coverUrl = it.coverUrl,
                        typeLabel = it.typeLabel,
                        createdAt = it.createdAt,
                        categories = parseCategoryIds(it.categoryIdsJson).mapNotNull(nameById::get).sortedBy(String::lowercase),
                    ),
                )
            },
            PROGRESS to dao.allProgress().associate {
                key(it.sourceId, it.titleId) to encode(
                    ProgressEntityDto.serializer(),
                    ProgressEntityDto(
                        it.sourceId, it.titleId, it.titleName, it.coverUrl, it.typeLabel, it.chapterId,
                        it.chapterName, it.chapterUrl, it.pageIndex, it.pageCount, it.updatedAt, it.pageOffset,
                    ),
                )
            },
            SyncMerge.FURTHEST_WINS to dao.allChapterProgress().associate {
                key(it.sourceId, it.titleId, it.chapterId) to encode(
                    ChapterProgressDto.serializer(),
                    ChapterProgressDto(
                        it.sourceId, it.titleId, it.chapterId, it.pageIndex, it.pageOffset, it.pageCount,
                        it.completed, it.pageAspectsJson, it.updatedAt,
                    ),
                )
            },
            CHAPTER_READ to dao.allChapterRead().associate {
                key(it.sourceId, it.titleId, it.chapterId) to
                    encode(ChapterReadDto.serializer(), ChapterReadDto(it.sourceId, it.titleId, it.chapterId))
            },
            PAGE_BOOKMARKS to dao.allPageBookmarks().associate {
                pageBookmarkKey(it) to encode(
                    PageBookmarkDto.serializer(),
                    PageBookmarkDto(
                        it.sourceId, it.titleId, it.chapterId, it.chapterName, it.pageIndex, it.pageOffset,
                        it.pageImageUrl, it.note, it.createdAt,
                    ),
                )
            },
            TITLE_SOURCES to dao.allTitleSources().associate {
                key(it.sourceId, it.titleId, it.linkSourceId, it.linkTitleId) to encode(
                    TitleSourceDto.serializer(),
                    TitleSourceDto(
                        it.sourceId, it.titleId, it.linkSourceId, it.linkTitleId, it.linkTitleName, it.coverUrl,
                        it.state, it.priority, it.addedAt,
                    ),
                )
            },
            // The last tracker sync time is this device's business; it would make every link "change".
            TRACKER_LINKS to dao.allTrackerLinks().associate {
                key(it.sourceId, it.titleId, it.tracker) to encode(
                    TrackerLinkDto.serializer(),
                    TrackerLinkDto(it.sourceId, it.titleId, it.tracker, it.remoteId, it.remoteUrl, it.status, it.score),
                )
            },
            TITLE_PREFS to dao.allTitlePrefs().associate {
                key(it.sourceId, it.titleId) to encode(
                    TitlePrefsDto.serializer(),
                    TitlePrefsDto(
                        it.sourceId, it.titleId, it.readerMode, it.readerTheme, it.gap, it.chapterFilter,
                        it.chapterSortAsc, it.branch, it.showDuplicates,
                    ),
                )
            },
            SOURCE_PACKS to dao.allSourcePacks().associate {
                it.id to encode(SourcePackDto.serializer(), SourcePackDto(it.id, it.json, it.installedAt))
            },
        ).filterValues { it.isNotEmpty() }
    }

    /** Applies the changes and returns the (collection, key) of every one that failed. */
    private suspend fun apply(changes: List<SyncChange>): List<Pair<String, String>> {
        failed.clear()
        val byCollection = changes.groupBy { it.collection }
        // Packs first (sources), then categories (the library refers to them by name).
        val packs = byCollection[SOURCE_PACKS].orEmpty()
        for (change in packs) guarded(change) { when (change) {
            is SyncChange.Put -> decode(SourcePackDto.serializer(), change.data).let {
                dao.upsertSourcePack(SourcePackEntity(it.id, it.json, it.installedAt))
            }
            is SyncChange.Remove -> dao.deleteSourcePack(change.key)
        } }
        if (packs.isNotEmpty()) sources.load()

        for (change in byCollection[CATEGORIES].orEmpty()) guarded(change) {
            val existing = dao.allCategories().firstOrNull { it.name.lowercase() == change.key }
            when (change) {
                is SyncChange.Put -> {
                    val dto = decode(CategorySyncDto.serializer(), change.data)
                    dao.upsertCategory(existing?.copy(name = dto.name, sortOrder = dto.sortOrder) ?: CategoryEntity(name = dto.name, sortOrder = dto.sortOrder))
                }
                is SyncChange.Remove -> if (existing != null) library.deleteCategory(existing.id)
            }
        }
        val idByName = dao.allCategories().associate { it.name.lowercase() to it.id }

        database.withTransaction {
            for (change in byCollection[SyncMerge.LIBRARY].orEmpty()) guarded(change) { when (change) {
                is SyncChange.Put -> decode(LibrarySyncDto.serializer(), change.data).let {
                    dao.upsertBookmark(
                        BookmarkEntity(
                            sourceId = it.sourceId,
                            titleId = it.titleId,
                            titleName = it.titleName,
                            coverUrl = it.coverUrl,
                            typeLabel = it.typeLabel,
                            createdAt = it.createdAt,
                            categoryIdsJson = encodeCategoryIds(it.categories.mapNotNull { n -> idByName[n.lowercase()] }.toSet()),
                        ),
                    )
                }
                is SyncChange.Remove -> parts(change.key).let { (s, t) -> library.remove(s, t) }
            } }
            for (change in byCollection[PROGRESS].orEmpty()) guarded(change) { when (change) {
                is SyncChange.Put -> decode(ProgressEntityDto.serializer(), change.data).let {
                    dao.upsertProgress(
                        ProgressEntity(
                            it.sourceId, it.titleId, it.titleName, it.coverUrl, it.typeLabel, it.chapterId,
                            it.chapterName, it.chapterUrl, it.pageIndex, it.pageCount, it.updatedAt, it.pageOffset,
                        ),
                    )
                }
                is SyncChange.Remove -> parts(change.key).let { (s, t) -> dao.clearProgress(s, t) }
            } }
            for (change in byCollection[SyncMerge.FURTHEST_WINS].orEmpty()) guarded(change) { when (change) {
                is SyncChange.Put -> decode(ChapterProgressDto.serializer(), change.data).let {
                    dao.upsertChapterProgress(
                        ChapterProgressEntity(
                            it.sourceId, it.titleId, it.chapterId, it.pageIndex, it.pageOffset, it.pageCount,
                            it.completed, it.pageAspectsJson, it.updatedAt,
                        ),
                    )
                }
                is SyncChange.Remove -> parts(change.key).let { (s, t, c) -> dao.deleteChapterProgress(s, t, listOf(c)) }
            } }
            val now = System.currentTimeMillis()
            for (change in byCollection[CHAPTER_READ].orEmpty()) guarded(change) { when (change) {
                is SyncChange.Put -> decode(ChapterReadDto.serializer(), change.data).let {
                    dao.markChapterRead(ChapterReadEntity(it.sourceId, it.titleId, it.chapterId, now))
                }
                is SyncChange.Remove -> parts(change.key).let { (s, t, c) -> dao.markChaptersUnread(s, t, listOf(c)) }
            } }
            val bookmarkIds = dao.allPageBookmarks().associate { pageBookmarkKey(it) to it.id }
            for (change in byCollection[PAGE_BOOKMARKS].orEmpty()) guarded(change) { when (change) {
                is SyncChange.Put -> decode(PageBookmarkDto.serializer(), change.data).let {
                    val entity = PageBookmarkEntity(
                        sourceId = it.sourceId,
                        titleId = it.titleId,
                        chapterId = it.chapterId,
                        chapterName = it.chapterName,
                        pageIndex = it.pageIndex,
                        pageOffset = it.pageOffset,
                        pageImageUrl = it.pageImageUrl,
                        note = it.note,
                        createdAt = it.createdAt,
                    )
                    val id = bookmarkIds[change.key]
                    if (id != null) dao.updatePageBookmarkNote(id, it.note) else dao.insertPageBookmark(entity)
                }
                is SyncChange.Remove -> bookmarkIds[change.key]?.let { dao.deletePageBookmark(it) }
            } }
            for (change in byCollection[TITLE_SOURCES].orEmpty()) guarded(change) { when (change) {
                is SyncChange.Put -> decode(TitleSourceDto.serializer(), change.data).let {
                    dao.upsertTitleSource(
                        TitleSourceEntity(
                            it.sourceId, it.titleId, it.linkSourceId, it.linkTitleId, it.linkTitleName, it.coverUrl,
                            it.state, it.priority, it.addedAt,
                        ),
                    )
                }
                is SyncChange.Remove -> parts(change.key).let { p -> dao.deleteTitleSource(p[0], p[1], p[2], p[3]) }
            } }
            for (change in byCollection[TRACKER_LINKS].orEmpty()) guarded(change) { when (change) {
                is SyncChange.Put -> decode(TrackerLinkDto.serializer(), change.data).let {
                    val (s, t, tracker) = parts(change.key)
                    val lastSync = dao.getTrackerLinks(s, t).firstOrNull { l -> l.tracker == tracker }?.lastSync ?: 0L
                    dao.upsertTrackerLink(
                        TrackerLinkEntity(it.sourceId, it.titleId, it.tracker, it.remoteId, it.remoteUrl, it.status, it.score, lastSync),
                    )
                }
                is SyncChange.Remove -> parts(change.key).let { (s, t, tracker) -> dao.deleteTrackerLink(s, t, tracker) }
            } }
            for (change in byCollection[TITLE_PREFS].orEmpty()) guarded(change) { when (change) {
                is SyncChange.Put -> decode(TitlePrefsDto.serializer(), change.data).let {
                    dao.upsertTitlePrefs(
                        TitlePrefsEntity(
                            sourceId = it.sourceId,
                            titleId = it.titleId,
                            readerMode = it.readerMode,
                            readerTheme = it.readerTheme,
                            gap = it.gap,
                            chapterFilter = it.chapterFilter,
                            chapterSortAsc = it.chapterSortAsc,
                            branch = it.branch,
                            showDuplicates = it.showDuplicates,
                        ),
                    )
                }
                is SyncChange.Remove -> parts(change.key).let { (s, t) -> dao.deleteTitlePrefs(s, t) }
            } }
        }
        return failed.toList()
    }

    /** Changes that failed in the current [apply]; only touched under [mutex]. */
    private val failed = mutableListOf<Pair<String, String>>()

    /** One record that can't be applied must not abort the whole sync. */
    private inline fun guarded(change: SyncChange, block: () -> Unit) {
        runCatching { block() }.onFailure {
            if (it is kotlinx.coroutines.CancellationException) throw it
            failed += change.collection to change.key
            android.util.Log.w("Sync", "Could not apply ${change.collection} change", it)
        }
    }

    private fun pageBookmarkKey(b: PageBookmarkEntity) =
        key(b.sourceId, b.titleId, b.chapterId, b.pageIndex.toString(), b.createdAt.toString())

    companion object {
        const val PREFIX = "lume-sync-"
        const val SUFFIX = ".bin"

        const val CATEGORIES = "categories"
        const val PROGRESS = SyncMerge.LAST_READ_WINS
        const val CHAPTER_READ = "chapter_read"
        const val PAGE_BOOKMARKS = "page_bookmarks"
        const val TITLE_SOURCES = "title_sources"
        const val TRACKER_LINKS = "tracker_links"
        const val TITLE_PREFS = "title_prefs"
        const val SOURCE_PACKS = "source_packs"

        private const val SEP = '\u001f'

        fun key(vararg parts: String) = parts.joinToString(SEP.toString())

        fun parts(key: String): List<String> = key.split(SEP)
    }
}
