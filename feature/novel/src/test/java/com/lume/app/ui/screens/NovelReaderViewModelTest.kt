package com.lume.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lume.app.AppContainer
import com.lume.app.data.db.ChapterProgressEntity
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.Shadows.shadowOf
import org.robolectric.annotation.Config
import java.io.File
import java.time.Duration

/**
 * [NovelReaderViewModel] on a real local TXT book (three chapters, the first with [LONG] blocks)
 * served by the real local novel engine. Position and read marks are checked in the Room
 * database the view model writes to.
 */
@RunWith(AndroidJUnit4::class)
@Config(instrumentedPackages = [VIEW_MODEL_SANDBOX])
class NovelReaderViewModelTest {
    private val context = ApplicationProvider.getApplicationContext<android.app.Application>()
    private val app: AppContainer = testContainer()
    private val dao = app.dao
    private val sourceId = "local-novel"
    private val titleId = "Book"

    @Before
    fun seedBook() {
        val dir = File(context.filesDir, "local-novel/Book").apply { deleteRecursively(); mkdirs() }
        val text = buildString {
            append("Chapter 1\n\n")
            repeat(LONG) { append("Paragraph $it of the first chapter.\n\n") }
            append("Chapter 2\n\nSecond one.\n\nSecond two.\n\nSecond three.\n\n")
            append("Chapter 3\n\nThird one.\n\nThird two.\n\n")
        }
        File(dir, "Book.txt").writeText(text)
        runBlocking {
            app.sources.load()
            app.prefs.setIncognito(false)
        }
    }

    private fun open(
        chapterId: String,
        block: Int? = null,
        offset: Float? = null,
        state: SavedStateHandle = SavedStateHandle(),
    ) = NovelReaderViewModel(app, sourceId, titleId, chapterId, block, offset, state)

    private fun NovelReaderViewModel.awaitLoaded() = awaitUntil(what = "reader load") { !loading }

    private fun NovelReaderViewModel.blocks(chapterId: String) =
        items.filterIsInstance<NovelItem.Block>().count { it.chapterId == chapterId }

    @Test
    fun loadsTheChapterWithItsTitleAndBlocks() {
        val vm = open("Book/0")
        assertTrue(vm.loading)
        vm.awaitLoaded()

        assertNull(vm.error)
        assertEquals("Book", vm.titleName)
        assertEquals(listOf("Book/0", "Book/1", "Book/2"), vm.chapters.map { it.id })
        assertEquals("Book/0", vm.currentChapterId)
        assertEquals(LONG, vm.blocks("Book/0"))
        assertTrue(vm.items.first() is NovelItem.Header)
        assertEquals(1, vm.session)
        // Top of the chapter: nothing is appended while the end is far away.
        assertEquals(0, vm.blocks("Book/1"))
    }

    @Test
    fun resumesFromTheRouteBlockAndCoercesItIntoTheChapter() {
        val vm = open("Book/0", block = 7, offset = 0.4f)
        vm.awaitLoaded()
        assertEquals(NovelAnchor("Book/0", 7, 0.4f), vm.anchor)
        assertEquals(7, vm.currentBlock)

        val past = open("Book/1", block = 99)
        past.awaitLoaded()
        assertEquals(2, past.currentBlock)
    }

    @Test
    fun resumesTheChaptersOwnProgressUnlessItWasFinished() {
        runBlocking {
            dao.upsertChapterProgress(ChapterProgressEntity(sourceId, titleId, "Book/0", pageIndex = 12, pageOffset = 0.5f, pageCount = LONG))
            dao.upsertChapterProgress(ChapterProgressEntity(sourceId, titleId, "Book/1", pageIndex = 2, pageOffset = 1f, pageCount = 3, completed = true))
        }
        val resumed = open("Book/0")
        resumed.awaitLoaded()
        assertEquals(NovelAnchor("Book/0", 12, 0.5f), resumed.anchor)

        val finished = open("Book/1")
        finished.awaitLoaded()
        assertEquals(NovelAnchor("Book/1", 0, 0f), finished.anchor)
    }

