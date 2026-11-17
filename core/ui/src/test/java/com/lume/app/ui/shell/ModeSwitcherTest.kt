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
    fun sourcesAreFilteredByTheActiveKind() {
        val all = listOf("a" to MediaKind.Comics, "b" to MediaKind.Novel, "c" to MediaKind.Comics)
        assertTrue(sourcesOfMode(all, MediaKind.Novel) { it.second }.map { it.first } == listOf("b"))
        assertTrue(sourcesOfMode(all, MediaKind.Comics) { it.second }.map { it.first } == listOf("a", "c"))
        assertTrue(sourcesOfMode(all, MediaKind.Video) { it.second }.isEmpty())
    }

    @Test
    fun savedItemsFollowTheirSourceKind() {
        val kinds = mapOf("a" to MediaKind.Comics, "b" to MediaKind.Novel)
        assertTrue(belongsToMode("b", kinds, MediaKind.Novel))
        assertFalse(belongsToMode("b", kinds, MediaKind.Comics))
        assertFalse(belongsToMode("a", kinds, MediaKind.Novel))
        // A removed source counts as comics.
        assertTrue(belongsToMode("gone", kinds, MediaKind.Comics))
        assertFalse(belongsToMode("gone", kinds, MediaKind.Novel))
    }

    @Test
    fun aRegularModeIsNeverComingSoon() {
        // Before sources load the list is empty; the comics mode must not flash the empty state.
        assertFalse(showsComingSoon(listOf(comics, novel), MediaKind.Comics, emptyList()))
        assertFalse(showsComingSoon(listOf(comics), MediaKind.Novel, emptyList()))
    }
}
