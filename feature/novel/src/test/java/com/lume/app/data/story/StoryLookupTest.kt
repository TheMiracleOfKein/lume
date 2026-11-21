package com.lume.app.data.story

import com.lume.app.data.db.ChapterMention
import com.lume.app.data.db.CodexAliasEntity
import com.lume.app.data.db.CodexEntryEntity
import com.lume.app.data.db.CodexEntryWithAliases
import com.lume.app.data.db.GlossaryTermEntity
import com.lume.app.data.novel.NovelBlock
import com.lume.app.data.novel.Span
import com.lume.app.ui.screens.NovelAnchor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class StoryLookupTest {
    private fun pick(text: String, at: String, known: List<String> = emptyList(), nth: Int = 0): String? {
        val offset = generateSequence(text.indexOf(at)) { text.indexOf(at, it + 1).takeIf { i -> i >= 0 } }.drop(nth).first()
        return termAt(text, offset, known)?.let { text.substring(it.first, it.last + 1) }
    }

    @Test
    fun capitalizedRunIsOneTerm() {
        assertEquals("Lin Feng", pick("Yesterday, said Lin Feng quietly.", "Feng"))
        assertEquals("Lin Feng", pick("Yesterday, said Lin Feng quietly.", "Lin"))
    }

    @Test
    fun cyrillicRun() {
        assertEquals("Ли Фэн", pick("вчера Ли Фэн ушёл.", "Фэн"))
    }

    @Test
    fun lowercaseWordAloneEvenNextToCapitalized() {
        assertEquals("said", pick("Lin Feng said Hello", "said"))
    }

    @Test
    fun hyphenAndApostropheStayInsideWords() {
        assertEquals("Lin-Feng", pick("then Lin-Feng left", "Feng"))
        assertEquals("O'Neil", pick("it was O'Neil here", "Neil"))
    }

    @Test
    fun punctuationAndSpaceGiveNothing() {
        assertNull(termAt("Hello, world", 5, emptyList()))
        assertNull(termAt("Hello, world", 6, emptyList()))
        assertNull(termAt("Hello", 9, emptyList()))
        assertNull(termAt("", 0, emptyList()))
    }

    @Test
    fun knownTermBeatsGuessingAndLongestWins() {
        val known = listOf("Feng", "lin feng the elder")
        assertEquals("Lin Feng The Elder", pick("saw Lin Feng The Elder today", "Feng", known))
        assertEquals("Sword of Dawn", pick("the Sword of Dawn shone", "of", listOf("Sword of Dawn")))
    }

    @Test
    fun knownTermNeedsWordEdges() {
        assertEquals("Lightning", pick("a Lightning strike", "ning", listOf("Light")))
    }

    @Test
    fun cjkIsOneCharacterUnlessKnown() {
        assertEquals("风", pick("林风说话", "风"))
        assertEquals("林风", pick("林风说话", "风", listOf("林风")))
    }

    @Test
    fun ftsQueryQuotesAndDeduplicates() {
        assertEquals("\"Lin Feng\" OR \"lin-feng\"", ftsQuery(listOf("Lin Feng", "LIN FENG", "lin-feng")))
        assertEquals("\"say hi\"", ftsQuery(listOf("say \"hi\"")))
        assertNull(ftsQuery(listOf("...", " ")))
    }

    @Test
    fun targetSearchesSourceAndCodexSearchesAliases() {
        val glossary = listOf(GlossaryTermEntity("s", "t", "林风", "Лин Фэн"))
        val entry = CodexEntryWithAliases(
            CodexEntryEntity(id = 1, sourceId = "s", titleId = "t", name = "Лин Фэн"),
            listOf(CodexAliasEntity(1, "Фэн")),
        )

        val viaGlossary = resolveTerms("лин фэн", glossary, emptyList())
        assertEquals(listOf("лин фэн", "林风"), viaGlossary.searchTerms)
        assertEquals("Лин Фэн", viaGlossary.glossaryTarget)

        val viaCodex = resolveTerms("Фэн", glossary, listOf(entry))
        assertEquals(listOf("Фэн", "Лин Фэн", "林风"), viaCodex.searchTerms)
        assertEquals(entry, viaCodex.codexEntry)
    }

    @Test
    fun knownTermsListsBothSidesAndAliases() {
        val entry = CodexEntryWithAliases(CodexEntryEntity(id = 1, sourceId = "s", titleId = "t", name = "A"), listOf(CodexAliasEntity(1, "B")))
        assertEquals(listOf("src", "tgt", "A", "B"), knownTerms(listOf(GlossaryTermEntity("s", "t", "src", "tgt")), listOf(entry)))
    }

    private val order = listOf("c1", "c2", "c3", "c4")

    @Test
    fun spoilerCutoffAndReadingOrder() {
        val mentions = listOf(
            ChapterMention("c3", 2, 2, 1),
            ChapterMention("c1", 0, 4, 3),
            ChapterMention("c4", 0, 0, 1),
            ChapterMention("zz", 0, 0, 1),
            ChapterMention("c2", 5, 5, 1),
        )

        val seen = visibleMentions(mentions, order, NovelAnchor("c3", 5, 0f))

        assertEquals(listOf("c1", "c2", "c3"), seen.map { it.chapterId })
    }

    @Test
    fun currentChapterNeedsAMentionBeforeTheBlock() {
        val mentions = listOf(ChapterMention("c2", 4, 9, 2))

        assertEquals(emptyList<ChapterMention>(), visibleMentions(mentions, order, NovelAnchor("c2", 4, 0f)))
        assertEquals(1, visibleMentions(mentions, order, NovelAnchor("c2", 5, 0f)).size)
    }

    @Test
    fun anchorOutsideReadingListSeesNothing() {
        assertEquals(emptyList<ChapterMention>(), visibleMentions(listOf(ChapterMention("c1", 0, 0, 1)), order, NovelAnchor("x", 3, 0f)))
    }

    @Test
    fun chapterTextRowsKeepBlockIndices() {
        val blocks = listOf(
            NovelBlock.Heading(1, listOf(Span("Chapter 1"))),
            NovelBlock.Image("a.png"),
            NovelBlock.Paragraph(listOf(Span("Lin "), Span("Feng", bold = true))),
            NovelBlock.Divider,
            NovelBlock.Paragraph(listOf(Span("  "))),
            NovelBlock.Paragraph(listOf(Span("End."))),
        )

        val rows = chapterTextRows("s", "t", "c1", blocks)

        assertEquals(listOf(0 to "Chapter 1", 2 to "Lin Feng", 5 to "End."), rows.map { it.block to it.text })
    }

    private val doc = listOf("p0", "p1", "p2", "p3", "p4", "p5")

    @Test
    fun recapMidChapter() {
        val r = recapBlocks(doc, startBlock = 4, startFraction = 0f, previousTail = listOf("tail"))

        assertEquals(listOf("p0", "p1", "p2", "p3"), r.map { it.text })
        assertEquals(false, r.any { it.fromPreviousChapter })
    }

    @Test
    fun recapIncludesStartBlockOnlyWhenEnteredIt() {
        assertEquals(listOf("p1", "p2", "p3", "p4"), recapBlocks(doc, 4, 0.2f, emptyList()).map { it.text })
    }

    @Test
    fun recapAtChapterStartUsesPreviousTail() {
        val r = recapBlocks(doc, 0, 0f, listOf("t1", "t2", "t3", "t4", "t5"))

        assertEquals(listOf("t2", "t3", "t4", "t5"), r.map { it.text })
        assertEquals(true, r.all { it.fromPreviousChapter })
    }

    @Test
    fun recapShortChapterTopsUpFromPreviousTail() {
        val r = recapBlocks(listOf("a", "", "b", "c"), 3, 0f, listOf("t1", "t2", "t3"))

        assertEquals(listOf("t2", "t3", "a", "b"), r.map { it.text })
        assertEquals(listOf(true, true, false, false), r.map { it.fromPreviousChapter })
    }

    @Test
    fun recapRespectsCharBudget() {
        val big = "x".repeat(500)
        val r = recapBlocks(listOf(big, big, big), 3, 0f, emptyList(), maxChars = 900)

        assertEquals(1, r.size)
        val huge = recapBlocks(listOf("y".repeat(2000)), 1, 0f, emptyList(), maxChars = 900)
        assertEquals(1, huge.size)
        assertEquals(901, huge.single().text.length)
    }

    @Test
    fun recapOfNothingIsEmpty() {
        assertEquals(emptyList<RecapParagraph>(), recapBlocks(emptyList(), 0, 0f, emptyList()))
    }
}
