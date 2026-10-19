package com.lume.app.domain

import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import java.net.URI

@Serializable
data class SourceManifest(
    val schemaVersion: Int = 1,
    val id: String,
    val name: String,
    val version: String = "1.0.0",
    val engine: String,
    val baseUrl: String,
    val mediaKind: String = "comics",
    val language: String? = null,
    val icon: String? = null,
    val nsfw: Boolean = false,
    val headers: Map<String, String> = emptyMap(),
    val engineConfig: JsonObject = JsonObject(emptyMap()),
) {
    val baseUri: URI get() = URI(baseUrl.trimEnd('/') + "/")

    fun configString(key: String): String? {
        val el = engineConfig[key] ?: return null
        return (el as? JsonPrimitive)?.contentOrNull
    }

    fun configBool(key: String, fallback: Boolean = false): Boolean {
        val el = engineConfig[key] ?: return fallback
        val prim = el as? JsonPrimitive ?: return fallback
        return prim.booleanOrNull ?: prim.contentOrNull?.toBooleanStrictOrNull() ?: fallback
    }
}

/**
 * CatalogueSource — Mihon-inspired contract, native to Lume.
 * Engines are first-party Kotlin; JSON packs only clone family engines.
 * No DEX / ExtensionLoader plugins.
 */
interface CatalogSource {
    val manifest: SourceManifest

    val supportsLatest: Boolean get() = true
    val supportsPopular: Boolean get() = true

    /** Popular + latest, fetched concurrently. Override for sources with a dedicated home feed. */
    suspend fun getHome(): HomeFeed = coroutineScope {
        val popular = async { getPopular() }
        val latest = async { getLatest() }
        HomeFeed(spotlight = popular.await().items.take(12), latest = latest.await().items)
    }

    suspend fun getLatest(page: Int = 1): PagedResult<TitleSummary>
    suspend fun getPopular(page: Int = 1): PagedResult<TitleSummary>

    /** Default filters for this source (may be empty). */
    fun getFilters(): FilterList = FilterList()

    suspend fun search(
        query: String,
        page: Int = 1,
        filters: FilterList = FilterList(),
    ): PagedResult<TitleSummary>

    suspend fun getTitle(id: String): TitleDetails
    suspend fun getChapters(titleId: String): List<Chapter>
    suspend fun getPages(chapter: Chapter): List<ComicPage>

    /**
     * Public website URL of a title (for "Open in browser" and solving site checks in a WebView).
     * Default: ids that are paths resolve against [SourceManifest.baseUrl]; opaque ids return null.
     */
    fun webUrl(titleId: String): String? {
        if (titleId.startsWith("http://") || titleId.startsWith("https://")) return titleId
        if (!titleId.startsWith("/")) return null
        return manifest.baseUrl.trimEnd('/') + titleId
    }

    /**
     * The title id behind a link to this source's website (a link shared from the browser, or a
     * Mihon backup entry), or null when the link is not a title page of this source.
     * Default: ids are paths, so any path on the source's host is taken as it is.
     */
    fun titleIdFromUrl(url: String): String? {
        val path = SourceLinks.pathOnHost(url, manifest.baseUrl) ?: return null
        return path.takeIf { it.length > 1 }
    }

    /** Settings this source offers (Sources → ⋮ → Settings). Values land in [SourceManifest.engineConfig]. */
    fun settings(): List<SourceSetting> = emptyList()
}

/**
 * One per-source setting. The chosen value is stored per source and merged into the manifest's
 * `engineConfig[key]` before the engine is built, so engines read it like any other config value.
 * Multi-choice values are stored comma-separated.
 */
sealed class SourceSetting {
    abstract val key: String
    abstract val title: String

    data class Toggle(
        override val key: String,
        override val title: String,
        val default: Boolean = false,
    ) : SourceSetting()

    data class Choice(
        override val key: String,
        override val title: String,
        /** value → label */
        val options: List<Pair<String, String>>,
        val default: String,
    ) : SourceSetting()

    data class MultiChoice(
        override val key: String,
        override val title: String,
        val options: List<Pair<String, String>>,
        val default: Set<String>,
    ) : SourceSetting()
}

class CloudflareException(val uri: URI) : Exception("Cloudflare challenge: $uri")
open class SourceFetchException(message: String) : Exception(message)

/** The source shows this content only to signed-in users (see `pagesNeedLogin`). */
class SourceLoginRequiredException(message: String) : SourceFetchException(message)
