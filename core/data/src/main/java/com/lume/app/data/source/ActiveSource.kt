package com.lume.app.data.source

import com.lume.app.domain.CatalogSource
import com.lume.app.domain.MediaKind

/**
 * The source to use for [mode]: the stored one if it is loaded and serves that mode, else the
 * mode's first source, preferring ones that are not the local-files source.
 */
fun resolveActiveSource(sources: List<CatalogSource>, storedId: String?, mode: MediaKind): CatalogSource? {
    val ofMode = sources.filter { it.manifest.kind == mode }
    return ofMode.firstOrNull { it.manifest.id == storedId }
        ?: ofMode.firstOrNull { !SourceEngines.isLocal(it.manifest.engine) }
        ?: ofMode.firstOrNull()
}
