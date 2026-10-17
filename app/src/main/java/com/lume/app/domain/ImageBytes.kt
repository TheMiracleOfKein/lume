package com.lume.app.domain

/**
 * Recognises image files by their first bytes. A site that answers a page request with an HTML
 * error or a Cloudflare check would otherwise leave a broken "page" in a download.
 */
object ImageBytes {
    /** File extension for the image in [bytes], or null when it is not an image Lume can show. */
    fun extension(bytes: ByteArray): String? {
        if (bytes.size < 12) return null
        fun at(i: Int) = bytes[i].toInt() and 0xFF
        fun ascii(from: Int, text: String) = text.indices.all { at(from + it) == text[it].code }
        return when {
            at(0) == 0xFF && at(1) == 0xD8 && at(2) == 0xFF -> "jpg"
            at(0) == 0x89 && ascii(1, "PNG") -> "png"
            ascii(0, "GIF8") -> "gif"
            ascii(0, "RIFF") && ascii(8, "WEBP") -> "webp"
            ascii(4, "ftyp") && (ascii(8, "avif") || ascii(8, "avis")) -> "avif"
            ascii(4, "ftyp") && (ascii(8, "heic") || ascii(8, "heix") || ascii(8, "mif1")) -> "heic"
            ascii(0, "BM") -> "bmp"
            at(0) == 0xFF && at(1) == 0x0A -> "jxl"
            at(0) == 0x00 && at(1) == 0x00 && at(2) == 0x00 && at(3) == 0x0C && ascii(4, "JXL ") -> "jxl"
            else -> null
        }
    }

    fun isImageName(name: String): Boolean =
        name.substringAfterLast('.', "").lowercase() in setOf("jpg", "jpeg", "png", "webp", "gif", "avif", "heic", "bmp", "jxl")
}

/** Metadata for ComicInfo.xml (the format comic readers and Mihon read from CBZ files). */
data class ComicInfo(
    val series: String,
    val title: String? = null,
    val number: String? = null,
    val volume: String? = null,
    val summary: String? = null,
    val writer: List<String> = emptyList(),
    val penciller: List<String> = emptyList(),
    val genre: List<String> = emptyList(),
    val pageCount: Int? = null,
    val web: String? = null,
    val status: String? = null,
)

fun ComicInfo.toXml(): String = buildString {
    fun esc(s: String) = s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;").replace("\"", "&quot;")
    fun tag(name: String, value: String?) {
        if (!value.isNullOrBlank()) append("  <$name>${esc(value)}</$name>\n")
    }
    append("<?xml version=\"1.0\" encoding=\"utf-8\"?>\n")
    append("<ComicInfo xmlns:xsd=\"http://www.w3.org/2001/XMLSchema\" xmlns:xsi=\"http://www.w3.org/2001/XMLSchema-instance\">\n")
    tag("Title", title)
    tag("Series", series)
    tag("Number", number)
    tag("Volume", volume)
    tag("Summary", summary)
    tag("Writer", writer.joinToString(", "))
    tag("Penciller", penciller.joinToString(", "))
    tag("Genre", genre.joinToString(", "))
    tag("Web", web)
    pageCount?.let { tag("PageCount", it.toString()) }
    // Mihon's extension of the schema.
    if (!status.isNullOrBlank()) append("  <ty:PublishingStatusTachiyomi xmlns:ty=\"http://www.w3.org/2001/XMLSchema\">${esc(status)}</ty:PublishingStatusTachiyomi>\n")
    append("</ComicInfo>\n")
}

/** Reads the fields Lume uses from a ComicInfo.xml (local imports). Unknown or broken input gives null. */
fun parseComicInfo(xml: String): ComicInfo? {
    fun field(name: String): String? =
        Regex("<$name>(.*?)</$name>", setOf(RegexOption.DOT_MATCHES_ALL)).find(xml)?.groupValues?.get(1)
            ?.replace("&lt;", "<")?.replace("&gt;", ">")?.replace("&quot;", "\"")?.replace("&apos;", "'")?.replace("&amp;", "&")
            ?.trim()?.ifEmpty { null }
    fun list(name: String) = field(name)?.split(',')?.map { it.trim() }?.filter { it.isNotEmpty() }.orEmpty()
    val series = field("Series") ?: field("Title") ?: return null
    return ComicInfo(
        series = series,
        title = field("Title"),
        number = field("Number"),
        volume = field("Volume"),
        summary = field("Summary"),
        writer = list("Writer"),
        penciller = list("Penciller"),
        genre = list("Genre"),
        pageCount = field("PageCount")?.toIntOrNull(),
        web = field("Web"),
        status = Regex("<ty:PublishingStatusTachiyomi[^>]*>(.*?)</ty:PublishingStatusTachiyomi>").find(xml)?.groupValues?.get(1),
    )
}
