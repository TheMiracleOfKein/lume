package com.lume.app.data.source

import android.content.Context
import com.lume.app.R
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.SourcePackEntity
import com.lume.app.data.network.AppHttp
import com.lume.app.data.network.ImageReferers
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.FilterList
import com.lume.app.domain.HomeFeed
import com.lume.app.domain.PackEntry
import com.lume.app.domain.PackIndex
import com.lume.app.domain.resolvePackUrl
import com.lume.app.domain.withSettings
import com.lume.app.domain.PagedResult
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import com.lume.app.domain.SourceManifest
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.serialization.json.Json
import java.net.URI

class SourceRegistry(
    private val context: Context,
    private val http: AppHttp,
    private val prefs: AppPreferences,
    private val dao: LibraryDao,
    private val libAuth: LibAuthStore? = null,
    private val referers: ImageReferers? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) {
    private val bundledIds = mutableSetOf<String>()

    /** Manifests as installed, before per-source settings are merged in. */
    private val baseManifests = java.util.concurrent.ConcurrentHashMap<String, SourceManifest>()
    private val _sources = MutableStateFlow<List<CatalogSource>>(emptyList())
    val sources: StateFlow<List<CatalogSource>> = _sources.asStateFlow()

    val activeSourceId: StateFlow<String?> = prefs.activeSourceId

    private val loadedOnce = CompletableDeferred<Unit>()

    /**
     * Suspends until [load] has run once. Background work (library checks, the download queue)
     * can start before `LumeApp` finishes loading sources and would otherwise find none.
     */
    suspend fun awaitLoaded() = loadedOnce.await()

    fun isBundled(id: String): Boolean = id in bundledIds || id == "local"

    suspend fun load() {
        try {
            bundledIds.clear()
            baseManifests.clear()
            val loaded = mutableListOf<CatalogSource>()
            val assetNames = context.assets.list("sources").orEmpty().filter { it.endsWith(".json") }
            for (name in assetNames) {
                val raw = context.assets.open("sources/$name").bufferedReader().use { it.readText() }
                val manifest = json.decodeFromString(SourceManifest.serializer(), raw)
                bundledIds += manifest.id
                upsert(loaded, createEngine(manifest))
            }
            for (pack in dao.allSourcePacks()) {
                runCatching {
                    val manifest = json.decodeFromString(SourceManifest.serializer(), pack.json)
                    // Legacy packs for non-family engines still load if already installed.
                    upsert(loaded, createEngine(manifest))
                }
            }
            upsert(loaded, DispatchingSource(LocalEngine(context)))
            bundledIds += "local"
            _sources.value = loaded
        } finally {
            // Even a broken bundled manifest must not leave background work waiting forever.
            loadedOnce.complete(Unit)
        }
        val loaded = _sources.value
        if (prefs.activeSourceId.first() == null && loaded.isNotEmpty()) {
            val preferred = loaded.firstOrNull { it.manifest.id != "local" } ?: loaded.first()
            setActive(preferred.manifest.id)
        }
    }

    suspend fun setActive(id: String) {
        prefs.setActiveSourceId(id)
    }

    fun sourceById(id: String): CatalogSource? =
        _sources.value.firstOrNull { it.manifest.id == id }

    suspend fun activeSource(): CatalogSource? {
        val id = prefs.activeSourceId.first()
        return sourceById(id ?: return _sources.value.firstOrNull())
            ?: _sources.value.firstOrNull()
    }

    /**
     * Install a JSON source pack. Only family engines (madara / libsocial / hivetoons)
     * may be imported — new platform engines require an app update.
     */
    suspend fun importJson(raw: String): SourceManifest {
        val manifest = json.decodeFromString(SourceManifest.serializer(), raw)
        if (!SourceEngines.isPackImportable(manifest.engine)) {
            error(SourceEngines.importRejectedMessage(context, manifest.engine))
        }
        if (manifest.id == "local") {
            error(context.getString(R.string.import_reject_local))
        }
        createEngine(manifest) // validate engine wiring
        dao.upsertSourcePack(
            SourcePackEntity(
                id = manifest.id,
                json = json.encodeToString(SourceManifest.serializer(), manifest),
            ),
        )
        val next = _sources.value.toMutableList()
        upsert(next, createEngine(manifest))
        _sources.value = next
        if (prefs.activeSourceId.first() == null) setActive(manifest.id)
        return manifest
    }

    suspend fun importUrl(url: String): SourceManifest {
        val response = http.get(URI(url), checkCloudflare = false)
        return importJson(response.body)
    }

    /** The version of an installed pack, or null when [id] is not installed. */
    fun installedVersion(id: String): String? = baseManifests[id]?.version

    /** Reads the pack catalogue at [url] (defaults to the one in the preferences). */
    suspend fun fetchPackIndex(url: String = prefs.packIndexUrl.value): PackIndex = withContext(Dispatchers.IO) {
        val response = http.get(URI(url), checkCloudflare = false)
        json.decodeFromString(PackIndex.serializer(), response.body)
    }

    suspend fun installPack(indexUrl: String, entry: PackEntry): SourceManifest =
        importUrl(resolvePackUrl(indexUrl, entry.url))

    /** Rebuilds [id]'s engine after its settings changed. */
    suspend fun saveSettings(id: String, values: Map<String, String>) {
        prefs.setSourceSettings(id, values)
        val base = baseManifests[id] ?: return
        val next = _sources.value.toMutableList()
        upsert(next, createEngine(base, values))
        _sources.value = next
    }

    /** Current setting values of [id] (only the ones changed from the defaults are stored). */
    fun settingsOf(id: String): Map<String, String> = prefs.sourceSettings.value[id].orEmpty()

    /**
     * Finds the source and title behind a website link. Bundled sources are asked first, so an
     * imported pack for the same site never wins over the app's own engine.
     */
    fun resolveLink(url: String): Pair<String, String>? {
        val ordered = _sources.value.sortedBy { !isBundled(it.manifest.id) }
        for (source in ordered) {
            if (source.manifest.id == "local") continue
            val titleId = runCatching { source.titleIdFromUrl(url) }.getOrNull() ?: continue
            return source.manifest.id to titleId
        }
        return null
    }

    suspend fun remove(id: String) {
        if (id in bundledIds) return
        dao.deleteSourcePack(id)
        baseManifests.remove(id)
        _sources.value = _sources.value.filterNot { it.manifest.id == id }
        val active = prefs.activeSourceId.first()
        if (active == id && _sources.value.isNotEmpty()) {
            setActive(_sources.value.first().manifest.id)
        }
    }

    private fun upsert(list: MutableList<CatalogSource>, source: CatalogSource) {
        list.removeAll { it.manifest.id == source.manifest.id }
        list += source
    }

    private fun createEngine(
        base: SourceManifest,
        settings: Map<String, String>? = prefs.sourceSettings.value[base.id],
    ): CatalogSource {
        baseManifests[base.id] = base
        val manifest = base.withSettings(settings)
        referers?.register(manifest)
        // engineConfig.rateLimit = requests/second for the API host (or the site when no apiBase).
        manifest.configString("rateLimit")?.toDoubleOrNull()?.let { perSecond ->
            val host = runCatching { URI(manifest.configString("apiBase") ?: manifest.baseUrl).host }.getOrNull()
            if (host != null) http.setRateLimit(host, perSecond)
        }
        val engine = buildEngine(manifest)
        val tracked = if (referers != null) ImageHostTrackingSource(engine, referers) else engine
        return DispatchingSource(tracked)
    }

    private fun buildEngine(manifest: SourceManifest): CatalogSource =
        if (manifest.engine == SourceEngines.LOCAL) LocalEngine(context) else SourceEngines.create(manifest, http, libAuth)
}

