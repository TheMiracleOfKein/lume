package com.lume.app.data.source

import com.lume.app.R
import com.lume.app.domain.MediaKind

/** Ranobe and web novels: local EPUB and TXT books and RanobeLib. */
object NovelModule : ContentModule {
    override val kind = MediaKind.Novel
    override val labelRes = R.string.mode_novel

    override val localBookExtensions: Set<String> = setOf("epub", "txt")

    override val engines: Map<String, EngineFactory> = mapOf(
        SourceEngines.LOCAL_NOVEL to { _, d ->
            val context = checkNotNull(d.context) { "Local books need a Context" }
            LocalNovelEngine(context.filesDir, context.cacheDir)
        },
        SourceEngines.RANOBELIB to { m, d -> RanobeLibEngine(m, d.http, d.libAuth) },
    )
}
