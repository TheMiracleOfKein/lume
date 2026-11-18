package com.lume.app

import coil3.ComponentRegistry
import com.lume.app.data.local.pdf.PdfPageFetcher
import com.lume.app.data.local.pdf.PdfPageKeyer
import com.lume.app.data.source.ComicsModule
import com.lume.app.data.source.ContentModule
import com.lume.app.data.source.NovelModule
import com.lume.app.ui.screens.ComicsScreens
import com.lume.app.ui.screens.NovelScreens
import com.lume.app.ui.shell.ContentScreens

/** The full build: manga and ranobe. */
object InstalledModules {
    /** The flavor name; release assets are lume-v<version>-<flavor>.apk. */
    const val FLAVOR = "full"

    val modules: List<ContentModule> = listOf(ComicsModule, NovelModule)
    val screens: List<ContentScreens> = listOf(ComicsScreens, NovelScreens)

    /** Image loading the modules bring: PDF pages (comics). */
    fun addImageComponents(builder: ComponentRegistry.Builder) {
        builder.add(PdfPageKeyer())
        builder.add(PdfPageFetcher.Factory())
    }
}
