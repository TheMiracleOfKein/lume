package com.lume.app.data.local

import android.content.Context
import android.net.Uri
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.WatchedFolderEntity
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlin.coroutines.cancellation.CancellationException

/** What a pass over the watched folders found. */
data class FolderScanSummary(val scanned: Int, val newChapters: Int)

/** Re-imports the watched folders (Settings → Library → Watched folders). */
class FolderScanner(private val context: Context, private val dao: LibraryDao, private val importer: LocalImporter) {
    private val lock = Mutex()

    /** True while Lume still holds a persisted read grant for [treeUri]. */
    fun hasPermission(treeUri: String): Boolean {
        val tree = Uri.parse(treeUri)
        return context.contentResolver.persistedUriPermissions.any { it.uri == tree && it.isReadPermission }
    }

    /** Scans every available folder. A folder that lost its permission is marked unavailable. */
    suspend fun scanAll(): FolderScanSummary = lock.withLock {
        var scanned = 0
        var added = 0
        for (folder in dao.watchedFolders()) {
            if (!folder.available) continue
            val result = scan(folder) ?: continue
            scanned++
            added += result.newChapterCount
        }
        FolderScanSummary(scanned, added)
    }

    private suspend fun scan(folder: WatchedFolderEntity): LocalImportResult? {
        if (!hasPermission(folder.treeUri)) {
            dao.setWatchedFolderAvailable(folder.treeUri, false)
            return null
        }
        return try {
            importer.import(Uri.parse(folder.treeUri), dao).also {
                dao.setWatchedFolderScanned(folder.treeUri, System.currentTimeMillis())
            }
        } catch (e: CancellationException) {
            throw e
        } catch (_: SecurityException) {
            dao.setWatchedFolderAvailable(folder.treeUri, false)
            null
        } catch (_: Exception) {
            // Folder removed or storage unmounted: try again at the next scan.
            null
        }
    }
}
