package com.lume.app

import coil3.ComponentRegistry
import com.lume.app.data.local.pdf.PdfPageFetcher
import com.lume.app.data.local.pdf.PdfPageKeyer
import com.lume.app.data.source.ComicsModule
import com.lume.app.data.source.ContentModule
import com.lume.app.ui.screens.ComicsScreens
import com.lume.app.ui.shell.ContentScreens

/** The manga build: comics only, without the novel module's code. */
object InstalledModules {
    /** The flavor name; release assets are lume-v<version>-<flavor>.apk. */
    const val FLAVOR = "manga"

    val modules: List<ContentModule> = listOf(ComicsModule)
    val screens: List<ContentScreens> = listOf(ComicsScreens)

    /** Image loading the modules bring: PDF pages (comics). */
    fun addImageComponents(builder: ComponentRegistry.Builder) {
        builder.add(PdfPageKeyer())
        builder.add(PdfPageFetcher.Factory())
    }
}
