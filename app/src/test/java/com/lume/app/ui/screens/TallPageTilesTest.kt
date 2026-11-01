package com.lume.app.ui.screens

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TallPageTilesTest {
    @Test
    fun sampleKeepsWidthAtOrAboveTarget() {
        assertEquals(1, tileSampleSize(800, 1080))
        assertEquals(1, tileSampleSize(1080, 1080))
        assertEquals(2, tileSampleSize(2160, 1080))
        assertEquals(2, tileSampleSize(3000, 1080))
        assertEquals(4, tileSampleSize(4400, 1080))
    }

    @Test
    fun sampleHandlesBadInput() {
        assertEquals(1, tileSampleSize(0, 1080))
        assertEquals(1, tileSampleSize(800, 0))
    }

    @Test
    fun normalPagesNeedNoTiling() {
        assertFalse(needsTiling(1200, 1))
        assertFalse(needsTiling(4096, 1))
    }

    @Test
    fun tallPagesNeedTiling() {
        assertTrue(needsTiling(14_500, 1))
        assertTrue(needsTiling(4097, 1))
    }

    @Test
    fun sampledPageMayFitWithoutTiling() {
        assertFalse(needsTiling(8000, 2))
        assertTrue(needsTiling(9000, 2))
    }

    @Test
    fun tilesCoverThePageWithoutGapsOrOverlap() {
        val tiles = tileBounds(14_500, 1)
        assertEquals(0, tiles.first().top)
        assertEquals(14_500, tiles.last().bottom)
        tiles.zipWithNext().forEach { (a, b) -> assertEquals(a.bottom, b.top) }
        assertEquals(14_500, tiles.sumOf { it.height })
    }

    @Test
    fun everyTileStaysUnderTheDrawLimitWhenDecoded() {
        for (sample in listOf(1, 2, 4)) {
            tileBounds(40_000, sample).forEach { assertTrue(it.height / sample <= MAX_DRAW_PX) }
        }
    }

    @Test
    fun tileCountAndLastTile() {
        val tiles = tileBounds(5000, 1)
        assertEquals(3, tiles.size)
        assertEquals(TileBounds(4096, 5000), tiles.last())
    }

    @Test
    fun exactMultipleHasNoEmptyTile() {
        val tiles = tileBounds(TILE_PX * 3, 1)
        assertEquals(3, tiles.size)
        assertTrue(tiles.all { it.height == TILE_PX })
    }

    @Test
    fun emptyImageHasNoTiles() {
        assertTrue(tileBounds(0, 1).isEmpty())
    }
}
