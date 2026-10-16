package com.lume.app.data.backup

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackupPayloadTest {
    @Test
    fun versionOneFileStillParses() {
        val v1 = """
            {
              "version": 1,
              "createdAt": 1700000000000,
              "bookmarks": [{"sourceId": "s", "titleId": "t", "titleName": "Title"}],
              "progress": [{
                "sourceId": "s", "titleId": "t", "titleName": "Title",
                "chapterId": "c1", "chapterName": "Ch 1", "chapterUrl": "/c1",
                "pageIndex": 4, "pageCount": 20, "updatedAt": 5
              }]
            }
        """.trimIndent()
        val payload = BackupPayload.parse(v1)
        assertEquals(1, payload.version)
        assertEquals(1, payload.bookmarks.size)
        assertEquals(4, payload.progress.single().pageIndex)
        assertEquals(0f, payload.progress.single().pageOffset, 0f)
        assertTrue(payload.chapterProgress.isEmpty())
        assertTrue(payload.pageBookmarks.isEmpty())
        assertTrue(payload.chapterRead.isEmpty())
    }

    @Test
    fun versionTwoRoundTrips() {
        val payload = BackupPayload(
            createdAt = 1,
            progress = listOf(
                ProgressEntityDto(
                    sourceId = "s", titleId = "t", titleName = "Title", chapterId = "c2",
                    chapterName = "Ch 2", chapterUrl = "/c2", pageIndex = 3, pageCount = 9,
                    updatedAt = 7, pageOffset = 0.42f,
                ),
            ),
            chapterProgress = listOf(
                ChapterProgressDto("s", "t", "c2", pageIndex = 3, pageOffset = 0.42f, pageCount = 9, pageAspectsJson = "[0.5000]", updatedAt = 7),
                ChapterProgressDto("s", "t", "c1", pageIndex = 8, pageOffset = 1f, pageCount = 9, completed = true, updatedAt = 6),
            ),
            pageBookmarks = listOf(
                PageBookmarkDto("s", "t", "c2", "Ch 2", pageIndex = 3, pageOffset = 0.42f, note = "fight", createdAt = 8),
            ),
            chapterRead = listOf(ChapterReadDto("s", "t", "c1", readAt = 6)),
        )
        val text = BackupPayload.json.encodeToString(BackupPayload.serializer(), payload)
        val back = BackupPayload.parse(text)
        assertEquals(BackupPayload.CURRENT_VERSION, back.version)
        assertEquals(payload, back)
    }

    @Test
    fun unknownFieldsFromNewerVersionsAreIgnored() {
        val future = """{"version": 9, "somethingNew": [1, 2, 3], "chapterRead": [{"sourceId":"s","titleId":"t","chapterId":"c"}]}"""
        val payload = BackupPayload.parse(future)
        assertEquals(9, payload.version)
        assertEquals("c", payload.chapterRead.single().chapterId)
    }
}
