package com.lume.app.testing

import com.lume.app.domain.SourceManifest
import kotlinx.serialization.json.Json

/**
 * Saved site responses for the parser tests, which run on the JVM and on a device (src/sharedTest).
 * Files live in `src/test/resources/fixtures/<source>/<case>.{json,html}`; record them with
 * `-Plume.live -Plume.live.record` (see docs/ENGINES.md). The bundled source manifests
 * (`src/main/assets/sources`) are test resources too, so both runs read them the same way.
 */
object Fixtures {
    fun text(path: String): String = resource("fixtures/$path")

    /** The bundled manifest of source [id]. */
    fun manifest(id: String): SourceManifest = Json { ignoreUnknownKeys = true }
        .decodeFromString(SourceManifest.serializer(), resource("sources/$id.json"))

    private fun resource(path: String): String {
        val stream = Fixtures::class.java.classLoader!!.getResourceAsStream(path)
            ?: error("Missing test resource: $path")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
