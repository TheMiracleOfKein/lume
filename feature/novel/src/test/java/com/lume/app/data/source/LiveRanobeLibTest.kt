package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.data.network.SourceHttpException
import com.lume.app.domain.FilterList
import com.lume.app.domain.SourceManifest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * RanobeLib against the real site, the way the app uses it:
 * popular → search → details → chapters → text of the first chapter.
 *
 * Opt-in (hits the network): ./gradlew :feature:novel:testDebugUnitTest -Plume.live
 * A region block (the API refuses some countries) is reported as skipped, not as a failure.
 */
class LiveRanobeLibTest {
    @Test
    fun ranobeLibWorks() = runBlocking {
        assumeTrue("Live source checks disabled (pass -Plume.live)", System.getProperty("lume.live") == "true")
        val manifest = Json { ignoreUnknownKeys = true }
            .decodeFromString(SourceManifest.serializer(), File("src/main/assets/sources/ranobelib.json").readText())
        val http = AppHttp(CookieJar.NO_COOKIES)
        http.setRateLimit("api.lib.social", LIB_REQUESTS_PER_SECOND)
        val engine = RanobeLibEngine(manifest, http)
        val popular = try {
            engine.getPopular(1)
        } catch (e: SourceHttpException) {
            assumeTrue("RanobeLib unreachable from here (HTTP ${e.code})", e.code !in setOf(403, 451))
            throw e
        }
        assertTrue("popular is empty", popular.items.isNotEmpty())
        val first = popular.items.first()
        val found = engine.search(first.title.take(20), 1, FilterList(emptyList()))
        assertTrue("search for '${first.title}' found nothing", found.items.isNotEmpty())
        val details = engine.getTitle(first.id)
        assertTrue("no title", details.title.isNotBlank())
        val chapters = engine.getChapters(first.id)
        assertTrue("no chapters", chapters.isNotEmpty())
        val text = engine.getChapterText(chapters.last()).html
        assertTrue("chapter text is empty", text.length > 100)
        println("RanobeLib OK: ${details.title}, ${chapters.size} chapters, first chapter ${text.length} chars of HTML")
    }

    private companion object {
        const val LIB_REQUESTS_PER_SECOND = 2.0
    }
}
