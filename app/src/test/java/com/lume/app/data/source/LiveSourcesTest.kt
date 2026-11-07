package com.lume.app.data.source

import com.lume.app.data.network.AntiBot
import com.lume.app.data.network.AppHttp
import com.lume.app.data.network.ImageReferers
import com.lume.app.data.network.SourceHttpException
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.PackIndex
import com.lume.app.domain.SourceFilter
import com.lume.app.domain.SourceLoginRequiredException
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.include
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import okhttp3.Request
import org.junit.Assume.assumeTrue
import org.junit.Test
import java.io.File
import org.junit.Assert.fail

/**
 * Exercises every bundled source and every pack in `docs/packs/index.json` against the real
 * site, the way the app does:
 * popular → page 2 → latest → search → details → chapters → pages → first image.
 *
 * Opt-in (hits the network):  ./gradlew :app:testDebugUnitTest -Plume.live
 * Subset:                     -Plume.live.sources=asura,mangadex  (also runs docs/packs/site samples, e.g. weebcentral,demonic)
 * Save the responses:        -Plume.live.record  (writes src/test/resources/fixtures/<source id>/)
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
        val bundled = File("src/main/assets/sources").listFiles { f -> f.extension == "json" }.orEmpty().toList()
        // The importable packs from the catalogue (docs/packs/index.json) are checked too.
        val packDir = File("../docs/packs")
        val packs = runCatching {
            json.decodeFromString(PackIndex.serializer(), File(packDir, "index.json").readText()).packs
                .map { File(packDir, it.url) }
        }.getOrDefault(emptyList())
        // The one-site samples (docs/packs/site) are not in the catalogue; they run when named in -Plume.live.sources.
        val samples = if (only == null) emptyList() else File(packDir, "site").listFiles { f -> f.extension == "json" }.orEmpty().toList()
        val manifests = (bundled + packs + samples)
            .map { json.decodeFromString(SourceManifest.serializer(), it.readText()) }
            .filter { only == null || it.id in only }
            .sortedBy { it.id }
        val record = System.getProperty("lume.live.record") == "true"
        val fixtureRoot = File("src/test/resources/fixtures")
        val http = AppHttp(CookieJar.NO_COOKIES)
        val referers = ImageReferers()
        val reports = manifests.map { manifest ->
            referers.register(manifest)
            manifest.configString("rateLimit")?.toDoubleOrNull()?.let { rate ->
                java.net.URI(manifest.configString("apiBase") ?: manifest.baseUrl).host?.let { http.setRateLimit(it, rate) }
            }
            val recorder = if (record) Recorder(File(fixtureRoot, manifest.id)) else null
            http.responseTap = recorder?.let { r -> { _, body -> r.save(body) } }
            check(SourceEngines.create(manifest, http), http, referers, recorder)
        }
        println("\n=== Live source report ===")
        reports.forEach { println(it) }
        // For CI summaries (live-sources.yml).
        runCatching { File("build/live-sources-report.txt").writeText(reports.joinToString("\n", postfix = "\n")) }
        val failed = reports.filter { it.status == Status.FAIL }
        if (failed.isNotEmpty()) fail("Sources failing: ${failed.joinToString { it.id }}")
    }

    private enum class Status { OK, WARN, SKIP, FAIL }

    private data class Report(val id: String, val status: Status, val notes: List<String>) {
        override fun toString() = "%-12s %-4s %s".format(id, status, notes.joinToString("; "))
    }

    private fun check(source: CatalogSource, http: AppHttp, referers: ImageReferers, recorder: Recorder?): Report = runBlocking {
        val id = source.manifest.id
        val notes = mutableListOf<String>()
        var warn = false
        try {
            recorder?.phase("popular")
            val popular = source.getPopular(1)
            recorder?.stop()
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
            // The site's own filter lists, a search with one genre, and every browse section.
            val static = source.getFilters().flat().size
            val loaded = source.loadFilters()
            if (loaded.flat().size > static) {
                notes += "site filters=${loaded.flat().size - static}"
                // The last genre of the alphabet: the first ("Action") is on most popular titles.
                val genre = loaded.filters.filterIsInstance<SourceFilter.Group>()
                    .firstOrNull { g -> g.id.startsWith("genre") }
                    ?.filters?.last()
                if (genre != null) {
                    val withGenre = source.search("", 1, loaded.include(genre.id)).items
                    if (withGenre.isEmpty()) {
                        warn = true
                        notes += "filter '${genre.name}' found nothing"
                    } else if (withGenre.map { it.id } == popular.items.map { it.id }) {
                        warn = true
                        notes += "filter '${genre.name}' ignored?"
                    }
                }
            }
            for (section in source.browseSections()) {
                val items = source.search("", 1, section.filters).items
                if (items.isEmpty()) {
                    warn = true
                    notes += "section ${section.id} empty"
                }
            }
            if (source.browseSections().isNotEmpty()) notes += "sections=${source.browseSections().size}"
            val sample = popular.items.first()
            recorder?.phase("search")
            val hits = source.search(sample.title.take(40), 1).items
            recorder?.stop()
            if (hits.none { it.id == sample.id }) {
                warn = true
                notes += "search miss for '${sample.title.take(30)}' (${hits.size} hits)"
            }
            recorder?.phase("details")
            val details = source.getTitle(sample.id)
            recorder?.stop()
            notes += "title='${details.title.take(24)}' desc=${details.description?.length ?: 0} genres=${details.genres.size}"
            if (details.title.isBlank()) return@runBlocking Report(id, Status.FAIL, notes + "blank title")
            // Prefer a title with chapters: walk the popular list until one has some.
            var chapterTitle = sample.id
            recorder?.phase("chapters")
            var chapters = source.getChapters(sample.id)
            if (chapters.isEmpty()) {
                for (other in popular.items.drop(1).take(4)) {
                    recorder?.phase("chapters")
                    chapters = source.getChapters(other.id)
                    chapterTitle = other.id
                    if (chapters.isNotEmpty()) break
                }
            }
            recorder?.stop()
            if (chapters.isEmpty()) return@runBlocking Report(id, Status.FAIL, notes + "no chapters")
            if (recorder != null && chapterTitle != sample.id) {
                // Keep details and chapters of the same title together in the fixtures.
                recorder.phase("details")
                source.getTitle(chapterTitle)
                recorder.stop()
            }
            notes += "chapters=${chapters.size}"
            val chapter = chapters.last() // usually the oldest → most likely free
            recorder?.phase("pages")
            val pages = runCatching { source.getPages(chapter) }.also { recorder?.stop() }.getOrThrow()
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
                    return@runBlocking Report(id, Status.FAIL, notes + "image HTTP ${response.code} $type from ${hostOf(imageUrl)}")
                }
                notes += "image ok ($type)"
            }
            Report(id, if (warn) Status.WARN else Status.OK, notes)
        } catch (e: CloudflareException) {
            Report(id, Status.SKIP, notes + "anti-bot challenge at ${e.uri.host} (needs WebView)")
        } catch (e: SourceLoginRequiredException) {
            Report(id, Status.SKIP, notes + "pages need a signed-in account")
        } catch (e: SourceHttpException) {
            val status = if (e.geoBlocked || e.code == 403) Status.SKIP else Status.FAIL
            Report(id, status, notes + "HTTP ${e.code} ${java.net.URI(e.url).host}${if (e.geoBlocked) " (geo-blocked)" else ""}")
        } catch (e: Exception) {
            Report(id, Status.FAIL, notes + "${e::class.simpleName}: ${e.message}")
        }
    }

    /**
     * With `-Plume.live.record` the raw response bodies of each phase (popular, search, details,
     * chapters, pages) are saved as `src/test/resources/fixtures/<source id>/<case>.{json,html}`
     * for the offline parser tests. A second body in the same phase becomes `<case>-2`.
     */
    private class Recorder(private val dir: File) {
        private var case: String? = null
        private var count = 0

        @Synchronized
        fun phase(name: String) {
            case = name
            count = 0
            dir.listFiles { f -> f.nameWithoutExtension.let { it == name || it.startsWith("$name-") } }
                ?.forEach { it.delete() }
        }

        @Synchronized
        fun stop() {
            case = null
        }

        @Synchronized
        fun save(body: String) {
            val name = case ?: return
            count++
            val ext = if (body.trimStart().let { it.startsWith("{") || it.startsWith("[") }) "json" else "html"
            dir.mkdirs()
            File(dir, (if (count == 1) name else "$name-$count") + ".$ext").writeText(body)
        }
    }


    private fun hostOf(url: String) = java.net.URI(url).host.orEmpty()
}
