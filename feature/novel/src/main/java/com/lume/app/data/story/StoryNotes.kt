package com.lume.app.data.story

import com.lume.app.data.db.CODEX_AI
import com.lume.app.data.db.CODEX_IGNORED
import com.lume.app.data.db.ChapterDigestEntity
import com.lume.app.data.db.CodexAliasEntity
import com.lume.app.data.db.CodexEntryEntity
import com.lume.app.data.db.CodexEntryWithAliases
import com.lume.app.data.db.CodexFactEntity
import com.lume.app.data.db.CodexRelationEntity
import com.lume.app.data.db.LibraryDao
import com.lume.app.llm.baseName
import com.lume.app.llm.nameKey
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A name the AI met in a text: its base form, other forms, translation (when translating), kind and a fact. */
data class NoteName(
    val name: String,
    val variants: List<String> = emptyList(),
    val target: String? = null,
    val kind: String? = null,
    val fact: String? = null,
)

/** How [b] relates to [a] ("sister", "member of"), as a text shows it. */
data class NoteRelation(val a: String, val b: String, val label: String)

/**
 * What the AI noted about a text (a chapter, or part of one): a short [summary], the [names] that
 * matter in it with a fact each, and [relations] between them. Translation brings these with each
 * part; the story pass with each chapter.
 */
data class ChapterNotes(
    val summary: String? = null,
    val names: List<NoteName> = emptyList(),
    val relations: List<NoteRelation> = emptyList(),
) {
    val isEmpty: Boolean get() = summary.isNullOrBlank() && names.isEmpty() && relations.isEmpty()

    companion object {
        /** Notes of a chapter's parts, in reading order, as one: summaries joined, names and relations together. */
        fun join(parts: List<ChapterNotes>): ChapterNotes = ChapterNotes(
            summary = parts.mapNotNull { it.summary?.trim()?.takeIf(String::isNotEmpty) }.joinToString(" ").ifEmpty { null },
            names = parts.flatMap { it.names },
            relations = parts.flatMap { it.relations },
        )
    }
}

private val NOTE_KINDS = setOf("character", "place", "group", "item")
private const val MAX_NAME_LENGTH = 60
private const val MAX_FACT_CHARS = 300
private const val MAX_LABEL_LENGTH = 80

/** Notes from the model's `{"summary", "names", "relations"}` object; missing parts are empty. */
internal fun parseNotes(o: JsonObject?): ChapterNotes {
    o ?: return ChapterNotes()
    fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }
    val names = (o["names"] as? JsonArray)?.mapNotNull { el ->
        val n = el as? JsonObject ?: return@mapNotNull null
        val name = n.str("name") ?: return@mapNotNull null
        val variants = (n["variants"] as? JsonArray)?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull?.trim()?.takeIf(String::isNotEmpty) }.orEmpty()
        NoteName(name, variants, n.str("target"), n.str("kind")?.lowercase(), n.str("fact"))
    }.orEmpty()
    val relations = (o["relations"] as? JsonArray)?.mapNotNull { el ->
        val r = el as? JsonObject ?: return@mapNotNull null
        NoteRelation(r.str("a") ?: return@mapNotNull null, r.str("b") ?: return@mapNotNull null, (r.str("label") ?: r.str("relation")) ?: return@mapNotNull null)
    }.orEmpty()
    return ChapterNotes(o.str("summary") ?: o.str("digest"), names, relations)
}

/**
 * The part of a prompt that asks for notes, written in [langName]. [summary] says how long the
 * summary is (a chapter's or a part's).
 */
internal fun notesInstructions(langName: String, summary: String): String =
    "\"summary\": $summary, in $langName, plain text. " +
        "\"names\": up to 8 characters, places, groups or organizations and items that matter in this text, each " +
        "{\"name\": as the original text writes it, in its base form without honorifics or possessive endings, " +
        "\"variants\": other forms of it the text uses, \"kind\": character, place, group or item, " +
        "\"fact\": one sentence in $langName on what this text shows about it}. " +
        "\"relations\": up to 5 between those names, each {\"a\": name, \"b\": name, \"label\": a few words in $langName saying what b is to a, " +
        "such as \"sister\", \"mentor\", \"member of\" or \"home town\"}. " +
        "Use only this text; don't guess what comes later."

