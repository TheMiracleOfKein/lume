package com.lume.app.ui.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.lume.app.data.novel.NovelBlock
import com.lume.app.data.novel.Span
import com.lume.app.data.prefs.NovelFont
import com.lume.app.data.prefs.NovelReaderSettings
import com.lume.app.data.prefs.NovelTheme
import com.lume.app.domain.Chapter
import com.lume.app.ui.screens.LoadedNovelChapter
import com.lume.app.ui.screens.NovelBottomBar
import com.lume.app.ui.screens.NovelReaderSettingsSheet
import com.lume.app.ui.screens.NovelTextList
import com.lume.app.ui.screens.NovelTopBar
import com.lume.app.ui.screens.buildNovelItems
import com.lume.app.ui.screens.novelPalette
import org.junit.Test

class NovelReaderScreenshotTest : ScreenshotTest() {
    private val chapter = Chapter(id = "c3", title = "Chapter 3: Second Floor", url = "c3")
    private val blocks = listOf(
        NovelBlock.Paragraph(
            listOf(
                Span("The lantern swung in the dark stairwell, and every step below it answered with a "),
                Span("quiet echo", italic = true),
                Span(". She counted the floors until the numbers stopped making sense."),
            ),
        ),
        NovelBlock.Paragraph(listOf(Span("\"Not that way,\"", bold = true), Span(" said the voice behind her. It was close, far too close."))),
        NovelBlock.Divider,
        NovelBlock.Heading(2, listOf(Span("Later"))),
        NovelBlock.Paragraph(
            listOf(Span("Morning found the tower exactly as quiet as before. Nothing had moved, and nothing had been forgotten, which was worse.")),
        ),
        NovelBlock.Paragraph(listOf(Span("She wrote one line in the margin of the map and closed the book."))),
    )
    private val items = buildNovelItems(listOf(LoadedNovelChapter(chapter, blocks)))

    @Composable
    private fun Reader(settings: NovelReaderSettings, percent: Int = 42) {
        val palette = novelPalette(settings.theme)
        Box(Modifier.fillMaxSize()) {
            NovelTextList(
                items = if (settings.showChapterTitle) items else buildNovelItems(listOf(LoadedNovelChapter(chapter, blocks)), showTitles = false),
                settings = settings,
                palette = palette,
                listState = rememberLazyListState(),
                contentPadding = PaddingValues(top = 72.dp, bottom = 120.dp),
            )
            NovelTopBar(
                titleName = "Tower of the Lantern",
                chapterTitle = chapter.title,
                palette = palette,
                onBack = {},
                onOpenTitle = {},
                modifier = Modifier.align(Alignment.TopCenter),
            )
            NovelBottomBar(
                percent = percent,
                hasPrev = true,
                hasNext = false,
                palette = palette,
                onPrev = {},
                onNext = {},
                onChapters = {},
                onSettings = {},
                modifier = Modifier.align(Alignment.BottomCenter),
            )
        }
    }

    @Test fun light() = shoot("novel_reader", Look.Light) { Reader(NovelReaderSettings()) }
    @Test fun dark() = shoot("novel_reader", Look.Dark) { Reader(NovelReaderSettings()) }
    @Test fun largeFont() = shoot("novel_reader", Look.LargeFont) { Reader(NovelReaderSettings()) }
    @Test fun sepia() = shoot("novel_reader_sepia", Look.Light) { Reader(NovelReaderSettings(theme = NovelTheme.Sepia, justify = true)) }
    @Test fun blackSans() = shoot("novel_reader_black", Look.Dark) {
        Reader(NovelReaderSettings(theme = NovelTheme.Black, font = NovelFont.Sans, fontSize = 20, lineHeight = 1.7f))
    }

    @Test fun titleHiddenIndented() = shoot("novel_reader_no_title", Look.Light) {
        Reader(NovelReaderSettings(showChapterTitle = false, paragraphIndent = 2, paragraphSpacing = 4))
    }

    @Test fun settingsSheet() = shoot("novel_settings", Look.Light) {
        NovelReaderSettingsSheet(
            settings = NovelReaderSettings(theme = NovelTheme.Sepia),
            onClose = {},
            onChange = {},
            onCommit = {},
            onReset = {},
            saveForTitle = false,
            onSaveForTitleChange = {},
        )
    }
}
