package com.lume.app.data.network

import com.lume.app.domain.SourceManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkSupportTest {
    @Test
    fun cloudflareChallengeIsDetected() {
        val body = "<html><head><title>Just a moment...</title></head><body>challenge-platform</body></html>"
        assertTrue(AntiBot.isChallenge(403, "cloudflare", null, body))
        assertTrue(AntiBot.isChallenge(200, "nginx", "challenge", ""))
    }

    @Test
    fun plainForbiddenIsNotAChallenge() {
        // e.g. api.cdnlibs.org region block: Google-style 403 page, no challenge markers.
        val body = "<title>Error 403</title> 403 - Forbidden. That’s an error."
        assertFalse(AntiBot.isChallenge(403, "Google Frontend", null, body))
        assertFalse(AntiBot.isChallenge(403, "cloudflare", null, """{"message":"Unauthenticated."}"""))
    }

    @Test
    fun ddosGuardGeoBlockIsNotSolvable() {
        val body = """<title>DDoS-Guard</title><link rel="stylesheet" href="/.well-known/ddos-guard/geoblocked.css">"""
        assertFalse(AntiBot.isChallenge(403, "ddos-guard", null, body))
        assertTrue(AntiBot.isGeoBlocked("ddos-guard", body))
        assertTrue(AntiBot.isChallenge(403, "ddos-guard", null, "<title>DDoS-Guard</title> checking browser"))
    }

    @Test
    fun refererComesFromSourceDomainsAndLearnedHosts() {
        val referers = ImageReferers()
        val asura = SourceManifest(
            id = "asura",
            name = "Asura",
            engine = "asura",
            baseUrl = "https://asurascans.com",
            headers = mapOf("Referer" to "https://asurascans.com/"),
        )
        referers.register(asura)
        assertEquals("https://asurascans.com/", referers.refererFor("cdn.asurascans.com"))
        assertNull(referers.refererFor("example.org"))
        referers.learn("https://img.unrelated-cdn.net/a.webp", asura)
        assertEquals("https://asurascans.com/", referers.refererFor("img.unrelated-cdn.net"))
        assertEquals("https://hentailib.me/", referers.refererFor("img2h.hentaicdn.org"))
    }

    @Test
    fun rootDomain() {
        assertEquals("mangadex.org", ImageReferers.rootDomain("uploads.mangadex.org"))
        assertEquals("example.com", ImageReferers.rootDomain("example.com"))
    }
}
