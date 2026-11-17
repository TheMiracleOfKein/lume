package com.lume.app.data.downloads

import org.jsoup.Jsoup
import org.jsoup.nodes.Document

/**
 * How a downloaded text chapter (a novel) is stored: the chapter's HTML as the source gave it to
 * the reader, in `chapter.html`, with its pictures next to it (`img-0000.jpg`, …) and `<img src>`
 * pointing at those names. Flat files in the chapter folder, like comic pages, so the same
 * storage code (app storage, SD card, a picked folder) holds them.
 */
internal object TextChapterFiles {
    const val HTML_NAME = "chapter.html"
    private const val IMAGE_PREFIX = "img-"

    fun imageName(index: Int, ext: String): String = "$IMAGE_PREFIX%04d.%s".format(index, ext)

    /** True for the picture files of a text chapter folder. */
    fun isImageName(name: String): Boolean = name.startsWith(IMAGE_PREFIX) && !name.endsWith(".part")

    /** The index in an [imageName], or null for any other name. */
    fun imageIndex(name: String): Int? =
        name.takeIf { it.startsWith(IMAGE_PREFIX) }?.removePrefix(IMAGE_PREFIX)?.substringBefore('.')?.toIntOrNull()

    /** The web pictures of [html], in order, each once. */
    fun remoteImages(html: String): List<String> =
        parse(html).select("img[src]").map { it.attr("src").trim() }
            .filter { it.startsWith("http://") || it.startsWith("https://") }
            .distinct()

    /** [html] with each `<img src>` replaced by [replace]'s answer; null keeps the link as it is. */
    fun rewriteImages(html: String, replace: (String) -> String?): String {
        val doc = parse(html)
        doc.select("img[src]").forEach { img ->
            replace(img.attr("src").trim())?.let { img.attr("src", it) }
        }
        return doc.body().html()
    }

    private fun parse(html: String): Document = Jsoup.parseBodyFragment(html).apply {
        outputSettings().prettyPrint(false)
    }
}
