package com.lume.app.data.source

import com.lume.app.data.network.ImageReferers
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.Chapter
import com.lume.app.domain.NovelChapter
import com.lume.app.domain.NovelSource
import com.lume.app.domain.PagedResult
import com.lume.app.domain.SourceFetchException
import com.lume.app.domain.SourceManifest
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import com.lume.app.domain.requireNovel
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

class NovelForwardingTest {
    private val manifest = SourceManifest(id = "n", name = "N", engine = "n", baseUrl = "https://n.test", mediaKind = "novel")

    private class FakeNovel(override val manifest: SourceManifest) : NovelSource {
        override suspend fun getLatest(page: Int) = PagedResult<TitleSummary>(emptyList(), page)
        override suspend fun getPopular(page: Int) = PagedResult<TitleSummary>(emptyList(), page)
        override suspend fun search(query: String, page: Int, filters: com.lume.app.domain.FilterList) =
            PagedResult<TitleSummary>(emptyList(), page)
        override suspend fun getTitle(id: String): TitleDetails = error("unused")
        override suspend fun getChapters(titleId: String): List<Chapter> = emptyList()
        override suspend fun getChapterText(chapter: Chapter) = NovelChapter(chapter.id, "<p>${chapter.title}</p>")
    }

    private val chapter = Chapter(id = "c1", title = "One", url = "c1")

    @Test
    fun dispatchingWrapperForwardsText() = runBlocking {
        val wrapped: CatalogSource = DispatchingSource(FakeNovel(manifest), SourceHealth())
        assertEquals("<p>One</p>", wrapped.requireNovel().getChapterText(chapter).html)
    }

    @Test
    fun trackingWrapperForwardsText() = runBlocking {
        val wrapped: CatalogSource = ImageHostTrackingSource(FakeNovel(manifest), ImageReferers())
        assertEquals("c1", wrapped.requireNovel().getChapterText(chapter).chapterId)
    }

    @Test
    fun stackedWrappersStillUnwrapToTheEngine() {
        val engine = FakeNovel(manifest)
        val wrapped = DispatchingSource(ImageHostTrackingSource(engine, ImageReferers()), SourceHealth())
        assertSame(engine, wrapped.unwrapped())
        assertTrue(wrapped.unwrapped() is NovelSource)
    }

    @Test
    fun requireNovelFailsForPlainSources() {
        val plain = object : CatalogSource by FakeNovel(manifest) {}
        try {
            plain.requireNovel()
            fail("expected an error")
        } catch (e: SourceFetchException) {
            assertTrue(e.message!!.contains("n"))
        }
    }
}
