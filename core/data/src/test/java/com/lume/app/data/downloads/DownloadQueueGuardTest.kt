package com.lume.app.data.downloads

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class DownloadQueueGuardTest {
    @Test
    fun `local chapters are never queued`() {
        assertFalse(DownloadQueue.canDownload("local"))
    }

    @Test
    fun `other sources can be downloaded`() {
        assertTrue(DownloadQueue.canDownload("mangadex"))
        assertTrue(DownloadQueue.canDownload("custom-pack"))
    }
}
