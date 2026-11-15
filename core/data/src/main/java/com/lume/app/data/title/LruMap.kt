package com.lume.app.data.title

/** A thread-safe map that keeps the [maxSize] most recently used entries. */
class LruMap<V : Any>(private val maxSize: Int) {
    private val map = object : LinkedHashMap<String, V>(16, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, V>): Boolean = size > maxSize
    }

    operator fun get(key: String): V? = synchronized(map) { map[key] }

    operator fun set(key: String, value: V) {
        synchronized(map) { map[key] = value }
    }

    fun remove(key: String): V? = synchronized(map) { map.remove(key) }

    val size: Int get() = synchronized(map) { map.size }
}
