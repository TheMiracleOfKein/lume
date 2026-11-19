package com.lume.app.data.source

import androidx.test.platform.app.InstrumentationRegistry
import com.lume.app.data.network.AppHttp
import com.lume.app.domain.MediaKind
import com.lume.app.domain.SourceManifest
import kotlinx.serialization.json.Json
import okhttp3.CookieJar
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.util.regex.PatternSyntaxException

/**
 * Runs on Android, where regexes go through ICU instead of the JVM's engine. A pattern that
 * compiles on the JVM but not here (the v1.4.0 failure of two novel sources) breaks an engine's
 * construction or its parsing. The parsers' saved-page tests (src/sharedTest) run on the device
 * too; this class builds every engine the app can create.
 */
class ComicsEnginesAndroidTest {
    private val http = AppHttp(CookieJar.NO_COOKIES)
    private val context = InstrumentationRegistry.getInstrumentation().targetContext

    @Test
    fun everyBundledComicsManifestBuildsItsEngine() {
        val json = Json { ignoreUnknownKeys = true; isLenient = true }
        val ids = context.assets.list("sources").orEmpty().filter { it.endsWith(".json") }
        assertTrue("no bundled manifests found", ids.isNotEmpty())
        for (file in ids) {
            val manifest = json.decodeFromString<SourceManifest>(context.assets.open("sources/$file").use { it.readBytes().decodeToString() })
            assertEquals("$file: kind", MediaKind.Comics, manifest.kind)
            SourceEngines.create(manifest, http, context = context, modules = listOf(ComicsModule))
        }
    }

    @Test
    fun everyEngineOfTheModuleHasRegexesThatCompile() {
        for (engine in ComicsModule.engines.keys) {
            val manifest = SourceManifest(id = "test-$engine", name = engine, engine = engine, baseUrl = "https://example.test")
            val failure = runCatching { SourceEngines.create(manifest, http, context = context, modules = listOf(ComicsModule)) }
                .exceptionOrNull()
            // An engine that needs a server login or other wiring may refuse this bare setup;
            // a pattern the platform can't compile is what this test is for.
            assertTrue("$engine: ${failure?.stackTraceToString()}", failure == null || !isRegexFailure(failure))
        }
    }

    private fun isRegexFailure(e: Throwable): Boolean =
        generateSequence(e) { it.cause }.any { it is PatternSyntaxException || it is ExceptionInInitializerError }
}
