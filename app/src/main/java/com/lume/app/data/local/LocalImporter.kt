package com.lume.app.data.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.lume.app.domain.ImageBytes
import com.lume.app.domain.NaturalOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/** What an import copied. */
data class LocalImportResult(val titles: Int, val chapters: Int, val skipped: Int)

/**
 * Copies a folder picked with the system file picker into Lume's local library
 * (`filesDir/local/<Series>/<Chapter>/…`), where [com.lume.app.data.source.LocalEngine] reads it.
 *
 * The picked folder may be
 * - a library: one folder per series, each with chapter folders or `.cbz` / `.zip` files;
 * - one series: chapter folders or archives directly inside;
 * - one chapter: images directly inside (the series is named after the folder).
 *
 * `cover.*`, `ComicInfo.xml` and `details.json` next to the chapters are copied too.
 */
class LocalImporter(private val context: Context) {
    private data class Doc(val id: String, val name: String, val isDir: Boolean)

    private val resolver get() = context.contentResolver

    fun localRoot(): File = File(context.filesDir, "local").also { it.mkdirs() }

    suspend fun import(tree: Uri): LocalImportResult = withContext(Dispatchers.IO) {
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val rootName = queryName(tree, rootId) ?: "Imported"
        val children = list(tree, rootId)
        var titles = 0
        var chapters = 0
        var skipped = 0
        when (layoutOf(tree, children)) {
            Layout.Library -> for (series in children.filter { it.isDir }) {
                val copied = copySeries(tree, series.id, series.name)
                if (copied > 0) titles++ else skipped++
                chapters += copied
            }
            Layout.Series -> {
                chapters += copySeries(tree, rootId, rootName)
                if (chapters > 0) titles++
            }
            Layout.Chapter -> {
                val dest = File(localRoot(), safeName(rootName))
                if (copyImages(tree, children, File(dest, safeName(rootName))) > 0) {
                    titles++
                    chapters++
                }
            }
            Layout.Empty -> skipped++
        }
        LocalImportResult(titles, chapters, skipped)
    }

    private enum class Layout { Library, Series, Chapter, Empty }

    private fun layoutOf(tree: Uri, children: List<Doc>): Layout {
        if (children.any { !it.isDir && isArchive(it.name) }) return Layout.Series
        if (children.any { !it.isDir && ImageBytes.isImageName(it.name) } && children.none { it.isDir }) return Layout.Chapter
        val dirs = children.filter { it.isDir }
        if (dirs.isEmpty()) return Layout.Empty
        // Series folders hold chapter folders or archives; chapter folders hold images.
        val sample = list(tree, dirs.first().id)
        val looksLikeChapter = sample.any { !it.isDir && ImageBytes.isImageName(it.name) } && sample.none { it.isDir || isArchive(it.name) }
        return if (looksLikeChapter) Layout.Series else Layout.Library
    }

    /** Copies one series; returns the number of chapters copied. */
    private suspend fun copySeries(tree: Uri, seriesId: String, seriesName: String): Int {
        val dest = File(localRoot(), safeName(seriesName)).also { it.mkdirs() }
        var chapters = 0
        for (child in list(tree, seriesId)) {
            coroutineContext.ensureActive()
            when {
                child.isDir -> if (copyImages(tree, list(tree, child.id), File(dest, safeName(child.name))) > 0) chapters++
                isArchive(child.name) -> {
                    copyFile(tree, child.id, File(dest, safeName(child.name)))
                    chapters++
                }
                isSeriesExtra(child.name) -> copyFile(tree, child.id, File(dest, child.name.lowercase().let { lower ->
                    when {
                        lower == "comicinfo.xml" -> "ComicInfo.xml"
                        lower == "details.json" -> "details.json"
                        else -> "cover." + lower.substringAfterLast('.')
                    }
                }))
            }
        }
        if (chapters == 0) dest.deleteRecursively()
        return chapters
    }

    private suspend fun copyImages(tree: Uri, files: List<Doc>, dest: File): Int {
        val images = files.filter { !it.isDir && ImageBytes.isImageName(it.name) }
        if (images.isEmpty()) return 0
        dest.mkdirs()
        for (image in images) {
            coroutineContext.ensureActive()
            copyFile(tree, image.id, File(dest, safeName(image.name)))
        }
        return images.size
    }

    private fun copyFile(tree: Uri, docId: String, dest: File) {
        if (dest.isFile && dest.length() > 0) return
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        val partial = File(dest.parentFile, dest.name + ".part")
        resolver.openInputStream(uri)?.use { input -> partial.outputStream().use { input.copyTo(it) } }
        if (!partial.renameTo(dest)) partial.delete()
    }

    private fun list(tree: Uri, parentId: String): List<Doc> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
        )
        val out = mutableListOf<Doc>()
        resolver.query(uri, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                out += Doc(id = c.getString(0), name = name, isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR)
            }
        }
        return out.sortedWith(compareBy(NaturalOrder) { it.name })
    }

    private fun queryName(tree: Uri, docId: String): String? {
        val uri = DocumentsContract.buildDocumentUriUsingTree(tree, docId)
        return resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME), null, null, null)?.use { c ->
            if (c.moveToFirst()) c.getString(0) else null
        }
    }

    private fun isArchive(name: String) = name.lowercase().let { it.endsWith(".cbz") || it.endsWith(".zip") }

    private fun isSeriesExtra(name: String): Boolean {
        val lower = name.lowercase()
        return lower == "comicinfo.xml" || lower == "details.json" ||
            (lower.substringBeforeLast('.') == "cover" && ImageBytes.isImageName(lower))
    }

    private fun safeName(name: String): String = name.replace(Regex("""[<>:"/\\|?*]"""), "_").trim().ifEmpty { "_" }

    /**
     * Removes files Lume can rebuild: unpacked archives of local titles and CBZ downloads, and
     * half-finished copies. Returns the bytes freed.
     */
    fun cleanTemporaryFiles(): Long {
        var freed = 0L
        for (dir in listOf(File(context.cacheDir, "local_zip"), File(context.cacheDir, "cbz"), File(context.cacheDir, "local_covers"))) {
            freed += sizeOf(dir)
            dir.deleteRecursively()
        }
        localRoot().walkTopDown().filter { it.isFile && it.name.endsWith(".part") }.forEach {
            freed += it.length()
            it.delete()
        }
        return freed
    }

    private fun sizeOf(file: File): Long = if (!file.exists()) 0L else file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
