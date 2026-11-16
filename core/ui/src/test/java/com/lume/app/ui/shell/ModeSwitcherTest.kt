package com.lume.app.ui.shell

import com.lume.app.data.source.ContentModule
import com.lume.app.data.source.EngineFactory
import com.lume.app.domain.MediaKind
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

internal class FakeModule(
    override val kind: MediaKind,
    override val preview: Boolean = false,
    override val labelRes: Int = when (kind) {
        MediaKind.Comics -> com.lume.app.R.string.mode_comics
        else -> com.lume.app.R.string.mode_novel
    },
) : ContentModule {
    override val engines: Map<String, EngineFactory> = emptyMap()
}

class ModeSwitcherTest {
    private val comics = FakeModule(MediaKind.Comics)
    private val novel = FakeModule(MediaKind.Novel, preview = true)

    @Test
    fun switchingNeedsMoreThanOneMode() {
        assertFalse(canSwitchModes(emptyList()))
        assertFalse(canSwitchModes(listOf(comics)))
        assertTrue(canSwitchModes(listOf(comics, novel)))
    }

    @Test
    fun aPreviewModeWithoutSourcesIsComingSoon() {
        val modules = listOf(comics, novel)
        assertTrue(showsComingSoon(modules, MediaKind.Novel, listOf(MediaKind.Comics)))
        assertFalse(showsComingSoon(modules, MediaKind.Novel, listOf(MediaKind.Comics, MediaKind.Novel)))
    }

    @Test
    fun aRegularModeIsNeverComingSoon() {
        // Before sources load the list is empty; the comics mode must not flash the empty state.
        assertFalse(showsComingSoon(listOf(comics, novel), MediaKind.Comics, emptyList()))
        assertFalse(showsComingSoon(listOf(comics), MediaKind.Novel, emptyList()))
    }
}
