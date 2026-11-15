package com.lume.app.data.source

import androidx.annotation.StringRes
import com.lume.app.data.network.AppHttp
import com.lume.app.data.server.ServerCredentials
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.MediaKind
import com.lume.app.domain.SourceManifest

/** What an engine may need besides its manifest. */
class EngineDeps(
    val http: AppHttp,
    val libAuth: LibAuthStore? = null,
    val serverCredentials: ServerCredentials? = null,
    val cacheDir: java.io.File? = null,
)

typealias EngineFactory = (SourceManifest, EngineDeps) -> CatalogSource

/**
 * One kind of content the app can serve (manga, novels, video). A module brings its engines;
 * [SourceEngines] looks an engine id up in the installed modules.
 */
interface ContentModule {
    val kind: MediaKind

    @get:StringRes
    val labelRes: Int

    /** Engine id to factory. */
    val engines: Map<String, EngineFactory>
}
