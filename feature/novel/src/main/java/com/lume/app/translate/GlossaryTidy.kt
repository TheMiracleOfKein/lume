package com.lume.app.translate

import com.lume.app.data.db.GLOSSARY_CONFIRMED
import com.lume.app.data.db.GLOSSARY_SUGGESTED
import com.lume.app.data.db.GlossaryTermEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.llm.isBaseName
import com.lume.app.llm.nameKey

/** Terms that are forms of one name (same [nameKey]: "Yu San A" and "Yu San A-san"), two or more per group. */
internal fun sameNameGroups(terms: List<GlossaryTermEntity>): List<List<GlossaryTermEntity>> =
    terms.groupBy { nameKey(it.source) }.values.filter { it.size > 1 }

/**
 * Suggestions that only repeat a name the glossary already has in another form: all suggested
 * forms when one form is confirmed, else all but one (the base form, or the shortest). The
 * user's own terms are never dropped; groups of those are [confirmedDuplicates].
 */
internal fun redundantSuggestions(terms: List<GlossaryTermEntity>): List<GlossaryTermEntity> =
    sameNameGroups(terms).flatMap { group ->
        val suggested = group.filter { it.status == GLOSSARY_SUGGESTED }
        if (suggested.isEmpty()) return@flatMap emptyList()
        if (suggested.size < group.size) return@flatMap suggested
        val keep = suggested.firstOrNull { isBaseName(it.source) } ?: suggested.minBy { it.source.length }
        suggested - keep
    }

/** Groups of confirmed terms that are forms of one name, for the user to merge. */
internal fun confirmedDuplicates(terms: List<GlossaryTermEntity>): List<List<GlossaryTermEntity>> =
    sameNameGroups(terms.filter { it.status == GLOSSARY_CONFIRMED })
        .map { group -> group.sortedWith(compareBy({ !isBaseName(it.source) }, { it.source.length })) }

/**
 * Drops [redundantSuggestions] of a title's glossary: what AI translation proposed before names
 * were merged (up to v1.10.0, "Yu San A" and "Yu San A-san" could both be suggested).
 */
suspend fun tidyGlossary(dao: LibraryDao, sourceId: String, titleId: String) {
    redundantSuggestions(dao.glossaryOnce(sourceId, titleId)).forEach { dao.deleteGlossaryTerm(sourceId, titleId, it.source) }
}
