package com.lume.app.data.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import com.lume.app.data.db.ImportedFileEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.local.archive.ArchiveReaders
import com.lume.app.domain.ImageBytes
import com.lume.app.domain.NaturalOrder
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.coroutines.coroutineContext

/**
 * What an import found. [newChapters] counts chapters that were not in the library before, per title
 * (changed files that were copied again are not new chapters).
 */
data class LocalImportResult(
    val titles: Int,
    val chapters: Int,
    val skipped: Int,
    val newChapters: Map<String, Int> = emptyMap(),
) {
    val newChapterCount: Int get() = newChapters.values.sum()
}

/**
 * Copies a folder picked with the system file picker into Lume's local library
 * (`filesDir/local/<Series>/<Chapter>/…`), where [com.lume.app.data.source.LocalEngine] reads it.
 *
 * The picked folder may be
 * - a library: one folder per series, each with chapter folders or `.cbz` / `.cbr` / `.cb7` / `.zip` / `.rar` / `.7z` / `.pdf` files;
 * - one series: chapter folders or archives directly inside;
 * - one chapter: images directly inside (the series is named after the folder).
 *
 * `cover.*`, `ComicInfo.xml` and `details.json` next to the chapters are copied too.
 */
class LocalImporter(private val context: Context) {
    private data class Doc(val id: String, val name: String, val isDir: Boolean, val size: Long = 0, val lastModified: Long = 0)

    private enum class Copy { New, Updated, Unchanged }

    /** One import: the ledger rows of the folder and what the run found. */
    private class Run(val tree: Uri, val ledger: LibraryDao) {
        val treeKey = tree.toString()
        var entries: Map<String, LedgerEntry> = emptyMap()
        val newChapters = mutableMapOf<String, Int>()
    }

    private val resolver get() = context.contentResolver

    fun localRoot(): File = File(context.filesDir, "local").also { it.mkdirs() }

    /**
     * Copies what is new or changed since the last import of [tree]. [ledger] remembers every copied
     * document (size, modified time, destination), so unchanged files are not read again.
     */
    suspend fun import(tree: Uri, ledger: LibraryDao): LocalImportResult = withContext(Dispatchers.IO) {
        val run = Run(tree, ledger)
        run.entries = ledger.importedFilesFor(run.treeKey).associate { it.documentId to LedgerEntry(it.size, it.lastModified, it.destPath) }
        val rootId = DocumentsContract.getTreeDocumentId(tree)
        val rootName = queryName(tree, rootId) ?: "Imported"
        val children = list(tree, rootId)
        var titles = 0
        var chapters = 0
        var skipped = 0
        when (layoutOf(tree, children)) {
            Layout.Library -> for (series in children.filter { it.isDir }) {
                val copied = copySeries(run, series.id, series.name)
                if (copied > 0) titles++ else skipped++
                chapters += copied
            }
            Layout.Series -> {
                chapters += copySeries(run, rootId, rootName)
                if (chapters > 0) titles++
            }
            Layout.Chapter -> {
                val dest = File(localRoot(), safeName(rootName))
                if (copyImages(run, rootName, children, File(dest, safeName(rootName))) > 0) {
                    titles++
                    chapters++
                }
            }
            Layout.Empty -> skipped++
        }
        LocalImportResult(titles, chapters, skipped, run.newChapters.toMap())
    }

    private enum class Layout { Library, Series, Chapter, Empty }

    private fun layoutOf(tree: Uri, children: List<Doc>): Layout {
        if (children.any { !it.isDir && ArchiveReaders.isChapterFile(it.name) }) return Layout.Series
        if (children.any { !it.isDir && ImageBytes.isImageName(it.name) } && children.none { it.isDir }) return Layout.Chapter
        val dirs = children.filter { it.isDir }
        if (dirs.isEmpty()) return Layout.Empty
        // Series folders hold chapter folders or archives; chapter folders hold images.
        val sample = list(tree, dirs.first().id)
        val looksLikeChapter = sample.any { !it.isDir && ImageBytes.isImageName(it.name) } && sample.none { it.isDir || ArchiveReaders.isChapterFile(it.name) }
        return if (looksLikeChapter) Layout.Series else Layout.Library
    }

