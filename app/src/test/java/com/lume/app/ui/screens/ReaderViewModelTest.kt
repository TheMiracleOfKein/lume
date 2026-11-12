package com.lume.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lume.app.AppContainer
import com.lume.app.data.db.ChapterProgressEntity
import com.lume.app.data.db.DownloadEntity
import com.lume.app.data.prefs.ReaderSettings
import com.lume.app.domain.Chapter
import com.lume.app.domain.TitleDetails
import kotlinx.coroutines.runBlocking
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config
import org.robolectric.Shadows.shadowOf
import java.io.File
import java.time.Duration

/**
 * [ReaderViewModel] with a title in the offline cache and its chapters "downloaded" to real
 * files, so pages load without any source or network. Position and read marks are checked in
 * the Room database the view model writes to.
 */
@RunWith(AndroidJUnit4::class)
@Config(instrumentedPackages = [VIEW_MODEL_SANDBOX])
class ReaderViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val app: AppContainer = testContainer()
    private val dao = app.dao
    private val sourceId = "s"
    private val titleId = "t"
    private val pagesPerChapter = 3

    @Before
    fun noImageWarming() = runBlocking {
        // Default prefs warm the next pages through Coil; there is no image data here.
        app.prefs.setReaderSettings(ReaderSettings(preloadPages = 0))
        app.prefs.setIncognito(false)
    }

    private fun chapter(n: Int) = Chapter(id = "c$n", title = "Chapter $n", url = "u$n", number = n.toString())

    /** Chapters 1..[count] cached for the title; [downloaded] of them have page files. */
    private fun seedTitle(count: Int = 3, downloaded: Set<Int> = (1..count).toSet()) = runBlocking {
        val details = TitleDetails(id = titleId, title = "Test Title", coverUrl = "cover")
        // Sources list newest first.
        app.offlineCache.save(sourceId, titleId, details, (count downTo 1).map(::chapter))
        val dir = File(context.cacheDir, "reader-test").apply { mkdirs() }
        for (n in downloaded) {
            val paths = (0 until pagesPerChapter).map { i ->
                File(dir, "c$n-$i.jpg").apply { writeBytes(byteArrayOf(1)) }.absolutePath
            }
            dao.upsertDownload(
                DownloadEntity(
                    sourceId, titleId, "c$n", "Test Title",
                    chapterName = "Chapter $n", pageCount = paths.size,
                    pagePathsJson = Json.encodeToString(paths),
                ),
            )
        }
    }

    private fun open(
        chapterId: String,
        page: Int? = null,
        offset: Float? = null,
        state: SavedStateHandle = SavedStateHandle(),
    ): ReaderViewModel = ReaderViewModel(app, sourceId, titleId, chapterId, page, offset, state)

    private fun ReaderViewModel.awaitLoaded() = awaitUntil(what = "reader load") { !loading }

    @Test
    fun loadsTheRequestedChapter() {
        seedTitle()
        val vm = open("c2")
        assertTrue(vm.loading)
        vm.awaitLoaded()

        assertNull(vm.error)
        assertEquals("Test Title", vm.titleName)
        assertEquals("cover", vm.coverUrl)
        assertEquals(listOf("c1", "c2", "c3"), vm.chapters.map { it.id })
        assertEquals("c2", vm.currentChapterId)
        assertEquals("c2", vm.currentChapter?.id)
        assertEquals(pagesPerChapter, vm.currentChapterPageCount)
        assertEquals("c2", vm.loaded.first().chapter.id)
        assertTrue(vm.strip.isNotEmpty())
        assertEquals(1, vm.session)
        assertEquals(0, vm.currentPageIndex)
    }

    @Test
    fun explicitPageFromTheRouteIsUsedAndCoercedIntoTheChapter() {
        seedTitle()
        val vm = open("c2", page = 2, offset = 0.4f)
        vm.awaitLoaded()
        assertEquals(ReaderAnchor("c2", 2, 0.4f), vm.anchor)
        assertEquals(2, vm.currentPageIndex)

        val past = open("c1", page = 99)
        past.awaitLoaded()
        assertEquals(pagesPerChapter - 1, past.currentPageIndex)
    }

    @Test
    fun openingResumesTheChaptersOwnProgressUnlessItWasFinished() {
        seedTitle()
        runBlocking {
            dao.upsertChapterProgress(ChapterProgressEntity(sourceId, titleId, "c1", pageIndex = 1, pageOffset = 0.5f, pageCount = 3))
            dao.upsertChapterProgress(ChapterProgressEntity(sourceId, titleId, "c2", pageIndex = 2, pageOffset = 1f, pageCount = 3, completed = true))
        }
        val resumed = open("c1")
        resumed.awaitLoaded()
        assertEquals(ReaderAnchor("c1", 1, 0.5f), resumed.anchor)

        val finished = open("c2")
        finished.awaitLoaded()
        assertEquals(ReaderAnchor("c2", 0, 0f), finished.anchor)
    }

    @Test
    fun savedStateWinsOverTheRoute() {
        seedTitle()
        val state = SavedStateHandle(
            mapOf("reader.chapterId" to "c3", "reader.pageIndex" to 1, "reader.pageOffset" to 0.25f),
        )
        val vm = open("c1", state = state)
        vm.awaitLoaded()
        assertEquals(ReaderAnchor("c3", 1, 0.25f), vm.anchor)
        assertEquals("c3", vm.currentChapterId)
    }

    @Test
    fun neighboursFollowReadingOrderAndGoChapterReloads() {
        seedTitle()
        val vm = open("c2")
        vm.awaitLoaded()
        assertEquals("c1", vm.neighbours.previous?.id)
        assertEquals("c3", vm.neighbours.next?.id)

        val firstSession = vm.session
        vm.goChapter(+1)
        awaitUntil(what = "next chapter") { vm.currentChapterId == "c3" && !vm.loading && vm.session > firstSession }
        assertEquals("c3", vm.loaded.first().chapter.id)
        assertNull(vm.neighbours.next)
        assertFalse(vm.hasChapterBeyondStrip(forward = true))
        assertTrue(vm.hasChapterBeyondStrip(forward = false))

        // At the end there is nothing further: no reload, no change.
        val session = vm.session
        vm.goChapter(+1)
        settle()
        assertEquals("c3", vm.currentChapterId)
        assertEquals(session, vm.session)

        vm.goChapter(-1)
        awaitUntil(what = "previous chapter") { vm.currentChapterId == "c2" && vm.session > session }
    }

    @Test
    fun unknownChapterShowsAnErrorAndLoadsNothing() {
        seedTitle(count = 2)
        val vm = open("c9")
        vm.awaitLoaded()
        assertNotNull(vm.error)
        assertTrue(vm.loaded.isEmpty())
        // The title itself did load, so the screen can still show its name and chapter list.
        assertEquals("Test Title", vm.titleName)
        assertEquals(2, vm.chapters.size)
    }

    @Test
    fun chapterWithoutPagesOrSourceIsAnError() {
        seedTitle(downloaded = setOf(1))
        val vm = open("c2")
        vm.awaitLoaded()
        assertNotNull(vm.error)
        assertTrue(vm.loaded.isEmpty())
        assertFalse(vm.loading)

        // Retry hits the same wall but never leaves the screen stuck loading.
        vm.retry()
        awaitUntil(what = "retry to finish") { !vm.loading }
        assertNotNull(vm.error)
    }

    @Test
    fun missingTitleIsAnError() {
        val vm = open("c1")
        vm.awaitLoaded()
        assertNotNull(vm.error)
    }

    @Test
    fun positionIsSavedAfterTheDebounce() {
        seedTitle()
        val vm = open("c2")
        vm.awaitLoaded()

        vm.onPosition(vm.session, ReaderAnchor("c2", 1, 0.3f), emptySet(), 0, 0)
        assertEquals(1, vm.currentPageIndex)
        // Nothing is written until scrolling has stopped for 400 ms.
        assertNull(runBlocking { dao.getProgress(sourceId, titleId) })
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        awaitUntil(what = "debounced save") { runBlocking { dao.getProgress(sourceId, titleId) } != null }

        val history = runBlocking { dao.getProgress(sourceId, titleId) }!!
        assertEquals("c2", history.chapterId)
        assertEquals(1, history.pageIndex)
        assertEquals(0.3f, history.pageOffset, 0f)
        assertEquals(pagesPerChapter, history.pageCount)
        assertEquals("Test Title", history.titleName)
        val perChapter = runBlocking { dao.getChapterProgress(sourceId, titleId, "c2") }!!
        assertEquals(1, perChapter.pageIndex)
        assertFalse(perChapter.completed)
    }

    @Test
    fun flushSavesAtOnceAndAStaleSessionIsIgnored() {
        seedTitle()
        val vm = open("c2")
        vm.awaitLoaded()

        vm.onPosition(vm.session + 1, ReaderAnchor("c2", 2, 0f), emptySet(), 0, 0)
        assertEquals(0, vm.currentPageIndex)

        vm.onPosition(vm.session, ReaderAnchor("c2", 2, 0f), emptySet(), 0, 0)
        vm.flush()
        awaitUntil(what = "flushed save") { runBlocking { dao.getProgress(sourceId, titleId) }?.pageIndex == 2 }
    }

    @Test
    fun reachingTheEndMarksTheChapterReadAndCompleted() {
        seedTitle()
        val vm = open("c2")
        vm.awaitLoaded()

        vm.onPosition(vm.session, ReaderAnchor("c2", pagesPerChapter - 1, 1f), setOf("c2"), 0, 0)
        vm.flush()
        awaitUntil(what = "read mark") { runBlocking { dao.readChapterIds(sourceId, titleId) } == listOf("c2") }
        awaitUntil(what = "completed progress") {
            runBlocking { dao.getChapterProgress(sourceId, titleId, "c2") }?.completed == true
        }
    }

    @Test
    fun incognitoKeepsPositionOutOfHistory() {
        seedTitle()
        runBlocking { app.prefs.setIncognito(true) }
        awaitUntil(what = "incognito on") { app.prefs.incognito.value }
        val vm = open("c2")
        vm.awaitLoaded()

        vm.onPosition(vm.session, ReaderAnchor("c2", pagesPerChapter - 1, 1f), setOf("c2"), 0, 0)
        vm.flush()
        settle(500)
        assertNull(runBlocking { dao.getProgress(sourceId, titleId) })
        assertNull(runBlocking { dao.getChapterProgress(sourceId, titleId, "c2") })
        assertTrue(runBlocking { dao.readChapterIds(sourceId, titleId) }.isEmpty())
    }

    @Test
    fun jumpToBookmarkOpensItsChapterAndPage() {
        seedTitle()
        val vm = open("c1")
        vm.awaitLoaded()
        val session = vm.session

        vm.jumpTo(
            com.lume.app.data.db.PageBookmarkEntity(
                sourceId = sourceId, titleId = titleId, chapterId = "c3", chapterName = "Chapter 3",
                pageIndex = 2, pageOffset = 0.5f,
            ),
        )
        awaitUntil(what = "bookmark chapter") { vm.currentChapterId == "c3" && vm.session > session }
        assertEquals(ReaderAnchor("c3", 2, 0.5f), vm.anchor)
    }
}
