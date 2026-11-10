package com.lume.app.data.source

import com.lume.app.domain.CloudflareException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.cancel
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

    private class FakeStore(var stored: Map<String, SourceProblem> = emptyMap()) : SourceHealthStore {
        val saves = mutableListOf<Map<String, SourceProblem>>()
        override fun load() = stored
        override suspend fun save(problems: Map<String, SourceProblem>) {
            saves += problems
        }
    }

    @Test
    fun entriesAreLoadedFromTheStore() {
        val store = FakeStore(mapOf("a" to SourceProblem("HTTP 503", 900L)))
        val loaded = SourceHealth(clock = { 1_000L }, store = store)
        assertEquals("HTTP 503", loaded.problems.value.getValue("a").message)
    }

    @Test
    fun staleEntriesAreDropped() {
        val now = 10 * SourceHealth.MAX_AGE_MS
        val store = FakeStore(
            mapOf(
                "old" to SourceProblem("x", now - SourceHealth.MAX_AGE_MS - 1),
                "fresh" to SourceProblem("y", now - 1_000L),
            ),
        )
        val loaded = SourceHealth(clock = { now }, store = store)
        assertEquals(setOf("fresh"), loaded.problems.value.keys)
    }

    @Test
    fun changesAreSaved() = runBlocking {
        val store = FakeStore()
        val scope = kotlinx.coroutines.CoroutineScope(kotlinx.coroutines.Dispatchers.Unconfined)
        val saved = SourceHealth(clock = { 1_000L }, store = store, scope = scope)
        runCatching { saved.track("a") { error("down") } }
        assertEquals(setOf("a"), store.saves.last().keys)
        saved.track("a") { 1 }
        assertTrue(store.saves.last().isEmpty())
        scope.cancel()
    }

    @Test
    fun cancellationRecordsNothing() = runBlocking {
        runCatching { health.track("a") { throw CancellationException("left the screen") } }
        assertTrue(health.problems.value.isEmpty())
    }
}
