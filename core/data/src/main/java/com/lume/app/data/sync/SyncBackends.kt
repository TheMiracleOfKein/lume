package com.lume.app.data.sync

import com.lume.app.data.backup.MAX_BACKUP_BYTES
import com.lume.app.data.backup.readCapped
import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import okhttp3.Credentials
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.jsoup.Jsoup
import org.jsoup.parser.Parser
import java.io.IOException
import java.net.URLDecoder

/** Where the sync files live. Each device reads every file and writes only its own. */
interface SyncBackend {
    /** File names in the sync place. */
    suspend fun list(): List<String>
    suspend fun read(name: String): ByteArray
    suspend fun write(name: String, bytes: ByteArray)
}

/** A sync failure with a message for the status line. */
class SyncBackendException(message: String, val httpCode: Int = 0) : IOException(message)

/** A folder picked with the system file picker; Syncthing, a cloud drive app or an SD card. */
class FolderSyncBackend(private val context: Context, private val tree: Uri) : SyncBackend {
    private companion object {
        const val TEMP_SUFFIX = ".tmp"
    }

    private val resolver get() = context.contentResolver
    private val rootId get() = DocumentsContract.getTreeDocumentId(tree)

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) { children().map { it.second } }

    override suspend fun read(name: String): ByteArray = withContext(Dispatchers.IO) {
        val id = children().firstOrNull { it.second == name }?.first ?: throw SyncBackendException("$name: not found")
        resolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree, id))?.use { it.readCapped(MAX_BACKUP_BYTES) }
            ?: throw SyncBackendException("$name: can't read")
    }

    /**
     * Writes `name.tmp` first, checks its size, then swaps it in by deleting the old file and
     * renaming. A half-written file therefore only ever has the `.tmp` name, which readers skip
     * (they take only names ending in `.bin`). A provider that can't rename gets a plain write.
     */
    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        val tempName = name + TEMP_SUFFIX
        children().filter { it.second == tempName }.forEach { (id, _) ->
            runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, id)) }
        }
        val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
        val temp = runCatching { DocumentsContract.createDocument(resolver, parent, "application/octet-stream", tempName) }.getOrNull()
        val written = temp != null && runCatching {
            resolver.openOutputStream(temp, "wt")?.use { it.write(bytes) } ?: error("no stream")
            sizeOf(temp)?.let { it == bytes.size.toLong() } ?: true
        }.getOrDefault(false)
        if (temp != null && written) {
            val existing = children().firstOrNull { it.second == name }?.first
            if (existing != null) {
                runCatching { DocumentsContract.deleteDocument(resolver, DocumentsContract.buildDocumentUriUsingTree(tree, existing)) }
            }
            val renamed = runCatching { DocumentsContract.renameDocument(resolver, temp, name) }.getOrNull()
            if (renamed != null) return@withContext
        }
        // No temp file or no rename: write the file itself, and drop the temp one.
        temp?.let { runCatching { DocumentsContract.deleteDocument(resolver, it) } }
        writeInPlace(name, bytes)
    }

    private fun sizeOf(uri: Uri): Long? = runCatching {
        resolver.query(uri, arrayOf(DocumentsContract.Document.COLUMN_SIZE), null, null, null)?.use { c ->
            if (c.moveToFirst() && !c.isNull(0)) c.getLong(0) else null
        }
    }.getOrNull()

    private fun writeInPlace(name: String, bytes: ByteArray) {
        val existing = children().firstOrNull { it.second == name }?.first
        val target = if (existing != null) {
            DocumentsContract.buildDocumentUriUsingTree(tree, existing)
        } else {
            val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
            DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
                ?: throw SyncBackendException("$name: can't create")
        }
        val out = runCatching { resolver.openOutputStream(target, "wt") }.getOrNull()
        if (out != null) {
            out.use { it.write(bytes) }
        } else {
            // Some providers refuse "wt" (truncate). Plain "w" may leave old bytes at the end, so
            // the file is made again instead.
            val dest = if (existing == null) {
                target
            } else {
                runCatching { DocumentsContract.deleteDocument(resolver, target) }
                val parent = DocumentsContract.buildDocumentUriUsingTree(tree, rootId)
                DocumentsContract.createDocument(resolver, parent, "application/octet-stream", name)
                    ?: throw SyncBackendException("$name: can't create")
            }
            resolver.openOutputStream(dest, "w")?.use { it.write(bytes) } ?: throw SyncBackendException("$name: can't write")
        }
    }

    /** (document id, display name) of the folder's files. */
    private fun children(): List<Pair<String, String>> {
        val uri = DocumentsContract.buildChildDocumentsUriUsingTree(tree, rootId)
        val found = mutableListOf<Pair<String, String>>()
        try {
            resolver.query(
                uri,
                arrayOf(DocumentsContract.Document.COLUMN_DOCUMENT_ID, DocumentsContract.Document.COLUMN_DISPLAY_NAME),
                null,
                null,
                null,
            )?.use { c ->
                while (c.moveToNext()) {
                    val name = c.getString(1) ?: continue
                    found += c.getString(0) to name
                }
            } ?: throw SyncBackendException("folder: can't open")
        } catch (e: SecurityException) {
            throw SyncBackendException("folder: access lost, pick it again")
        }
        return found
    }
}

