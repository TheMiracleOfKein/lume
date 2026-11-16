package com.lume.app.data.source

import com.lume.app.R
import com.lume.app.domain.MediaKind

/** Ranobe and web novels. No engines yet: the mode exists, its sources come after 1.0. */
object NovelModule : ContentModule {
    override val kind = MediaKind.Novel
    override val labelRes = R.string.mode_novel

    override val engines: Map<String, EngineFactory> = emptyMap()
}
