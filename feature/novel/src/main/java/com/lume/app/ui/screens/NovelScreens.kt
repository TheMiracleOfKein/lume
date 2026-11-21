package com.lume.app.ui.screens

import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.lume.app.domain.MediaKind
import com.lume.app.translate.GLOSSARY_ROUTE
import com.lume.app.translate.GlossaryScreen
import com.lume.app.ui.shell.ContentScreens

/** The novel reader, opened for titles of novel sources, its defaults in Settings → Reader, and the translation glossary. */
val NovelScreens = ContentScreens { graph, nav ->
    graph.readerSettings(MediaKind.Novel) { NovelReaderDefaults() }
    graph.reader(MediaKind.Novel) { navController, args ->
        NovelReaderScreen(
            nav = navController,
            sourceId = args.sourceId,
            titleId = args.titleId,
            chapterId = args.chapterId,
            block = args.page,
            offset = args.offset,
        )
    }
    graph.screen(
        GLOSSARY_ROUTE,
        arguments = listOf(
            navArgument("sourceId") { type = NavType.StringType },
            navArgument("titleId") { type = NavType.StringType },
            navArgument("name") { type = NavType.StringType; defaultValue = "" },
        ),
    ) { entry ->
        val args = entry.arguments
        GlossaryScreen(
            nav = nav,
            sourceId = args?.getString("sourceId").orEmpty(),
            titleId = args?.getString("titleId").orEmpty(),
            titleName = args?.getString("name").orEmpty(),
        )
    }
}
