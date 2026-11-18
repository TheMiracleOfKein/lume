package com.lume.app

import coil3.ComponentRegistry
import com.lume.app.data.source.ContentModule
import com.lume.app.data.source.NovelModule
import com.lume.app.ui.screens.NovelScreens
import com.lume.app.ui.shell.ContentScreens

/** The ranobe build: novels only, without the comics module's code. */
object InstalledModules {
    /** The flavor name; release assets are lume-v<version>-<flavor>.apk. */
    const val FLAVOR = "ranobe"

    val modules: List<ContentModule> = listOf(NovelModule)
    val screens: List<ContentScreens> = listOf(NovelScreens)

    /** Image loading the modules bring: none beyond the web (PDF pages are comics). */
    @Suppress("UNUSED_PARAMETER")
    fun addImageComponents(builder: ComponentRegistry.Builder) = Unit
}
