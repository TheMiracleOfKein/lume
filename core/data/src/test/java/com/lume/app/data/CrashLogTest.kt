package com.lume.app.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File
import java.nio.file.Files

class CrashLogTest {
    private fun tempFile(): File = Files.createTempDirectory("crash").toFile().resolve("crash_log.json")

    @Test
    fun `saves crashes newest first and capped`() {
        var now = 0L
        val file = tempFile()
        val log = CrashLog(file, "1.2.3", clock = { now++ })
        repeat(CrashLog.MAX_ENTRIES + 3) { log.record(Thread.currentThread(), IllegalStateException("boom $it")) }
        val entries = CrashLog(file, "1.2.3").entries()
        assertEquals(CrashLog.MAX_ENTRIES, entries.size)
        assertTrue(entries.first().trace.contains("boom ${CrashLog.MAX_ENTRIES + 2}"))
        assertEquals("1.2.3", entries.first().version)
    }

    @Test
    fun `clear removes the file and a broken file reads as empty`() {
        val file = tempFile()
        val log = CrashLog(file, "1")
        log.record(Thread.currentThread(), RuntimeException("x"))
        log.clear()
        assertFalse(file.exists())
        file.writeText("not json")
        assertTrue(log.entries().isEmpty())
    }

    @Test
    fun `the handler saves the crash and passes it on`() {
        val file = tempFile()
        val log = CrashLog(file, "1")
        val before = Thread.getDefaultUncaughtExceptionHandler()
        var passedOn: Throwable? = null
        try {
            Thread.setDefaultUncaughtExceptionHandler { _, e -> passedOn = e }
            log.install()
            val thread = Thread { throw IllegalArgumentException("worker crash") }
            thread.start()
            thread.join()
        } finally {
            Thread.setDefaultUncaughtExceptionHandler(before)
        }
        assertEquals("worker crash", passedOn?.message)
        assertTrue(log.entries().single().trace.contains("worker crash"))
    }

    @Test
    fun `report lists the device, crashes and recent errors`() {
        val crash = CrashEntry(0L, "main", "1.0", "java.lang.IllegalStateException: boom")
        val errors = List(40) { ErrorEntry(it.toLong(), "Src", "error $it") }
        val text = CrashLog.report("Lume 1.0 · Android 15", listOf(crash), errors)
        assertTrue(text.startsWith("Lume 1.0 · Android 15"))
        assertTrue(text.contains("== Crashes (1) =="))
        assertTrue(text.contains("boom"))
        assertTrue(text.contains("== Recent errors (30 of 40) =="))
        assertFalse(text.contains("error 30"))
        assertTrue(CrashLog.report("d", emptyList(), emptyList()).contains("none"))
    }
}
