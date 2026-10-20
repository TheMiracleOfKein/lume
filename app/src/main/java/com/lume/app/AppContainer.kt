package com.lume.app

import android.app.Application
import androidx.compose.runtime.staticCompositionLocalOf
import androidx.room.Room
import coil.ImageLoader
import coil.imageLoader
import com.lume.app.data.backup.BackupRepository
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.LumeDatabase
import com.lume.app.data.downloads.DownloadQueue
import com.lume.app.data.downloads.DownloadStore
import com.lume.app.data.library.LibraryChapters
import com.lume.app.data.library.LibraryRepository
import com.lume.app.data.library.LibraryUpdateWorker
import com.lume.app.data.network.AppHttp
import com.lume.app.data.network.ImageReferers
import com.lume.app.data.network.NetworkMonitor
import com.lume.app.data.offline.OfflineTitleCache
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.data.source.LibAuthStore
import com.lume.app.data.source.SourceRegistry
import com.lume.app.data.title.TitleRepository
import com.lume.app.data.tracker.TrackerRegistry
import com.lume.app.data.tracker.TrackerSync
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.launch

/**
 * Everything the app shares, built once per process. Screens get it through [LocalAppContainer],
 * ViewModels through their constructor, workers and activities through [LumeApp.container].
 */
class AppContainer(val app: Application) {
    /**
     * Lives as long as the process. Used for work that must finish even when the screen that
     * started it is gone (for example saving reading progress after Back).
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    val http = AppHttp()
    val network = NetworkMonitor(app)
    val imageReferers = ImageReferers()

    val database: LumeDatabase = Room.databaseBuilder(app, LumeDatabase::class.java, "lume.db")
        // Prefer additive migrations; destructive wipes library/download metadata.
        .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
        .addMigrations(*LumeDatabase.MIGRATIONS)
        .build()
    val dao: LibraryDao = database.libraryDao()

    // Reads the preferences file once, so every screen's first frame has the stored values.
    val prefs = AppPreferences(app, appScope)
    val libAuth = LibAuthStore(app)
    val sources = SourceRegistry(app, http, prefs, dao, libAuth, imageReferers)
    val downloads = DownloadStore(app, dao, http, prefs)
    val offlineCache = OfflineTitleCache(dao)
    val localImporter = com.lume.app.data.local.LocalImporter(app)
    val libraryChapters = LibraryChapters(database, dao)
    val titles = TitleRepository(sources, offlineCache, libraryChapters, dao)
    val downloadQueue = DownloadQueue(app, dao, downloads, sources, titles, prefs, network)
    val backup = BackupRepository(app, dao, sources, prefs, titles, libraryChapters)
    val trackers = TrackerRegistry(http, dao, prefs)
    val trackerSync = TrackerSync(app, dao, trackers, prefs)
    val similarTitles = com.lume.app.data.similar.SimilarTitlesRepository(http, dao, trackers.anilist, trackers.myAnimeList)
    val titleSources = com.lume.app.data.title.TitleSourceRepository(database, dao, sources, titles, libraryChapters, downloads, prefs)
    val library = LibraryRepository(database, dao, prefs, titles, libraryChapters, trackerSync, titleSources, appScope)
    val readingLists = com.lume.app.data.library.ReadingLists(app, dao, sources, library)

    val imageLoader: ImageLoader get() = app.imageLoader

    /** Launcher shortcuts and the home-screen widget for "Continue". */
    val continueReading = com.lume.app.data.continuity.ContinueReading(app, dao, prefs, { app.imageLoader }, appScope)

    /** Background start-up work: sources, download recovery, the library schedule. */
    fun start() {
        com.lume.app.domain.ChapterLabels.volume = { com.lume.app.data.locale.appString(com.lume.app.R.string.chapter_volume, it) }
        com.lume.app.domain.ChapterLabels.chapter = { com.lume.app.data.locale.appString(com.lume.app.R.string.chapter_number, it) }
        appScope.launch { prefs.translateChapterLabels.collect { com.lume.app.domain.ChapterLabels.enabled = it } }
        continueReading.start()
        appScope.launch(Dispatchers.IO) {
            prefs.dnsOverHttps.collect { id -> http.dns.use(com.lume.app.data.network.DohProvider.byId(id)) }
        }
        appScope.launch(Dispatchers.IO) {
            sources.load()
            val recovered = downloads.recoverFromDisk()
            if (recovered > 0) {
                android.util.Log.i("LumeApp", "Recovered $recovered download(s) from disk")
            }
            runCatching {
                val enriched = downloads.enrichMetadata(sources, offlineCache)
                if (enriched > 0) {
                    android.util.Log.i("LumeApp", "Enriched $enriched download row(s)")
                }
            }
            // After the v8 upgrade: know the chapters of every library title, so the first check
            // reports only chapters that are really new.
            runCatching { libraryChapters.seedFromCache(offlineCache) }
            com.lume.app.data.backup.BackupWorker.schedule(app, prefs.autoBackupDays.value)
            LibraryUpdateWorker.schedule(
                app,
                enabled = prefs.libraryUpdatesEnabled.value,
                intervalHours = prefs.libraryUpdateIntervalHours.value,
                wifiOnly = prefs.libraryUpdateWifiOnly.value,
            )
        }
    }
}

val LocalAppContainer = staticCompositionLocalOf<AppContainer> {
    error("AppContainer not provided")
}
