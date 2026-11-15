package com.lume.app.domain

import kotlinx.serialization.json.JsonPrimitive
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SourceLinksTest {
    @Test
    fun pathOnHostIgnoresWwwSchemeAndQuery() {
        assertEquals("/manga/x/", SourceLinks.pathOnHost("http://www.site.org/manga/x/?a=1#top", "https://site.org"))
        assertEquals("/", SourceLinks.pathOnHost("https://site.org", "https://www.site.org/"))
        assertNull(SourceLinks.pathOnHost("https://other.org/manga/x", "https://site.org"))
        assertNull(SourceLinks.pathOnHost("ftp://site.org/x", "https://site.org"))
    }

    @Test
    fun firstLinkInSharedText() {
        assertEquals(
            "https://mangadex.org/title/abc",
            SourceLinks.firstLink("Look at this: https://mangadex.org/title/abc!"),
        )
        assertNull(SourceLinks.firstLink("no link here"))
    }

    @Test
    fun versionsCompareNumerically() {
        assertTrue(compareVersions("1.10.0", "1.9") > 0)
        assertEquals(0, compareVersions("1.0", "1.0.0"))
        assertTrue(compareVersions("v2", "1.99.99") > 0)
        assertTrue(compareVersions("1.0.0", "1.0.1") < 0)
    }

    @Test
    fun packStates() {
        val entry = PackEntry(id = "x", name = "X", version = "1.2.0", engine = "madara", url = "x.json")
        assertEquals(PackState.NotInstalled, packState(entry, null))
        assertEquals(PackState.UpdateAvailable, packState(entry, "1.1.9"))
        assertEquals(PackState.Installed, packState(entry, "1.2.0"))
    }

    @Test
    fun packUrlsResolveNextToTheIndex() {
        assertEquals(
            "https://h.org/docs/packs/madara/a.json",
            resolvePackUrl("https://h.org/docs/packs/index.json", "madara/a.json"),
        )
        assertEquals("https://x.org/a.json", resolvePackUrl("https://h.org/index.json", "https://x.org/a.json"))
    }

    @Test
    fun settingsOverrideEngineConfig() {
        val manifest = SourceManifest(
            id = "m",
            name = "M",
            engine = "mangadex",
            baseUrl = "https://m.org",
            engineConfig = kotlinx.serialization.json.JsonObject(mapOf("languages" to JsonPrimitive("en"), "apiBase" to JsonPrimitive("a"))),
        )
        val merged = manifest.withSettings(mapOf("languages" to "ru,uk"))
        assertEquals("ru,uk", merged.configString("languages"))
        assertEquals("a", merged.configString("apiBase"))
        assertEquals(manifest, manifest.withSettings(emptyMap()))
    }
}
