package com.lume.app.data.source

import org.junit.Assert.assertEquals
import org.junit.Test

class LibSocialImageServersTest {
    @Test
    fun lastWorkingServerComesFirst() {
        val servers = orderedImageServers(
            lastWorking = "https://img3.hentaicdn.org",
            fromChapter = null,
            siteServers = listOf("https://img2.hentaicdn.org", "https://img3.hentaicdn.org"),
            siteId = 2,
        )
        assertEquals(listOf("https://img3.hentaicdn.org", "https://img2.hentaicdn.org"), servers)
    }

    @Test
    fun chapterServerThenSiteServersThenDefault() {
        val servers = orderedImageServers(
            lastWorking = null,
            fromChapter = "https://cdn.example/",
            siteServers = listOf("https://img2.imglib.info", "https://img3.cdnlibs.org"),
            siteId = 1,
        )
        assertEquals(listOf("https://cdn.example", "https://img2.imglib.info", "https://img3.cdnlibs.org"), servers)
    }

    @Test
    fun blankAndInvalidServersAreSkipped() {
        val servers = orderedImageServers(lastWorking = "", fromChapter = "null", siteServers = listOf(" "), siteId = 4)
        assertEquals(listOf("https://img2h.hentaicdn.org"), servers)
    }
}
