package com.lume.app.ui.screens

import androidx.test.ext.junit.runners.AndroidJUnit4
import com.lume.app.data.db.BookmarkEntity
import com.lume.app.data.db.CategoryEntity
import com.lume.app.data.db.ChapterReadEntity
import com.lume.app.data.db.LibraryChapterEntity
import com.lume.app.data.db.DownloadEntity
import com.lume.app.domain.LibraryFilter
import com.lume.app.domain.LibrarySettings
import com.lume.app.domain.LibrarySort
import com.lume.app.domain.TriState
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.annotation.Config

/**
 * [LibraryViewModel] against the real container and an empty Room database: titles are written
 * through the DAO and the view model's own flows turn them into what the screen shows.
 */
@RunWith(AndroidJUnit4::class)
@Config(instrumentedPackages = [VIEW_MODEL_SANDBOX])
class LibraryViewModelTest {
    private var app = testContainer()
    private val dao get() = app.dao

    /** The preferences file outlives a test in this JVM; start each one from the defaults. */
    @Before
    fun resetSettings() = runBlocking {
        app.prefs.setLibrarySettings(LibrarySettings())
    }

    private fun bookmark(id: String, name: String, addedAt: Long, categories: String = "[]") =
        BookmarkEntity("s", id, name, createdAt = addedAt, categoryIdsJson = categories)

    private fun chapter(titleId: String, id: String) =
        LibraryChapterEntity("s", titleId, id, dedupeKey = id, name = id)

    /** Alpha (2 unread, category 1), Beta (all read, categories 1 and 2), Gamma (no chapters, no category). */
    private fun seed(): Pair<Long, Long> = runBlocking {
        val reading = dao.upsertCategory(CategoryEntity(name = "Reading", sortOrder = 0))
        val later = dao.upsertCategory(CategoryEntity(name = "Later", sortOrder = 1))
        dao.upsertBookmark(bookmark("a", "Alpha", addedAt = 3, categories = "[$reading]"))
        dao.upsertBookmark(bookmark("b", "Beta", addedAt = 1, categories = "[$reading,$later]"))
        dao.upsertBookmark(bookmark("g", "Gamma", addedAt = 2))
        dao.upsertLibraryChapters(listOf(chapter("a", "a1"), chapter("a", "a2"), chapter("b", "b1")))
        dao.markChapterRead(ChapterReadEntity("s", "b", "b1"))
        reading to later
    }

    private fun LibraryViewModel.titles() = visible.value.map { it.title }

    private fun newViewModel() = LibraryViewModel(app)

    @Test
    fun emptyLibraryIsLoadedButShowsNothing() {
        val vm = newViewModel()
        assertFalse(vm.loaded.value)
        awaitUntil(what = "first database answer") { vm.loaded.value }
        assertTrue(vm.visible.value.isEmpty())
    }

    @Test
    fun offlineOnlyTitlesWithDownloadsAreShown() {
        app = testContainer(online = false)
        seed()
        runBlocking {
            dao.upsertDownload(DownloadEntity("s", "g", "g1", "Gamma", chapterName = "g1", pageCount = 1, pagePathsJson = "[]"))
        }
        val vm = newViewModel()
        awaitUntil(what = "only the downloaded title") { vm.titles() == listOf("Gamma") }
        assertFalse(vm.online.value)
    }

    @Test
    fun titlesAreSortedByTitleAndCountsComeFromChapters() {
        seed()
        val vm = newViewModel()
        awaitUntil(what = "three titles") { vm.visible.value.size == 3 }
        assertEquals(listOf("Alpha", "Beta", "Gamma"), vm.titles())
        val alpha = vm.visible.value.first { it.title == "Alpha" }
        assertEquals(2, alpha.totalChapters)
        assertEquals(2, alpha.unread)
        assertEquals(0, vm.visible.value.first { it.title == "Beta" }.unread)
    }

    @Test
    fun sortAndDirectionFollowTheSettings() {
        seed()
        val vm = newViewModel()
        awaitUntil(what = "three titles") { vm.visible.value.size == 3 }

        vm.updateSettings { it.copy(ascending = false) }
        awaitUntil(what = "descending title order") { vm.titles() == listOf("Gamma", "Beta", "Alpha") }

        vm.updateSettings { it.copy(sort = LibrarySort.DateAdded, ascending = true) }
        awaitUntil(what = "oldest added first") { vm.titles() == listOf("Beta", "Gamma", "Alpha") }

        vm.updateSettings { it.copy(sort = LibrarySort.Unread, ascending = false) }
        awaitUntil(what = "most unread first") { vm.titles().firstOrNull() == "Alpha" }
    }

