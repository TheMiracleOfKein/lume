package com.lume.app.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.graphics.Color
import com.lume.app.LocalAppContainer
import com.lume.app.data.library.parseCategoryIds
import com.lume.app.domain.LibraryStatus
import com.lume.app.domain.libraryStatusOf
import com.lume.app.ui.theme.AppColors

/** The colour bar for a library title of this status. */
@Composable
fun libraryStatusColor(status: LibraryStatus): Color = when (status) {
    LibraryStatus.Reading -> Color(0xFF43A047)
    LibraryStatus.Planned -> Color(0xFF1E88E5)
    LibraryStatus.Dropped -> Color(0xFFE53935)
    LibraryStatus.Completed -> Color(0xFF8E24AA)
    LibraryStatus.Other -> AppColors.accent
}

/** Library titles by [libraryMarkKey], each with its status; titles outside the library are absent. */
@Composable
fun rememberLibraryMarks(): Map<String, LibraryStatus> {
    val dao = LocalAppContainer.current.dao
    val bookmarks by remember(dao) { dao.observeBookmarks() }.collectAsState(initial = emptyList())
    val categories by remember(dao) { dao.observeCategories() }.collectAsState(initial = emptyList())
    return remember(bookmarks, categories) {
        val names = categories.associate { it.id to it.name }
        bookmarks.associate { b ->
            libraryMarkKey(b.sourceId, b.titleId) to
                libraryStatusOf(parseCategoryIds(b.categoryIdsJson).mapNotNull { names[it] })
        }
    }
}

fun libraryMarkKey(sourceId: String, titleId: String): String = "$sourceId\u0000$titleId"
