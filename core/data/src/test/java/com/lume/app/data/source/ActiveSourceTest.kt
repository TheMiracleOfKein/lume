package com.lume.app.data.source

import com.lume.app.domain.CatalogSource
import com.lume.app.domain.MediaKind
import com.lume.app.domain.SourceManifest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test
import java.lang.reflect.Proxy

class ActiveSourceTest {
    private fun source(id: String, engine: String, kind: String): CatalogSource {
        val manifest = SourceManifest(id = id, name = id, engine = engine, baseUrl = "https://$id.test", mediaKind = kind)
        return Proxy.newProxyInstance(javaClass.classLoader, arrayOf(CatalogSource::class.java)) { _, method, _ ->
            if (method.name == "getManifest") manifest else error("unused: ${method.name}")
        } as CatalogSource
    }

    private val sources = listOf(
        source("local", SourceEngines.LOCAL, "comics"),
        source("asura", "madara", "comics"),
        source("local-novel", SourceEngines.LOCAL_NOVEL, "novel"),
    )

    private fun id(s: CatalogSource?) = s?.manifest?.id

    @Test
    fun storedSourceOfTheModeWins() {
        assertEquals("local", id(resolveActiveSource(sources, "local", MediaKind.Comics)))
    }

    @Test
    fun storedSourceOfAnotherModeIsIgnored() {
        assertEquals("local-novel", id(resolveActiveSource(sources, "asura", MediaKind.Novel)))
        assertEquals("asura", id(resolveActiveSource(sources, "local-novel", MediaKind.Comics)))
    }

    @Test
    fun fallbackPrefersNonLocalSources() {
        assertEquals("asura", id(resolveActiveSource(sources, null, MediaKind.Comics)))
        assertEquals("asura", id(resolveActiveSource(sources, "gone", MediaKind.Comics)))
    }

    @Test
    fun onlyLocalSourceIsUsedWhenAlone() {
        assertEquals("local-novel", id(resolveActiveSource(sources, null, MediaKind.Novel)))
    }

    @Test
    fun modeWithoutSourcesGivesNull() {
        assertNull(resolveActiveSource(sources, null, MediaKind.Video))
    }
}
