package com.lume.app.data.source

import com.lume.app.data.network.AntiBot
import com.lume.app.data.network.AppHttp
import com.lume.app.data.network.ImageReferers
import com.lume.app.data.network.SourceHttpException
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.SourceManifest
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import org.junit.Assert.fail

/**
 * Exercises every bundled source against the real site, the way the app does:
 * popular → page 2 → latest → search → details → chapters → pages → first image.
 *
 * Opt-in (hits the network):  ./gradlew :app:testDebugUnitTest -Plume.live
 * Subset:                     -Plume.live.sources=asura,mangadex
 *
 * Sources that are unreachable from the test machine (region blocks, anti-bot challenges that
 * need a WebView) are reported as SKIP rather than failures.
 */
class LiveSourcesTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun bundledSourcesWork() {
        assumeTrue("Live source checks disabled (pass -Plume.live)", System.getProperty("lume.live") == "true")
        val only = System.getProperty("lume.live.sources")?.split(',')?.map { it.trim() }?.toSet()
        val manifests = File("src/main/assets/sources").listFiles { f -> f.extension == "json" }.orEmpty()
            .map { json.decodeFromString(SourceManifest.serializer(), it.readText()) }
            .filter { only == null || it.id in only }
            .sortedBy { it.id }
        val http = AppHttp(CookieJar.NO_COOKIES)
        val referers = ImageReferers()
        val reports = manifests.map { manifest ->
            referers.register(manifest)
            manifest.configString("rateLimit")?.toDoubleOrNull()?.let { rate ->
                java.net.URI(manifest.configString("apiBase") ?: manifest.baseUrl).host?.let { http.setRateLimit(it, rate) }
            }
            check(SourceEngines.create(manifest, http), http, referers)
        }
        println("\n=== Live source report ===")
        reports.forEach { println(it) }
        val failed = reports.filter { it.status == Status.FAIL }
        if (failed.isNotEmpty()) fail("Sources failing: ${failed.joinToString { it.id }}")
    }

    private enum class Status { OK, WARN, SKIP, FAIL }

    private data class Report(val id: String, val status: Status, val notes: List<String>) {
        override fun toString() = "%-12s %-4s %s".format(id, status, notes.joinToString("; "))
    }

    private fun check(source: CatalogSource, http: AppHttp, referers: ImageReferers): Report = runBlocking {
        val id = source.manifest.id
        val notes = mutableListOf<String>()
        var warn = false
        try {
            val popular = source.getPopular(1)
            if (popular.items.isEmpty()) return@runBlocking Report(id, Status.FAIL, listOf("popular page 1 empty"))
            notes += "popular=${popular.items.size}"
            if (popular.hasNext) {
                val page2 = source.getPopular(2)
                val overlap = page2.items.map { it.id }.intersect(popular.items.map { it.id }.toSet()).size
                if (page2.items.isEmpty() || overlap == page2.items.size) {
                    return@runBlocking Report(id, Status.FAIL, notes + "page 2 repeats page 1 (pagination ignored)")
                }
                notes += "page2 ok"
            }
            val latest = source.getLatest(1)
            if (latest.items.map { it.id } == popular.items.map { it.id }) {
                warn = true
                notes += "latest == popular (sort ignored?)"
            }
            val sample = popular.items.first()
            val hits = source.search(sample.title.take(40), 1).items
            if (hits.none { it.id == sample.id }) {
                warn = true
                notes += "search miss for '${sample.title.take(30)}' (${hits.size} hits)"
            }
            val details = source.getTitle(sample.id)
            notes += "title='${details.title.take(24)}' desc=${details.description?.length ?: 0} genres=${details.genres.size}"
            if (details.title.isBlank()) return@runBlocking Report(id, Status.FAIL, notes + "blank title")
            // Prefer a title with chapters: walk the popular list until one has some.
            var chapters = source.getChapters(sample.id)
            if (chapters.isEmpty()) {
                for (other in popular.items.drop(1).take(4)) {
                    chapters = source.getChapters(other.id)
                    if (chapters.isNotEmpty()) break
                }
            }
            if (chapters.isEmpty()) return@runBlocking Report(id, Status.FAIL, notes + "no chapters")
            notes += "chapters=${chapters.size}"
            val chapter = chapters.last() // usually the oldest → most likely free
            val pages = source.getPages(chapter)
            if (pages.isEmpty()) return@runBlocking Report(id, Status.FAIL, notes + "no pages")
            notes += "pages=${pages.size}"
            referers.learn(pages.first().imageUrl, source.manifest)
            val imageUrl = pages.first().imageUrl
            val request = Request.Builder().url(imageUrl)
                .header("User-Agent", http.userAgent)
                .apply { referers.refererFor(hostOf(imageUrl))?.let { header("Referer", it) } }
                .build()
            http.okHttp.newCall(request).execute().use { response ->
                val type = response.header("Content-Type").orEmpty()
                if (!response.isSuccessful &&
                    AntiBot.isGeoBlocked(response.header("Server"), response.peekBody(16_384).string())
                ) {
                    return@runBlocking Report(id, Status.SKIP, notes + "image CDN geo-blocked (${hostOf(imageUrl)})")
                }
                if (!response.isSuccessful || !(type.startsWith("image/") || type.contains("octet-stream"))) {
                    return@runBlocking Report(id, Status.FAIL, notes + "image HTTP ${response.code} $type")
                }
                notes += "image ok ($type)"
            }
            Report(id, if (warn) Status.WARN else Status.OK, notes)
        } catch (e: CloudflareException) {
            Report(id, Status.SKIP, notes + "anti-bot challenge at ${e.uri.host} (needs WebView)")
        } catch (e: SourceHttpException) {
            val status = if (e.geoBlocked || e.code == 403) Status.SKIP else Status.FAIL
            Report(id, status, notes + "HTTP ${e.code} ${java.net.URI(e.url).host}${if (e.geoBlocked) " (geo-blocked)" else ""}")
        } catch (e: Exception) {
            Report(id, Status.FAIL, notes + "${e::class.simpleName}: ${e.message}")
        }
    }

    private fun hostOf(url: String) = java.net.URI(url).host.orEmpty()
}
