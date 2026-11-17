package com.lume.app.ui.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lume.app.AppContainer
import com.lume.app.domain.Chapter
import com.lume.app.domain.FilterList
import com.lume.app.domain.NovelChapter
import com.lume.app.domain.NovelSource
import com.lume.app.domain.PagedResult
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import java.io.File

/**
 * Text chapters (novels) in [com.lume.app.data.downloads.DownloadStore]: saved as `chapter.html`
 * with their pictures, read back by the reader, found again after the database lost its rows.
 */
@RunWith(AndroidJUnit4::class)
@Config(instrumentedPackages = [VIEW_MODEL_SANDBOX])
class NovelDownloadTest {
    private val app: AppContainer = testContainer()
    private val store = app.downloads
    private val manifest = SourceManifest(id = "novel-test", name = "Novel", engine = "novel-test", baseUrl = "https://novel.test", mediaKind = "novel")
    private val title = TitleDetails(id = "/book", title = "Book")
    private val chapter = Chapter(id = "/book/v1/c1", title = "Chapter 1", url = "/book/v1/c1", number = "1")

    private class FakeNovel(override val manifest: SourceManifest, private val html: String) : NovelSource {
        override suspend fun getLatest(page: Int) = PagedResult<TitleSummary>(emptyList(), page)
        override suspend fun getPopular(page: Int) = PagedResult<TitleSummary>(emptyList(), page)
        override suspend fun search(query: String, page: Int, filters: FilterList) = PagedResult<TitleSummary>(emptyList(), page)
        override suspend fun getTitle(id: String): TitleDetails = error("unused")
        override suspend fun getChapters(titleId: String): List<Chapter> = emptyList()
        override suspend fun getChapterText(chapter: Chapter) = NovelChapter(chapter.id, html)
    }

    @Test
    fun textIsSavedAndReadBackAPictureThatFailsKeepsItsLink() = runBlocking {
        // Nothing listens on port 9: the picture fails, the chapter is still saved.
        val picture = "https://127.0.0.1:9/art.png"
        val source = FakeNovel(manifest, "<p>Первый абзац</p><p><img src=\"$picture\"></p><p>Второй</p>")
        store.downloadChapter(source, title.id, title, chapter)

        val row = checkNotNull(app.dao.getDownload(manifest.id, title.id, chapter.id)) { "no download row" }
        assertEquals(0, row.pageCount)
        val text = checkNotNull(store.localText(manifest.id, title.id, chapter.id)) { "no local text" }
        assertEquals("<p>Первый абзац</p><p><img src=\"$picture\"></p><p>Второй</p>", text.html)
        assertTrue(store.bytesOf(row) > 0)

        store.delete(manifest.id, title.id, chapter.id)
        assertNull(store.localText(manifest.id, title.id, chapter.id))
    }

    @Test
    fun aChapterFolderOnDiskIsFoundAgainWithItsPictures() = runBlocking {
        val dir = File(store.downloadsDir(), "${store.safe(manifest.id)}/${store.safe(title.id)}/${store.safe(chapter.id)}").apply {
            deleteRecursively()
            mkdirs()
        }
        File(dir, "chapter.html").writeText("<p>Текст</p><p><img src=\"img-0000.png\"></p><img src=\"img-0001.png\">")
        File(dir, "img-0000.png").writeBytes(PNG)
        File(dir, "img-0001.png").writeBytes(PNG)
        File(dir, "meta.json").writeText("""{"chapterId":"${chapter.id}","chapterName":"Chapter 1"}""")
        File(dir.parentFile, "meta.json").writeText("""{"sourceId":"${manifest.id}","titleId":"${title.id}","titleName":"Book"}""")

        assertTrue(store.recoverFromDisk() >= 1)
        val row = checkNotNull(app.dao.getDownload(manifest.id, title.id, chapter.id)) { "not recovered" }
        assertEquals(2, row.pageCount)
        val html = store.localText(manifest.id, title.id, chapter.id)!!.html
        assertEquals(
            "<p>Текст</p><p><img src=\"${File(dir, "img-0000.png").absolutePath}\"></p><img src=\"${File(dir, "img-0001.png").absolutePath}\">",
            html,
        )
        store.delete(manifest.id, title.id, chapter.id)
        assertTrue(!dir.exists())
    }

    private companion object {
        /** The PNG signature and a header: enough for the image checks. */
        val PNG = byteArrayOf(
            0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A,
            0, 0, 0, 13, 0x49, 0x48, 0x44, 0x52, 0, 0, 0, 1, 0, 0, 0, 1, 8, 6, 0, 0, 0,
        )
    }
}
