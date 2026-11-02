package com.lume.app.util

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MarkdownTest {

    @Test
    fun releaseNotesBecomeBlocks() {
        val blocks = parseMarkdown(
            """
            # Lume v0.7.12

            Polish: a tidier menu
            and more.

            ## New

            - **Search from Home.** Type and press enter.
            - **Sync:**
              - A phone whose clock is behind
                keeps its changes.
            """.trimIndent(),
        )
        assertEquals(MdBlock.Heading(1, listOf(MdSpan("Lume v0.7.12"))), blocks[0])
        assertEquals(MdBlock.Paragraph(listOf(MdSpan("Polish: a tidier menu and more."))), blocks[1])
        assertEquals(2, (blocks[2] as MdBlock.Heading).level)
        val first = blocks[3] as MdBlock.Bullet
        assertEquals(0, first.depth)
        assertEquals(listOf(MdSpan("Search from Home.", bold = true), MdSpan(" Type and press enter.")), first.spans)
        val nested = blocks[5] as MdBlock.Bullet
        assertEquals(1, nested.depth)
        assertEquals("A phone whose clock is behind keeps its changes.", nested.spans.joinToString("") { it.text })
        assertEquals(6, blocks.size)
    }

    @Test
    fun inlineMarks() {
        assertEquals(
            listOf(
                MdSpan("Use "),
                MdSpan("adb", code = true),
                MdSpan(", see "),
                MdSpan("notes", url = "https://example.org/n"),
                MdSpan(" and "),
                MdSpan("this", italic = true),
            ),
            parseInline("Use `adb`, see [notes](https://example.org/n) and *this*"),
        )
    }

    @Test
    fun unclosedMarksStayAsText() {
        assertEquals("2 * 3 = 6, **open, [x](", parseInline("2 * 3 = 6, **open, [x](").joinToString("") { it.text })
        assertTrue(parseInline("snake_case_name").all { !it.italic })
    }
}
