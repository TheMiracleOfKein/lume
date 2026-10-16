package com.lume.app

import android.app.Application
import androidx.room.Room
import coil.ImageLoader
import coil.ImageLoaderFactory
import coil.disk.DiskCache
import coil.memory.MemoryCache
import com.lume.app.data.backup.BackupRepository
import com.lume.app.data.db.LumeDatabase
import com.lume.app.data.downloads.DownloadQueue
import com.lume.app.data.downloads.DownloadStore
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
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch
import okhttp3.OkHttpClient

class LumeApp : Application(), ImageLoaderFactory {
    lateinit var http: AppHttp
        private set
    lateinit var database: LumeDatabase
        private set
    lateinit var prefs: AppPreferences
        private set
    lateinit var sources: SourceRegistry
        private set
    lateinit var downloads: DownloadStore
        private set
    lateinit var downloadQueue: DownloadQueue
        private set
    lateinit var backup: BackupRepository
        private set
    lateinit var trackers: TrackerRegistry
        private set
    lateinit var trackerSync: TrackerSync
        private set
    lateinit var offlineCache: OfflineTitleCache
        private set
    lateinit var titles: TitleRepository
        private set
    lateinit var network: NetworkMonitor
        private set
    lateinit var libAuth: LibAuthStore
        private set
    val imageReferers = ImageReferers()

    val dao get() = database.libraryDao()

    /**
     * Lives as long as the process. Used for work that must finish even when the screen that
     * started it is gone (for example saving reading progress after Back).
     */
    val appScope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        instance = this
        http = AppHttp()
        network = NetworkMonitor(this)
        database = Room.databaseBuilder(this, LumeDatabase::class.java, "lume.db")
            // Prefer additive migrations; destructive wipes library/download metadata.
            .fallbackToDestructiveMigrationOnDowngrade(dropAllTables = true)
            .addMigrations(LumeDatabase.MIGRATION_5_6, LumeDatabase.MIGRATION_6_7)
            .build()
        prefs = AppPreferences(this)
        libAuth = LibAuthStore(this)
        // Per-app language: empty list = follow system (see AppLanguage.SYSTEM).
        runCatching {
            kotlinx.coroutines.runBlocking {
                com.lume.app.data.locale.AppLocaleController.applyStoredTag(prefs.appLanguage.first())
            }
        }
        sources = SourceRegistry(this, http, prefs, dao, libAuth, imageReferers)
        downloads = DownloadStore(this, dao, http)
        offlineCache = OfflineTitleCache(dao)
        titles = TitleRepository(sources, offlineCache)
        downloadQueue = DownloadQueue(dao, downloads, sources, titles)
        backup = BackupRepository(this, dao, sources)
        trackers = TrackerRegistry(http, dao, prefs)
        trackerSync = TrackerSync(this, dao, trackers)
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
            LibraryUpdateWorker.schedule(
                this@LumeApp,
                enabled = prefs.libraryUpdatesEnabled.first(),
                intervalHours = prefs.libraryUpdateIntervalHours.first(),
                wifiOnly = prefs.libraryUpdateWifiOnly.first(),
            )
        }
    }

    override fun newImageLoader(): ImageLoader {
        val client: OkHttpClient = http.okHttp.newBuilder()
            .addInterceptor { chain ->
                val original = chain.request()
                // Same UA as catalogue requests and the challenge WebView (cf_clearance is UA-bound).
                val builder = original.newBuilder()
                    .header("User-Agent", http.userAgent)
                    .header("Accept", "image/avif,image/webp,image/apng,image/*,*/*;q=0.8")
                if (original.header("Referer").isNullOrBlank()) {
                    imageReferers.refererFor(original.url.host)?.let { builder.header("Referer", it) }
                }
                chain.proceed(builder.build())
            }
            .build()
        return ImageLoader.Builder(this)
            .okHttpClient(client)
            .crossfade(true)
            .memoryCache { MemoryCache.Builder(this).maxSizePercent(0.25).build() }
            .diskCache {
                DiskCache.Builder()
                    .directory(cacheDir.resolve("image_cache"))
                    .maxSizeBytes(150L * 1024 * 1024)
                    .build()
            }
            .build()
    }

    companion object {
        lateinit var instance: LumeApp
            private set
    }
}
