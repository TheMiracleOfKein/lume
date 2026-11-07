package com.lume.app.domain

import java.io.File

/**
 * What Coil should load for a reader page URL: the URL itself for web pages and rendered PDF
 * pages (`lume-pdf://`), a [File] for unpacked or downloaded images.
 */
fun pageData(url: String): Any =
    if (url.startsWith("http") || url.startsWith("lume-pdf://")) url else File(url)
