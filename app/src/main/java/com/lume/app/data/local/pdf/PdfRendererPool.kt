package com.lume.app.data.local.pdf

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.File

/**
 * Keeps a [PdfRenderer] open per recently used PDF. A renderer allows one open page at a time, so
 * every use of one runs under its own [Mutex]. Renderers idle for [IDLE_MS] are closed, and at most
 * [MAX_OPEN] stay open (least recently used go first).
 */
object PdfRendererPool {
    const val MAX_WIDTH_PX = 2000
    private const val MAX_OPEN = 3
    private const val IDLE_MS = 30_000L

    private class Entry(val descriptor: ParcelFileDescriptor, val renderer: PdfRenderer) {
        val mutex = Mutex()
        var lastUse = 0L
        var closed = false
    }

    private val entries = LinkedHashMap<String, Entry>(8, 0.75f, true)
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private var cleanup: Job? = null

    private fun entryFor(path: String): Entry = synchronized(entries) {
        entries.getOrPut(path) {
            val pfd = ParcelFileDescriptor.open(File(path), ParcelFileDescriptor.MODE_READ_ONLY)
            try {
                Entry(pfd, PdfRenderer(pfd))
            } catch (e: Throwable) {
                pfd.close()
                throw e
            }
        }
    }

    private suspend fun <T> withRenderer(path: String, block: (PdfRenderer) -> T): T {
        while (true) {
            val entry = entryFor(path)
            val result = entry.mutex.withLock {
                if (entry.closed) {
                    null
                } else {
                    entry.lastUse = System.currentTimeMillis()
                    Holder(block(entry.renderer))
                }
            }
            if (result != null) {
                scheduleCleanup()
                return result.value
            }
        }
    }

    private class Holder<T>(val value: T)

    suspend fun pageCount(file: File): Int = withRenderer(file.absolutePath) { it.pageCount }

    /** Most pixels in one rendered page (about 32 MB as ARGB). */
    const val MAX_PIXELS = 2000L * 4000L

    /** Bitmap size for a [pageWidth] x [pageHeight] page: up to twice its size, at most [maxWidth] wide and [MAX_PIXELS] in all. */
    internal fun fitSize(pageWidth: Int, pageHeight: Int, maxWidth: Int): Pair<Int, Int> {
        val pw = pageWidth.coerceAtLeast(1)
        val ph = pageHeight.coerceAtLeast(1)
        var width = minOf(pw * 2, maxWidth).coerceAtLeast(1)
        var height = ph.toLong() * width / pw
        if (width * height > MAX_PIXELS) {
            val scale = Math.sqrt(MAX_PIXELS.toDouble() / (width.toDouble() * height))
            width = (width * scale).toInt().coerceAtLeast(1)
            height = (ph.toLong() * width / pw)
        }
        return width to height.toInt().coerceAtLeast(1)
    }

    /** Page [index] on a white background, [MAX_WIDTH_PX] wide at most (twice the page's size in points otherwise). */
    suspend fun render(path: String, index: Int, maxWidth: Int = MAX_WIDTH_PX): Bitmap =
        withRenderer(path) { renderer ->
            renderer.openPage(index).use { page ->
                val (width, height) = fitSize(page.width, page.height, maxWidth)
                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                Canvas(bitmap).drawColor(Color.WHITE)
                page.render(bitmap, null, null, PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                bitmap
            }
        }

    private fun scheduleCleanup() {
        synchronized(entries) {
            cleanup?.cancel()
            cleanup = scope.launch {
                delay(IDLE_MS)
                closeIdle(IDLE_MS)
            }
        }
        trimToLimit()
    }

    private fun trimToLimit() {
        val extra = synchronized(entries) { entries.keys.toList().dropLast(MAX_OPEN) }
        extra.forEach { close(it) }
    }

    private fun closeIdle(idleMs: Long) {
        val now = System.currentTimeMillis()
        val idle = synchronized(entries) { entries.filter { now - it.value.lastUse >= idleMs }.keys.toList() }
        idle.forEach { close(it) }
    }

    /** Closes the renderer of [path] unless it is rendering right now. */
    private fun close(path: String) {
        val entry = synchronized(entries) { entries[path] } ?: return
        if (!entry.mutex.tryLock()) return
        try {
            if (entry.closed) return
            entry.closed = true
            synchronized(entries) { if (entries[path] === entry) entries.remove(path) }
            runCatching { entry.renderer.close() }
            runCatching { entry.descriptor.close() }
        } finally {
            entry.mutex.unlock()
        }
    }
}
