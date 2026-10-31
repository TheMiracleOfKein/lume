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
    fun allStatusesAnswerKeepsEachEntrysOwnFolder() {
        // Shape of a real `status=0` answer: built-in and custom statuses side by side.
        val raw = """
            {"data":[
              {"id":727387848,"type":"manga_bookmark","item_id":1490455,"status":1,"meta":null,
               "media":{"id":71979,"rus_name":"Насколько же богат этот граф?!","slug_url":"71979--yeongjunim-ui-koin-i-julji-anh-eum",
                "cover":{"default":"https://cover/a.jpg"},"site":1,"type":{"id":5,"label":"Манхва"}},"item":null},
              {"id":1298207171,"type":"manga_bookmark","item_id":0,"status":2598197,
               "meta":{"comment":false,"rewatches":null,"item_number":null},
               "media":{"id":25786,"name":"yong-wangnim-ui syepeuga","slug_url":"25786--i-became-the-chef-of-the-dragon-king","site":1}}
            ],
            "links":{"first":"http://api/bookmarks?page=1","last":null,"prev":null,"next":"http://api/bookmarks?page=2"},
            "meta":{"current_page":1,"from":1,"per_page":[24,24],"to":24,"page":1,"next_page_url":true}}
        """.trimIndent()
        val page = parseLibBookmarksPage(raw, siteId = "1", folderId = null)
        assertTrue(page.hasNext)
        assertEquals(listOf("1", "2598197"), page.items.map { it.folderId })
        assertEquals("/25786--i-became-the-chef-of-the-dragon-king", page.items[1].titleId)
    }

    @Test
    fun lastReadChapterIsReadFromItem() {
        // Shapes from a real `status=0` answer: a read chapter, a team-less one, and none at all.
        val raw = """
            {"data":[
              {"status":1,"progress":"3","updated_at":"2022-11-11T00:16:11.000000Z",
               "media":{"slug_url":"71979--a","site":1},
               "item":{"id":1490455,"model":"chapter","volume":"1","number":"16","branch_id":5427,"type":"chapter"}},
              {"status":4,"created_at":"2019-11-22 13:54:32",
               "media":{"slug_url":"18743--b","site":1},
               "item":{"id":414497,"model":"chapter","volume":"10","number":"3.5","branch_id":null}},
              {"status":2598197,"media":{"slug_url":"25786--c","site":1},"item":null}
            ]}
        """.trimIndent()
        val items = parseLibBookmarksPage(raw, "1", null).items
        assertEquals(LibReadChapter("1", "16", "5427", 1668125771000L), items[0].lastRead)
        assertEquals(LibReadChapter("10", "3.5", null, 1574430872000L), items[1].lastRead)
        assertNull(items[2].lastRead)
    }

    @Test
    fun readUpToCoversEarlierChaptersOfEveryTeam() {
        fun ch(v: String, n: String, team: String?) =
            com.lume.app.domain.Chapter(id = "/s/v$v/c$n${team?.let { "?bid=$it" }.orEmpty()}", title = "$v-$n", url = "", number = n, volume = v, branchId = team)
        // Newest first, as getChapters returns them; chapter 2 has two teams.
        val chapters = listOf(ch("2", "1", null), ch("1", "3", null), ch("1", "2", "7"), ch("1", "2", "8"), ch("1", "1", null))
        val target = com.lume.app.data.library.libChaptersReadUpTo(chapters, LibReadChapter("1", "2", "8"))!!
        assertEquals("/s/v1/c2?bid=8", target.last.id)
        assertEquals(setOf("/s/v1/c2?bid=7", "/s/v1/c2?bid=8", "/s/v1/c1"), target.read.map { it.id }.toSet())
        // "2.0" matches "2"; a volume the list doesn't know still finds a number that occurs once.
        assertEquals("/s/v1/c3", com.lume.app.data.library.libChaptersReadUpTo(chapters, LibReadChapter("9", "3.0", null))!!.last.id)
        // Number 1 is in two volumes: with the wrong volume nothing is guessed.
        assertNull(com.lume.app.data.library.libChaptersReadUpTo(chapters, LibReadChapter("9", "1", null)))
        assertNull(com.lume.app.data.library.libChaptersReadUpTo(emptyList(), LibReadChapter("1", "1", null)))
    }

    @Test
    fun lastPageFlagEndsPaging() {
        val raw = """{"data":[{"media":{"slug_url":"1--a"}}],"links":{"next":null},"meta":{"next_page_url":false}}"""
        assertFalse(parseLibBookmarksPage(raw, "1", null).hasNext)
    }

    @Test
    fun builtInFoldersAreNamedWhenTheSiteListsNone() {
        val site = listOf(LibBookmarkFolder("2598197", "Манхвы"))
        val folders = withDefaultLibFolders(site, setOf("1", "4", "2598197", "777"))
        assertEquals(listOf("2598197", "1", "4"), folders.map { it.id })
        assertEquals("Читаю", folders[1].name)
        // A site name wins over the built-in one.
        assertEquals(listOf("Reading"), withDefaultLibFolders(listOf(LibBookmarkFolder("1", "Reading")), setOf("1")).map { it.name })
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
