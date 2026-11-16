package com.lume.app.data.source

import com.lume.app.R
import com.lume.app.domain.MediaKind

/** Manga, manhwa and comics: image-page sources, servers and local files. */
object ComicsModule : ContentModule {
    override val kind = MediaKind.Comics
    override val labelRes = R.string.mode_comics

    override val engines: Map<String, EngineFactory> = mapOf(
        SourceEngines.LOCAL to { _, d -> LocalEngine(checkNotNull(d.context) { "Local files need a Context" }) },
        SourceEngines.OPDS to { m, d -> OpdsEngine(m, d.http, d.serverCredentials, d.cacheDir) },
        SourceEngines.KOMGA to { m, d -> KomgaEngine(m, d.http) },
        SourceEngines.KAVITA to { m, d -> KavitaEngine(m, d.http, d.serverCredentials) },
        SourceEngines.MADARA to { m, d -> MadaraEngine(m, d.http) },
        SourceEngines.LIBSOCIAL to { m, d -> LibSocialEngine(m, d.http, d.libAuth) },
        SourceEngines.MANGADEX to { m, d -> MangaDexEngine(m, d.http) },
        SourceEngines.MANGAKATANA to { m, d -> MangaKatanaEngine(m, d.http) },
        SourceEngines.ASURA to { m, d -> AsuraEngine(m, d.http) },
        SourceEngines.WEEBCENTRAL to { m, d -> WeebCentralEngine(m, d.http) },
        SourceEngines.DEMONIC to { m, d -> DemonicEngine(m, d.http) },
        SourceEngines.HIVETOONS to { m, d -> HiveToonsEngine(m, d.http) },
        SourceEngines.REMANGA to { m, d -> RemangaEngine(m, d.http) },
        SourceEngines.MANGABUFF to { m, d -> MangaBuffEngine(m, d.http) },
        SourceEngines.INKSTORY to { m, d -> InkStoryEngine(m, d.http) },
    )
}
