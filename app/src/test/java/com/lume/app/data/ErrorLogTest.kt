package com.lume.app.data

import com.lume.app.data.network.AppUserAgentInterceptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class ErrorLogTest {
    @Test
    fun `newest first and capped`() {
        var now = 0L
        val log = ErrorLog(file = null, clock = { now++ })
        repeat(ErrorLog.MAX_ENTRIES + 5) { log.record("src", "error $it") }
        val entries = log.entries.value
        assertEquals(ErrorLog.MAX_ENTRIES, entries.size)
        assertEquals("error ${ErrorLog.MAX_ENTRIES + 4}", entries.first().message)
    }

    @Test
    fun `keeps the full message and the stack trace`() {
        val log = ErrorLog(file = null, clock = { 0L })
        val long = "Unexpected JSON token at offset 11: " + "x".repeat(500)
        log.record("MangaDex", IllegalStateException(long))
        val entry = log.entries.value.single()
        assertEquals("MangaDex", entry.where)
        assertEquals(long, entry.message)
        assertTrue(entry.details.contains("IllegalStateException"))
        assertTrue(log.asText().contains(long))
    }

    @Test
    fun `an error without a message is named by its class`() {
        val log = ErrorLog(file = null)
        log.record("s", RuntimeException())
        assertEquals("RuntimeException", log.entries.value.single().message)
    }

    @Test
    fun `reads entries kept in the file`() {
        val dir = Files.createTempDirectory("errlog").toFile()
        val file = File(dir, "log.json")
        file.writeText("""[{"at":5,"where":"A","message":"boom","details":""}]""")
        val log = ErrorLog(file = file)
        assertEquals("boom", log.entries.value.single().message)
        log.clear()
        assertTrue(log.entries.value.isEmpty())
        dir.deleteRecursively()
    }

    @Test
    fun `mangadex hosts get the app user agent`() {
        assertTrue(AppUserAgentInterceptor.wantsAppUserAgent("api.mangadex.org"))
        assertTrue(AppUserAgentInterceptor.wantsAppUserAgent("uploads.mangadex.org"))
        assertTrue(AppUserAgentInterceptor.wantsAppUserAgent("abc.xyz.mangadex.network"))
        assertFalse(AppUserAgentInterceptor.wantsAppUserAgent("notmangadex.org"))
        assertFalse(AppUserAgentInterceptor.wantsAppUserAgent("mangalib.me"))
    }
}
