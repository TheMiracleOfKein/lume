package com.lume.app.ui.screens

import androidx.navigation.NavType
import androidx.navigation.navArgument
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.shell.ContentScreens

/** The comics reader, added to the app's navigation graph. */
val ComicsScreens = ContentScreens { graph, nav ->
    graph.screen(
        route = Routes.Reader,
        arguments = listOf(
            navArgument("sourceId") { type = NavType.StringType },
            navArgument("titleId") { type = NavType.StringType },
            navArgument("chapterId") { type = NavType.StringType },
            navArgument("page") { type = NavType.IntType; defaultValue = -1 },
            navArgument("offset") { type = NavType.FloatType; defaultValue = -1f },
        ),
    ) { entry ->
        val args = entry.arguments!!
        ReaderScreen(
            nav = nav,
            sourceId = args.getString("sourceId")!!,
            titleId = args.getString("titleId")!!,
            chapterId = args.getString("chapterId")!!,
            page = args.getInt("page").takeIf { it >= 0 },
            offset = args.getFloat("offset").takeIf { it >= 0f },
        )
    }
}
