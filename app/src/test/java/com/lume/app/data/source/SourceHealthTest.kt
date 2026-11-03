package com.lume.app.data.source

import com.lume.app.domain.CloudflareException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.net.URI

class SourceHealthTest {
    private val health = SourceHealth(clock = { 1_000L })

    @Test
    fun failureIsRecordedAndRethrown() = runBlocking {
        try {
            health.track("a") { error("HTTP 503") }
            fail("expected the error")
        } catch (e: IllegalStateException) {
            assertEquals("HTTP 503", e.message)
        }
        val problem = health.problems.value.getValue("a")
        assertEquals("HTTP 503", problem.message)
        assertEquals(1_000L, problem.at)
        assertFalse(problem.siteCheck)
    }

    @Test
    fun successClearsTheProblem() = runBlocking {
        runCatching { health.track("a") { error("down") } }
        assertEquals(42, health.track("a") { 42 })
        assertTrue(health.problems.value.isEmpty())
    }

    @Test
    fun siteCheckIsMarked() = runBlocking {
        runCatching { health.track("cf") { throw CloudflareException(URI("https://example.org")) } }
        assertTrue(health.problems.value.getValue("cf").siteCheck)
    }

    @Test
    fun cancellationRecordsNothing() = runBlocking {
        runCatching { health.track("a") { throw CancellationException("left the screen") } }
        assertTrue(health.problems.value.isEmpty())
    }
}
