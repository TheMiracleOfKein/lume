package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.FilterList
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceFilter
import com.lume.app.domain.SourceManifest
import com.lume.app.testing.Fixtures
import com.lume.app.testing.assertHttpUrls
import com.lume.app.testing.assertSequentialIndexes
import com.lume.app.testing.assertUniqueIds
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/**
 * InkStory pages (fixtures/inkstory): trimmed copies of the live pages. `chapters.html` is the
 * list of a title with two translation teams, edited so that chapter 364 is paid;
 * `chapters-restricted.html` is a title the site shows to signed-in readers only.
 */
class InkStoryParsingTest {
    private val engine = InkStoryEngine(
        SourceManifest(id = "inkstory", name = "InkStory", engine = "inkstory", baseUrl = "https://inkstory.net"),
        AppHttp(CookieJar.NO_COOKIES),
    )

    @Test
    fun popularListSkipsRestrictedTitles() {
        val page = engine.parseTitleList(Fixtures.text("inkstory/popular.html"), 1)
        // Nine titles in the fixture, four of them need an account or a library.
        assertEquals(
            listOf(
                "blue-lock",
                "berserk",
                "an-villains-ending-is-death_aaabdfd",
                "lookism",
                "the-returners-magic-should-be-special-2018_aafgdf",
            ),
            page.items.map { it.id },
        )
        assertUniqueIds(page.items.map { it.id })
        assertEquals("Берсерк", page.items[1].title)
        assertHttpUrls(page.items.mapNotNull { it.coverUrl })
        assertEquals(page.items.size, page.items.mapNotNull { it.coverUrl }.size)
        assertTrue(page.items[1].latestChapter!!.toInt() > 300) // chapter count of Berserk
        assertTrue(page.hasNext) // 21 thousand titles, 40 a page
    }

    @Test
    fun adultFlaggedTitlesAreLeftOut() {
        fun book(slug: String, status: String) =
            """${'$'}R[1]={slug:"$slug",poster:"https://static.inuko.me/p.jpg",contentStatus:"$status",readingRestriction:"NONE",name:{ru:"$slug"}}"""
        val body = """<script>(${'$'}R=>${'$'}R[0]={total:3,books:[${book("a", "SAFE")},${book("b", "EROTIC")},${book("c", "PORNOGRAPHIC")}]})(${'$'}R["tsr"])</script>"""
        assertEquals(listOf("a"), engine.parseTitleList(body, 1).items.map { it.id })
    }

    @Test
    fun latestList() {
        val page = engine.parseTitleList(Fixtures.text("inkstory/latest.html"), 1)
        assertEquals(5, page.items.size)
        assertEquals("geu-gisaga-leidilo-saneun-beob", page.items.first().id)
        assertTrue(page.hasNext)
    }

    @Test
    fun searchList() {
        val page = engine.parseTitleList(Fixtures.text("inkstory/search.html"), 1)
        assertEquals("berserk", page.items.first().id)
        assertEquals(5, page.items.size)
        assertFalse(page.hasNext) // 15 hits fit one page
    }

    @Test
    fun pagingUsesTheTotal() {
        val html = Fixtures.text("inkstory/popular.html")
        assertTrue(engine.parseTitleList(html, 542).hasNext)
        assertFalse(engine.parseTitleList(html, 543).hasNext) // 21691 titles = 543 pages of 40
    }

    @Test
    fun details() {
        val details = engine.parseDetails(Fixtures.text("inkstory/details.html"), "blue-lock")
        assertEquals("blue-lock", details.id)
        assertEquals("Синяя тюрьма: Блю Лок", details.title)
        assertEquals("Blue lock", details.altTitle)
        assertTrue(details.description!!.startsWith("После горького поражения"))
        assertEquals(2018, details.year)
        assertTrue(details.score!! > 9)
        assertTrue("психология" in details.genres)
        assertTrue("ブルーロック" in details.otherNames)
        assertHttpUrls(listOfNotNull(details.coverUrl))
        assertTrue(details.coverUrl!!.startsWith("https://static.inuko.me/book/"))
    }

    @Test
    fun chaptersKeepEveryTeamNewestFirst() {
        val chapters = engine.parseChapters(Fixtures.text("inkstory/chapters.html"), "blue-lock")
        // 13 chapters in the fixture, the paid 364 is skipped.
        assertEquals(12, chapters.size)
        assertUniqueIds(chapters.map { it.id })
        assertFalse(chapters.any { it.number == "364" })
        assertEquals(listOf("367", "366", "365", "363"), chapters.take(4).map { it.number })
        val first = chapters.first()
        assertEquals("ecac9911-1e58-41a9-841f-af9961045a11", first.id)
        assertEquals("/content/blue-lock/ecac9911-1e58-41a9-841f-af9961045a11", first.url)
        assertEquals("Том 41 Глава 367 — Переработка и созидание", first.title)
        assertEquals("41", first.volume)
        assertEquals("flayt", first.branchName)
        assertEquals(10, first.dateLabel?.length) // yyyy-mm-dd
        assertEquals("1", chapters.last().number)
        // Chapter 336 is translated by both teams: two entries, two teams.
        val both = chapters.filter { it.number == "336" }
        assertEquals(2, both.size)
        assertEquals(2, both.map { it.branchId }.distinct().size)
        assertTrue(chapters.all { it.url.endsWith(it.id) })
    }

