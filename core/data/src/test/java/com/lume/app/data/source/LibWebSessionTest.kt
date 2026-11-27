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
    fun googleCookiesComeBackWithTheirScope() {
        assertEquals(
            "SID=a; Domain=.google.com; Path=/; Secure; SameSite=Lax; Max-Age=15552000",
            LibWebSession.googleCookie("SID=a"),
        )
        assertEquals(
            "__Host-GAPS=b; Path=/; Secure; SameSite=Lax; Max-Age=15552000",
            LibWebSession.googleCookie("__Host-GAPS=b"),
        )
        assertTrue(LibWebSession.googleCookie("__Secure-3PSID=c").contains("SameSite=None"))
        assertEquals(listOf("SID=a", "HSID=b=c"), LibWebSession.cookiePairs("SID=a; HSID=b=c; ; =x; SID=a"))
    }

    @Test
    fun parentDomainOfSubdomainOnly() {
        assertEquals("lib.social", LibWebSession.parentDomain("auth.lib.social"))
        assertNull(LibWebSession.parentDomain("mangalib.me"))
    }
}
