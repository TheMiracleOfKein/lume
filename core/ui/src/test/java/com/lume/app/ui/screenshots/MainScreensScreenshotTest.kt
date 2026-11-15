package com.lume.app.ui.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.lume.app.R
import com.lume.app.data.db.CategoryEntity
import com.lume.app.data.db.DownloadQueueEntity
import com.lume.app.data.db.ProgressEntity
import com.lume.app.domain.Chapter
import com.lume.app.domain.HomeFeed
import com.lume.app.domain.LibraryDisplay
import com.lume.app.domain.LibraryItem
import com.lume.app.domain.LibraryFilter
import com.lume.app.domain.LibrarySettings
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleSummary
import com.lume.app.domain.TriState
import com.lume.app.ui.components.EmptyBox
import com.lume.app.ui.components.QuickSearchBar
import com.lume.app.ui.screens.CategoryRow
import com.lume.app.ui.screens.ChapterRow
import com.lume.app.ui.screens.FilterChipRow
import com.lume.app.ui.screens.HomeFeedList
import com.lume.app.ui.screens.LibraryContent
import com.lume.app.ui.screens.ReaderBottomBar
import com.lume.app.ui.screens.ReaderTopBar
import com.lume.app.ui.screens.TitleHeader
import com.lume.app.ui.screens.settings.SettingsScaffold
import com.lume.app.ui.screens.settings.SettingsSections
import org.junit.Test

/* Fixed data shared by the tests. Covers are left empty so nothing touches the network. */

private val summaries = listOf(
    TitleSummary("t1", "Tower of the Lantern", typeLabel = "Manhwa", latestChapter = "Chapter 112", updatedLabel = "2 hours ago"),
    TitleSummary("t2", "A Quiet Harbour", typeLabel = "Manga", latestChapter = "Chapter 48", updatedLabel = "Yesterday"),
    TitleSummary("t3", "Paper Moon Detective", typeLabel = "Manga", latestChapter = "Chapter 9", updatedLabel = "3 days ago"),
    TitleSummary("t4", "Salt and Ember", typeLabel = "Manhua", latestChapter = "Chapter 230", updatedLabel = "Last week"),
)

private val history = listOf(
    ProgressEntity("demo", "t1", "Tower of the Lantern", chapterId = "c10", chapterName = "Chapter 10", chapterUrl = "u", pageIndex = 7, pageCount = 20, updatedAt = 1L),
    ProgressEntity("demo", "t2", "A Quiet Harbour", chapterId = "c3", chapterName = "Chapter 3", chapterUrl = "u", pageIndex = 2, pageCount = 30, updatedAt = 1L),
)

private val libraryItems = listOf(
    LibraryItem("demo", "t1", "Tower of the Lantern", typeLabel = "Manhwa", totalChapters = 112, unread = 12, downloaded = 4, lastReadAt = 1L),
    LibraryItem("demo", "t2", "A Quiet Harbour", typeLabel = "Manga", totalChapters = 48, unread = 0, downloaded = 0, lastReadAt = 1L, completed = true),
    LibraryItem("demo", "t3", "Paper Moon Detective", typeLabel = "Manga", totalChapters = 9, unread = 9, sources = 2),
    LibraryItem("demo", "t4", "Salt and Ember", typeLabel = "Manhua", totalChapters = 230, unread = 31, downloaded = 31, hasNew = true),
    LibraryItem("demo", "t5", "The Last Ferry", typeLabel = "Manga", totalChapters = 20, unread = 20),
    LibraryItem("demo", "t6", "Glass Orchard", typeLabel = "Manhwa", totalChapters = 64, unread = 3),
)

private val categories = listOf(CategoryEntity(1, "Reading"), CategoryEntity(2, "Plan to read"))

private val details = TitleDetails(
    id = "t1",
    title = "Tower of the Lantern",
    altTitle = "The Lantern Keeper's Ascent",
    status = "Ongoing",
    authors = listOf("Mira Okafor"),
    artists = listOf("Jun Park"),
    typeLabel = "Manhwa",
    year = 2021,
    score = 8.7,
)

private val chapters = listOf(
    Chapter("c5", "Chapter 5: The Landing", "u", dateLabel = "May 12"),
    Chapter("c4", "Chapter 4: Lantern Light", "u", dateLabel = "May 5"),
    Chapter("c3", "Chapter 3: Second Floor", "u", dateLabel = "Apr 28"),
    Chapter("c2", "Chapter 2: The Keeper", "u", dateLabel = "Apr 21"),
    Chapter("c1", "Chapter 1: The Door", "u", dateLabel = "Apr 14"),
)

class HomeScreenshotTest : ScreenshotTest() {
    private fun home(look: Look) = shoot("home", look) {
        Column(Modifier.fillMaxSize()) {
            QuickSearchBar(value = "", onValueChange = {}, onSubmit = {}, modifier = Modifier.padding(horizontal = 16.dp, vertical = 12.dp))
            HomeFeedList(
                feed = HomeFeed(spotlight = summaries, latest = summaries),
                history = history,
                error = null,
                sourceId = "demo",
                onClearHistory = {},
                onOpenCatalog = {},
                onOpenTitle = {},
                onRetry = {},
                onRemoveContinue = {},
                onContinue = {},
            )
        }
    }

