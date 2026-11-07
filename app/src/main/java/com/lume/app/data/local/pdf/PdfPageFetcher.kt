package com.lume.app.data.local.pdf

import coil3.ImageLoader
import coil3.Uri
import coil3.asImage
import coil3.decode.DataSource
import coil3.fetch.FetchResult
import coil3.fetch.Fetcher
import coil3.fetch.ImageFetchResult
import coil3.key.Keyer
import coil3.request.Options
import java.io.File

/** Renders `lume-pdf://` page URLs (see [PdfPageUrl]) with [PdfRendererPool]. */
class PdfPageFetcher(private val ref: PdfPageRef) : Fetcher {
    override suspend fun fetch(): FetchResult = ImageFetchResult(
        image = PdfRendererPool.render(ref.path, ref.index).asImage(),
        isSampled = false,
        dataSource = DataSource.DISK,
    )

    class Factory : Fetcher.Factory<Uri> {
        override fun create(data: Uri, options: Options, imageLoader: ImageLoader): Fetcher? =
            PdfPageUrl.parse(data.toString())?.let { PdfPageFetcher(it) }
    }
}

/** Memory-cache key of a PDF page: the file's size and date make an edited file miss the cache. */
class PdfPageKeyer : Keyer<Uri> {
    override fun key(data: Uri, options: Options): String? {
        val ref = PdfPageUrl.parse(data.toString()) ?: return null
        val file = File(ref.path)
        return "lume-pdf:${ref.path}:${file.length()}:${file.lastModified()}:${ref.index}"
    }
}
