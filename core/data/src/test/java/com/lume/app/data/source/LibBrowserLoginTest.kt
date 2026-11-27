package com.lume.app.data.source

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class LibBrowserLoginTest {
    @Test
    fun challengeIsS256OfTheVerifier() {
        // RFC 7636, appendix B.
        assertEquals(
            "E9Melhoa2OwvFrEMTJguCHaoeK1t8URWbuGJSstw-cM",
            LibBrowserLogin.challenge("dBjftJeZ4CVP-mB92K27uhbUJU1p1r_wW1gFWFOEjXk"),
        )
    }

    @Test
    fun startPointsTheSignInAtTheSitesCallback() {
        val attempt = LibBrowserLogin.start("https://mangalib.me/")
        assertEquals("https://mangalib.me/ru/front/auth/oauth/callback", attempt.redirectUri)
        assertTrue(attempt.url.startsWith("https://auth.lib.social/auth/oauth/authorize?"))
        assertTrue("redirect_uri=https%3A%2F%2Fmangalib.me%2Fru%2Ffront%2Fauth%2Foauth%2Fcallback" in attempt.url)
        assertTrue("state=${attempt.state}" in attempt.url)
        assertTrue("code_challenge=${LibBrowserLogin.challenge(attempt.verifier)}" in attempt.url)
        // The verifier stays in Lume.
        assertTrue(attempt.verifier !in attempt.url)
        assertEquals(128, attempt.verifier.length)
        assertNotEquals(attempt.state, LibBrowserLogin.start("https://mangalib.me").state)
    }

    @Test
    fun readsTheCodeFromACopiedAddress() {
        val callback = LibBrowserLogin.parseCallback(
            "MangaLib https://mangalib.me/ru/front/auth/oauth/callback?code=def%2B50&state=abc#top",
        )
        assertEquals("def+50", callback?.code)
        assertEquals("abc", callback?.state)
        assertNull(callback?.error)
    }

    @Test
    fun readsAnError() {
        val callback = LibBrowserLogin.parseCallback("https://mangalib.me/ru/front/auth/oauth/callback?error=access_denied&state=s")
        assertEquals("access_denied", callback?.error)
    }

    @Test
    fun otherAddressesAreNotACallback() {
        assertNull(LibBrowserLogin.parseCallback("https://mangalib.me/ru/manga/solo-leveling"))
        assertNull(LibBrowserLogin.parseCallback("https://mangalib.me/ru/front/auth/oauth/callback?state=s"))
        assertNull(LibBrowserLogin.parseCallback(""))
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
}
