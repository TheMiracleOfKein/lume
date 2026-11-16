package com.lume.app.ui.shell

import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController

/**
 * Screens a content module adds to the app's navigation graph (the comics reader, for now).
 * The app passes the installed modules' screens to [LumeRoot]; the shell calls [register] while
 * it builds its graph.
 */
fun interface ContentScreens {
    fun register(graph: ShellGraph, nav: NavHostController)
}

/** Adds screens to the shell's graph with the shell's insets, transitions and back handling. */
class ShellGraph internal constructor(private val builder: NavGraphBuilder) {
    fun screen(
        route: String,
        arguments: List<NamedNavArgument> = emptyList(),
        content: @Composable (NavBackStackEntry) -> Unit,
    ) = builder.lumeComposable(route, arguments, content)
}
