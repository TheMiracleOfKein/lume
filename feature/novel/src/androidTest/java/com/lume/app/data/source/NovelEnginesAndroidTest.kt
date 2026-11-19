package com.lume.app.data.source

import androidx.test.platform.app.InstrumentationRegistry
import com.lume.app.data.network.AppHttp
import com.lume.app.domain.MediaKind
import com.lume.app.domain.NovelSource
import com.lume.app.testing.Fixtures
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Runs on Android, where regexes go through ICU instead of the JVM's engine. A pattern that
 * compiles on the JVM but not here (the v1.4.0 Royal Road and Scribble Hub failure) breaks the
 * engine's construction or its parsing. The parsers' saved-page tests (src/sharedTest) run on the
 * device too; this class checks that every bundled novel engine can be built at all.
 */
class NovelEnginesAndroidTest {
    private val http = AppHttp(CookieJar.NO_COOKIES)

    @Test
    fun everyBundledNovelManifestBuildsItsEngine() {
        val assets = InstrumentationRegistry.getInstrumentation().targetContext.assets
        val ids = assets.list("sources").orEmpty().filter { it.endsWith(".json") }.map { it.removeSuffix(".json") }
        assertTrue("no bundled manifests found", ids.isNotEmpty())
        for (id in ids) {
            val manifest = Fixtures.manifest(id)
            assertEquals("$id: kind", MediaKind.Novel, manifest.kind)
            val engine = SourceEngines.create(manifest, http, modules = listOf(NovelModule))
            assertTrue("$id: not a novel source", engine is NovelSource)
        }
    }
}