    @Test
    fun filtersIncludeAndExclude() {
        seed()
        val vm = newViewModel()
        awaitUntil(what = "three titles") { vm.visible.value.size == 3 }

        vm.updateSettings { it.copy(filters = mapOf(LibraryFilter.Unread to TriState.Include)) }
        awaitUntil(what = "only unread titles") { vm.titles() == listOf("Alpha") }

        vm.updateSettings { it.copy(filters = mapOf(LibraryFilter.Unread to TriState.Exclude)) }
        awaitUntil(what = "unread titles hidden") { vm.titles() == listOf("Beta", "Gamma") }

        vm.updateSettings { it.copy(filters = emptyMap()) }
        awaitUntil(what = "filters cleared") { vm.visible.value.size == 3 }
    }

    @Test
    fun searchMatchesTitlesAndCloseSearchClearsIt() {
        seed()
        val vm = newViewModel()
        awaitUntil(what = "three titles") { vm.visible.value.size == 3 }

        vm.openSearch()
        assertTrue(vm.searchOpen)
        vm.search = "gam"
        awaitUntil(what = "search result") { vm.titles() == listOf("Gamma") }

        vm.closeSearch()
        assertFalse(vm.searchOpen)
        assertEquals("", vm.search)
        awaitUntil(what = "all titles again") { vm.visible.value.size == 3 }
    }

    @Test
    fun switchingCategoryNarrowsTheList() {
        val (reading, later) = seed()
        val vm = newViewModel()
        awaitUntil(what = "three titles") { vm.visible.value.size == 3 }

        vm.categoryId = reading
        awaitUntil(what = "Reading tab") { vm.titles() == listOf("Alpha", "Beta") }
        vm.categoryId = later
        awaitUntil(what = "Later tab") { vm.titles() == listOf("Beta") }
        assertEquals("Later", vm.exportName("All"))
        vm.categoryId = null
        awaitUntil(what = "All tab") { vm.visible.value.size == 3 }
        assertEquals("All", vm.exportName("All"))
    }

    @Test
    fun deletedCategoryCannotStaySelected() {
        val (reading, _) = seed()
        val vm = newViewModel()
        awaitUntil(what = "categories loaded") { vm.categories.value.size == 2 }

        vm.categoryId = reading
        awaitUntil(what = "Reading tab") { vm.titles() == listOf("Alpha", "Beta") }
        runBlocking { dao.deleteCategory(reading) }
        awaitUntil(what = "fall back to All") { vm.categoryId == null }
        assertEquals(1, vm.categories.value.size)
    }

    @Test
    fun selectionToggleRangeAllInvertAndClear() {
        val (reading, later) = seed()
        val vm = newViewModel()
        awaitUntil(what = "three titles") { vm.visible.value.size == 3 }
        val (alpha, beta, gamma) = vm.visible.value

        assertFalse(vm.selecting)
        vm.toggle(alpha)
        assertTrue(vm.selecting)
        assertEquals(setOf(alpha.key), vm.selected)
        vm.toggle(alpha)
        assertFalse(vm.selecting)

        // Long press from the last tapped title selects everything in between.
        vm.toggle(alpha)
        vm.selectRange(gamma)
        assertEquals(setOf(alpha.key, beta.key, gamma.key), vm.selected)

        vm.clearSelection()
        vm.toggle(beta)
        vm.invertSelection()
        assertEquals(setOf(alpha.key, gamma.key), vm.selected)
        vm.selectAll()
        assertEquals(3, vm.selected.size)

        vm.clearSelection()
        assertTrue(vm.selected.isEmpty())

        // Categories every selected title shares.
        vm.toggle(alpha)
        vm.toggle(beta)
        assertEquals(setOf(reading), vm.commonCategories())
        vm.clearSelection()
        vm.toggle(beta)
        assertEquals(setOf(reading, later), vm.commonCategories())
        vm.toggle(gamma)
        assertEquals(emptySet<Long>(), vm.commonCategories())
    }

    @Test
    fun exportItemsFollowSelectionElseTheScreen() {
        seed()
        val vm = newViewModel()
        awaitUntil(what = "three titles") { vm.visible.value.size == 3 }
        assertEquals(3, vm.exportItems().size)
        vm.toggle(vm.visible.value.first())
        assertEquals(listOf("Alpha"), vm.exportItems().map { it.title })
    }

    @Test
    fun setCategoriesWritesThemAndClearsTheSelection() {
        val (_, later) = seed()
        val vm = newViewModel()
        awaitUntil(what = "three titles") { vm.visible.value.size == 3 }

        vm.toggle(vm.visible.value.first { it.title == "Gamma" })
        vm.setCategories(setOf(later))
        assertTrue(vm.selected.isEmpty())
        awaitUntil(what = "Gamma in Later") { vm.visible.value.first { it.title == "Gamma" }.categoryIds == setOf(later) }
        assertNull(vm.visible.value.firstOrNull { it.title == "Alpha" }?.categoryIds?.firstOrNull { it == later })
    }
}
