package com.lume.app.data.source

import com.lume.app.data.library.planLibBookmarkImport
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibBookmarksTest {
    @Test
    fun foldersAreRead() {
        val raw = """{"data":[{"id":1,"name":"Читаю"},{"id":2,"name":"В планах"},{"id":3,"name":" "},{"name":"no id"}]}"""
        assertEquals(
            listOf(LibBookmarkFolder("1", "Читаю"), LibBookmarkFolder("2", "В планах")),
            parseLibBookmarkFolders(raw),
        )
    }

    @Test
    fun brokenAnswerGivesNoFolders() {
        assertEquals(emptyList<LibBookmarkFolder>(), parseLibBookmarkFolders("<html>403</html>"))
    }

    @Test
    fun bookmarkUnderMediaIsRead() {
        val raw = """
            {"data":[
              {"id":10,"status":1,"media":{"slug_url":"7580--solo-leveling","rus_name":"Поднятие уровня","name":"Na Honjaman",
               "site":1,"cover":{"default":"https://cover/d.jpg","thumbnail":"https://cover/t.jpg"},"type":{"label":"Манхва"}}}
            ],"meta":{"has_next_page":false}}
        """.trimIndent()
        val page = parseLibBookmarksPage(raw, siteId = "1", folderId = "9")
        assertFalse(page.hasNext)
        val item = page.items.single()
        assertEquals("/7580--solo-leveling", item.titleId)
        assertEquals("Поднятие уровня", item.title)
        assertEquals("https://cover/d.jpg", item.coverUrl)
        assertEquals("Манхва", item.typeLabel)
        assertEquals("1", item.folderId)
    }

    @Test
    fun otherSitesAreSkippedAndQueriedFolderIsUsed() {
        val raw = """
            {"data":[
              {"media":{"slug_url":"1--a","name":"A","site":"1"}},
              {"media":{"slug_url":"2--b","name":"B","site":"4"}},
              {"slug_url":"3--c","eng_name":"C"}
            ]}
        """.trimIndent()
        val items = parseLibBookmarksPage(raw, siteId = "1", folderId = "5").items
        assertEquals(listOf("/1--a", "/3--c"), items.map { it.titleId })
        assertEquals(listOf("5", "5"), items.map { it.folderId })
        assertEquals("C", items[1].title)
        assertNull(items[1].coverUrl)
    }

    @Test
    fun pagingFollowsMetaOrLinks() {
        val one = """{"data":[{"media":{"slug_url":"1--a"}}]"""
        assertTrue(parseLibBookmarksPage("$one,\"meta\":{\"has_next_page\":true}}", "1", null).hasNext)
        assertTrue(parseLibBookmarksPage("$one,\"links\":{\"next\":\"https://x?page=2\"}}", "1", null).hasNext)
        assertFalse(parseLibBookmarksPage("$one,\"links\":{\"next\":null}}", "1", null).hasNext)
        assertFalse(parseLibBookmarksPage("$one,\"meta\":{\"current_page\":2,\"last_page\":2}}", "1", null).hasNext)
        // An empty page always ends the folder.
        assertFalse(parseLibBookmarksPage("""{"data":[],"meta":{"has_next_page":true}}""", "1", null).hasNext)
    }

    @Test
    fun planGroupsFoldersPerTitleAndDropsEmptyFolders() {
        val folders = listOf(LibBookmarkFolder("1", "Читаю"), LibBookmarkFolder("2", "Любимое"), LibBookmarkFolder("3", "Брошено"))
        val bookmarks = listOf(
            LibBookmark("/1--a", "A", null, null, "1"),
            LibBookmark("/1--a", "A", "https://c", "Манга", "2"),
            LibBookmark("/2--b", "B", null, null, "1"),
        )
        val plan = planLibBookmarkImport(folders, bookmarks)
        assertEquals(listOf("1", "2"), plan.folders.map { it.id })
        val a = plan.titles.first { it.titleId == "/1--a" }
        assertEquals(setOf("1", "2"), a.folderIds)
        assertEquals("https://c", a.coverUrl)
        assertEquals("Манга", a.typeLabel)
        assertEquals(2, plan.titles.size)
    }
}
