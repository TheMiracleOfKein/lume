package com.lume.app.data.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class NovelBlocksTest {
    private fun parse(html: String, base: String? = null) = NovelBlocks.parse(html, base)

    private fun texts(blocks: List<NovelBlock>) = blocks.map { (it as NovelBlock.Paragraph).spans.plainText }

    @Test
    fun headingsAndParagraphs() {
        val blocks = parse("<h1>Title</h1><p>One.</p><h3>Sub</h3><p>Two.</p>")
        assertEquals(
            listOf(
                NovelBlock.Heading(1, listOf(Span("Title", bold = true))),
                NovelBlock.Paragraph(listOf(Span("One."))),
                NovelBlock.Heading(3, listOf(Span("Sub", bold = true))),
                NovelBlock.Paragraph(listOf(Span("Two."))),
            ),
            blocks,
        )
    }

    @Test
    fun boldAndItalicSpansAreKept() {
        val blocks = parse("<p>Plain <b>bold</b> and <em>italic <strong>both</strong></em>.</p>")
        val spans = (blocks.single() as NovelBlock.Paragraph).spans
        assertEquals(
            listOf(
                Span("Plain "),
                Span("bold", bold = true),
                Span(" and "),
                Span("italic ", italic = true),
                Span("both", bold = true, italic = true),
                Span("."),
            ),
            spans,
        )
    }

    @Test
    fun linksKeepOnlyTheirText() {
        val blocks = parse("<p>See <a href=\"http://x.test\">the page</a> now</p>")
        assertEquals(listOf("See the page now"), texts(blocks))
    }

    @Test
    fun doubleBreakSplitsParagraphsSingleBreakStaysInside() {
        val blocks = parse("<p>First<br><br>Second<br>line</p>")
        assertEquals(listOf("First", "Second\nline"), texts(blocks))
    }

    @Test
    fun bareTextNodesBecomeParagraphs() {
        val blocks = parse("Just text<br /><br />More text")
        assertEquals(listOf("Just text", "More text"), texts(blocks))
    }

    @Test
    fun scriptsAndStylesAreDropped() {
        val blocks = parse("<style>p{color:red}</style><script>alert(1)</script><p>Text</p><script>x()</script>")
        assertEquals(listOf(NovelBlock.Paragraph(listOf(Span("Text")))), blocks)
    }

    @Test
    fun emptyParagraphsAreDropped() {
        val blocks = parse("<p></p><p>   </p><p>&nbsp;</p><p><br></p><p>Kept</p>")
        assertEquals(listOf(NovelBlock.Paragraph(listOf(Span("Kept")))), blocks)
    }

    @Test
    fun whitespaceIsCollapsed() {
        val blocks = parse("<p>  a \n  b\t\tc  </p>")
        assertEquals(listOf("a b c"), texts(blocks))
    }

    @Test
    fun relativeImageSourcesAreResolvedAgainstTheBase() {
        val blocks = parse("<p>Before</p><img src=\"../img/a.png\"><p><img src=\"b.jpg\"></p>", "https://site.test/book/ch/1.html")
        assertEquals(
            listOf(
                NovelBlock.Paragraph(listOf(Span("Before"))),
                NovelBlock.Image("https://site.test/book/img/a.png"),
                NovelBlock.Image("https://site.test/book/ch/b.jpg"),
            ),
            blocks,
        )
    }

    @Test
    fun imageSourcesStayAsWrittenWithoutABase() {
        assertEquals(listOf(NovelBlock.Image("img/a.png")), parse("<img src=\"img/a.png\">"))
    }

    @Test
    fun rulesAndSceneBreaksAreDividers() {
        val blocks = parse("<p>a</p><hr><p>b</p><p>* * *</p><p>c</p>")
        assertEquals(
            listOf(
                NovelBlock.Paragraph(listOf(Span("a"))),
                NovelBlock.Divider,
                NovelBlock.Paragraph(listOf(Span("b"))),
                NovelBlock.Divider,
                NovelBlock.Paragraph(listOf(Span("c"))),
            ),
            blocks,
        )
    }

    @Test
    fun nestedBlocksFlattenInOrder() {
        val blocks = parse("<div><p>one</p><blockquote><p>two</p></blockquote>three</div>")
        assertEquals(listOf("one", "two", "three"), texts(blocks))
        assertTrue(parse("").isEmpty())
    }
}
