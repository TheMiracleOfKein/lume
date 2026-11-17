package com.lume.app.data.novel

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TxtBookTest {
    private fun titles(book: TxtBook) = book.chapters.map { it.title }

    @Test
    fun englishHeadingsSplitChapters() {
        val text = "Prologue\n\nIt began.\n\nChapter 1: The Start\n\nHello there.\n\nChapter Two\n\nSecond.\n\nEpilogue\n\nEnd."
        val book = TxtBook.parse(text, "Book")
        assertEquals(listOf("Prologue", "Chapter 1: The Start", "Chapter Two", "Epilogue"), titles(book))
        assertEquals(listOf("Hello there."), book.chapters[1].paragraphs)
    }

    @Test
    fun russianHeadingsSplitChapters() {
        val text = "Пролог\n\nНачало.\n\nГлава 1. Дорога\n\nТекст первой.\n\nГлава 2\n\nТекст второй.\n\nЧасть III\n\nКонец."
        val book = TxtBook.parse(text, "Книга")
        assertEquals(listOf("Пролог", "Глава 1. Дорога", "Глава 2", "Часть III"), titles(book))
    }

    @Test
    fun chineseHeadingsSplitChapters() {
        val text = "第一章 开始\n\n很久以前。\n\n第12章 继续\n\n后来。\n\n第三百回\n\n结束。"
        val book = TxtBook.parse(text, "书")
        assertEquals(listOf("第一章 开始", "第12章 继续", "第三百回"), titles(book))
    }

    @Test
    fun textBeforeTheFirstHeadingBecomesAChapterNamedAfterTheBook() {
        val book = TxtBook.parse("A Novel\nby Someone\n\nChapter 1\n\nText.", "My Book")
        assertEquals(listOf("My Book", "Chapter 1"), titles(book))
    }

    @Test
    fun headingWordsInsideSentencesAreNotHeadings() {
        assertFalse(TxtBook.isHeading("He read chapter 3 twice before sleeping."))
        assertFalse(TxtBook.isHeading("Part of the plan was simple."))
        assertFalse(TxtBook.isHeading("Part one of the plan"))
        assertFalse(TxtBook.isHeading("Chapter"))
        assertTrue(TxtBook.isHeading("  Chapter 12  "))
        assertTrue(TxtBook.isHeading("CHAPTER XII"))
        assertTrue(TxtBook.isHeading("Part 2 - The Road"))
        assertFalse(TxtBook.isHeading("Chapter " + "x".repeat(100)))
    }

    @Test
    fun noHeadingsFallsBackToPartsOnParagraphBreaks() {
        val paragraph = "word ".repeat(400).trim()
        val text = (1..25).joinToString("\n\n") { paragraph }
        val book = TxtBook.parse(text, "Plain")
        assertEquals(listOf("Part 1", "Part 2", "Part 3"), titles(book))
        assertEquals(25, book.chapters.sumOf { it.paragraphs.size })
        assertTrue(book.chapters.dropLast(1).all { chapter -> chapter.paragraphs.sumOf { it.length } >= TxtBook.PART_SIZE })
    }

    @Test
    fun singleNewlinesAreParagraphsWhenTheFileHasNoBlankLines() {
        val book = TxtBook.parse("Chapter 1\nFirst line.\nSecond line.\nChapter 2\nThird.", "B")
        assertEquals(listOf("First line.", "Second line."), book.chapters[0].paragraphs)
        assertEquals(listOf("Third."), book.chapters[1].paragraphs)
    }

    @Test
    fun wrappedLinesJoinWhenBlankLinesSeparateParagraphs() {
        val book = TxtBook.parse("Chapter 1\n\nline one\nline two\n\nnext paragraph", "B")
        assertEquals(listOf("line one line two", "next paragraph"), book.chapters.single().paragraphs)
    }

    @Test
    fun utf8WithBomIsDecodedWithoutTheBom() {
        val bytes = byteArrayOf(0xEF.toByte(), 0xBB.toByte(), 0xBF.toByte()) + "Привет, мир".toByteArray(Charsets.UTF_8)
        assertEquals("Привет, мир", TxtBook.decode(bytes))
    }

    @Test
    fun plainUtf8IsDecoded() {
        assertEquals("Café 日本", TxtBook.decode("Café 日本".toByteArray(Charsets.UTF_8)))
    }

    @Test
    fun windows1251RussianIsDecoded() {
        val text = "Глава 1\n\nПривет, это русский текст в старой кодировке."
        assertEquals(text, TxtBook.decode(text.toByteArray(charset("windows-1251"))))
    }

    @Test
    fun otherSingleByteTextFallsBackToLatin1() {
        val text = "Café crème and über"
        assertEquals(text, TxtBook.decode(text.toByteArray(Charsets.ISO_8859_1)))
    }

    @Test
    fun chapterHtmlWrapsAndEscapesParagraphs() {
        val chapter = TxtChapter("C", listOf("a < b & \"c\"", "second"))
        assertEquals("<p>a &lt; b &amp; &quot;c&quot;</p>\n<p>second</p>", chapter.html())
    }
}
