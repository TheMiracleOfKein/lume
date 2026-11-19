package com.lume.app.data.tracker

import com.lume.app.domain.Chapter
import com.lume.app.domain.MediaKind
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
fun parseAniListReadingList(json: Json, text: String, kind: MediaKind = MediaKind.Comics): List<TrackerListEntry> {
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
            // Novels and comics share AniList's MANGA lists; the format tells them apart.
            if ((media["format"].text() == "NOVEL") != (kind == MediaKind.Novel)) continue
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
fun parseMalReadingList(
    json: Json,
    text: String,
    kind: MediaKind = MediaKind.Comics,
): Pair<List<TrackerListEntry>, String?> {
    val root = json.parseToJsonElement(text).jsonObject
    val data = root["data"] as? JsonArray ?: return emptyList<TrackerListEntry>() to null
    val next = (root["paging"] as? JsonObject)?.get("next").text()
    val entries = data.mapNotNull { el ->
        val item = el as? JsonObject ?: return@mapNotNull null
        val node = item["node"] as? JsonObject ?: return@mapNotNull null
        if (isMalNovel(node["media_type"].text()) != (kind == MediaKind.Novel)) return@mapNotNull null
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

/** MyAnimeList keeps light novels and novels in its manga lists; this is their `media_type`. */
fun isMalNovel(mediaType: String?): Boolean = mediaType == "light_novel" || mediaType == "novel"

/** Reads a MyAnimeList `/manga` search answer, keeping the titles of [kind]. */
fun parseMalSearch(json: Json, text: String, kind: MediaKind): List<TrackerSearchResult> {
    val data = json.parseToJsonElement(text).jsonObject["data"] as? JsonArray ?: return emptyList()
    return data.mapNotNull { el ->
        val node = (el as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
        if (isMalNovel(node["media_type"].text()) != (kind == MediaKind.Novel)) return@mapNotNull null
        val mangaId = node["id"].number() ?: return@mapNotNull null
        val alt = node["alternative_titles"] as? JsonObject
        TrackerSearchResult(
            remoteId = mangaId,
            title = node["title"].text() ?: return@mapNotNull null,
            url = "https://myanimelist.net/manga/$mangaId",
            coverUrl = (node["main_picture"] as? JsonObject)?.get("medium").text(),
            altTitles = listOfNotNull(alt?.get("en").text(), alt?.get("ja").text()) +
                (alt?.get("synonyms") as? JsonArray).orEmpty().mapNotNull { it.text() },
            format = TitleFormat.parse(node["media_type"].text()),
            year = TitleFormat.year(node["start_date"].text()),
        )
    }
}

/** Shikimori's search request: novels are under `/ranobe`, comics under `/mangas`. */
fun shikimoriSearchUrl(apiUrl: String, kind: MediaKind, encodedQuery: String): String =
    "$apiUrl/${if (kind == MediaKind.Novel) "ranobe" else "mangas"}?search=$encodedQuery&limit=10"

/** Reads a Shikimori `/mangas` or `/ranobe` search answer. */
fun parseShikimoriSearch(json: Json, text: String, kind: MediaKind, baseUrl: String): List<TrackerSearchResult> {
    val arr = json.parseToJsonElement(text) as? JsonArray ?: return emptyList()
    val section = if (kind == MediaKind.Novel) "ranobe" else "mangas"
    return arr.mapNotNull { el ->
        val obj = el as? JsonObject ?: return@mapNotNull null
        val id = obj["id"].number() ?: return@mapNotNull null
        val name = obj["name"].text()
        val russian = obj["russian"].text()
        val image = (obj["image"] as? JsonObject)?.get("original").text()
        TrackerSearchResult(
            remoteId = id,
            title = russian ?: name ?: return@mapNotNull null,
            url = "$baseUrl/$section/$id",
            coverUrl = image?.let { if (it.startsWith("http")) it else "$baseUrl$it" },
            altTitles = listOfNotNull(name, russian),
            format = TitleFormat.parse(obj["kind"].text()),
            year = TitleFormat.year(obj["aired_on"].text()),
        )
    }
}

/** Shikimori's `kind` of a manga-like title that is a ranobe. */
fun isShikimoriRanobe(kind: String?): Boolean = kind == "novel" || kind == "light_novel"

/**
 * Reads Shikimori's `/users/:id/manga_rates` answer (manga and ranobe together), keeping the
 * titles of [kind]. [baseUrl] is the site host the title links point to.
 */
fun parseShikimoriReadingList(json: Json, text: String, kind: MediaKind, baseUrl: String): List<TrackerListEntry> {
    val rates = json.parseToJsonElement(text) as? JsonArray ?: return emptyList()
    return rates.mapNotNull { el ->
        val rate = el as? JsonObject ?: return@mapNotNull null
        val manga = rate["manga"] as? JsonObject ?: return@mapNotNull null
        if (isShikimoriRanobe(manga["kind"].text()) != (kind == MediaKind.Novel)) return@mapNotNull null
        val id = manga["id"].number() ?: return@mapNotNull null
        val name = manga["name"].text()
        val russian = manga["russian"].text()
        val title = russian ?: name ?: return@mapNotNull null
        val image = (manga["image"] as? JsonObject)?.get("original").text()
        val section = if (kind == MediaKind.Novel) "ranobe" else "mangas"
        TrackerListEntry(
            remoteId = id,
            title = title,
            altTitles = listOfNotNull(name, russian).filter { normalName(it) != normalName(title) }
                .distinctBy { normalName(it) },
            coverUrl = image?.let { if (it.startsWith("http")) it else "$baseUrl$it" },
            url = "$baseUrl/$section/$id",
            chaptersRead = rate["chapters"].number()?.toIntOrNull() ?: 0,
            score = rate["score"].number()?.toFloatOrNull()?.takeIf { it > 0f },
        )
    }
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
