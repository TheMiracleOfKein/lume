package com.lume.app.domain

import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertSame
import org.junit.Assert.assertTrue
import org.junit.Test

class HostOverridesTest {
    private val lib = { key: String -> key == "libsocial" || key == "mangalib" }

    @Test
    fun aLibSourceMayMoveWithinTheTrustedDomains() {
        val file = HostsFile(sources = mapOf("libsocial" to HostEntry(apiBase = "https://api.lib.social/api")))
        assertEquals("https://api.lib.social/api", HostOverrides.sanitize(file, lib).sources["libsocial"]?.apiBase)
    }

    @Test
    fun aLibSourceNeverMovesToAnUnknownDomain() {
        val file = HostsFile(sources = mapOf("libsocial" to HostEntry(apiBase = "https://api.evil.example/api")))
        assertTrue(HostOverrides.sanitize(file, lib).sources.isEmpty())
    }

    @Test
    fun otherSourcesMayMoveAnywhereOverHttps() {
        val file = HostsFile(sources = mapOf("asura" to HostEntry(apiBase = "https://api.asurascans.example/api")))
        assertEquals("https://api.asurascans.example/api", HostOverrides.sanitize(file, lib).sources["asura"]?.apiBase)
    }

    @Test
    fun plainHttpAndOddAddressesAreDropped() {
        val file = HostsFile(
            sources = mapOf(
                "a" to HostEntry(apiBase = "http://api.example.com"),
                "b" to HostEntry(apiBase = "https://user:pw@api.example.com"),
                "c" to HostEntry(apiBase = "ftp://api.example.com"),
                "d" to HostEntry(apiBase = "not a url"),
                "e" to HostEntry(apiBase = "https://localhost/api"),
            ),
        )
        assertTrue(HostOverrides.sanitize(file, lib).sources.isEmpty())
    }

    @Test
    fun aNewerFileFormatIsIgnored() {
        val file = HostsFile(schemaVersion = 2, sources = mapOf("asura" to HostEntry(apiBase = "https://a.example.com")))
        assertTrue(HostOverrides.sanitize(file, lib).sources.isEmpty())
    }

    @Test
    fun fallbacksKeepOrderAndDropBadHosts() {
        val file = HostsFile(fallbacks = mapOf("api.example.com" to listOf("b.example.com", "bad host", "api.example.com", "c.example.com")))
        assertEquals(listOf("b.example.com", "c.example.com"), HostOverrides.sanitize(file, lib).fallbacks["api.example.com"])
    }

    @Test
    fun aLibHostNeverFallsBackOutsideTheTrustedDomains() {
        val file = HostsFile(fallbacks = mapOf("api.lib.social" to listOf("evil.example.com", "api.mangalib.me")))
        assertEquals(listOf("api.mangalib.me"), HostOverrides.sanitize(file, lib).fallbacks["api.lib.social"])
    }

    @Test
    fun theSourceIdBeatsItsEngine() {
        val file = HostsFile(
            sources = mapOf(
                "libsocial" to HostEntry(apiBase = "https://a.lib.social"),
                "mangalib" to HostEntry(apiBase = "https://b.lib.social"),
            ),
        )
        val manifest = SourceManifest(id = "mangalib", name = "MangaLib", engine = "libsocial", baseUrl = "https://mangalib.me")
        assertEquals("https://b.lib.social", HostOverrides.entryFor(file, manifest)?.apiBase)
        assertEquals("https://a.lib.social", HostOverrides.entryFor(file, manifest.copy(id = "hentailib"))?.apiBase)
        assertNull(HostOverrides.entryFor(file, manifest.copy(id = "x", engine = "madara")))
    }

    @Test
    fun withHostsReplacesOnlyAddresses() {
        val manifest = SourceManifest(
            id = "m", name = "M", engine = "libsocial", baseUrl = "https://old.example.com",
            headers = mapOf("Referer" to "https://old.example.com/"),
            engineConfig = JsonObject(mapOf("apiBase" to JsonPrimitive("https://api.old/api"), "siteId" to JsonPrimitive("1"))),
        )
        val moved = manifest.withHosts(HostEntry(apiBase = "https://api.new/api", imageHosts = listOf("img.a.com", "img.b.com")))
        assertEquals("https://api.new/api", moved.configString("apiBase"))
        assertEquals("img.a.com,img.b.com", moved.configString("imageHosts"))
        assertEquals("1", moved.configString("siteId"))
        assertEquals("https://old.example.com", moved.baseUrl)
        assertEquals(manifest.headers, moved.headers)
        assertSame(manifest, manifest.withHosts(null))
    }
}
