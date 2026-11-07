package com.lume.app.data.source

import com.lume.app.domain.parseComicInfo
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** ComicInfo.xml reading used by the local source for series details. */
class LocalEngineParsingTest {
    @Test
    fun readsSeriesFieldsFromComicInfo() {
        val xml = """
            <?xml version="1.0" encoding="utf-8"?>
            <ComicInfo xmlns:xsi="http://www.w3.org/2001/XMLSchema-instance">
              <Series>Berserk &amp; Friends</Series>
              <Title>The Black Swordsman</Title>
              <Number>1</Number>
              <Volume>2</Volume>
              <Summary>Guts &lt;the swordsman&gt; walks alone.</Summary>
              <Writer>Kentaro Miura, Studio Gaga</Writer>
              <Penciller>Kentaro Miura</Penciller>
              <Genre>Action, Dark Fantasy</Genre>
              <PageCount>42</PageCount>
              <Web>https://example.org/berserk</Web>
              <ty:PublishingStatusTachiyomi>Ongoing</ty:PublishingStatusTachiyomi>
            </ComicInfo>
        """.trimIndent()
        val info = parseComicInfo(xml)!!
        assertEquals("Berserk & Friends", info.series)
        assertEquals("The Black Swordsman", info.title)
        assertEquals("1", info.number)
        assertEquals("2", info.volume)
        assertEquals("Guts <the swordsman> walks alone.", info.summary)
        assertEquals(listOf("Kentaro Miura", "Studio Gaga"), info.writer)
        assertEquals(listOf("Kentaro Miura"), info.penciller)
        assertEquals(listOf("Action", "Dark Fantasy"), info.genre)
        assertEquals(42, info.pageCount)
        assertEquals("https://example.org/berserk", info.web)
        assertEquals("Ongoing", info.status)
    }

    @Test
    fun seriesFallsBackToTitle() {
        val info = parseComicInfo("<ComicInfo><Title>Solo</Title><Writer></Writer></ComicInfo>")!!
        assertEquals("Solo", info.series)
        assertEquals(emptyList<String>(), info.writer)
        assertNull(info.pageCount)
    }

    @Test
    fun withoutSeriesOrTitleIsNull() {
        assertNull(parseComicInfo("<ComicInfo><Number>3</Number></ComicInfo>"))
        assertNull(parseComicInfo("not xml at all"))
    }
}
