package com.lume.app.ui.screens

import com.lume.app.domain.MediaKind
import com.lume.app.ui.shell.ContentScreens

/** The novel reader; the shell opens it for titles of novel sources. */
val NovelScreens = ContentScreens { graph, _ ->
    graph.reader(MediaKind.Novel) { nav, args ->
        NovelReaderScreen(
            nav = nav,
            sourceId = args.sourceId,
            titleId = args.titleId,
            chapterId = args.chapterId,
            block = args.page,
            offset = args.offset,
        )
    }
}
