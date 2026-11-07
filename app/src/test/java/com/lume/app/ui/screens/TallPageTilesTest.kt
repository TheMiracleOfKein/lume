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

    @Test
    fun unzoomedListShowsTheWholeVisiblePart() {
        // A 1000 x 3000 item at the top of a 1000 x 2000 viewport: its top two thirds show.
        val r = zoomedVisibleRegion(0f, 0f, 1000f, 3000f, 1000f, 2000f, scale = 1f, offsetX = 0f, offsetY = 0f)!!
        assertEquals(0f, r.left, 0.001f)
        assertEquals(1f, r.right, 0.001f)
        assertEquals(0f, r.top, 0.001f)
        assertEquals(2f / 3f, r.bottom, 0.001f)
    }

    @Test
    fun zoomedInAroundTheCentreShowsTheMiddle() {
        // 2x around the centre of a page that fills the viewport: the middle half each way.
        val r = zoomedVisibleRegion(0f, 0f, 1000f, 2000f, 1000f, 2000f, scale = 2f, offsetX = 0f, offsetY = 0f)!!
        assertEquals(0.25f, r.left, 0.001f)
        assertEquals(0.75f, r.right, 0.001f)
        assertEquals(0.25f, r.top, 0.001f)
        assertEquals(0.75f, r.bottom, 0.001f)
    }

    @Test
    fun panningRightShowsTheLeftSide() {
        // Content moved right by 500 screen px at 2x: the view centre is 250 list px left of centre.
        val r = zoomedVisibleRegion(0f, 0f, 1000f, 2000f, 1000f, 2000f, scale = 2f, offsetX = 500f, offsetY = 0f)!!
        assertEquals(0f, r.left, 0.001f)
        assertEquals(0.5f, r.right, 0.001f)
    }

    @Test
    fun itemsOffScreenHaveNoRegion() {
        val below = zoomedVisibleRegion(0f, 2500f, 1000f, 1000f, 1000f, 2000f, scale = 2f, offsetX = 0f, offsetY = 0f)
        assertEquals(null, below)
    }

    @Test
    fun marginWidensTheRegion() {
        val r = zoomedVisibleRegion(0f, 0f, 1000f, 2000f, 1000f, 2000f, scale = 2f, offsetX = 0f, offsetY = 0f, margin = 0.2f)!!
        assertEquals(0.2f, r.left, 0.001f)
        assertEquals(0.8f, r.right, 0.001f)
    }
}
