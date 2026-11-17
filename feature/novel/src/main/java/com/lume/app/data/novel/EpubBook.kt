package com.lume.app.data.novel

import org.jsoup.Jsoup
import org.jsoup.nodes.Document
import org.jsoup.nodes.Element
import org.jsoup.parser.Parser
import java.io.File
import java.net.URLDecoder
import java.util.zip.ZipFile

/** One chapter of an EPUB: a spine item and the name the table of contents gives it. */
data class EpubChapter(val index: Int, val title: String, val entry: String)

/**
 * An EPUB read with the zip classes and jsoup. Opening reads only the package (metadata, spine,
 * table of contents); chapter text and images are read from the zip when asked for.
 */
class EpubBook private constructor(
    val file: File,
    val title: String?,
    val author: String?,
    val description: String?,
    val language: String?,
    /** Zip entry of the cover image, or null. */
    val coverEntry: String?,
    val chapters: List<EpubChapter>,
) {
    /**
     * The chapter's HTML (the body's content). Each image is unpacked into a folder under
     * [imageRoot] and its `src` points at the unpacked file as a `file://` URI.
     */
    fun chapterHtml(index: Int, imageRoot: File): String {
        val chapter = chapters.getOrNull(index) ?: error("No chapter $index in ${file.name}")
        ZipFile(file).use { zip ->
            val names = EntryNames(zip)
            val entry = names.find(chapter.entry) ?: error("Missing ${chapter.entry} in ${file.name}")
            val doc = Jsoup.parse(zip.getInputStream(entry).use { it.readBytes() }.toString(Charsets.UTF_8))
            val dir = File(imageRoot, bookKey(file))
            val base = chapter.entry.substringBeforeLast('/', "")
            for (img in doc.select("img, image")) {
                val key = IMAGE_ATTRS.firstOrNull { img.attr(it).isNotBlank() } ?: continue
                val href = img.attr(key)
                if (href.startsWith("data:") || href.contains("://")) continue
                val path = resolve(base, href.substringBefore('#'))
                val imageEntry = names.find(path) ?: continue
                val target = File(dir, "%08x_%s".format(path.hashCode(), safeFileName(path.substringAfterLast('/'))))
                if (!target.isFile || target.length() == 0L) {
                    dir.mkdirs()
                    val partial = File(dir, target.name + ".part")
                    zip.getInputStream(imageEntry).use { input -> partial.outputStream().use { input.copyTo(it) } }
                    if (!partial.renameTo(target)) partial.delete()
                }
                if (target.isFile) img.attr(key, fileUri(target))
            }
            return doc.body().html()
        }
    }

    /** Copies the cover into [dir] (as `cover.<ext>`) and returns it, or null without a cover. */
    fun extractCover(dir: File): File? {
        val cover = coverEntry ?: return null
        return runCatching {
            ZipFile(file).use { zip ->
                val entry = EntryNames(zip).find(cover) ?: return@use null
                dir.mkdirs()
                val ext = cover.substringAfterLast('.', "jpg").lowercase().filter { it.isLetterOrDigit() }.ifEmpty { "jpg" }
                val target = File(dir, "cover.$ext")
                zip.getInputStream(entry).use { input -> target.outputStream().use { input.copyTo(it) } }
                target
            }
        }.getOrNull()
    }

    private class EntryNames(private val zip: ZipFile) {
        private val lower: Map<String, String> by lazy {
            zip.entries().asSequence().associate { it.name.lowercase() to it.name }
        }

        fun find(name: String) = zip.getEntry(name) ?: lower[name.lowercase()]?.let { zip.getEntry(it) }
    }

    companion object {
        private val IMAGE_ATTRS = listOf("src", "xlink:href", "href")

        /** Reads the package of [file]; throws when it is not a readable EPUB. */
        fun open(file: File): EpubBook = ZipFile(file).use { zip ->
            val names = EntryNames(zip)
            fun text(path: String): String? =
                names.find(path)?.let { zip.getInputStream(it).use { input -> input.readBytes() }.toString(Charsets.UTF_8) }

            val container = xml(text("META-INF/container.xml") ?: error("Not an EPUB: no container.xml"))
            val opfPath = container.local("rootfile").firstOrNull()?.attr("full-path")?.takeIf { it.isNotBlank() }
                ?: error("Not an EPUB: no package file")
            val opf = xml(text(opfPath) ?: error("Missing package file $opfPath"))
            val opfDir = opfPath.substringBeforeLast('/', "")

            val manifest = opf.local("item").associate { item ->
                item.attr("id") to ManifestItem(
                    entry = resolve(opfDir, item.attr("href").substringBefore('#')),
                    mediaType = item.attr("media-type"),
                    properties = item.attr("properties").split(' ').filter { it.isNotEmpty() },
                )
            }

            val title = opf.local("title").firstOrNull()?.text()?.clean()?.ifEmpty { null }
            val author = opf.local("creator").map { it.text().clean() }.filter { it.isNotEmpty() }
                .joinToString(", ").ifEmpty { null }
            val description = opf.local("description").firstOrNull()?.text()
                ?.let { Jsoup.parse(it).text().clean() }?.ifEmpty { null }
            val language = opf.local("language").firstOrNull()?.text()?.clean()?.ifEmpty { null }

            val coverId = opf.local("meta").firstOrNull { it.attr("name") == "cover" }?.attr("content")
            val cover = manifest.values.firstOrNull { "cover-image" in it.properties }
                ?: coverId?.let { manifest[it] }

            val spine = opf.local("itemref")
                .mapNotNull { manifest[it.attr("idref")] }
                .filter { it.mediaType.contains("html") }
            val tocTitles = tocTitles(opf, manifest, ::text)
            val chapters = spine.mapIndexed { i, item ->
                EpubChapter(i, tocTitles[item.entry] ?: "Chapter ${i + 1}", item.entry)
            }
            EpubBook(file, title, author, description, language, cover?.entry, chapters)
        }

        private class ManifestItem(val entry: String, val mediaType: String, val properties: List<String>)

        /** Entry path to name: the EPUB 3 nav document when there is one, else the EPUB 2 NCX. */
        private fun tocTitles(opf: Document, manifest: Map<String, ManifestItem>, text: (String) -> String?): Map<String, String> {
            val nav = manifest.values.firstOrNull { "nav" in it.properties }
            if (nav != null) {
                val doc = text(nav.entry)?.let { Jsoup.parse(it) }
                val navEl = doc?.select("nav")?.let { navs ->
                    navs.firstOrNull { it.attr("epub:type").split(' ').contains("toc") } ?: navs.firstOrNull()
                }
                val base = nav.entry.substringBeforeLast('/', "")
                val titles = linkedMapOf<String, String>()
                navEl?.select("a[href]")?.forEach { a ->
                    val name = a.text().clean()
                    val href = a.attr("href").substringBefore('#')
                    if (name.isNotEmpty() && href.isNotEmpty()) titles.putIfAbsent(resolve(base, href), name)
                }
                if (titles.isNotEmpty()) return titles
            }
            val ncxId = opf.local("spine").firstOrNull()?.attr("toc")?.takeIf { it.isNotBlank() }
            val ncx = ncxId?.let { manifest[it] }
                ?: manifest.values.firstOrNull { it.mediaType == "application/x-dtbncx+xml" }
            if (ncx != null) {
                val doc = text(ncx.entry)?.let { xml(it) }
                val base = ncx.entry.substringBeforeLast('/', "")
                val titles = linkedMapOf<String, String>()
                doc?.local("navPoint")?.forEach { point ->
                    val label = point.local("navLabel").firstOrNull()?.text()?.clean().orEmpty()
                    val src = point.local("content").firstOrNull()?.attr("src")?.substringBefore('#').orEmpty()
                    if (label.isNotEmpty() && src.isNotEmpty()) titles.putIfAbsent(resolve(base, src), label)
                }
                return titles
            }
            return emptyMap()
        }

        private fun xml(raw: String): Document = Jsoup.parse(raw.trimStart(Char(0xFEFF)), "", Parser.xmlParser())

        /** Elements by local name, whatever the namespace prefix (`dc:title`, `opf:item`). */
        private fun Element.local(name: String): List<Element> =
            getAllElements().filter { it.normalName().substringAfter(':').equals(name, ignoreCase = true) }

        private fun String.clean() = replace(Regex("""\s+"""), " ").trim()

        /** Joins [href] (percent-encoded, may use `..`) onto the directory [base] inside the zip. */
        internal fun resolve(base: String, href: String): String {
            val decoded = runCatching { URLDecoder.decode(href.replace("+", "%2B"), "UTF-8") }.getOrDefault(href)
            val parts = ArrayList<String>()
            val all = if (decoded.startsWith("/")) decoded.split('/') else (base.split('/') + decoded.split('/'))
            for (part in all) {
                when (part) {
                    "", "." -> Unit
                    ".." -> if (parts.isNotEmpty()) parts.removeAt(parts.lastIndex)
                    else -> parts += part
                }
            }
            return parts.joinToString("/")
        }

        private fun bookKey(file: File): String =
            "%08x".format((file.absolutePath + file.lastModified()).hashCode())

        private fun safeFileName(name: String) = name.replace(Regex("""[^A-Za-z0-9._-]"""), "_")

        internal fun fileUri(file: File): String {
            val path = file.absolutePath.replace('\\', '/')
            return "file://" + if (path.startsWith("/")) path else "/$path"
        }
    }
}
