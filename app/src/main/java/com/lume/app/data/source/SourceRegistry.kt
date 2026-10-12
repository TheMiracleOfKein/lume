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
import com.lume.app.domain.PagedResult
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import com.lume.app.domain.SourceManifest
import kotlinx.coroutines.flow.Flow
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
    private val _sources = MutableStateFlow<List<CatalogSource>>(emptyList())
    val sources: StateFlow<List<CatalogSource>> = _sources.asStateFlow()

    val activeSourceId: Flow<String?> = prefs.activeSourceId

    fun isBundled(id: String): Boolean = id in bundledIds || id == "local"

    suspend fun load() {
        bundledIds.clear()
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
        upsert(loaded, LocalEngine(context))
        bundledIds += "local"
        _sources.value = loaded
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

    suspend fun remove(id: String) {
        if (id in bundledIds) return
        dao.deleteSourcePack(id)
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

    private fun createEngine(manifest: SourceManifest): CatalogSource {
        referers?.register(manifest)
        // engineConfig.rateLimit = requests/second for the API host (or the site when no apiBase).
        manifest.configString("rateLimit")?.toDoubleOrNull()?.let { perSecond ->
            val host = runCatching { URI(manifest.configString("apiBase") ?: manifest.baseUrl).host }.getOrNull()
            if (host != null) http.setRateLimit(host, perSecond)
        }
        val engine = buildEngine(manifest)
        return if (referers != null) ImageHostTrackingSource(engine, referers) else engine
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
