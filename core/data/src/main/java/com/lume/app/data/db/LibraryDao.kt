package com.lume.app.data.db

import androidx.room.Dao

/**
 * The library database access. The queries live in per-area interfaces (bookmarks, progress,
 * downloads, codex ...); Room reads the inherited methods, so callers keep using this one type.
 */
@Dao
interface LibraryDao :
    BookmarkQueries,
    LibraryChapterQueries,
    ProgressQueries,
    PageBookmarkQueries,
    SessionQueries,
    DownloadQueries,
    SourceQueries,
    WatchedFolderQueries,
    CategoryQueries,
    TitleMetaQueries,
    TrackerQueries,
    GlossaryQueries,
    StoryTextQueries,
    DigestQueries,
    CodexFactQueries,
    CodexRelationQueries,
    CodexQueries

data class TitleKey(val sourceId: String, val titleId: String)

data class LinkedSourceCount(val sourceId: String, val titleId: String, val linked: Int)

data class TitleJsonRow(val sourceId: String, val titleId: String, val titleJson: String)