    @Test
    fun savedStateWinsOverTheRoute() {
        val state = SavedStateHandle(mapOf("novel.chapterId" to "Book/2", "novel.block" to 1, "novel.fraction" to 0.25f))
        val vm = open("Book/0", state = state)
        vm.awaitLoaded()
        assertEquals(NovelAnchor("Book/2", 1, 0.25f), vm.anchor)
        assertEquals("Book/2", vm.currentChapterId)
    }

    @Test
    fun positionIsSavedAsANovelAfterTheDebounce() {
        val vm = open("Book/0")
        vm.awaitLoaded()

        vm.onPosition(vm.session, NovelAnchor("Book/0", 5, 0.3f), lastVisibleIndex = 8)
        assertEquals(5, vm.currentBlock)
        // Nothing is written until scrolling has stopped for 400 ms.
        assertNull(runBlocking { dao.getProgress(sourceId, titleId) })
        shadowOf(android.os.Looper.getMainLooper()).idleFor(Duration.ofMillis(500))
        awaitUntil(what = "debounced save") { runBlocking { dao.getProgress(sourceId, titleId) } != null }

        val history = runBlocking { dao.getProgress(sourceId, titleId) }!!
        assertEquals("novel", history.mediaKind)
        assertEquals("Book/0", history.chapterId)
        assertEquals(5, history.pageIndex)
        assertEquals(0.3f, history.pageOffset, 0f)
        assertEquals(LONG, history.pageCount)
        assertNull(history.pageImageUrl)
        assertEquals("Book", history.titleName)
        val perChapter = runBlocking { dao.getChapterProgress(sourceId, titleId, "Book/0") }!!
        assertEquals(5, perChapter.pageIndex)
        assertFalse(perChapter.completed)
        // Not at the end: nothing is read yet.
        assertTrue(runBlocking { dao.readChapterIds(sourceId, titleId) }.isEmpty())
    }

    @Test
    fun flushSavesAtOnceAndAStaleSessionIsIgnored() {
        val vm = open("Book/0")
        vm.awaitLoaded()

        vm.onPosition(vm.session + 1, NovelAnchor("Book/0", 9, 0f), lastVisibleIndex = 12)
        assertEquals(0, vm.currentBlock)

        vm.onPosition(vm.session, NovelAnchor("Book/0", 9, 0f), lastVisibleIndex = 12)
        vm.flush()
        awaitUntil(what = "flushed save") { runBlocking { dao.getProgress(sourceId, titleId) }?.pageIndex == 9 }
    }

    @Test
    fun reachingTheLastBlockMarksTheChapterReadAndCompleted() {
        val vm = open("Book/1")
        vm.awaitLoaded()
        val last = vm.items.lastIndex

        vm.onPosition(vm.session, NovelAnchor("Book/1", 2, 0.2f), lastVisibleIndex = last)
        assertTrue(vm.atChapterEnd)
        assertEquals(100, vm.percent)
        vm.flush()
        awaitUntil(what = "read mark") { runBlocking { dao.readChapterIds(sourceId, titleId) }.contains("Book/1") }
        awaitUntil(what = "completed progress") {
            runBlocking { dao.getChapterProgress(sourceId, titleId, "Book/1") }?.completed == true
        }
    }

    @Test
    fun incognitoSavesNothing() {
        runBlocking { app.prefs.setIncognito(true) }
        awaitUntil(what = "incognito on") { app.prefs.incognito.value }
        val vm = open("Book/1")
        vm.awaitLoaded()

        vm.onPosition(vm.session, NovelAnchor("Book/1", 2, 1f), lastVisibleIndex = vm.items.lastIndex)
        vm.flush()
        settle(500)
        assertNull(runBlocking { dao.getProgress(sourceId, titleId) })
        assertNull(runBlocking { dao.getChapterProgress(sourceId, titleId, "Book/1") })
        assertTrue(runBlocking { dao.readChapterIds(sourceId, titleId) }.isEmpty())
        // The spot is still kept for rotation and process death.
        assertEquals(2, vm.currentBlock)
    }

