package com.lume.app.data.source

import android.content.Context
import com.lume.app.R
import com.lume.app.data.network.AppHttp
import com.lume.app.data.server.ServerCredentials
import com.lume.app.domain.CatalogSource
import com.lume.app.domain.SourceManifest

/**
 * Catalogue engines are first-party Kotlin adapters.
 * JSON packs only clone *families* (Madara / LibSocial / HiveToons).
 * New engines ship with app updates — no DEX / ExtensionLoader.
 */
object SourceEngines {
    const val MADARA = "madara"
    const val LIBSOCIAL = "libsocial"
    const val HIVETOONS = "hivetoons"
    const val MANGADEX = "mangadex"
    const val MANGAKATANA = "mangakatana"
    const val ASURA = "asura"
    const val REMANGA = "remanga"
    const val MANGABUFF = "mangabuff"
    const val INKSTORY = "inkstory"
    const val WEEBCENTRAL = "weebcentral"
    const val DEMONIC = "demonic"
    const val LOCAL = "local"
    const val LOCAL_NOVEL = "local-novel"
    const val RANOBELIB = "ranobelib"
    const val ROYALROAD = "royalroad"
    const val SCRIBBLEHUB = "scribblehub"
    const val OPDS = "opds"
    const val KOMGA = "komga"
    const val KAVITA = "kavita"

    /** Self-hosted servers: created by the user in Sources, never bundled and never imported as a pack. */
    val serverEngines: Set<String> = setOf(OPDS, KOMGA, KAVITA)

    /** Product core: stable platforms we intend to maintain. */
    val corePlatform: Set<String> = setOf(
        MANGADEX,
        LIBSOCIAL,
        MADARA,
        HIVETOONS,
        MANGAKATANA,
        ASURA,
        REMANGA,
        MANGABUFF,
        INKSTORY,
        LOCAL,
        LOCAL_NOVEL,
        RANOBELIB,
    )

    /**
     * Importable JSON packs may only target these family engines.
     * One-site engines (weebcentral, demonic, …) are not pack templates.
     */
    val packImportable: Set<String> = setOf(MADARA, LIBSOCIAL, HIVETOONS)

    /** Compiled one-site adapters; bundled only if present in assets. */
    val siteOnly: Set<String> = setOf(WEEBCENTRAL, DEMONIC, ROYALROAD, SCRIBBLEHUB)

    /** The built-in local-files source; the registry adds it when a module provides [LOCAL]. */
    val localManifest = SourceManifest(
        id = LOCAL,
        name = "Local",
        version = "1.0.0",
        engine = LOCAL,
        baseUrl = "file://local",
        mediaKind = "comics",
        language = "local",
    )

    /** The built-in local-books source; the registry adds it when a module provides [LOCAL_NOVEL]. */
    val localNovelManifest = SourceManifest(
        id = LOCAL_NOVEL,
        name = "Local books",
        version = "1.0.0",
        engine = LOCAL_NOVEL,
        baseUrl = "file://local-novel",
        mediaKind = "novel",
        language = "local",
    )

    /** Engines that read files on the phone (comics and books). */
    fun isLocal(engine: String): Boolean = engine == LOCAL || engine == LOCAL_NOVEL

    fun isKnown(engine: String): Boolean = engine in corePlatform || engine in siteOnly || engine in serverEngines

    fun isServer(engine: String): Boolean = engine in serverEngines

    /** The MangaLib family on the shared cdnlibs API: one sign-in per site, bookmark import, one request limit. */
    fun isLib(engine: String): Boolean = engine == LIBSOCIAL || engine == RANOBELIB

    fun isPackImportable(engine: String): Boolean = engine in packImportable

    fun displayName(engine: String): String = when (engine) {
        MADARA -> "Madara"
        LIBSOCIAL -> "LibSocial (cdnlibs)"
        HIVETOONS -> "HiveToons / Iken"
        MANGADEX -> "MangaDex"
        MANGAKATANA -> "MangaKatana"
        ASURA -> "Asura"
        REMANGA -> "ReManga"
        MANGABUFF -> "MangaBuff"
        INKSTORY -> "InkStory"
        WEEBCENTRAL -> "Weeb Central"
        DEMONIC -> "Demonic Scans"
        LOCAL -> "Local"
        LOCAL_NOVEL -> "Local books"
        RANOBELIB -> "RanobeLib (cdnlibs)"
        ROYALROAD -> "Royal Road"
        SCRIBBLEHUB -> "Scribble Hub"
        OPDS -> "OPDS"
        KOMGA -> "Komga"
        KAVITA -> "Kavita"
        else -> engine
    }

    /** Short UI tag: platform engine vs family pack vs one-site. */
    fun kindLabel(context: Context, engine: String): String = when (engine) {
        in packImportable -> context.getString(R.string.engine_kind_family)
        LOCAL, LOCAL_NOVEL -> context.getString(R.string.engine_kind_local)
        in serverEngines -> context.getString(R.string.engine_kind_server)
        in siteOnly -> context.getString(R.string.engine_kind_site)
        else -> context.getString(R.string.engine_kind_platform)
    }

    fun importRejectedMessage(context: Context, engine: String): String =
        context.getString(R.string.import_reject_engine, engine)

    /** Build the engine for [manifest] from the installed [modules]. [LOCAL] needs [context]. */
    fun create(
        manifest: SourceManifest,
        http: AppHttp,
        libAuth: LibAuthStore? = null,
        serverCredentials: ServerCredentials? = null,
        cacheDir: java.io.File? = null,
        context: android.content.Context? = null,
        modules: List<ContentModule>,
    ): CatalogSource {
        val factory = modules.firstNotNullOfOrNull { it.engines[manifest.engine] }
            ?: error(
                if (isKnown(manifest.engine)) {
                    "Engine wiring missing for ${manifest.engine}"
                } else {
                    "Unknown engine: ${manifest.engine}. New engines ship with app updates."
                },
            )
        return factory(manifest, EngineDeps(http, libAuth, serverCredentials, cacheDir, context))
    }
}
