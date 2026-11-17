package com.lume.app.data.local

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.OpenableColumns
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
 * (`filesDir/local/<Series>/<Chapter>/…`), where the local engine of the comics module reads it.
 *
 * The picked folder may be
 * - a library: one folder per series, each with chapter folders or `.cbz` / `.cbr` / `.cb7` / `.zip` / `.rar` / `.7z` / `.pdf` files;
 * - one series: chapter folders or archives directly inside;
 * - one chapter: images directly inside (the series is named after the folder).
 *
 * `cover.*`, `ComicInfo.xml` and `details.json` next to the chapters are copied too.
 *
 * When [bookExtensions] is not empty (a build with the novel module), book files found in the folder or
 * a few levels below it are copied to `filesDir/local-novel/<file name>/<file name>` (see [BookFiles]).
 */
class LocalImporter(
    private val context: Context,
    /** Extensions of book files to copy too (the installed modules'); empty: comics only. */
    private val bookExtensions: Set<String> = emptySet(),
) {
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

    private fun booksRoot(): File = File(context.filesDir, BookFiles.FOLDER).also { it.mkdirs() }

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
        // Books (`.epub`, `.txt`) anywhere in the folder; each is one title. Comics are untouched.
        val books = mutableListOf<Pair<String, Doc>>()
        if (bookExtensions.isNotEmpty()) findBooks(tree, rootId, children, 0, books)
        val bookParents = books.map { it.first }.toSet()
        when (layoutOf(tree, children)) {
            Layout.Library -> for (series in children.filter { it.isDir }) {
                val copied = copySeries(run, series.id, series.name)
                if (copied > 0) titles++ else if (series.id !in bookParents) skipped++
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
            Layout.Empty -> if (books.isEmpty()) skipped++
        }
        for ((_, book) in books) {
            coroutineContext.ensureActive()
            val copy = copyFile(run, book, BookFiles.destination(booksRoot(), book.name))
            // The book's chapters are not counted here: a book is one title and one new item.
            titles++
            chapters++
            if (copy == Copy.New) run.addNew(book.name.substringBeforeLast('.'))
        }
        LocalImportResult(titles, chapters, skipped, run.newChapters.toMap())
    }

    /** Collects book files in [children] and the folders below (a few levels), with their parent folder id. */
    private fun findBooks(tree: Uri, parentId: String, children: List<Doc>, depth: Int, out: MutableList<Pair<String, Doc>>) {
        // A text file beside comic pages or archives is a note ("info.txt"), not a book.
        val comicFolder = children.any { !it.isDir && (ImageBytes.isImageName(it.name) || ArchiveReaders.isChapterFile(it.name)) }
        for (child in children) {
            if (child.isDir || !BookFiles.isBook(child.name, bookExtensions)) continue
            if (comicFolder && child.name.substringAfterLast('.').equals(BookFiles.PLAIN_TEXT, ignoreCase = true)) continue
            out += parentId to child
        }
        if (depth >= BookFiles.MAX_DEPTH) return
        for (dir in children.filter { it.isDir }) findBooks(tree, dir.id, list(tree, dir.id), depth + 1, out)
    }

    /**
     * Copies single book files picked with the file picker to `local-novel/<name>/<name.ext>`.
     * Files whose extension is not a book's are skipped; an identical file (same size) is not copied again.
     */
    suspend fun importBooks(uris: List<Uri>): LocalImportResult = withContext(Dispatchers.IO) {
        var titles = 0
        var skipped = 0
        for (uri in uris) {
            coroutineContext.ensureActive()
            val name = resolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { c ->
                if (c.moveToFirst()) c.getString(0) else null
            }
            if (name == null || !BookFiles.isBook(name, bookExtensions)) {
                skipped++
                continue
            }
            val dest = BookFiles.destination(booksRoot(), name)
            dest.parentFile?.mkdirs()
            val size = resolver.query(uri, arrayOf(OpenableColumns.SIZE), null, null, null)?.use { c ->
                if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else 0L
            } ?: 0L
            if (BookFiles.needsCopy(dest.isFile, dest.length(), size)) {
                val partial = File(dest.parentFile, dest.name + ".part")
                val opened = resolver.openInputStream(uri)?.use { input -> partial.outputStream().use { input.copyTo(it) } }
                if (opened == null) {
                    partial.delete()
                    skipped++
                    continue
                }
                if (dest.exists()) dest.delete()
                if (!partial.renameTo(dest)) {
                    partial.delete()
                    skipped++
                    continue
                }
            }
            titles++
        }
        LocalImportResult(titles, titles, skipped)
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
        dest.parentFile?.mkdirs()
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

    private fun safeName(name: String): String = BookFiles.safeName(name)

    /**
     * Removes files Lume can rebuild: unpacked archives of local titles and CBZ downloads, and
     * half-finished copies. Returns the bytes freed.
     */
    fun cleanTemporaryFiles(): Long {
        var freed = 0L
        for (dir in listOf(File(context.cacheDir, "local_zip"), File(context.cacheDir, "cbz"), File(context.cacheDir, "local_covers"), File(context.cacheDir, "novel_img"))) {
            freed += sizeOf(dir)
            dir.deleteRecursively()
        }
        (localRoot().walkTopDown() + booksRoot().walkTopDown()).filter { it.isFile && it.name.endsWith(".part") }.forEach {
            freed += it.length()
            it.delete()
        }
        return freed
    }

    private fun sizeOf(file: File): Long = if (!file.exists()) 0L else file.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
