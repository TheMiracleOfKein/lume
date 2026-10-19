package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertThrows
import org.junit.Test

class ReadingListTest {
    private val list = ReadingList(
        name = "Weekend",
        createdAt = 1_700_000_000_000,
        titles = listOf(
            ReadingListEntry("mangadex", "a1", "Solo Leveling", url = "https://mangadex.org/title/a1"),
            ReadingListEntry("mangalib", "b2", "Omniscient Reader", url = "https://mangalib.me/ru/manga/b2"),
            ReadingListEntry("gone", "c3", "Tower of God", url = "https://gone.example/c3"),
            ReadingListEntry("gone", "d4", "No link here"),
        ),
    )

    @Test
    fun roundTrips() {
        assertEquals(list, parseReadingList(encodeReadingList(list)))
    }

    @Test
    fun otherJsonIsRejected() {
        assertThrows(NotAReadingListException::class.java) { parseReadingList("""{"version": 2, "bookmarks": []}""") }
        assertThrows(NotAReadingListException::class.java) { parseReadingList("""{"format": "x", "name": "n", "createdAt": 1, "titles": []}""") }
        assertThrows(NotAReadingListException::class.java) { parseReadingList("not json") }
    }

    @Test
    fun newerVersionsKeepWorkingAndBadEntriesAreDropped() {
        val raw = """
            {"format": "lume-reading-list", "version": 7, "name": "Later", "createdAt": 5, "colour": "red",
             "titles": [
               {"sourceId": "s", "titleId": "1", "title": "One", "rating": 5},
               {"sourceId": "s", "titleId": "1", "title": "One again"},
               {"sourceId": "", "titleId": "2", "title": "No source"},
               {"sourceId": "s", "titleId": "3", "title": " "}
             ]}
        """.trimIndent()
        val parsed = parseReadingList(raw)
        assertEquals(7, parsed.version)
        assertEquals(listOf("One"), parsed.titles.map { it.title })
    }

    @Test
    fun planSortsEntries() {
        val rows = planReadingList(
            list,
            inLibrary = { source, title -> source == "mangadex" && title == "a1" || source == "mangalib" && title == "found" },
            hasSource = { it == "mangadex" || it == "mangalib" },
            resolveLink = { url -> if (url.startsWith("https://gone.example/")) "mangalib" to "found" else null },
        )
        assertEquals(
            listOf(ReadingListState.InLibrary, ReadingListState.New, ReadingListState.InLibrary, ReadingListState.NoSource),
            rows.map { it.state },
        )
        // Found through its link: added under the source that has it here.
        assertEquals("mangalib" to "found", rows[2].sourceId to rows[2].titleId)
    }

    @Test
    fun fileNames() {
        assertEquals("lume-list-weekend-2026-10-20.json", readingListFileName("Weekend", "2026-10-20"))
        assertEquals("lume-list-читаю-сейчас-2026-10-20.json", readingListFileName("Читаю сейчас!", "2026-10-20"))
        assertEquals("lume-list-café-2026-10-20.json", readingListFileName("  Café ", "2026-10-20"))
        assertEquals("lume-list-2026-10-20.json", readingListFileName("***", "2026-10-20"))
    }
}
