package com.lume.app.ui.screens

import com.lume.app.domain.MediaKind
import com.lume.app.ui.shell.ContentScreens

/** The novel reader, opened for titles of novel sources, and its defaults in Settings → Reader. */
val NovelScreens = ContentScreens { graph, _ ->
    graph.readerSettings(MediaKind.Novel) { NovelReaderDefaults() }
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
