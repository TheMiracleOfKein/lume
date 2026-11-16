package com.lume.app.data.local.pdf

import java.net.URLDecoder
import java.net.URLEncoder

/** One page of a local PDF, as a reader page URL: `lume-pdf://<urlencoded absolute path>#<index>`. */
data class PdfPageRef(val path: String, val index: Int)

object PdfPageUrl {
    const val SCHEME = "lume-pdf://"

    fun build(path: String, index: Int): String = SCHEME + URLEncoder.encode(path, "UTF-8") + "#" + index

    fun isPdfPage(url: String): Boolean = url.startsWith(SCHEME)

    /** The file and page of [url], or null when it isn't a PDF page URL. */
    fun parse(url: String): PdfPageRef? {
        if (!isPdfPage(url)) return null
        val body = url.removePrefix(SCHEME)
        val hash = body.lastIndexOf('#')
        if (hash < 0) return null
        val index = body.substring(hash + 1).toIntOrNull()?.takeIf { it >= 0 } ?: return null
        val path = runCatching { URLDecoder.decode(body.substring(0, hash), "UTF-8") }.getOrNull() ?: return null
        return PdfPageRef(path, index)
    }
}
