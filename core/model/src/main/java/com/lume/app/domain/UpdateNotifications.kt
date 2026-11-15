package com.lume.app.domain

/** The pseudo-category for titles that sit in no category. */
const val UNCATEGORIZED_ID = 0L

/** The categories of a title as the category settings see them: none means [UNCATEGORIZED_ID]. */
fun effectiveCategories(ids: Set<Long>): Set<Long> = ids.ifEmpty { setOf(UNCATEGORIZED_ID) }

/**
 * The results that may raise a notification. A title is muted only when every one of its
 * categories is muted; [categoriesOf] gives the categories for a "sourceId/titleId" key.
 */
fun notifiable(
    results: List<LibraryUpdateResult>,
    categoriesOf: (key: String) -> Set<Long>,
    muted: Set<Long>,
): List<LibraryUpdateResult> {
    if (muted.isEmpty()) return results
    return results.filterNot { r ->
        effectiveCategories(categoriesOf(titleKey(r.sourceId, r.titleId))).all { it in muted }
    }
}

fun titleKey(sourceId: String, titleId: String): String = "$sourceId/$titleId"
