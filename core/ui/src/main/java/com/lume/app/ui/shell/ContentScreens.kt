package com.lume.app.ui.shell

import androidx.compose.runtime.Composable
import androidx.navigation.NamedNavArgument
import androidx.navigation.NavBackStackEntry
import androidx.navigation.NavGraphBuilder
import androidx.navigation.NavHostController
import com.lume.app.domain.MediaKind

/**
 * Screens a content module adds to the app's navigation graph: its reader. The app passes the
 * installed modules' screens to [LumeRoot]; the shell calls [register] while it builds its graph.
 */
fun interface ContentScreens {
    fun register(graph: ShellGraph, nav: NavHostController)
}

/** Where a reader opens: the title's chapter, and optionally a saved position inside it. */
data class ReaderArgs(
    val sourceId: String,
    val titleId: String,
    val chapterId: String,
    /** Saved page (comics) or block (novels), or null to use the stored progress. */
    val page: Int? = null,
    /** Saved offset inside that page or block, or null. */
    val offset: Float? = null,
)

/** Adds screens to the shell's graph with the shell's insets, transitions and back handling. */
class ShellGraph internal constructor(private val builder: NavGraphBuilder) {
    internal val readers = mutableMapOf<MediaKind, @Composable (NavHostController, ReaderArgs) -> Unit>()

    fun screen(
        route: String,
        arguments: List<NamedNavArgument> = emptyList(),
        content: @Composable (NavBackStackEntry) -> Unit,
    ) = builder.lumeComposable(route, arguments, content)

    /**
     * The reader for titles of [kind]. The shell registers the one reader route and opens the
     * reader of the source's kind, so callers keep navigating with `openReader` whatever the kind.
     */
    fun reader(kind: MediaKind, content: @Composable (NavHostController, ReaderArgs) -> Unit) {
        readers[kind] = content
    }

    internal val readerSettings = mutableMapOf<MediaKind, @Composable () -> Unit>()

    /**
     * The reader defaults that Settings → Reader shows while the app is in the [kind] mode. Without
     * one, the page shows the comics reader's defaults.
     */
    fun readerSettings(kind: MediaKind, content: @Composable () -> Unit) {
        readerSettings[kind] = content
    }
}

/**
 * The kind whose reader opens a chapter: the source's kind when it is installed, else the kind
 * the library bookmark remembers, else the kind of the title's saved reading progress (a History
 * entry that is not in the library), else comics.
 */
internal fun readerKindFor(sourceKind: MediaKind?, bookmarkKind: String?, progressKind: String? = null): MediaKind =
    sourceKind ?: bookmarkKind?.let { MediaKind.fromId(it) } ?: progressKind?.let { MediaKind.fromId(it) } ?: MediaKind.Comics
