package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.MediaKind
import com.lume.app.domain.NovelSource
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceManifest
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.File

class RanobeLibEngineTest {
    private val manifest = Json { ignoreUnknownKeys = true }
        .decodeFromString(SourceManifest.serializer(), File("src/main/assets/sources/ranobelib.json").readText())
    private val engine = RanobeLibEngine(manifest, AppHttp(CookieJar.NO_COOKIES))

    @Test
    fun bundledManifestIsANovelSourceOfTheNovelModule() {
        assertEquals(MediaKind.Novel, manifest.kind)
        assertEquals("3", manifest.configString("siteId"))
        val created = SourceEngines.create(manifest, AppHttp(CookieJar.NO_COOKIES), modules = listOf(NovelModule))
        assertTrue(created is NovelSource)
        assertTrue(SourceEngines.isLib(manifest.engine))
    }

    @Test
    fun chapterAnswerBecomesHtml() {
        // Trimmed from a real answer of /manga/<slug>/chapter?volume=1&number=42&branch_id=… (Site-Id 3).
        val body = """
            {"data":{"id":3508327,"model":"chapter","volume":"1","number":"42","name":"","branch_id":21644,
            "teams":[{"id":26458,"name":"Team"}],"type":"chapter",
            "content":{"type":"doc","content":[
              {"type":"paragraph","attrs":{"textAlign":"center"},"content":[{"type":"text","marks":[{"type":"bold"}],"text":"Глава 42."}]},
              {"type":"paragraph","content":[{"type":"text","text":"Я тихонько шёл по выставке."}]}
            ]},"attachments":[]}}
        """.trimIndent()
        assertEquals(
            "<p><b>Глава 42.</b></p><p>Я тихонько шёл по выставке.</p>",
            engine.parseChapterText(body),
        )
    }

    @Test
    fun htmlChapterWithImagesKeepsTheImageLinks() {
        // Trimmed from a real web novel answer: `content` is an HTML string, and the attachment
        // URLs differ from the <img> ones (a trailing "-" in the slug) and answer 500, while the
        // <img> links work. The <img> links must be the ones kept, without extra copies.
        val img = "https://ranobelib.me/uploads/ranobe/death-is-the-only-ending-for-the-villain-novel/chapters/595365/1_FX1o.png"
        val body = """
            {"data":{"id":595365,"volume":"1","number":"0","type":"chapter",
            "content":"<p data-paragraph-index=\"1\">☆Пенелопа Эккарт☆ (ГГ)</p><p data-paragraph-index=\"2\">• 17-18 лет.</p><p><img loading=\"lazy\" src=\"$img\" /></p>",
            "attachments":[{"id":null,"filename":"1_FX1o.png","name":"1_FX1o","extension":"png",
              "url":"/uploads/ranobe/death-is-the-only-ending-for-the-villain-novel-/chapters/595365/1_FX1o.png","width":0,"height":0}]}}
        """.trimIndent()
        val blocks = com.lume.app.data.novel.NovelBlocks.parse(engine.parseChapterText(body))
        assertEquals(3, blocks.size)
        assertTrue(blocks[0] is com.lume.app.data.novel.NovelBlock.Paragraph)
        assertEquals(img, (blocks[2] as com.lume.app.data.novel.NovelBlock.Image).src)
    }

    @Test
    fun toastIsAnError() {
        try {
            engine.parseChapterText("""{"data":{"toast":{"type":"error","message":"Глава недоступна"}}}""")
            fail("expected an error")
        } catch (e: SourceFetchException) {
            assertEquals("Глава недоступна", e.message)
        }
    }

    @Test
    fun linksUseTheBookPath() {
        val id = "/214246--even-if-you-find-yourself-in-a-haunted-tale-work-still-calls"
        assertEquals("https://ranobelib.me/ru/book/214246--even-if-you-find-yourself-in-a-haunted-tale-work-still-calls", engine.webUrl(id))
        assertEquals(id, engine.titleIdFromUrl("https://ranobelib.me/ru/book/214246--even-if-you-find-yourself-in-a-haunted-tale-work-still-calls"))
        assertEquals(id, engine.titleIdFromUrl("https://ranobelib.me/ru/214246--even-if-you-find-yourself-in-a-haunted-tale-work-still-calls/read/v1/c42?bid=21644"))
        assertEquals(null, engine.titleIdFromUrl("https://mangalib.me/ru/manga/7580--x"))
    }
}
