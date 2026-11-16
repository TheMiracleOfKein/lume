package com.lume.app

import com.lume.app.data.source.ComicsModule
import com.lume.app.data.source.ContentModule
import com.lume.app.data.source.NovelModule
import com.lume.app.ui.screens.ComicsScreens
import com.lume.app.ui.shell.ContentScreens

/** The full build: manga and ranobe. */
object InstalledModules {
    /** The flavor name; release assets are lume-v<version>-<flavor>.apk. */
    const val FLAVOR = "full"

    val modules: List<ContentModule> = listOf(ComicsModule, NovelModule)
    val screens: List<ContentScreens> = listOf(ComicsScreens)
}
