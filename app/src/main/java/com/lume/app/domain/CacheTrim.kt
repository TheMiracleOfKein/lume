package com.lume.app.domain

/** One folder of a cache directory, as far as trimming cares. */
data class CacheEntry(val name: String, val sizeBytes: Long, val lastModified: Long)

/**
 * Names of the folders to delete so that the rest fits in [capBytes]: least recently modified
 * first. [keep] (the folder just written) is never chosen, even when it alone is over the cap.
 */
fun selectCacheTrim(entries: List<CacheEntry>, capBytes: Long, keep: String? = null): List<String> {
    var total = entries.sumOf { it.sizeBytes }
    if (total <= capBytes) return emptyList()
    val out = mutableListOf<String>()
    for (e in entries.filter { it.name != keep }.sortedWith(compareBy({ it.lastModified }, { it.name }))) {
        if (total <= capBytes) break
        out += e.name
        total -= e.sizeBytes
    }
    return out
}
