package com.lume.app.domain

import java.io.File

/**
 * What Coil should load for a reader page URL: the URL itself for web pages and rendered PDF
 * pages (`lume-pdf://`), a [File] for unpacked or downloaded images.
 */
fun pageData(url: String): Any =
    if (url.startsWith("http") || url.startsWith("lume-pdf://") || isContentUri(url)) url else File(url)

/** True for a `content://` URI (a page stored in a folder picked through the Storage Access Framework). */
fun isContentUri(value: String): Boolean = value.startsWith("content://")