/**
 * Stores AI story notes of a title: a chapter's digest, the names it met (into the codex, as
 * entries found by AI, without asking), a fact per name and chapter, and relations. Names are
 * matched by [nameKey] against every codex name and other spelling, so forms of one name meet in
 * one entry; a name whose entry the reader removed isn't added again.
 */
class StoryNotes(private val dao: LibraryDao, private val sourceId: String, private val titleId: String) {
    /**
     * Saves [notes] of [chapterId], written in [lang] by [model] (a service and model id). [order]
     * gives each chapter's place in reading order, so an entry first met further on (a chapter
     * translated ahead) moves back when an earlier chapter names it. [digest] stores the summary as
     * the chapter's digest; false when the notes cover only part of the chapter. [original] is the
     * chapter's text when known: a name not in it is dropped.
     */
    suspend fun save(
        chapterId: String,
        lang: String,
        model: String,
        notes: ChapterNotes,
        order: Map<String, Int>,
        digest: Boolean = true,
        original: String? = null,
    ) {
        val now = System.currentTimeMillis()
        if (digest) {
            notes.summary?.trim()?.takeIf { it.isNotEmpty() }?.let {
                dao.upsertDigest(ChapterDigestEntity(sourceId, titleId, chapterId, lang, it.take(MAX_DIGEST_CHARS), model, now))
            }
        }
        val codex = dao.codexWithIgnored(sourceId, titleId).toMutableList()
        val names = intakeNames(notes.names, original)
        val facts = LinkedHashMap<String, MutableList<String>>()
        for (n in names) {
            val entry = upsertEntry(codex, n, chapterId, order, now) ?: continue
            n.fact?.let { facts.getOrPut(nameKey(entry.name)) { mutableListOf() } += it }
        }
        if (facts.isNotEmpty()) {
            dao.upsertFacts(
                facts.map { (key, list) ->
                    CodexFactEntity(sourceId, titleId, key, chapterId, lang, list.distinct().joinToString(" ").take(MAX_FACT_CHARS), model, now)
                },
            )
        }
        saveRelations(notes.relations, codex, chapterId, model, now)
    }

    /** The entry for [n]: an existing one (with its new spellings added) or a new AI entry; null for a removed name. */
    private suspend fun upsertEntry(
        codex: MutableList<CodexEntryWithAliases>,
        n: NoteName,
        chapterId: String,
        order: Map<String, Int>,
        now: Long,
    ): CodexEntryEntity? {
        val keys = (listOf(n.name) + n.variants).map(::nameKey).toSet()
        val spellings = (n.variants + listOfNotNull(n.target)).map { it.trim() }.filter { it.isNotEmpty() && it.length <= MAX_NAME_LENGTH }
        val found = codex.indexOfFirst { e -> (listOf(e.entry.name) + e.aliases.map { it.alias }).any { nameKey(it) in keys } }
        if (found < 0) {
            val entry = CodexEntryEntity(
                sourceId = sourceId, titleId = titleId, name = n.name, kind = n.kind ?: "other",
                createdAt = now, updatedAt = now, origin = CODEX_AI, firstChapterId = chapterId,
            )
            val aliases = spellings.filter { !it.equals(n.name, ignoreCase = true) }.distinctBy { it.lowercase() }
            val id = dao.saveCodexEntry(entry, aliases)
            val saved = entry.copy(id = id)
            codex += CodexEntryWithAliases(saved, aliases.map { CodexAliasEntity(id, it) })
            return saved
        }
        val e = codex[found]
        if (e.entry.origin == CODEX_IGNORED) return null
        val known = (listOf(e.entry.name) + e.aliases.map { it.alias }).map { it.lowercase() }.toMutableSet()
        val added = spellings.filter { known.add(it.lowercase()) }
        val earlier = e.entry.origin == CODEX_AI && e.entry.firstChapterId != chapterId &&
            (order[chapterId] ?: Int.MAX_VALUE) < (order[e.entry.firstChapterId] ?: Int.MAX_VALUE)
        if (added.isEmpty() && !earlier) return e.entry
        val entry = if (earlier) e.entry.copy(firstChapterId = chapterId, updatedAt = now) else e.entry
        val aliases = e.aliases.map { it.alias } + added
        dao.saveCodexEntry(entry, aliases)
        codex[found] = CodexEntryWithAliases(entry, aliases.map { CodexAliasEntity(entry.id, it) })
        return entry
    }

