package com.lume.app

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
}
