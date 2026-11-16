package com.lume.app.ui.screenshots

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import com.lume.app.ui.screens.ReaderBottomBar
import com.lume.app.ui.screens.ReaderTopBar
import org.junit.Test

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
