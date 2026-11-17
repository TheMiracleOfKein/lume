package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.Chapter
import com.lume.app.domain.ComicPage
import com.lume.app.domain.ComicSource
import com.lume.app.domain.ImageBytes
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Request
import java.net.URI

/** MangaLib, HentaiLib and YaoiLib: the shared Lib API ([LibSocialBase]) with image pages. */
class LibSocialEngine(
    manifest: SourceManifest,
    http: AppHttp,
    auth: LibAuthStore? = null,
    json: Json = Json { ignoreUnknownKeys = true; isLenient = true },
) : LibSocialBase(manifest, http, auth, json), ComicSource {
    /** The image server that last served this source's pages; tried first. */
    @Volatile
    private var imageServerCache: String? = null

    /** The site's image servers from `/constants`, in order of preference. */
    @Volatile
    private var imageServersCache: List<String>? = null

    override suspend fun getPages(chapter: Chapter): List<ComicPage> {
        val response = fetchChapter(chapter)
        val raw = parseRawPages(response.body)
        val server = raw.urls.firstOrNull()?.let { pickImageServer(raw.data, it) }.orEmpty()
        return buildPages(raw, server)
    }

    /** The chapter payload and its page paths, before an image server is chosen. */
    internal class RawPages(val data: JsonObject, val urls: List<String>)

    internal fun parseRawPages(body: String): RawPages {
        val root = asObject(json.parseToJsonElement(body))
        val data = asObject(root["data"] ?: root)
        if (data["toast"] != null || data["pages"] == null) {
            val toast = (data["toast"] as? JsonObject)?.get("message")?.jsonPrimitive?.contentOrNull
            throw SourceFetchException(
                toast ?: com.lume.app.data.locale.appString(
                    com.lume.app.R.string.error_pages_failed_named,
                    manifest.name,
                ),
            )
        }
        val rawPages = (data["pages"] as? JsonArray ?: JsonArray(emptyList())).mapNotNull { page ->
            when (page) {
                is JsonPrimitive -> page.contentOrNull
                is JsonObject -> stringOrNull(page["url"])
                    ?: stringOrNull(page["link"])
                    ?: stringOrNull(page["src"])
                else -> null
            }?.takeIf { it.isNotBlank() }
        }
        return RawPages(data, rawPages)
    }

    /** Pages of [raw] served from [server]; page paths that are already absolute stay as they are. */
    internal fun buildPages(raw: RawPages, server: String): List<ComicPage> {
        val pages = raw.urls.mapIndexed { index, url -> ComicPage(index = index, imageUrl = absolutizePageUrl(url, server)) }
        if (pages.isEmpty()) {
            throw SourceFetchException(
                com.lume.app.data.locale.appString(
                    com.lume.app.R.string.error_pages_failed_named,
                    manifest.name,
                ),
            )
        }
        return pages
    }

    /**
     * The first image server that serves [firstPage]. A site's main server sometimes refuses
     * requests (YaoiLib's answered 403 while its compressed mirror worked), so the others are
     * tried in turn. The server that worked is remembered for the next chapters.
     */
    private suspend fun pickImageServer(chapterData: JsonObject, firstPage: String): String {
        val fromChapter = stringOrNull(chapterData["imageServer"])
            ?: (chapterData["servers"] as? JsonObject)?.let { stringOrNull(it["main"]) }
        val candidates = orderedImageServers(imageServerCache, fromChapter, siteImageServers(), siteId.toIntOrNull() ?: 1)
        if (firstPage.trim().startsWith("http")) return candidates.first()
        // A server that serves the page as an AVIF Android draws with green stripes (12-bit
        // 4:4:4 uploads): its mirrors serve the same page as JPEG, so this chapter uses one of
        // them. It stays a per-chapter choice: other chapters on the main server are fine.
        var unsupportedOnly: String? = null
        for (server in candidates) {
            val head = imageHead(absolutizePageUrl(firstPage, server)) ?: continue
            if (ImageBytes.isUnsupportedAvif(head)) {
                if (unsupportedOnly == null) unsupportedOnly = server
                continue
            }
            if (unsupportedOnly == null) imageServerCache = server
            return server
        }
        return unsupportedOnly ?: candidates.first()
    }

    private suspend fun siteImageServers(): List<String> {
        imageServersCache?.let { return it }
        val site = siteId.toIntOrNull() ?: 1
        val servers = runCatching {
            val uri = URI("$apiRoot/constants?fields[]=imageServers")
            val response = http.get(uri, source = manifest, headers = apiHeaders(), checkCloudflare = false)
            val root = asObject(json.parseToJsonElement(response.body))
            val list = asObject(root["data"] ?: root)["imageServers"] as? JsonArray ?: return@runCatching emptyList()
            list.mapNotNull { it as? JsonObject }
                .filter { obj -> (obj["site_ids"] as? JsonArray).orEmpty().any { it.jsonPrimitive.contentOrNull?.toIntOrNull() == site } }
                .mapNotNull { obj -> stringOrNull(obj["id"])?.let { id -> id to stringOrNull(obj["url"]) } }
                .sortedBy { (id, _) -> SERVER_PREFERENCE.indexOf(id).let { if (it < 0) SERVER_PREFERENCE.size else it } }
                .mapNotNull { it.second }
        }.getOrDefault(emptyList())
        if (servers.isNotEmpty()) imageServersCache = servers
        return servers
    }

    /**
     * The first [IMAGE_HEAD_BYTES] of [url], asked for with the headers the image loader sends;
     * null when the server refuses it. Enough to tell the image's format and AV1 settings.
     */
    private suspend fun imageHead(url: String): ByteArray? = withContext(Dispatchers.IO) {
        runCatching {
            val request = Request.Builder().url(url)
                .header("User-Agent", http.userAgent)
                .header("Referer", manifest.headers["Referer"] ?: (manifest.baseUrl.trimEnd('/') + "/"))
                .header("Range", "bytes=0-${IMAGE_HEAD_BYTES - 1}")
                .build()
            http.okHttp.newCall(request).execute().use { response ->
                if (!response.isSuccessful) return@use null
                // A server that ignores Range sends the whole page; read only the start.
                val source = response.body.source()
                source.request(IMAGE_HEAD_BYTES.toLong())
                val buffer = source.buffer
                buffer.readByteArray(minOf(buffer.size, IMAGE_HEAD_BYTES.toLong()))
            }
        }.getOrNull()
    }

    private fun absolutizePageUrl(raw: String, server: String): String {
        val trimmed = raw.trim()
        when {
            trimmed.startsWith("http://") || trimmed.startsWith("https://") -> return trimmed
            trimmed.startsWith("//") && trimmed.count { it == '/' } >= 3 &&
                !trimmed.startsWith("//manga/") && !trimmed.startsWith("//uploads/") -> {
                // Protocol-relative host: //cdn.example/path
                return "https:$trimmed"
            }
            else -> {
                val path = when {
                    trimmed.startsWith("//") -> "/" + trimmed.removePrefix("//")
                    trimmed.startsWith("/") -> trimmed
                    else -> "/$trimmed"
                }
                return server.trimEnd('/') + path
            }
        }
    }
}

/** Image server ids from `/constants`, best first: full quality before compressed copies. */
private val SERVER_PREFERENCE = listOf("main", "secondary", "compress", "download")

/** How much of a page [LibSocialEngine] reads to check the image server's format. */
private const val IMAGE_HEAD_BYTES = 1024

/**
 * Image servers to try for a LibSocial chapter, best first and without duplicates: the server
 * that worked last time, the one the chapter names, the site's servers, then a built-in default.
 */
internal fun orderedImageServers(lastWorking: String?, fromChapter: String?, siteServers: List<String>, siteId: Int): List<String> {
    val fallback = when (siteId) {
        2 -> "https://img2.hentaicdn.org"
        4 -> "https://img2h.hentaicdn.org"
        else -> "https://img2.imglib.info"
    }
    return (listOfNotNull(lastWorking, fromChapter) + siteServers + fallback)
        .map { it.trim().trimEnd('/') }
        .filter { it.startsWith("http") }
        .distinct()
}
