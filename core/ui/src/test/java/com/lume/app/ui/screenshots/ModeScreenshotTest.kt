package com.lume.app.ui.screenshots

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import com.lume.app.domain.MediaKind
import com.lume.app.ui.shell.FakeModule
import com.lume.app.ui.shell.ModeComingSoon
import com.lume.app.ui.shell.ModeSwitcherPanel
import org.junit.Test

private val modules = listOf(FakeModule(MediaKind.Comics), FakeModule(MediaKind.Novel, preview = true))

class ModeSwitcherScreenshotTest : ScreenshotTest() {
    private fun switcher(look: Look) = shoot("mode_switcher", look) {
        Box(Modifier.fillMaxSize(), contentAlignment = Alignment.BottomCenter) {
            ModeSwitcherPanel(modules = modules, active = MediaKind.Comics, onSelect = {})
        }
    }

    @Test fun light() = switcher(Look.Light)
    @Test fun dark() = switcher(Look.Dark)
    @Test fun largeFont() = switcher(Look.LargeFont)
}

class ModeComingSoonScreenshotTest : ScreenshotTest() {
    private fun comingSoon(look: Look) = shoot("mode_coming_soon", look) {
        ModeComingSoon(modules[1])
    }

    @Test fun light() = comingSoon(Look.Light)
    @Test fun dark() = comingSoon(Look.Dark)
    @Test fun largeFont() = comingSoon(Look.LargeFont)
}