    private suspend fun saveRelations(
        relations: List<NoteRelation>,
        codex: List<CodexEntryWithAliases>,
        chapterId: String,
        model: String,
        now: Long,
    ) {
        if (relations.isEmpty()) return
        fun canonical(name: String): String {
            val key = nameKey(name)
            return codex.firstOrNull { e -> (listOf(e.entry.name) + e.aliases.map { it.alias }).any { nameKey(it) == key } }?.entry?.name
                ?: baseName(name)
        }
        val seen = dao.titleRelations(sourceId, titleId)
            .mapTo(HashSet()) { Triple(nameKey(it.fromName), nameKey(it.toName), it.label.trim().lowercase()) }
        val fresh = relations.mapNotNull { r ->
            val from = canonical(r.a)
            val to = canonical(r.b)
            val label = r.label.trim().take(MAX_LABEL_LENGTH)
            if (nameKey(from) == nameKey(to) || label.isEmpty() || from.length > MAX_NAME_LENGTH || to.length > MAX_NAME_LENGTH) return@mapNotNull null
            // The first chapter that shows a relation is the one it is visible from.
            if (!seen.add(Triple(nameKey(from), nameKey(to), label.lowercase()))) return@mapNotNull null
            CodexRelationEntity(
                sourceId = sourceId, titleId = titleId, fromName = from, toName = to, label = label,
                upToChapterId = chapterId, origin = "$NOTES_ORIGIN$model", createdAt = now,
            )
        }
        if (fresh.isNotEmpty()) dao.insertRelations(fresh)
    }

    companion object {
        /** Origin prefix of relations from story notes; a summary only replaces its own. */
        const val NOTES_ORIGIN = "notes:"
        private const val MAX_DIGEST_CHARS = 1_500
    }
}

/**
 * The names worth keeping from [found]: in the codex kinds, a name (capital letter or CJK) of
 * sane length in its base form, present in [original] when that is known, one per [nameKey]
 * (facts and forms of repeats are joined).
 */
internal fun intakeNames(found: List<NoteName>, original: String?): List<NoteName> {
    val byKey = LinkedHashMap<String, NoteName>()
    for (raw in found) {
        val name = baseName(raw.name)
        if (name.isEmpty() || name.length > MAX_NAME_LENGTH) continue
        val first = name.first()
        if (!first.isUpperCase() && !isCjk(first)) continue
        if (original != null && !original.contains(name, ignoreCase = true)) continue
        val kind = raw.kind?.takeIf { it in NOTE_KINDS }
        val variants = (raw.variants + raw.name).map { it.trim() }.filter { it.isNotEmpty() && !it.equals(name, ignoreCase = true) && it.length <= MAX_NAME_LENGTH }
        val target = raw.target?.let(::baseName)?.takeIf { it.isNotEmpty() && !it.equals(name, ignoreCase = true) }
        val key = nameKey(name)
        val prev = byKey[key]
        byKey[key] = if (prev == null) {
            NoteName(name, variants.distinctBy { it.lowercase() }, target, kind, raw.fact?.trim()?.takeIf { it.isNotEmpty() })
        } else {
            prev.copy(
                variants = (prev.variants + variants).distinctBy { it.lowercase() },
                target = prev.target ?: target,
                kind = prev.kind ?: kind,
                fact = listOfNotNull(prev.fact, raw.fact?.trim()?.takeIf { it.isNotEmpty() }).distinct().joinToString(" ").ifEmpty { null },
            )
        }
    }
    return byKey.values.toList()
}

private fun isCjk(c: Char): Boolean = when (Character.UnicodeScript.of(c.code)) {
    Character.UnicodeScript.HAN, Character.UnicodeScript.HIRAGANA, Character.UnicodeScript.KATAKANA, Character.UnicodeScript.HANGUL -> true
    else -> false
}
