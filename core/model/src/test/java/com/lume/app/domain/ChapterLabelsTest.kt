package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ChapterLabelsTest {
    private fun en(label: String) = ChapterLabels.translate(label, { "Vol. $it" }, { "Ch. $it" })

    @Test
    fun translatesVolumeAndChapterWords() {
        assertEquals("Vol. 2 Ch. 15 - Начало", en("Том 2 Глава 15 - Начало"))
        assertEquals("Ch. 10.5", en("Глава 10.5"))
        assertEquals("Vol. 1 Ch. 3", en("Т. 1 Гл. 3"))
    }

    @Test
    fun leavesOtherTextAlone() {
        assertEquals("Chapter 3", en("Chapter 3"))
        // "Глава" inside a word or without a number is a title, not a label.
        assertEquals("Заглавие", en("Заглавие"))
        assertEquals("Глава без номера", en("Глава без номера"))
    }
}
