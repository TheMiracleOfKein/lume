package com.lume.app.data.backup

import kotlinx.serialization.descriptors.SerialDescriptor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Keeps the Lume backup format stable (docs/BACKUP_FORMAT.md).
 *
 * - Every saved backup in `src/test/resources/backups` (one per format version) must keep loading
 *   with the values it holds. Never edit those files; add `lume-vN.json` for a new version.
 * - [FORMAT] pins every field name and whether it may be left out. A change here means old files
 *   or older Lume versions read backups differently: update BACKUP_FORMAT.md in the same change,
 *   and only ever add optional fields.
 */
class BackupFormatTest {
    private fun load(version: Int): BackupPayload {
        val text = javaClass.classLoader!!.getResource("backups/lume-v$version.json")!!.readText()
        return BackupPayload.parse(text)
    }

    @Test
    fun `every saved version keeps loading`() {
        for (v in 1..BackupPayload.CURRENT_VERSION) {
            val payload = load(v)
            assertEquals("version field of lume-v$v.json", v, payload.version)
            assertTrue("lume-v$v.json has its library", payload.bookmarks.isNotEmpty())
        }
    }

    @Test
    fun `version 1 keeps library, progress, categories, packs, title settings and tracker links`() {
        val p = load(1)
        val b = p.bookmarks.single()
        assertEquals("First Title", b.titleName)
        assertEquals("[1]", b.categoryIdsJson)
        assertEquals(4, p.progress.single().pageIndex)
        assertEquals(0f, p.progress.single().pageOffset, 0f)
        assertEquals("Reading", p.categories.single().name)
        assertEquals("pack.example", p.sourcePacks.single().id)
        val prefs = p.titlePrefs.single()
        assertEquals(2, prefs.readerMode)
        assertEquals(false, prefs.autoDownload)
        assertEquals(8.5f, p.trackerLinks.single().score!!, 0f)
        assertTrue(p.chapterProgress.isEmpty() && p.settings.isEmpty() && p.titleSources.isEmpty() && p.readingSessions.isEmpty())
    }

    @Test
    fun `version 2 adds chapter progress, page bookmarks and read marks`() {
        val p = load(2)
        assertEquals(2, p.chapterProgress.size)
        assertTrue(p.chapterProgress.first { it.chapterId == "c2" }.completed)
        assertEquals("[]", p.chapterProgress.first { it.chapterId == "c2" }.pageAspectsJson)
        assertEquals("the fight", p.pageBookmarks.single().note)
        assertNull(p.pageBookmarks.single().pageImageUrl)
        assertEquals(listOf("c1", "c2"), p.chapterRead.map { it.chapterId })
    }

    @Test
    fun `version 3 adds settings`() {
        val p = load(3)
        assertEquals(listOf("int", "bool", "set"), p.settings.map { it.type })
        assertTrue(p.progress.isEmpty())
    }

    @Test
    fun `version 4 adds other sources of a title`() {
        val p = load(4)
        val link = p.titleSources.single()
        assertEquals("mangakatana", link.linkSourceId)
        assertEquals("linked", link.state)
        assertEquals("team-a", p.titlePrefs.single().branch)
    }

    @Test
    fun `version 5 adds reading sessions`() {
        val p = load(5)
        val s = p.readingSessions.single()
        assertEquals(600_000L, s.durationMs)
        assertEquals(12, s.pagesRead)
        assertNotNull(p.pageBookmarks.single().pageImageUrl)
    }

    @Test
    fun `the newest saved file round-trips unchanged`() {
        val p = load(BackupPayload.CURRENT_VERSION)
        val again = BackupPayload.parse(BackupPayload.json.encodeToString(BackupPayload.serializer(), p))
        assertEquals(p, again)
    }

    @Test
    fun `a written file always names its version and date`() {
        val text = BackupPayload.json.encodeToString(BackupPayload.serializer(), BackupPayload(createdAt = 1234))
        assertTrue(text, text.contains("\"version\": ${BackupPayload.CURRENT_VERSION}"))
        assertTrue(text, text.contains("\"createdAt\": 1234"))
    }

    @Test
    fun `field names and optional fields are pinned`() {
        val actual = listOf(
            BackupPayload.serializer().descriptor,
            BookmarkEntityDto.serializer().descriptor,
            ProgressEntityDto.serializer().descriptor,
            CategoryEntityDto.serializer().descriptor,
            SourcePackDto.serializer().descriptor,
            TitlePrefsDto.serializer().descriptor,
            TrackerLinkDto.serializer().descriptor,
            ChapterProgressDto.serializer().descriptor,
            PageBookmarkDto.serializer().descriptor,
            ChapterReadDto.serializer().descriptor,
            SettingDto.serializer().descriptor,
            TitleSourceDto.serializer().descriptor,
            ReadingSessionDto.serializer().descriptor,
        ).joinToString("\n") { describe(it) }
        assertEquals(FORMAT, actual)
    }

    /** `Name: required, optional?` with the class name only, so a package move doesn't count. */
    private fun describe(d: SerialDescriptor): String {
        val fields = (0 until d.elementsCount).joinToString(", ") { i ->
            d.getElementName(i) + if (d.isElementOptional(i)) "?" else ""
        }
        return d.serialName.substringAfterLast('.') + ": " + fields
    }

    private companion object {
        val FORMAT = """
            BackupPayload: version?, createdAt?, bookmarks?, progress?, categories?, sourcePacks?, titlePrefs?, trackerLinks?, chapterProgress?, pageBookmarks?, chapterRead?, settings?, titleSources?, readingSessions?
            BookmarkEntityDto: sourceId, titleId, titleName, coverUrl?, typeLabel?, createdAt?, categoryIdsJson?, genresJson?
            ProgressEntityDto: sourceId, titleId, titleName, coverUrl?, typeLabel?, chapterId, chapterName, chapterUrl, pageIndex?, pageCount?, updatedAt?, pageOffset?
            CategoryEntityDto: id?, name, sortOrder?
            SourcePackDto: id, json, installedAt?
            TitlePrefsDto: sourceId, titleId, readerMode?, readerTheme?, gap?, chapterFilter?, chapterSortAsc?, branch?, showDuplicates?, autoDownload?
            TrackerLinkDto: sourceId, titleId, tracker, remoteId, remoteUrl?, status?, score?, lastSync?
            ChapterProgressDto: sourceId, titleId, chapterId, pageIndex?, pageOffset?, pageCount?, completed?, pageAspectsJson?, updatedAt?
            PageBookmarkDto: sourceId, titleId, chapterId, chapterName, pageIndex, pageOffset?, pageImageUrl?, note?, createdAt?
            ChapterReadDto: sourceId, titleId, chapterId, readAt?
            SettingDto: key, type, value
            TitleSourceDto: sourceId, titleId, linkSourceId, linkTitleId, linkTitleName, coverUrl?, state?, priority?, addedAt?
            ReadingSessionDto: sourceId, titleId, titleName?, chapterId, startedAt, durationMs?, pagesRead?
        """.trimIndent()
    }
}
