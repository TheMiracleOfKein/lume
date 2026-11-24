package com.lume.app.domain

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import java.net.URI

/**
 * Source addresses that can change without an app update (`docs/packs/hosts.json`).
 *
 * Only addresses can be replaced: [HostEntry.apiBase], [HostEntry.baseUrl] and
 * [HostEntry.imageHosts]. Headers, sign-in and engines never come from this file.
 */
@Serializable
data class HostsFile(
    val schemaVersion: Int = 1,
    /** Keyed by source id (`mangalib`) or by engine (`libsocial`, for every source of that engine). */
    val sources: Map<String, HostEntry> = emptyMap(),
    /** A host that fails to answer, and the hosts to try instead, in order. */
    val fallbacks: Map<String, List<String>> = emptyMap(),
)

@Serializable
data class HostEntry(
    val apiBase: String? = null,
    val baseUrl: String? = null,
    val imageHosts: List<String>? = null,
)

object HostOverrides {
    /** The newest file format this app understands. */
    const val SCHEMA = 1

    /**
     * Domains a source with sign-in may be moved to. A Lib token is sent to the API host, so an
     * address outside this set is ignored: moving to a brand-new domain needs an app update.
     */
    private val SIGN_IN_DOMAINS = setOf(
        "lib.social", "cdnlibs.org", "imglib.info", "imgslib.link", "cdnrulib.link",
        "mangalib.me", "mangalib.org", "ranobelib.me", "hentailib.me", "hentailib.org",
        "slashlib.me", "yaoilib.me", "lib.social",
    )

    /** Keeps only the parts of [file] that are safe to use. */
    fun sanitize(file: HostsFile, signIn: (key: String) -> Boolean = { false }): HostsFile {
        if (file.schemaVersion > SCHEMA) return HostsFile()
        val sources = file.sources.mapNotNull { (key, entry) ->
            if (key.isBlank() || key.length > 64) return@mapNotNull null
            val trusted = signIn(key)
            val clean = HostEntry(
                apiBase = entry.apiBase?.takeIf { acceptableUrl(it, trusted) },
                baseUrl = entry.baseUrl?.takeIf { acceptableUrl(it, trusted) },
                imageHosts = entry.imageHosts?.filter { acceptableHost(it) }?.takeIf { it.isNotEmpty() },
            )
            if (clean.apiBase == null && clean.baseUrl == null && clean.imageHosts == null) null else key to clean
        }.toMap()
        val fallbacks = file.fallbacks.mapNotNull { (host, alternatives) ->
            // Requests to a sign-in host never move to a domain outside the trusted ones.
            val signInHost = rootDomain(host) in SIGN_IN_DOMAINS
            val alts = alternatives.filter {
                acceptableHost(it) && !it.equals(host, ignoreCase = true) && (!signInHost || rootDomain(it) in SIGN_IN_DOMAINS)
            }.take(4)
            if (acceptableHost(host) && alts.isNotEmpty()) host.lowercase() to alts.map { it.lowercase() } else null
        }.toMap()
        return HostsFile(file.schemaVersion, sources, fallbacks)
    }

    /** True when [url] is https with a plain host; a sign-in source is also held to the trusted domains. */
    fun acceptableUrl(url: String, signIn: Boolean): Boolean {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return false
        if (uri.scheme != "https" || uri.userInfo != null) return false
        val host = uri.host ?: return false
        if (!acceptableHost(host)) return false
        return !signIn || rootDomain(host) in SIGN_IN_DOMAINS
    }

    fun acceptableHost(host: String): Boolean =
        host.length in 4..253 && host.contains('.') && host.all { it.isLetterOrDigit() || it == '.' || it == '-' }

    fun rootDomain(host: String): String = host.lowercase().split('.').takeLast(2).joinToString(".")

    /** The entry that applies to [manifest]: its own id first, then its engine. */
    fun entryFor(file: HostsFile, manifest: SourceManifest): HostEntry? =
        file.sources[manifest.id] ?: file.sources[manifest.engine]
}

/** [manifest] with the addresses of [entry] in place of its own. */
fun SourceManifest.withHosts(entry: HostEntry?): SourceManifest {
    if (entry == null) return this
    val config = engineConfig.toMutableMap()
    entry.apiBase?.let { config["apiBase"] = JsonPrimitive(it) }
    entry.imageHosts?.let { config["imageHosts"] = JsonPrimitive(it.joinToString(",")) }
    return copy(baseUrl = entry.baseUrl ?: baseUrl, engineConfig = JsonObject(config))
}
