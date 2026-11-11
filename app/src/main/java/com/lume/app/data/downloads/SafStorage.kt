package com.lume.app.data.downloads

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.provider.DocumentsContract.Document
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.util.concurrent.ConcurrentHashMap

/**
 * Files and folders inside a tree the user picked (Storage Access Framework), through
 * [DocumentsContract]. Folder document ids are cached by path, because every lookup by name is a
 * query of the whole parent folder; sizes are cached by document URI (downloaded files never change).
 */
class SafStorage(private val context: Context) {
    private val resolver get() = context.contentResolver
    private val dirIds = ConcurrentHashMap<String, String>()
    private val sizes = ConcurrentHashMap<String, Long>()

    data class Child(val id: String, val name: String, val mime: String, val size: Long) {
        val isDir get() = mime == Document.MIME_TYPE_DIR
    }

    /** True while Lume still holds a persisted read and write grant for [tree]. */
    fun hasAccess(tree: Uri): Boolean =
        resolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission && it.isWritePermission }

    /** The tree a document URI (`content://…/tree/<id>/document/<id>`) lives in, or null for other URIs. */
    fun treeOf(document: Uri): Uri? = runCatching {
        val authority = document.authority ?: return@runCatching null
        DocumentsContract.buildTreeDocumentUri(authority, DocumentsContract.getTreeDocumentId(document))
    }.getOrNull()

    fun documentUri(tree: Uri, documentId: String): Uri = DocumentsContract.buildDocumentUriUsingTree(tree, documentId)

    fun rootId(tree: Uri): String = DocumentsContract.getTreeDocumentId(tree)

    /** The picked folder's own name, or null when it can't be read. */
    fun displayName(tree: Uri): String? = runCatching { stat(documentUri(tree, rootId(tree)))?.name }.getOrNull()

    /** Name, size and modification time of one document, or null when it doesn't exist. */
    data class Stat(val name: String, val size: Long, val modified: Long)

    fun stat(document: Uri): Stat? = runCatching {
        resolver.query(
            document,
            arrayOf(Document.COLUMN_DISPLAY_NAME, Document.COLUMN_SIZE, Document.COLUMN_LAST_MODIFIED),
            null,
            null,
            null,
        )?.use { c ->
            if (!c.moveToFirst()) return@use null
            Stat(c.getString(0).orEmpty(), if (c.isNull(1)) 0L else c.getLong(1), if (c.isNull(2)) 0L else c.getLong(2))
        }
    }.getOrNull()

    fun exists(document: Uri): Boolean = stat(document) != null

    /** Size in bytes of a document (0 when missing); remembered, since downloaded files don't change. */
    fun size(document: Uri): Long {
        val key = document.toString()
        sizes[key]?.let { return it }
        val found = stat(document)?.size ?: return 0L
        sizes[key] = found
        return found
    }

    fun children(tree: Uri, parentId: String): List<Child> {
        val out = mutableListOf<Child>()
        runCatching {
            resolver.query(
                DocumentsContract.buildChildDocumentsUriUsingTree(tree, parentId),
                arrayOf(Document.COLUMN_DOCUMENT_ID, Document.COLUMN_DISPLAY_NAME, Document.COLUMN_MIME_TYPE, Document.COLUMN_SIZE),
                null,
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    out += Child(c.getString(0), name, c.getString(2).orEmpty(), if (c.isNull(3)) 0L else c.getLong(3))
                }
            }
        }
        return out
    }

    /**
     * The id of the folder reached through [segments] from the root of [tree]; created when
     * [create] is set. Null when it doesn't exist (or can't be created).
     */
    fun folder(tree: Uri, segments: List<String>, create: Boolean): String? {
        var id = rootId(tree)
        var key = tree.toString()
        for (segment in segments) {
            key += "/$segment"
            id = dirIds[key] ?: run {
                val found = children(tree, id).firstOrNull { it.isDir && it.name == segment }?.id
                    ?: if (create) createFolder(tree, id, segment) else null
                found?.also { dirIds[key] = it }
            } ?: return null
        }
        return id
    }

    fun createFolder(tree: Uri, parentId: String, name: String): String? = runCatching {
        DocumentsContract.createDocument(resolver, documentUri(tree, parentId), Document.MIME_TYPE_DIR, name)
            ?.let { DocumentsContract.getDocumentId(it) }
    }.getOrNull()

    /** Copies [file] into [parentId] as [name]; returns the new document's URI. */
    fun copyIn(tree: Uri, parentId: String, file: File, name: String, mime: String): Uri {
        val target = DocumentsContract.createDocument(resolver, documentUri(tree, parentId), mime, name)
            ?: throw IOException("Can't create $name")
        try {
            resolver.openOutputStream(target, "w")?.use { out -> file.inputStream().use { it.copyTo(out) } }
                ?: throw IOException("Can't write $name")
        } catch (e: Throwable) {
            runCatching { DocumentsContract.deleteDocument(resolver, target) }
            throw e
        }
        return target
    }

    /** Writes [text] as the file [name] in [parentId], replacing an older one. */
    fun writeText(tree: Uri, parentId: String, name: String, text: String) {
        children(tree, parentId).filter { !it.isDir && it.name == name }.forEach { delete(documentUri(tree, it.id)) }
        val target = DocumentsContract.createDocument(resolver, documentUri(tree, parentId), DownloadLocations.mimeFor(name), name)
            ?: throw IOException("Can't create $name")
        resolver.openOutputStream(target, "w")?.use { it.write(text.toByteArray()) } ?: throw IOException("Can't write $name")
    }

    fun readText(document: Uri): String? = runCatching {
        resolver.openInputStream(document)?.use { it.readBytes().toString(Charsets.UTF_8) }
    }.getOrNull()

    fun open(document: Uri): InputStream? = runCatching { resolver.openInputStream(document) }.getOrNull()

    /** Renames a folder; returns its (possibly new) id, or null when the provider can't rename. */
    fun rename(tree: Uri, documentId: String, newName: String): String? = runCatching {
        DocumentsContract.renameDocument(resolver, documentUri(tree, documentId), newName)
            ?.let { DocumentsContract.getDocumentId(it) }
    }.getOrNull()

    fun delete(document: Uri): Boolean = runCatching { DocumentsContract.deleteDocument(resolver, document) }.getOrDefault(false)

    /** Deletes the folders and files called [name] in [parentId] (an older or unfinished copy). */
    fun deleteChild(tree: Uri, parentId: String, name: String) {
        children(tree, parentId).filter { it.name == name }.forEach { delete(documentUri(tree, it.id)) }
    }

    /** Deletes the folder at [segments] with everything inside it. */
    fun deleteFolder(tree: Uri, segments: List<String>) {
        val id = folder(tree, segments, create = false) ?: return
        delete(documentUri(tree, id))
        forget(tree, segments)
    }

    /** Drops cached ids of the folder at [segments] and everything below it. */
    fun forget(tree: Uri, segments: List<String>) {
        val prefix = tree.toString() + segments.joinToString("") { "/$it" }
        dirIds.keys.removeAll { it == prefix || it.startsWith("$prefix/") }
    }

    /** Drops every cached id of [tree], after something it pointed at went missing. */
    fun forgetAll(tree: Uri) {
        val prefix = tree.toString() + "/"
        dirIds.keys.removeAll { it.startsWith(prefix) }
    }
}
