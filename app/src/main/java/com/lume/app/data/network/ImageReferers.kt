package com.lume.app.data.network

import com.lume.app.domain.SourceManifest
import java.net.URI
import java.util.concurrent.ConcurrentHashMap

/**
 * Decides which `Referer` an image request needs (many manga CDNs answer 403 without one).
 *
 * Mappings come from three places, most specific first:
 * 1. hosts *learned* from URLs a source actually returned (covers, pages);
 * 2. the registrable domain of each source's `baseUrl`, `apiBase` and `engineConfig.imageHosts`;
 * 3. a few legacy hints for shared CDNs that are hit before any source has loaded.
 */
class ImageReferers {
    private val learned = ConcurrentHashMap<String, String>()
    private val byDomain = ConcurrentHashMap<String, String>()

    fun register(manifest: SourceManifest) {
        val referer = refererOf(manifest)
        hostOf(manifest.baseUrl)?.let { byDomain.putIfAbsent(rootDomain(it), referer) }
        manifest.configString("apiBase")?.let(::hostOf)?.let { byDomain.putIfAbsent(rootDomain(it), referer) }
        manifest.configString("imageHosts")?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }?.forEach {
            byDomain[rootDomain(it)] = referer
        }
    }

    /** Remember the host of a URL a source returned, so the reader/cover loader can match it. */
    fun learn(url: String?, manifest: SourceManifest) {
        if (url.isNullOrBlank() || !url.startsWith("http")) return
        val host = hostOf(url) ?: return
        learned.putIfAbsent(host, refererOf(manifest))
    }

    fun refererFor(host: String): String? {
        val h = host.lowercase()
        return learned[h] ?: byDomain[rootDomain(h)] ?: legacyReferer(h)
    }

    private fun refererOf(manifest: SourceManifest): String =
        manifest.headers["Referer"] ?: "${manifest.baseUrl.trimEnd('/')}/"

    private fun legacyReferer(h: String): String? = when {
        h.contains("hentaicdn") -> "https://hentailib.me/"
        h.contains("cdnlibs") || h.contains("imglib") || h.contains("imgslib") -> "https://mangalib.me/"
        h.contains("reimg") -> "https://remanga.org/"
        h.contains("mangabuff") -> "https://mangabuff.ru/"
        else -> null
    }

    companion object {
        private fun hostOf(url: String): String? = runCatching { URI(url.trim()).host?.lowercase() }.getOrNull()

        /** `img2.cdn.example.co` → `example.co`; good enough for referer matching. */
        fun rootDomain(host: String): String {
            val parts = host.lowercase().trimEnd('.').split('.')
            return if (parts.size <= 2) parts.joinToString(".") else parts.takeLast(2).joinToString(".")
        }
    }
}