    @Test
    fun restrictedTitleWithoutChaptersIsAnError() {
        try {
            engine.parseChapters(Fixtures.text("inkstory/chapters-restricted.html"), "solo-leveling")
            fail("an empty chapter list of a restricted title must not pass quietly")
        } catch (e: SourceFetchException) {
            // expected
        }
    }

    @Test
    fun pagesInOrderWithTheImageKey() {
        val pages = engine.parsePages(Fixtures.text("inkstory/pages.html"))
        assertEquals(5, pages.size)
        assertSequentialIndexes(pages.map { it.index })
        assertHttpUrls(pages.map { it.imageUrl })
        assertTrue(pages.all { it.imageUrl.startsWith("https://static.inuko.me/chapters/04384588-e26e-4b7b-a047-a5a32380229f/") })
        assertTrue(pages.all { it.imageUrl.contains("?lume_xor=") })
        assertEquals(5, pages.map { it.imageUrl }.distinct().size)
    }

    @Test
    fun pagesWithoutAKeyStayPlain() {
        val body = """<script>(${'$'}R=>${'$'}R[0]={initialChapter:${'$'}R[1]={id:"c",pages:${'$'}R[2]=[
            ${'$'}R[3]={image:"https://static.inuko.me/a.png",index:1},${'$'}R[4]={image:"https://static.inuko.me/b.png",index:0}]}})(${'$'}R["tsr"])</script>"""
        val pages = engine.parsePages(body)
        assertEquals(listOf("https://static.inuko.me/b.png", "https://static.inuko.me/a.png"), pages.map { it.imageUrl })
    }

    @Test
    fun filtersFromTheLabelDictionary() {
        val filters = engine.parseFilters(Fixtures.text("inkstory/popular.html"))
        val groups = filters.filters.filterIsInstance<SourceFilter.Group>()
        assertEquals(listOf("genres", "tags"), groups.map { it.id })
        assertEquals(8, groups[0].filters.size)
        assertTrue(groups.all { g -> g.filters.all { it is SourceFilter.TriState && it.id.startsWith("${g.id}:") } })
        assertNotNull(filters.filters.firstOrNull { it is SourceFilter.Select && it.id == "sort" })
    }

    @Test
    fun catalogueQueries() {
        fun q(page: Int, search: String? = null, sort: String? = null, filters: FilterList = FilterList()) =
            engine.catalogQuery(page, search, sort, filters)
        assertEquals("", q(1))
        assertEquals("?page=2", q(3)) // the site counts from 0 and answers page=0 with a redirect
        assertEquals("?sort=latestChapterAt&direction=desc", q(1, sort = "latestChapterAt"))
        assertEquals("?sort=latestChapterAt&direction=desc&page=1", q(2, sort = "latestChapterAt"))
        assertEquals("?search=solo+leveling&page=1", q(2, search = "solo leveling"))
        val filters = FilterList(
            listOf(
                SourceFilter.Group(
                    "genres",
                    "Genres",
                    listOf(SourceFilter.TriState("genres:aaa", "A", state = 1), SourceFilter.TriState("genres:bbb", "B", state = 2)),
                ),
                SourceFilter.Group("tags", "Tags", listOf(SourceFilter.TriState("tags:ccc", "C", state = 1))),
            ),
        )
        // The site wants JSON arrays, even with a single value.
        assertEquals(
            "?labelsInclude=%5B%22aaa%22%2C%22ccc%22%5D&labelsExclude=%5B%22bbb%22%5D",
            q(1, filters = filters),
        )
    }

    @Test
    fun links() {
        assertEquals("https://inkstory.net/content/berserk", engine.webUrl("berserk"))
        assertEquals("berserk", engine.titleIdFromUrl("https://inkstory.net/content/berserk"))
        assertEquals("berserk", engine.titleIdFromUrl("https://inkstory.net/content/berserk/chapters"))
        assertNull(engine.titleIdFromUrl("https://inkstory.net/catalog"))
        assertNull(engine.titleIdFromUrl("https://example.com/content/berserk"))
    }

    @Test
    fun aPageWithoutDataIsAnError() {
        try {
            engine.parseTitleList("<html><body>DDoS-Guard</body></html>", 1)
            fail("a page without the data script must not read as an empty list")
        } catch (e: SourceFetchException) {
            // expected
        }
    }

    // The `$R[n]=…` reader on its own.

    private fun parse(text: String) = InkStoryHydration.parse(text)