    /** Copies one series; returns the number of chapters copied. */
    private suspend fun copySeries(run: Run, seriesId: String, seriesName: String): Int {
        val dest = File(localRoot(), safeName(seriesName)).also { it.mkdirs() }
        var chapters = 0
        for (child in list(run.tree, seriesId)) {
            coroutineContext.ensureActive()
            when {
                child.isDir -> if (copyImages(run, seriesName, list(run.tree, child.id), File(dest, safeName(child.name))) > 0) chapters++
                ArchiveReaders.isChapterFile(child.name) -> {
                    if (copyFile(run, child, File(dest, safeName(child.name))) == Copy.New) run.addNew(seriesName)
                    chapters++
                }
                isSeriesExtra(child.name) -> copyFile(run, child, File(dest, child.name.lowercase().let { lower ->
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

    /** A folder of images is one chapter; it is new when every image in it is new. */
    private suspend fun copyImages(run: Run, title: String, files: List<Doc>, dest: File): Int {
        val images = files.filter { !it.isDir && ImageBytes.isImageName(it.name) }
        if (images.isEmpty()) return 0
        dest.mkdirs()
        var added = 0
        for (image in images) {
            coroutineContext.ensureActive()
            if (copyFile(run, image, File(dest, safeName(image.name))) == Copy.New) added++
        }
        if (added == images.size) run.addNew(title)
        return images.size
    }

    private fun Run.addNew(title: String) {
        newChapters[title] = (newChapters[title] ?: 0) + 1
    }

    /** Copies [doc] to [dest] unless the ledger says it is already there unchanged. */
    private suspend fun copyFile(run: Run, doc: Doc, dest: File): Copy {
        val entry = run.entries[doc.id]
        val destExists = dest.isFile && dest.length() > 0
        if (!LedgerDiff.needsCopy(entry, doc.size, doc.lastModified, destExists)) {
            // Copied before the ledger existed: remember it now so later changes are noticed.
            if (entry == null) record(run, doc, dest)
            return Copy.Unchanged
        }
        val uri = DocumentsContract.buildDocumentUriUsingTree(run.tree, doc.id)
        val partial = File(dest.parentFile, dest.name + ".part")
        resolver.openInputStream(uri)?.use { input -> partial.outputStream().use { input.copyTo(it) } } ?: return Copy.Unchanged
        if (dest.exists()) dest.delete()
        if (!partial.renameTo(dest)) {
            partial.delete()
            return Copy.Unchanged
        }
        record(run, doc, dest)
        return if (entry == null) Copy.New else Copy.Updated
    }

    private suspend fun record(run: Run, doc: Doc, dest: File) {
        run.ledger.upsertImportedFile(ImportedFileEntity(run.treeKey, doc.id, doc.size, doc.lastModified, dest.absolutePath))
    }

    private fun list(tree: Uri, parentId: String): List<Doc> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId)
        val columns = arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,
            DocumentsContract.Document.COLUMN_DISPLAY_NAME,
            DocumentsContract.Document.COLUMN_MIME_TYPE,
            DocumentsContract.Document.COLUMN_SIZE,
            DocumentsContract.Document.COLUMN_LAST_MODIFIED,
        )
        val out = mutableListOf<Doc>()
        resolver.query(uri, columns, null, null, null)?.use { c ->
            while (c.moveToNext()) {
                val name = c.getString(1) ?: continue
                if (name.startsWith(".")) continue
                out += Doc(
                    id = c.getString(0),
                    name = name,
                    isDir = c.getString(2) == DocumentsContract.Document.MIME_TYPE_DIR,
                    size = if (c.isNull(3)) 0 else c.getLong(3),
                    lastModified = if (c.isNull(4)) 0 else c.getLong(4),
                )
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
