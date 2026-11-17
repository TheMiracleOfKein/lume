package com.lume.app.data.source

import com.lume.app.domain.MediaKind
import com.lume.app.domain.SourceManifest
import kotlinx.serialization.json.Json
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

class ContentModuleTest {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    @Test
    fun comicsModuleServesComics() {
        assertEquals(MediaKind.Comics, ComicsModule.kind)
    }

    @Test
    fun everyBundledManifestEngineHasAFactory() {
        val files = File("src/main/assets/sources").listFiles { f -> f.extension == "json" }.orEmpty()
        assertTrue("no bundled manifests found", files.isNotEmpty())
        for (file in files) {
            val manifest = json.decodeFromString<SourceManifest>(file.readText())
            assertNotNull("${file.name}: no factory for engine ${manifest.engine}", ComicsModule.engines[manifest.engine])
            assertEquals("${file.name}: kind", MediaKind.Comics, manifest.kind)
        }
    }

    @Test
    fun everyKnownEngineHasAFactory() {
        // The local-novel engine comes from the novel module.
        val known = SourceEngines.corePlatform + SourceEngines.siteOnly + SourceEngines.serverEngines - SourceEngines.LOCAL_NOVEL
        for (engine in known) assertNotNull("no factory for $engine", ComicsModule.engines[engine])
    }

    @Test
    fun unknownEngineIsRejected() {
        val manifest = SourceManifest(id = "x", name = "X", engine = "nope", baseUrl = "https://x.test")
        val failure = runCatching { SourceEngines.create(manifest, com.lume.app.data.network.AppHttp(), modules = listOf(ComicsModule)) }.exceptionOrNull()
        assertTrue(failure is IllegalStateException)
    }
}