    @Test
    fun readsBooleansNullsAndNumbers() {
        val root = parse("""${'$'}R[0]={a:!0,b:!1,c:null,d:void 0,e:12,f:-3.5,g:1e3,h:true}""") as JsonObject
        assertEquals(JsonPrimitive(true), root["a"])
        assertEquals(JsonPrimitive(false), root["b"])
        assertEquals(JsonNull, root["c"])
        assertEquals(JsonNull, root["d"])
        assertEquals("12", (root["e"] as JsonPrimitive).content)
        assertEquals(-3.5, (root["f"] as JsonPrimitive).content.toDouble(), 0.0)
        assertEquals(1000.0, (root["g"] as JsonPrimitive).content.toDouble(), 0.0)
        assertEquals(JsonPrimitive(true), root["h"])
    }

    @Test
    fun readsNestedObjectsAndArrays() {
        val root = parse("""${'$'}R[0]={x:${'$'}R[1]={y:${'$'}R[2]=[${'$'}R[3]={z:1},${'$'}R[4]=[1,2,[3]],{}],"quoted-key":"v",0:"zero"},end:!0}""") as JsonObject
        val x = root["x"] as JsonObject
        val y = x["y"] as JsonArray
        assertEquals(3, y.size)
        assertEquals("1", ((y[0] as JsonObject)["z"] as JsonPrimitive).content)
        assertEquals(3, (y[1] as JsonArray).size)
        assertEquals(JsonPrimitive("v"), x["quoted-key"])
        assertEquals(JsonPrimitive("zero"), x["0"])
        assertEquals(JsonPrimitive(true), root["end"])
    }

    @Test
    fun readsStringEscapes() {
        val text = """${'$'}R[0]={s:"a\"b\\c\ndб\x41\x3Cscript>\/ end",t:'it\'s',u:"\u{1F600}"}"""
        val root = parse(text) as JsonObject
        assertEquals("a\"b\\c\ndбA<script>/ end", (root["s"] as JsonPrimitive).content)
        assertEquals("it's", (root["t"] as JsonPrimitive).content)
        assertEquals("😀", (root["u"] as JsonPrimitive).content)
    }

    @Test
    fun stringsMayHoldBracketsAndCommas() {
        val root = parse("""${'$'}R[0]={a:"}],{",b:"${'$'}R[9]=",c:1}""") as JsonObject
        assertEquals("}],{", (root["a"] as JsonPrimitive).content)
        assertEquals("${'$'}R[9]=", (root["b"] as JsonPrimitive).content)
        assertEquals("1", (root["c"] as JsonPrimitive).content)
    }

    @Test
    fun resolvesReferencesToEarlierDefinitions() {
        // The second use of a shared value is only `$R[n]`; the numbers themselves mean nothing.
        val root = parse("""${'$'}R[40]={first:${'$'}R[41]=[${'$'}R[42]={id:"a"}],second:${'$'}R[41],third:${'$'}R[42],lost:${'$'}R[99]}""") as JsonObject
        val first = root["first"] as JsonArray
        assertEquals(first, root["second"])
        assertEquals(first[0], root["third"])
        assertEquals(JsonNull, root["lost"])
    }

    @Test
    fun skipsExpressionsItDoesNotKnow() {
        val root = parse("""${'$'}R[0]={d:new Date("2026-01-01T00:00:00Z"),m:new Map([[1,"a,b"],[2,{x:3}]]),after:"ok",o:Object.create(null),last:7}""") as JsonObject
        assertEquals(JsonNull, root["d"])
        assertEquals(JsonNull, root["m"])
        assertEquals(JsonPrimitive("ok"), root["after"])
        assertEquals(JsonNull, root["o"])
        assertEquals("7", (root["last"] as JsonPrimitive).content)
    }

    @Test
    fun findsTheScriptInsideHtml() {
        val html = """<html><head><title>x {y: 1}</title></head><body><script>(self.${'$'}R=self.${'$'}R||{})["tsr"]=[];
            ${'$'}_TSR.router=(${'$'}R=>${'$'}R[0]={a:${'$'}R[1]=["x"]})(${'$'}R["tsr"]);document.currentScript.remove()</script></body></html>"""
        val root = parse(html) as JsonObject
        assertEquals(JsonArray(listOf(JsonPrimitive("x"))), root["a"])
    }

    @Test
    fun cutOffOrMissingScriptReadsAsNull() {
        assertNull(parse("<html>no data</html>"))
        assertNull(parse("""${'$'}R[0]={a:"unfinished"""))
        assertNull(parse("""${'$'}R[0]={a:[1,2"""))
    }

    @Test
    fun walkVisitsSharedNodesOnce() {
        val root = parse("""${'$'}R[0]={a:${'$'}R[1]={k:1},b:${'$'}R[1],c:[${'$'}R[1]]}""")!!
        assertEquals(3, root.walk().count()) // root, the shared object, the array
    }

    @Test
    fun realPageParsesCompletely() {
        val root = parse(Fixtures.text("inkstory/chapters.html"))
        assertTrue(root is JsonObject)
        assertTrue(root!!.walk().filterIsInstance<JsonObject>().any { it["chapters"] is JsonArray })
    }
}