    @Test fun light() = home(Look.Light)
    @Test fun dark() = home(Look.Dark)
    @Test fun largeFont() = home(Look.LargeFont)
}

class LibraryScreenshotTest : ScreenshotTest() {
    private fun library(name: String, look: Look, items: List<LibraryItem>, settings: LibrarySettings = LibrarySettings()) =
        shoot(name, look) {
            Column(Modifier.fillMaxSize()) {
                CategoryRow(categories = categories, selectedId = null, onSelect = {}, onEdit = {})
                if (settings.filterChips) FilterChipRow(settings = settings, onCycle = {})
                Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                    if (items.isEmpty()) {
                        EmptyBox(stringResource(R.string.library_empty))
                    } else {
                        LibraryContent(
                            items = items,
                            settings = settings,
                            selected = emptySet(),
                            bottomPadding = 0.dp,
                            onClick = {},
                            onLongClick = {},
                        )
                    }
                }
            }
        }

    private val filtered = LibrarySettings(filters = mapOf(LibraryFilter.Unread to TriState.Include))

    @Test fun gridLight() = library("library", Look.Light, libraryItems, filtered)
    @Test fun gridDark() = library("library", Look.Dark, libraryItems, filtered)
    @Test fun gridLargeFont() = library("library", Look.LargeFont, libraryItems, filtered)
    @Test fun listLight() = library("library_list", Look.Light, libraryItems, LibrarySettings(display = LibraryDisplay.List, continueButton = true))
    @Test fun emptyLight() = library("library_empty", Look.Light, emptyList())
    @Test fun emptyDark() = library("library_empty", Look.Dark, emptyList())
    @Test fun emptyLargeFont() = library("library_empty", Look.LargeFont, emptyList())
}

class TitleScreenshotTest : ScreenshotTest() {
    private fun title(look: Look) = shoot("title", look) {
        Column(Modifier.fillMaxSize().padding(16.dp)) {
            TitleHeader(d = details, sourceId = "demo", hasProgress = true, canRead = true, onRead = {})
        }
    }

    @Test fun light() = title(Look.Light)
    @Test fun dark() = title(Look.Dark)
    @Test fun largeFont() = title(Look.LargeFont)
}

class ChaptersScreenshotTest : ScreenshotTest() {
    private val running = DownloadQueueEntity(
        sourceId = "demo", titleId = "t1", chapterId = "c3", titleName = "Tower of the Lantern",
        chapterName = "Chapter 3", status = DownloadQueueEntity.STATUS_RUNNING, progress = 0.4f, createdAt = 1L,
    )

    private fun chapterList(look: Look) = shoot("chapters", look) {
        LazyColumn(Modifier.fillMaxSize()) {
            items(chapters, key = { it.id }) { chapter ->
                ChapterRow(
                    chapter = chapter,
                    showTeam = false,
                    sourceLabel = null,
                    isRead = chapter.id in setOf("c1", "c2"),
                    isCurrent = chapter.id == "c3",
                    isDownloaded = chapter.id == "c2",
                    queueItem = running.takeIf { chapter.id == "c4" },
                    partial = if (chapter.id == "c3") 8 to 24 else null,
                    selected = chapter.id == "c5",
                    selecting = false,
                    onClick = {},
                    onLongClick = {},
                    onDownload = {},
                    onCancel = {},
                )
            }
        }
    }

    @Test fun light() = chapterList(Look.Light)
    @Test fun dark() = chapterList(Look.Dark)
    @Test fun largeFont() = chapterList(Look.LargeFont)
}

class SettingsScreenshotTest : ScreenshotTest() {
    private fun settings(look: Look) = shoot("settings", look) {
        SettingsScaffold(title = stringResource(R.string.settings_title), onBack = {}) {
            SettingsSections(onOpen = {})
        }
    }

    @Test fun light() = settings(Look.Light)
    @Test fun dark() = settings(Look.Dark)
    @Test fun largeFont() = settings(Look.LargeFont)
}

class ReaderScreenshotTest : ScreenshotTest() {
    // The reader draws its bars over the pages, always on dark scrims.
    private fun reader(look: Look) = shoot("reader_menu", look) {
        Column(Modifier.fillMaxSize().background(Color(0xFF202020))) {
            ReaderTopBar(
                titleName = "Tower of the Lantern",
                chapterTitle = "Chapter 3: Second Floor",
                inLibrary = true,
                onBack = {},
                onOpenTitle = {},
                onToggleLibrary = {},
                onSettings = {},
                onSharePage = {},
                onSavePage = {},
                guidedPanels = false,
            )
            Box(Modifier.weight(1f).fillMaxWidth())
            ReaderBottomBar(
                pageLabel = "Page 8 / 24",
                pageIndex = 7,
                pageCount = 24,
                reversed = false,
                onSeek = {},
                autoScroll = null,
                onToggleAutoScroll = {},
                hasPrev = true,
                hasNext = false,
                onPrev = {},
                onNext = {},
                onChapters = {},
                onBookmark = {},
                onBookmarkList = {},
                onSettings = {},
            )
        }
    }

    @Test fun light() = reader(Look.Light)
    @Test fun dark() = reader(Look.Dark)
    @Test fun largeFont() = reader(Look.LargeFont)
}