    @Test
    fun nextChapterIsAppendedWhenTheEndIsNear() {
        val vm = open("Book/0")
        vm.awaitLoaded()
        val session = vm.session
        assertEquals(0, vm.blocks("Book/1"))

        // 1 title row + LONG blocks: 15 rows left below the last visible one.
        vm.onPosition(session, NovelAnchor("Book/0", LONG - 20, 0f), lastVisibleIndex = vm.items.lastIndex - 16)
        settle()
        assertEquals(0, vm.blocks("Book/1"))

        vm.onPosition(session, NovelAnchor("Book/0", LONG - 18, 0f), lastVisibleIndex = vm.items.lastIndex - 15)
        awaitUntil(what = "next chapter appended") { vm.blocks("Book/1") == 3 }
        assertEquals(session, vm.session)
        assertEquals("Book/1", (vm.items.first { it is NovelItem.Header && it.chapter.id == "Book/1" } as NovelItem.Header).chapter.id)
        // Chapter 1 was only approached, not finished.
        assertTrue(runBlocking { dao.readChapterIds(sourceId, titleId) }.isEmpty())

        // Scrolling into chapter 2 finishes chapter 1.
        vm.onPosition(session, NovelAnchor("Book/1", 0, 0f), lastVisibleIndex = vm.items.indexOfFirst { it is NovelItem.Header && it.chapter.id == "Book/1" } + 1)
        awaitUntil(what = "first chapter read") { runBlocking { dao.readChapterIds(sourceId, titleId) }.contains("Book/0") }
        assertEquals("Book/1", vm.currentChapterId)
    }

    @Test
    fun shortFirstChapterAppendsTheNextOneRightAway() {
        val vm = open("Book/1")
        vm.awaitLoaded()
        awaitUntil(what = "next chapter appended") { vm.blocks("Book/2") == 2 }
    }

    @Test
    fun neighboursFollowReadingOrderAndGoChapterReloads() {
        val vm = open("Book/1")
        vm.awaitLoaded()
        assertEquals("Book/0", vm.neighbours.previous?.id)
        assertEquals("Book/2", vm.neighbours.next?.id)

        val first = vm.session
        vm.goChapter(+1)
        awaitUntil(what = "next chapter") { vm.currentChapterId == "Book/2" && !vm.loading && vm.session > first }
        assertEquals(0, vm.blocks("Book/1"))
        assertNull(vm.neighbours.next)

        val session = vm.session
        vm.goChapter(+1)
        settle()
        assertEquals(session, vm.session)

        vm.openChapter("Book/0")
        awaitUntil(what = "chapter from the list") { vm.currentChapterId == "Book/0" && vm.session > session }
    }

    @Test
    fun unknownChapterShowsAnErrorAndStillKnowsTheTitle() {
        val vm = open("Book/9")
        vm.awaitLoaded()
        assertNotNull(vm.error)
        assertTrue(vm.items.isEmpty())
        assertEquals("Book", vm.titleName)
        assertEquals(3, vm.chapters.size)
    }

    @Test
    fun missingTitleIsAnError() {
        File(context.filesDir, "local-novel/Book").deleteRecursively()
        val vm = NovelReaderViewModel(app, sourceId, "Nothing", "Nothing/0", null, null, SavedStateHandle())
        vm.awaitLoaded()
        assertNotNull(vm.error)
        assertFalse(vm.loading)
    }

    @Test
    fun settingsAreClampedAndCommittedToThePreferences() {
        val vm = open("Book/0")
        vm.awaitLoaded()
        vm.updateSettings(vm.settings.copy(fontSize = 99), commit = false)
        assertEquals(28, vm.settings.fontSize)
        assertEquals(18, app.prefs.novelReaderSettings.value.fontSize)
        vm.updateSettings(vm.settings.copy(fontSize = 22), commit = true)
        awaitUntil(what = "settings saved") { app.prefs.novelReaderSettings.value.fontSize == 22 }
    }

    @Test
    fun resetSettingsRestoresTheDefaultsAndSavesThem() {
        val vm = open("Book/0")
        vm.awaitLoaded()
        vm.updateSettings(vm.settings.copy(fontSize = 24, justify = !vm.settings.justify), commit = true)
        awaitUntil(what = "custom settings saved") { app.prefs.novelReaderSettings.value.fontSize == 24 }

        vm.resetSettings()
        assertEquals(com.lume.app.data.prefs.NovelReaderSettings(), vm.settings)
        awaitUntil(what = "defaults saved") {
            app.prefs.novelReaderSettings.value == com.lume.app.data.prefs.NovelReaderSettings()
        }
    }

    private companion object {
        const val LONG = 40
    }
}
