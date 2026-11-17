package com.lume.app.data.source

import com.lume.app.domain.Chapter
import com.lume.app.domain.ChapterLabels
import com.lume.app.domain.MediaKind
import org.junit.After
import org.junit.Assert.assertEquals
import org.junit.Before
import org.junit.Test

class LocalizedChaptersTest {
    private val chapters = listOf(Chapter(id = "1", title = "Глава 1", url = "1"), Chapter(id = "2", title = "Пролог", url = "2"))
    private lateinit var saved: (String) -> String

    @Before
    fun setUp() {
        saved = ChapterLabels.chapter
        ChapterLabels.chapter = { "Ch. $it" }
        ChapterLabels.enabled = true
    }

    @After
    fun tearDown() {
        ChapterLabels.chapter = saved
    }

    @Test
    fun comicsLabelsFollowTheAppLanguage() {
        assertEquals(listOf("Ch. 1", "Пролог"), localizedChapters(MediaKind.Comics, chapters).map { it.title })
    }

    @Test
    fun novelHeadingsStayAsWritten() {
        assertEquals(listOf("Глава 1", "Пролог"), localizedChapters(MediaKind.Novel, chapters).map { it.title })
    }
}
