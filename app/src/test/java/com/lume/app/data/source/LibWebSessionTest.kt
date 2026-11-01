package com.lume.app.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibWebSessionTest {
    @Test
    fun keepsAntiBotClearanceCookies() {
        listOf("cf_clearance", "__cf_bm", "__ddg1_", "__ddgid_", "ddg_last_challenge").forEach {
            assertTrue(it, LibWebSession.isClearanceCookie(it))
        }
    }

    @Test
    fun dropsSessionCookies() {
        listOf("mangalib_session", "XSRF-TOKEN", "remember_web").forEach {
            assertFalse(it, LibWebSession.isClearanceCookie(it))
        }
    }

    @Test
    fun keepsOnlyClearancePairs() {
        val raw = "mangalib_session=abc; cf_clearance=x=y; XSRF-TOKEN=t; __ddg1_=d; cf_clearance=x=y"
        assertEquals(listOf("cf_clearance=x=y", "__ddg1_=d"), LibWebSession.clearancePairs(raw))
    }

    @Test
    fun parentDomainOfSubdomainOnly() {
        assertEquals("lib.social", LibWebSession.parentDomain("auth.lib.social"))
        assertNull(LibWebSession.parentDomain("mangalib.me"))
    }
}