/**
 * Wraps an engine and records which hosts its covers and pages come from, so the shared image
 * loader can attach the right `Referer` (see [ImageReferers]).
 */
private class ImageHostTrackingSource(
    private val delegate: CatalogSource,
    private val referers: ImageReferers,
) : CatalogSource by delegate {
    private fun learn(url: String?) = referers.learn(url, delegate.manifest)

    private fun PagedResult<TitleSummary>.learned() = also { result -> result.items.forEach { learn(it.coverUrl) } }

    override suspend fun getHome(): HomeFeed = delegate.getHome().also { feed ->
        (feed.spotlight + feed.latest).forEach { learn(it.coverUrl) }
    }

    override suspend fun getLatest(page: Int) = delegate.getLatest(page).learned()
    override suspend fun getPopular(page: Int) = delegate.getPopular(page).learned()

    override suspend fun search(query: String, page: Int, filters: FilterList) =
        delegate.search(query, page, filters).learned()

    override suspend fun getTitle(id: String): TitleDetails = delegate.getTitle(id).also { learn(it.coverUrl) }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> =
        delegate.getPages(chapter).also { pages -> pages.firstOrNull()?.let { learn(it.imageUrl) } }
}

/**
 * Runs every network/parse call of a source on [Dispatchers.IO]. Engines call `AppHttp.execute`
 * and then parse JSON / HTML in the caller's context, which for screens was the main thread —
 * right in the middle of screen transitions. One wrapper covers every engine.
 */
private class DispatchingSource(private val delegate: CatalogSource) : CatalogSource by delegate {
    override suspend fun getHome(): HomeFeed = withContext(Dispatchers.IO) { delegate.getHome() }
    override suspend fun getLatest(page: Int) = withContext(Dispatchers.IO) { delegate.getLatest(page) }
    override suspend fun getPopular(page: Int) = withContext(Dispatchers.IO) { delegate.getPopular(page) }

    override suspend fun search(query: String, page: Int, filters: FilterList) =
        withContext(Dispatchers.IO) { delegate.search(query, page, filters) }

    override suspend fun getTitle(id: String): TitleDetails = withContext(Dispatchers.IO) { delegate.getTitle(id) }
    override suspend fun getChapters(titleId: String): List<Chapter> =
        withContext(Dispatchers.IO) {
            // Russian-only labels in the app language (Settings → General).
            delegate.getChapters(titleId).map { ch ->
                val label = com.lume.app.domain.ChapterLabels.localize(ch.title)
                if (label == ch.title) ch else ch.copy(title = label)
            }
        }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> =
        withContext(Dispatchers.IO) { delegate.getPages(chapter) }
}
