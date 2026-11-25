package com.lume.app.ui.screens

import androidx.lifecycle.SavedStateHandle
import androidx.test.core.app.ApplicationProvider
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lume.app.AppContainer
import com.lume.app.data.db.ChapterProgressEntity
import com.lume.app.data.db.TitlePrefsEntity
import com.lume.app.data.prefs.NovelReaderSettings
import com.lume.app.data.prefs.NovelTheme
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.After
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
        // Off the main thread while the main looper keeps running: DataStore runs an edit's change
        // in its caller's context, so an edit the last test's view model left queued on the main
        // thread must get to finish first. runBlocking on the main thread would wait on it forever.
        val seed = CoroutineScope(Dispatchers.IO).launch {
            app.sources.load()
            app.prefs.setIncognito(false)
            // The preferences file outlives a test: start from the defaults.
            app.prefs.setNovelReaderSettings(NovelReaderSettings())
        }
        awaitUntil(what = "seeded") { seed.isCompleted }
        awaitUntil(what = "default text settings") { app.prefs.novelReaderSettings.value == NovelReaderSettings() }
    }

    /**
     * Lets the preference edits a view model queued finish inside this test. Robolectric drops the
     * main looper's queue between tests, and an edit whose change was due to run there would
     * block the shared preferences file for every later test. Edits run in order, so once this
     * last one is through, the earlier ones are too.
     */
    @After
    fun drainPreferenceEdits() {
        val last = CoroutineScope(Dispatchers.IO).launch { app.prefs.setIncognito(false) }
        awaitUntil(what = "preference edits") { last.isCompleted }
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
    fun hidingChapterTitlesRebuildsTheRowsAndSavesTheComfortSettings() {
        val vm = open("Book/0")
        vm.awaitLoaded()
        assertTrue(vm.items.any { it is NovelItem.Header })
        val version = vm.textVersion
        vm.updateSettings(
            vm.settings.copy(showChapterTitle = false, brightness = 0.4f, volumeKeys = true, paragraphIndent = 2, autoScrollSpeed = 7, statusLine = false),
            commit = true,
        )
        assertFalse(vm.items.any { it is NovelItem.Header })
        assertTrue(vm.textVersion > version)
        awaitUntil(what = "comfort settings saved") { !app.prefs.novelReaderSettings.value.showChapterTitle }
        val saved = app.prefs.novelReaderSettings.value
        assertEquals(0.4f, saved.brightness)
        assertTrue(saved.volumeKeys)
        assertEquals(2, saved.paragraphIndent)
        assertEquals(7, saved.autoScrollSpeed)
        assertFalse(saved.statusLine)

        vm.updateSettings(vm.settings.copy(showChapterTitle = true), commit = true)
        assertTrue(vm.items.any { it is NovelItem.Header })
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

    private fun titlePrefs() = runBlocking { dao.getTitlePrefs(sourceId, titleId) }

    @Test
    fun titleOverridesAreAppliedOverTheGlobalSettings() {
        runBlocking {
            dao.upsertTitlePrefs(
                TitlePrefsEntity(sourceId, titleId, novelFontSize = 24, novelTheme = NovelTheme.Sepia.ordinal, novelPaged = true),
            )
        }
        val vm = open("Book/0")
        awaitUntil(what = "title overrides") { vm.saveForTitle }
        assertEquals(24, vm.settings.fontSize)
        assertEquals(NovelTheme.Sepia, vm.settings.theme)
        assertTrue(vm.settings.paged)
        // Everything else is still the global value.
        assertEquals(NovelReaderSettings().font, vm.settings.font)
    }

    @Test
    fun editsWhileTheSwitchIsOnGoToTheTitleAndOtherSettingsStayGlobal() {
        val vm = open("Book/0")
        vm.awaitLoaded()
        vm.changeSaveForTitle(true)
        awaitUntil(what = "overrides stored") { titlePrefs()?.novelFontSize == 18 }

        vm.updateSettings(vm.settings.copy(fontSize = 26, theme = NovelTheme.Black, paged = true, justify = true), commit = true)
        awaitUntil(what = "title saved") { titlePrefs()?.novelFontSize == 26 }
        val row = titlePrefs()!!
        assertEquals(NovelTheme.Black.ordinal, row.novelTheme)
        assertEquals(true, row.novelPaged)
        awaitUntil(what = "global saved") { app.prefs.novelReaderSettings.value.justify }
        val global = app.prefs.novelReaderSettings.value
        assertEquals(18, global.fontSize)
        assertEquals(NovelTheme.App, global.theme)
        assertFalse(global.paged)
    }

    @Test
    fun switchingOffClearsTheOverridesAndFallsBackToTheGlobalSettings() {
        val vm = open("Book/0")
        vm.awaitLoaded()
        vm.changeSaveForTitle(true)
        vm.updateSettings(vm.settings.copy(fontSize = 26, paged = true), commit = true)
        awaitUntil(what = "title saved") { titlePrefs()?.novelFontSize == 26 }

        vm.changeSaveForTitle(false)
        assertFalse(vm.saveForTitle)
        assertEquals(18, vm.settings.fontSize)
        assertFalse(vm.settings.paged)
        awaitUntil(what = "overrides cleared") {
            titlePrefs()?.let { it.novelFontSize == null && it.novelTheme == null && it.novelPaged == null } == true
        }
    }

    @Test
    fun resetDropsTheTitleOverridesButKeepsTheOtherTitlePrefs() {
        runBlocking {
            dao.upsertTitlePrefs(
                TitlePrefsEntity(sourceId, titleId, readerMode = 2, novelFontSize = 24, novelPaged = true),
            )
        }
        val vm = open("Book/0")
        awaitUntil(what = "title overrides") { vm.saveForTitle }

        vm.resetSettings()
        assertFalse(vm.saveForTitle)
        assertEquals(NovelReaderSettings(), vm.settings)
        awaitUntil(what = "overrides cleared") { titlePrefs()?.novelFontSize == null && titlePrefs()?.novelPaged == null }
        assertEquals(2, titlePrefs()!!.readerMode)
    }

    private companion object {
        const val LONG = 40
    }
}
