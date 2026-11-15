package com.lume.app.domain

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoDownloadTest {
    @Test
    fun offDownloadsNothing() {
        assertFalse(AutoDownloadMode.OFF.downloads(subscribed = true))
        assertFalse(AutoDownloadMode.OFF.downloads(subscribed = false))
    }

    @Test
    fun allDownloadsEveryTitle() {
        assertTrue(AutoDownloadMode.ALL.downloads(subscribed = false))
    }

    @Test
    fun subscribedDownloadsOnlySubscribedTitles() {
        assertTrue(AutoDownloadMode.SUBSCRIBED.downloads(subscribed = true))
        assertFalse(AutoDownloadMode.SUBSCRIBED.downloads(subscribed = false))
    }

    @Test
    fun oldSwitchMapsToAllOrOff() {
        assertEquals(AutoDownloadMode.ALL, AutoDownloadMode.fromStored(null, legacyOn = true))
        assertEquals(AutoDownloadMode.OFF, AutoDownloadMode.fromStored(null, legacyOn = false))
        assertEquals(AutoDownloadMode.OFF, AutoDownloadMode.fromStored(null, legacyOn = null))
    }

    @Test
    fun storedModeWinsOverOldSwitch() {
        assertEquals(AutoDownloadMode.SUBSCRIBED, AutoDownloadMode.fromStored(2, legacyOn = true))
        assertEquals(AutoDownloadMode.OFF, AutoDownloadMode.fromStored(0, legacyOn = true))
    }

    @Test
    fun unknownStoredValueFallsBack() {
        assertEquals(AutoDownloadMode.ALL, AutoDownloadMode.fromStored(9, legacyOn = true))
    }
}
