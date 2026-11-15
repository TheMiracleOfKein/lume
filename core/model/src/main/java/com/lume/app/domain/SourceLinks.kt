package com.lume.app.domain

import java.net.URI

/** Pure helpers for matching website links to sources. */
object SourceLinks {
    /** Host without a leading `www.` / `m.`, lower case. */
    fun normalHost(host: String?): String? =
        host?.lowercase()?.removePrefix("www.")?.removePrefix("m.")?.takeIf { it.isNotBlank() }

    /**
     * The path of [url] when it points at the same site as [baseUrl] (ignoring `www.` and the
     * scheme), else null. Query and fragment are dropped.
     */
    fun pathOnHost(url: String, baseUrl: String): String? {
        val link = parse(url) ?: return null
        val base = parse(baseUrl) ?: return null
        if (normalHost(link.host) == null || normalHost(link.host) != normalHost(base.host)) return null
        return link.rawPath?.ifEmpty { "/" } ?: "/"
    }

    /** Path segments of [url] on [baseUrl]'s site, or null for another site. */
    fun segments(url: String, baseUrl: String): List<String>? =
        pathOnHost(url, baseUrl)?.split('/')?.filter { it.isNotEmpty() }

    /** First `http(s)://…` link in a shared text ("Look at this: https://… !"). */
    fun firstLink(text: String): String? =
        Regex("""https?://[^\s<>"']+""").find(text)?.value?.trimEnd('.', ',', ')', '!', '?', ';')

    private fun parse(url: String): URI? = runCatching { URI(url.trim()) }.getOrNull()
        ?.takeIf { it.scheme == "http" || it.scheme == "https" }
}

/**
 * Compares dotted version strings ("1.10.0" > "1.9"). Missing parts count as 0; a non-number part
 * compares as text. Returns a negative number, zero or a positive number like [Comparable].
 */
fun compareVersions(a: String, b: String): Int {
    val left = a.trim().removePrefix("v").split('.', '-')
    val right = b.trim().removePrefix("v").split('.', '-')
    for (i in 0 until maxOf(left.size, right.size)) {
        val x = left.getOrElse(i) { "0" }
        val y = right.getOrElse(i) { "0" }
        val xn = x.toIntOrNull()
        val yn = y.toIntOrNull()
        val c = if (xn != null && yn != null) xn.compareTo(yn) else x.compareTo(y)
        if (c != 0) return c
    }
    return 0
}
