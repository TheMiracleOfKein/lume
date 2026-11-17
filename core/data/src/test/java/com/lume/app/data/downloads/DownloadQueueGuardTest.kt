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

    @Test
    fun `only comics download`() {
        assertTrue(DownloadQueue.canDownload("mangadex", com.lume.app.domain.MediaKind.Comics))
        assertFalse(DownloadQueue.canDownload("ranobe", com.lume.app.domain.MediaKind.Novel))
        assertFalse(DownloadQueue.canDownload("local-novel", com.lume.app.domain.MediaKind.Novel))
    }
}
