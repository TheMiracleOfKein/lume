package com.lume.app.data.tracker

import com.lume.app.domain.Chapter
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.TitleMatch
import com.lume.app.domain.chapterSortKey
import com.lume.app.domain.bestNameSimilarity
import com.lume.app.domain.normalName
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject

/** One title of the user's "Reading" list on a tracker. */
data class TrackerListEntry(
    val remoteId: String,
    val title: String,
    /** Other names (romaji, English, native, synonyms), used to find the title on sources. */
    val altTitles: List<String> = emptyList(),
    val coverUrl: String? = null,
    val url: String? = null,
    val chaptersRead: Int = 0,
    /** 0–10, null when not scored. */
    val score: Float? = null,
    val authors: List<String> = emptyList(),
)

private fun JsonElement?.text(): String? =
    (this as? JsonPrimitive)?.takeIf { it.isString }?.contentOrNull?.trim()?.takeIf { it.isNotEmpty() }

private fun JsonElement?.number(): String? = (this as? JsonPrimitive)?.contentOrNull

/** Reads the `Viewer { id }` answer: the signed-in AniList user's id. */
fun parseAniListViewerId(json: Json, text: String): Long? =
    (json.parseToJsonElement(text).jsonObject["data"] as? JsonObject)
        ?.let { it["Viewer"] as? JsonObject }?.get("id").number()?.toLongOrNull()

/**
 * Reads a `MediaListCollection` answer (all lists of one status). A title in several lists
 * (custom lists) is kept once.
 */
fun parseAniListReadingList(json: Json, text: String): List<TrackerListEntry> {
    val data = json.parseToJsonElement(text).jsonObject["data"] as? JsonObject ?: return emptyList()
    val lists = (data["MediaListCollection"] as? JsonObject)?.get("lists") as? JsonArray ?: return emptyList()
    val seen = HashSet<String>()
    val out = mutableListOf<TrackerListEntry>()
    for (list in lists) {
        val entries = (list as? JsonObject)?.get("entries") as? JsonArray ?: continue
        for (el in entries) {
            val entry = el as? JsonObject ?: continue
            val media = entry["media"] as? JsonObject ?: continue
            val id = media["id"].number()?.toLongOrNull()?.toString() ?: continue
            val titles = media["title"] as? JsonObject
            val names = listOf("english", "romaji", "native").mapNotNull { titles?.get(it).text() }
            val title = names.firstOrNull() ?: continue
            if (!seen.add(id)) continue
            val synonyms = (media["synonyms"] as? JsonArray).orEmpty().mapNotNull { it.text() }
            val staff = ((media["staff"] as? JsonObject)?.get("nodes") as? JsonArray).orEmpty().mapNotNull {
                ((it as? JsonObject)?.get("name") as? JsonObject)?.get("full").text()
            }
            out += TrackerListEntry(
                remoteId = id,
                title = title,
                altTitles = (names.drop(1) + synonyms)
                    .filter { normalName(it) != normalName(title) }
                    .distinctBy { normalName(it) },
                coverUrl = (media["coverImage"] as? JsonObject)?.get("large").text(),
                url = media["siteUrl"].text(),
                chaptersRead = entry["progress"].number()?.toIntOrNull() ?: 0,
                score = entry["score"].number()?.toFloatOrNull()?.takeIf { it > 0f },
                authors = staff,
            )
        }
    }
    return out
}

/** Reads one page of MyAnimeList's `mangalist`: its entries and the link to the next page, if any. */
fun parseMalReadingList(json: Json, text: String): Pair<List<TrackerListEntry>, String?> {
    val root = json.parseToJsonElement(text).jsonObject
    val data = root["data"] as? JsonArray ?: return emptyList<TrackerListEntry>() to null
    val next = (root["paging"] as? JsonObject)?.get("next").text()
    val entries = data.mapNotNull { el ->
        val item = el as? JsonObject ?: return@mapNotNull null
        val node = item["node"] as? JsonObject ?: return@mapNotNull null
        val id = node["id"].number() ?: return@mapNotNull null
        val title = node["title"].text() ?: return@mapNotNull null
        val alt = node["alternative_titles"] as? JsonObject
        val altTitles = (
            listOfNotNull(alt?.get("en").text(), alt?.get("ja").text()) +
                (alt?.get("synonyms") as? JsonArray).orEmpty().mapNotNull { it.text() }
            )
            .filter { normalName(it) != normalName(title) }
            .distinctBy { normalName(it) }
        val authors = (node["authors"] as? JsonArray).orEmpty().mapNotNull {
            val n = (it as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
            listOfNotNull(n["first_name"].text(), n["last_name"].text()).joinToString(" ").takeIf { s -> s.isNotBlank() }
        }
        val list = item["list_status"] as? JsonObject
        TrackerListEntry(
            remoteId = id,
            title = title,
            altTitles = altTitles,
            coverUrl = (node["main_picture"] as? JsonObject)?.get("medium").text(),
            url = "https://myanimelist.net/manga/$id",
            chaptersRead = list?.get("num_chapters_read").number()?.toIntOrNull() ?: 0,
            score = list?.get("score").number()?.toFloatOrNull()?.takeIf { it > 0f },
            authors = authors,
        )
    }
    return entries to next
}

/** What "Found for you" searches with: the entry's names and authors as a title. */
fun TrackerListEntry.toTitleDetails(): TitleDetails = TitleDetails(
    id = remoteId,
    title = title,
    coverUrl = coverUrl,
    authors = authors,
    otherNames = altTitles,
)

/** A title found on a source for a list entry. */
data class ListImportCandidate(
    val sourceId: String,
    val sourceName: String,
    val titleId: String,
    val title: String,
    val coverUrl: String?,
    val match: TitleMatch,
)

/**
 * The candidate to add, picked like "Found for you": only clear matches ([TitleMatch.Same]),
 * the one named exactly like the entry first, then the closest name, then the source name's order.
 */
fun chooseListCandidate(entry: TrackerListEntry, candidates: List<ListImportCandidate>): ListImportCandidate? {
    val names = listOf(entry.title) + entry.altTitles
    val wanted = names.map { normalName(it) }.filter { it.isNotEmpty() }.toSet()
    return candidates
        .filter { it.match == TitleMatch.Same }
        .sortedWith(
            compareByDescending<ListImportCandidate> { normalName(it.title) in wanted }
                .thenByDescending { bestNameSimilarity(names, listOf(it.title)) }
                .thenBy { it.sourceName.lowercase() },
        )
        .firstOrNull()
}

/** The chapters a tracker's "N chapters read" covers: numbered 1..N (11.5 counts as 11). */
fun chaptersCoveredBy(chapters: List<Chapter>, chaptersRead: Int): List<Chapter> {
    if (chaptersRead <= 0) return emptyList()
    // Chapters without a number are left out: their position in the list says nothing about order.
    return chapters.filter { chapterSortKey(it) != null && chapterProgressNumber(chapters, it.id) in 1..chaptersRead }
}