/**
 * A WebDAV folder (Nextcloud, ownCloud, a NAS…). [url] is the folder; it is created when missing.
 * Uses its own client without the sites' cookies.
 */
class WebDavSyncBackend(
    base: OkHttpClient,
    url: String,
    private val user: String,
    private val password: String,
) : SyncBackend {
    private val folder = url.trim().let { if (it.endsWith("/")) it else "$it/" }
    private val client = base.newBuilder().cookieJar(okhttp3.CookieJar.NO_COOKIES).build()

    override suspend fun list(): List<String> = withContext(Dispatchers.IO) {
        val body = """<?xml version="1.0"?><d:propfind xmlns:d="DAV:"><d:prop><d:resourcetype/></d:prop></d:propfind>"""
        val request = request(folder)
            .method("PROPFIND", body.toRequestBody(XML))
            .header("Depth", "1")
            .build()
        client.newCall(request).execute().use { response ->
            if (response.code == 404) {
                mkcol()
                return@withContext emptyList()
            }
            check(response, "PROPFIND")
            fileNames(response.body?.string().orEmpty())
        }
    }

    override suspend fun read(name: String): ByteArray = withContext(Dispatchers.IO) {
        client.newCall(request(folder + name).get().build()).execute().use { response ->
            check(response, "GET")
            response.body?.bytes() ?: ByteArray(0)
        }
    }

    override suspend fun write(name: String, bytes: ByteArray) = withContext(Dispatchers.IO) {
        // Upload under a temp name, then move it over the real one, so a reader never gets half a file.
        val temp = request(folder + name + ".tmp").put(bytes.toRequestBody(BINARY)).build()
        client.newCall(temp).execute().use { check(it, "PUT") }
        val move = request(folder + name + ".tmp")
            .method("MOVE", null)
            .header("Destination", folder + name)
            .header("Overwrite", "T")
            .build()
        val moved = runCatching { client.newCall(move).execute().use { it.isSuccessful } }.getOrDefault(false)
        if (!moved) {
            // A server without MOVE: put the file itself, then remove the temp one.
            val direct = request(folder + name).put(bytes.toRequestBody(BINARY)).build()
            client.newCall(direct).execute().use { check(it, "PUT") }
            runCatching { client.newCall(request(folder + name + ".tmp").delete().build()).execute().close() }
        }
    }

    private fun mkcol() {
        client.newCall(request(folder).method("MKCOL", null).build()).execute().use { response ->
            // 405: it exists after all.
            if (!response.isSuccessful && response.code != 405) check(response, "MKCOL")
        }
    }

    private fun request(url: String): Request.Builder {
        val builder = try {
            Request.Builder().url(url)
        } catch (_: IllegalArgumentException) {
            throw SyncBackendException("WebDAV: not a valid address")
        }
        if (user.isNotEmpty()) builder.header("Authorization", Credentials.basic(user, password))
        return builder
    }

    private fun check(response: okhttp3.Response, what: String) {
        if (response.isSuccessful || response.code == 207) return
        val reason = when (response.code) {
            401, 403 -> "sign-in refused"
            404 -> "folder not found"
            else -> "HTTP ${response.code}"
        }
        throw SyncBackendException("WebDAV $what: $reason", response.code)
    }

    companion object {
        /** File names in a PROPFIND answer; servers differ in their namespace prefix (`d:`, `D:`, none). */
        fun fileNames(xml: String): List<String> {
            val doc = Jsoup.parse(xml, "", Parser.xmlParser())
            fun org.jsoup.nodes.Element.local() = tagName().substringAfter(':').lowercase()
            return doc.allElements.filter { it.local() == "response" }
                .filter { r -> r.allElements.none { it.local() == "collection" } }
                .mapNotNull { r ->
                    val href = r.allElements.firstOrNull { it.local() == "href" }?.text() ?: return@mapNotNull null
                    URLDecoder.decode(href.trimEnd('/').substringAfterLast('/'), "UTF-8").takeIf { it.isNotBlank() }
                }
        }

        private val XML = "application/xml; charset=utf-8".toMediaType()
        private val BINARY = "application/octet-stream".toMediaType()
    }
}
