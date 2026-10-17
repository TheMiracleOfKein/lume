package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.SourceManifest
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/** Website links → title ids, per engine (shared links and Mihon backup entries use this). */
class SourceLinkParsingTest {
    private val http = AppHttp(CookieJar.NO_COOKIES)

    private fun manifest(engine: String, baseUrl: String) =
        SourceManifest(id = engine, name = engine, engine = engine, baseUrl = baseUrl)

    @Test
    fun mangaDex() {
        val engine = MangaDexEngine(manifest("mangadex", "https://mangadex.org"), http)
        val id = "a1c7c817-4e59-43b7-9365-09675a149a6f"
        assertEquals(id, engine.titleIdFromUrl("https://mangadex.org/title/$id/one-piece"))
        assertNull(engine.titleIdFromUrl("https://mangadex.org/chapter/$id"))
        assertNull(engine.titleIdFromUrl("https://example.org/title/$id"))
    }

    @Test
    fun libSocial() {
        val engine = LibSocialEngine(manifest("libsocial", "https://mangalib.me"), http, null)
        assertEquals("/7580--solo-leveling", engine.titleIdFromUrl("https://mangalib.me/ru/manga/7580--solo-leveling"))
        assertEquals("/7580--solo-leveling", engine.titleIdFromUrl("https://mangalib.me/ru/7580--solo-leveling/read/v1/c1"))
    }

    @Test
    fun madaraKeepsTheTitleFolder() {
        val engine = MadaraEngine(manifest("madara", "https://www.mangaread.org"), http)
        assertEquals("/manga/abc/", engine.titleIdFromUrl("https://www.mangaread.org/manga/abc/chapter-3/"))
        assertNull(engine.titleIdFromUrl("https://www.mangaread.org/"))
    }

    @Test
    fun remangaAndMangaBuff() {
        assertEquals(
            "solo-leveling",
            RemangaEngine(manifest("remanga", "https://remanga.org"), http).titleIdFromUrl("https://remanga.org/manga/solo-leveling/main"),
        )
        assertEquals(
            "berserk",
            MangaBuffEngine(manifest("mangabuff", "https://mangabuff.ru"), http).titleIdFromUrl("https://mangabuff.ru/manga/berserk/1/1"),
        )
    }
}
