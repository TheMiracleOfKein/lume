package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.data.network.SourceHttpException
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.FilterList
import com.lume.app.domain.NovelSource
import com.lume.app.domain.SourceManifest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import org.junit.Assert.assertTrue
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File

/**
 * Royal Road, Scribble Hub, NovelFull and NOVGO against the real sites, the way the app uses them:
 * popular → search → details → chapters → text of the first chapter.
 *
 * Opt-in (hits the network): ./gradlew :feature:novel:testDebugUnitTest -Plume.live
 * The sites sit behind Cloudflare. A challenge (the app solves it in its WebView) or a region
 * block is reported as skipped, not as a failure.
 */
class LiveEnglishNovelsTest {
    @Test
    fun royalRoadWorks() = check("royalroad") { m, http -> RoyalRoadEngine(m, http) }

    @Test
    fun scribbleHubWorks() = check("scribblehub") { m, http -> ScribbleHubEngine(m, http) }

    @Test
    fun novelFullWorks() = check("novelfull") { m, http -> NovelFullEngine(m, http) }

    @Test
    fun novgoWorks() = check("novgo") { m, http -> NovelFullEngine(m, http) }

    private fun check(id: String, create: (SourceManifest, AppHttp) -> NovelSource) = runBlocking {
        assumeTrue("Live source checks disabled (pass -Plume.live)", System.getProperty("lume.live") == "true")
        val manifest = Json { ignoreUnknownKeys = true }
            .decodeFromString(SourceManifest.serializer(), File("src/main/assets/sources/$id.json").readText())
        val engine = create(manifest, AppHttp(CookieJar.NO_COOKIES))
        val popular = try {
            engine.getPopular(1)
        } catch (e: CloudflareException) {
            assumeTrue("$id shows a Cloudflare check here", false)
            throw e
        } catch (e: SourceHttpException) {
            assumeTrue("$id unreachable from here (HTTP ${e.code})", e.code !in setOf(403, 451, 503))
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
        val text = engine.getChapterText(chapters.first()).html
        assertTrue("chapter text is empty", text.length > 100)
        println("${manifest.name} OK: ${details.title}, ${chapters.size} chapters, first chapter ${text.length} chars of HTML")    }
}
