package com.lume.app.data.source

import android.content.Context
import com.lume.app.R
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.SourcePackEntity
import com.lume.app.data.network.AppHttp
import com.lume.app.data.network.ImageReferers
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.data.server.ServerAuth
import com.lume.app.data.server.ServerCredentialStore
import com.lume.app.data.server.ServerCredentials
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
    private val credentials: ServerCredentialStore? = null,
    private val json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
    scope: kotlinx.coroutines.CoroutineScope? = null,
) {
    private val bundledIds = mutableSetOf<String>()

    /** Manifests as installed, before per-source settings are merged in. */
    private val baseManifests = java.util.concurrent.ConcurrentHashMap<String, SourceManifest>()
    private val _sources = MutableStateFlow<List<CatalogSource>>(emptyList())
    val sources: StateFlow<List<CatalogSource>> = _sources.asStateFlow()

    val activeSourceId: StateFlow<String?> = prefs.activeSourceId

    /** The last failure of each source, kept across restarts (Sources screen, global search). */
    val health = SourceHealth(store = PrefsSourceHealthStore(prefs), scope = scope)

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
            bundledIds += "local"
            runCatching { credentials?.load() }
            for (pack in dao.allSourcePacks()) {
                runCatching {
                    val manifest = json.decodeFromString(SourceManifest.serializer(), pack.json)
                    // A pack may never replace a built-in source (it would get that source's sign-in).
                    if (manifest.id in bundledIds || !isValidPackId(manifest.id)) return@runCatching
                    // Legacy packs for non-family engines still load if already installed.
                    upsert(loaded, createEngine(manifest))
                }
            }
            upsert(loaded, DispatchingSource(LocalEngine(context), health))
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

    /**
     * The engine behind a source, without the registry's wrappers. Use it only for
     * engine-specific calls (such as Lib bookmarks); everything else goes through [sourceById].
     */
    fun engineById(id: String): CatalogSource? = sourceById(id)?.unwrapped()

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
        if (isBundled(manifest.id) || !isValidPackId(manifest.id)) {
            error(context.getString(R.string.import_reject_bundled, manifest.id))
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

    /**
     * Adds a self-hosted server as a source `server-<uuid>`. It is stored like a pack, with its
     * sign-in in the credential store, never in the manifest.
     */
    suspend fun addServer(engine: String, name: String, url: String, sign: ServerCredentials): SourceManifest {
        require(SourceEngines.isServer(engine)) { "Not a server engine: $engine" }
        val baseUrl = ServerAuth.normalizeUrl(url) ?: error(context.getString(R.string.server_error_url))
        val manifest = ServerAuth.manifest(ServerAuth.newId(), engine, name, baseUrl)
        credentials?.put(manifest.id, sign)
        saveServerManifest(manifest)
        val next = _sources.value.toMutableList()
        upsert(next, createEngine(manifest))
        _sources.value = next
        return manifest
    }

    /** Changes the name, address or sign-in of a server. A blank sign-in keeps the stored one. */
    suspend fun updateServer(id: String, name: String, url: String, sign: ServerCredentials?) {
        val old = baseManifests[id]?.takeIf { ServerAuth.isServerId(id) && SourceEngines.isServer(it.engine) }
            ?: error("Unknown server: $id")
        val baseUrl = ServerAuth.normalizeUrl(url) ?: error(context.getString(R.string.server_error_url))
        val sameOrigin = ServerAuth.sameOrigin(old.baseUrl, baseUrl)
        val stored = credentials?.get(id)
        if (ServerAuth.needsReentry(sign, stored, sameOrigin)) error(context.getString(R.string.server_error_reenter))
        if (baseUrl != old.baseUrl) http.servers.unregister(old.baseUrl)
        // A session cookie from the old sign-in or address must not outlive the edit.
        http.forgetCookies(old.baseUrl)
        http.forgetCookies(baseUrl)
        val manifest = ServerAuth.manifest(id, old.engine, name, baseUrl).copy(engineConfig = old.engineConfig)
        // Typing only the user name keeps the saved password; the sign-in follows the new address.
        ServerAuth.mergeCredentials(sign, stored, sameOrigin)?.let { credentials?.put(id, it) }
        saveServerManifest(manifest)
        val next = _sources.value.toMutableList()
        upsert(next, createEngine(manifest))
        _sources.value = next
    }

    /** The stored sign-in of a server (for the edit dialog), or null. */
    fun serverCredentials(id: String): ServerCredentials? = credentials?.get(id)

    /** The base manifest of a user-added server, or null for any other source. */
    fun serverManifest(id: String): SourceManifest? =
        baseManifests[id]?.takeIf { ServerAuth.isServerId(id) && SourceEngines.isServer(it.engine) }

    private suspend fun saveServerManifest(manifest: SourceManifest) {
        dao.upsertSourcePack(
            SourcePackEntity(id = manifest.id, json = json.encodeToString(SourceManifest.serializer(), manifest)),
        )
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
        baseManifests.remove(id)?.takeIf { SourceEngines.isServer(it.engine) }?.let { server ->
            http.servers.unregister(server.baseUrl)
            http.forgetCookies(server.baseUrl)
            credentials?.remove(id)
        }
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
        // The Lib sites share one API that refuses bursts, so they get a limit even without one.
        val rateLimit = manifest.configString("rateLimit")?.toDoubleOrNull()
            ?: LIB_API_RATE_LIMIT.takeIf { manifest.engine == SourceEngines.LIBSOCIAL }
        rateLimit?.let { perSecond ->
            val host = runCatching { URI(manifest.configString("apiBase") ?: manifest.baseUrl).host }.getOrNull()
            if (host != null) http.setRateLimit(host, perSecond)
        }
        val sign = if (SourceEngines.isServer(manifest.engine)) credentials?.get(manifest.id) else null
        if (SourceEngines.isServer(manifest.engine)) {
            // The host may now be reached over http and gets the sign-in headers.
            http.servers.register(manifest.baseUrl, ServerAuth.access(manifest.engine, sign))
        }
        val engine = buildEngine(manifest, sign)
        val tracked = if (referers != null) ImageHostTrackingSource(engine, referers) else engine
        return DispatchingSource(tracked, health)
    }

    private fun buildEngine(manifest: SourceManifest, sign: ServerCredentials?): CatalogSource =
        if (manifest.engine == SourceEngines.LOCAL) LocalEngine(context) else SourceEngines.create(manifest, http, libAuth, sign, context.cacheDir)
}

/**
 * Wraps an engine and records which hosts its covers and pages come from, so the shared image
 * loader can attach the right `Referer` (see [ImageReferers]).
 */
private class ImageHostTrackingSource(
    private val delegate: CatalogSource,
    private val referers: ImageReferers,
) : CatalogSource by delegate, WrappingSource {
    override val inner: CatalogSource get() = delegate

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
private class DispatchingSource(
    private val delegate: CatalogSource,
    private val health: SourceHealth,
) : CatalogSource by delegate, WrappingSource {
    override val inner: CatalogSource get() = delegate

    /** On [Dispatchers.IO], with the outcome recorded for the Sources screen. */
    private suspend fun <T> io(block: suspend () -> T): T =
        withContext(Dispatchers.IO) { health.track(delegate.manifest.id, block) }

    override suspend fun getHome(): HomeFeed = io { delegate.getHome() }
    override suspend fun getLatest(page: Int) = io { delegate.getLatest(page) }
    override suspend fun getPopular(page: Int) = io { delegate.getPopular(page) }

    override suspend fun search(query: String, page: Int, filters: FilterList) =
        io { delegate.search(query, page, filters) }

    override suspend fun loadFilters(): FilterList = withContext(Dispatchers.IO) { delegate.loadFilters() }

    override suspend fun getTitle(id: String): TitleDetails = io { delegate.getTitle(id) }
    override suspend fun getChapters(titleId: String): List<Chapter> =
        io {
            // Russian-only labels in the app language (Settings → General).
            delegate.getChapters(titleId).map { ch ->
                val label = com.lume.app.domain.ChapterLabels.localize(ch.title)
                if (label == ch.title) ch else ch.copy(title = label)
            }
        }

    override suspend fun getPages(chapter: Chapter): List<ComicPage> = io { delegate.getPages(chapter) }
}

/** A registry wrapper around an engine; [unwrapped] peels them off to reach the engine itself. */
internal interface WrappingSource {
    val inner: CatalogSource
}

internal fun CatalogSource.unwrapped(): CatalogSource {
    var current = this
    while (current is WrappingSource) current = current.inner
    return current
}

/**
 * Ids an imported pack may use: letters, digits, `-` and `_`, up to 64 characters. The id names
 * the source's download folder and its stored settings, so dots and slashes are not allowed.
 */
internal fun isValidPackId(id: String): Boolean = id.length in 1..64 && id.all { it.isLetterOrDigit() || it == '-' || it == '_' }

/** Requests per second to the shared MangaLib-family API: one every half second. */
internal const val LIB_API_RATE_LIMIT = 2.0
