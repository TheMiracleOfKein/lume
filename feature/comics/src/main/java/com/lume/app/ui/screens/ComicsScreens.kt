package com.lume.app.ui.screens

import com.lume.app.domain.MediaKind
import com.lume.app.ui.shell.ContentScreens

/** The comics reader; the shell opens it for titles of comics sources. */
val ComicsScreens = ContentScreens { graph, _ ->
    graph.reader(MediaKind.Comics) { nav, args ->
        ReaderScreen(
            nav = nav,
            sourceId = args.sourceId,
            titleId = args.titleId,
            chapterId = args.chapterId,
            page = args.page,
            offset = args.offset,
        )
    }
}
