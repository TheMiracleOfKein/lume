package com.lume.app.data.source

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** RanobeLib chapter answers (`data` of `/manga/<slug>/chapter`, Site-Id 3) turned into HTML. */
class LibChapterTextTest {
    private val site = "https://ranobelib.me"

    private fun data(raw: String): JsonObject = Json.parseToJsonElement(raw).jsonObject

    @Test
    fun docParagraphsWithMarks() {
        // The shape of a real chapter: centred bold heading line, then plain paragraphs.
        val html = LibChapterText.toHtml(
            data(
                """
                {"type":"chapter","content":{"type":"doc","content":[
                  {"type":"paragraph","attrs":{"textAlign":"center"},"content":[{"type":"text","marks":[{"type":"bold"}],"text":"Глава 42."}]},
                  {"type":"paragraph","content":[{"type":"text","text":"*Стук, стук.*"}]},
                  {"type":"paragraph","content":[
                    {"type":"text","marks":[{"type":"italic"}],"text":"Он"},
                    {"type":"text","text":" "},
                    {"type":"text","marks":[{"type":"bold"},{"type":"italic"}],"text":"ушёл"},
                    {"type":"text","text":" <тихо> & быстро"}
                  ]}
                ]},"attachments":[]}
                """,
            ),
            site,
        )
        assertEquals(
            "<p><b>Глава 42.</b></p><p>*Стук, стук.*</p>" +
                "<p><i>Он</i> <b><i>ушёл</i></b> &lt;тихо&gt; &amp; быстро</p>",
            html,
        )
    }

    @Test
    fun docBlocksAndUnknownNodes() {
        val html = LibChapterText.toHtml(
            data(
                """
                {"content":{"type":"doc","content":[
                  {"type":"heading","attrs":{"level":2},"content":[{"type":"text","text":"Пролог"}]},
                  {"type":"paragraph","content":[{"type":"text","text":"А"},{"type":"hardBreak"},{"type":"text","text":"Б"}]},
                  {"type":"horizontalRule"},
                  {"type":"spoiler","content":[{"type":"paragraph","content":[{"type":"text","text":"Тайна"}]}]},
                  {"type":"bulletList","content":[{"type":"listItem","content":[{"type":"paragraph","content":[{"type":"text","text":"Пункт"}]}]}]}
                ]}}
                """,
            ),
            site,
        )
        assertEquals(
            "<h2>Пролог</h2><p>А<br>Б</p><hr><p>Тайна</p><ul><li><p>Пункт</p></li></ul>",
            html,
        )
    }

    @Test
    fun docImagesFromAttachments() {
        val html = LibChapterText.toHtml(
            data(
                """
                {"content":{"type":"doc","content":[
                  {"type":"paragraph","content":[{"type":"text","text":"До"}]},
                  {"type":"image","attrs":{"images":[{"image":"a1b2"}]}}
                ]},
                "attachments":[{"id":7,"name":"a1b2","filename":"a1b2.jpg","url":"/uploads/ranobe/book/chapters/1/a1b2.jpg"}]}
                """,
            ),
            site,
        )
        assertEquals(
            "<p>До</p><p><img src=\"https://ranobelib.me/uploads/ranobe/book/chapters/1/a1b2.jpg\"></p>",
            html,
        )
    }

    @Test
    fun htmlStringChapter() {
        val html = LibChapterText.toHtml(
            data("""{"content":"<p>Первый</p><script>x()</script><p><img src=\"/uploads/p.png\"></p>"}"""),
            site,
        )!!
        assertTrue(html, html.contains("<p>Первый</p>"))
        assertTrue(html, html.contains("src=\"https://ranobelib.me/uploads/p.png\""))
        assertTrue(html, !html.contains("script"))
    }

    @Test
    fun emptyChapterIsNull() {
        assertNull(LibChapterText.toHtml(data("""{"content":{"type":"doc","content":[]}}"""), site))
        assertNull(LibChapterText.toHtml(data("""{"content":""}"""), site))
        assertNull(LibChapterText.toHtml(data("""{"pages":[]}"""), site))
    }
}
