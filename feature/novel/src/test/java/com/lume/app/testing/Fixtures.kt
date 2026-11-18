package com.lume.app.testing

/**
 * Saved site responses for the offline parser tests.
 * Files live in `src/test/resources/fixtures/<source>/<case>.{json,html}`; record them with
 * `-Plume.live -Plume.live.record` (see docs/ENGINES.md).
 */
object Fixtures {
    fun text(path: String): String {
        val stream = Fixtures::class.java.classLoader!!.getResourceAsStream("fixtures/$path")
            ?: error("Missing fixture: fixtures/$path")
        return stream.use { it.readBytes().toString(Charsets.UTF_8) }
    }
}
