package com.lume.app.data

import com.lume.app.domain.CacheEntry
import com.lume.app.domain.selectCacheTrim
import java.io.File

/** Size cap for folders of unpacked archive pages in the cache directory. */
object ArchiveCache {
    const val MAX_BYTES = 300L * 1024 * 1024

    /** Marks [dir] as just used, so trimming removes other chapters first. */
    fun touch(dir: File) {
        runCatching { dir.setLastModified(System.currentTimeMillis()) }
    }

    /** Deletes the least recently used folders of [root] until it fits in [capBytes]; [keep] stays. */
    fun trim(root: File, keep: File?, capBytes: Long = MAX_BYTES) {
        runCatching {
            val dirs = root.listFiles()?.filter { it.isDirectory }.orEmpty()
            val entries = dirs.map { CacheEntry(it.name, sizeOf(it), it.lastModified()) }
            val doomed = selectCacheTrim(entries, capBytes, keep?.name).toSet()
            dirs.filter { it.name in doomed }.forEach { it.deleteRecursively() }
        }
    }

    private fun sizeOf(dir: File): Long = dir.walkTopDown().filter { it.isFile }.sumOf { it.length() }
}
