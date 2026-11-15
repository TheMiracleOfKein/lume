package com.lume.app.data.similar

import com.lume.app.domain.bestNameSimilarity
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonElement
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull

/** A title a tracker recommends for another one. A tap searches the user's sources for [title]. */
data class SimilarTitle(
    val title: String,
    val coverUrl: String? = null,
    val url: String? = null,
    val adult: Boolean = false,
)

/** A tracker entry found by searching for the title, with what that tracker recommends for it. */
data class SimilarCandidate(
    val remoteId: String,
    val names: List<String>,
    val similar: List<SimilarTitle>,
)

/** Recommendations for one title and the tracker they came from ("AniList", "MyAnimeList"). */
data class SimilarResult(
    val items: List<SimilarTitle>,
    val from: String,
)

/** The most recommendations a row shows. */
const val MAX_SIMILAR = 15

/**
 * The candidate that is this title: an equal name first (after normalising), else the closest
 * one that shares most of its words. Null when nothing is close enough, so a search that found
 * another title never shows that title's recommendations.
 */
fun pickSimilarCandidate(names: List<String>, candidates: List<SimilarCandidate>, minSimilarity: Double = 0.75): SimilarCandidate? {
    if (names.isEmpty()) return null
    val scored = candidates.map { it to bestNameSimilarity(names, it.names) }
    scored.firstOrNull { it.second >= 1.0 }?.let { return it.first }
    return scored.filter { it.second >= minSimilarity }.maxByOrNull { it.second }?.first
}

/** Recommendations without duplicates, without the title itself, adult ones only when allowed. */
fun cleanSimilar(items: List<SimilarTitle>, ownNames: List<String>, allowAdult: Boolean): List<SimilarTitle> =
    items
        .filter { allowAdult || !it.adult }
        .filter { bestNameSimilarity(listOf(it.title), ownNames) < 1.0 }
        .distinctBy { it.title.lowercase() }
        .take(MAX_SIMILAR)

// ---- AniList --------------------------------------------------------------------------------

private const val ANILIST_TITLE = "title{romaji english native}"
private const val ANILIST_FIELDS =
    "id $ANILIST_TITLE synonyms recommendations(sort:RATING_DESC,perPage:20){nodes{rating mediaRecommendation{id $ANILIST_TITLE coverImage{large} siteUrl isAdult}}}"

/** GraphQL body that searches AniList's manga for [query] and asks for each result's recommendations. */
fun aniListSearchBody(json: Json, query: String): String =
    """{"query":"query(${'$'}search:String){Page(perPage:5){media(search:${'$'}search,type:MANGA){$ANILIST_FIELDS}}}","variables":{"search":${json.encodeToString(query)}}}"""

/** GraphQL body for the recommendations of one AniList manga (a title already linked to AniList). */
fun aniListByIdBody(mediaId: Long): String =
    """{"query":"query(${'$'}id:Int){Media(id:${'$'}id,type:MANGA){$ANILIST_FIELDS}}","variables":{"id":$mediaId}}"""

/** Candidates from an AniList answer to either body above; empty for errors or no data. */
fun parseAniListSimilar(json: Json, text: String): List<SimilarCandidate> {
    val data = runCatching { json.parseToJsonElement(text) }.getOrNull().obj()?.get("data").obj() ?: return emptyList()
    val media = data["Page"].obj()?.get("media") as? JsonArray
        ?: listOfNotNull(data["Media"].obj())
    return media.mapNotNull { el ->
        val obj = el.obj() ?: return@mapNotNull null
        val id = obj["id"].str() ?: return@mapNotNull null
        val names = aniListNames(obj["title"].obj()) + (obj["synonyms"] as? JsonArray).orEmpty().mapNotNull { it.str() }
        val nodes = (obj["recommendations"].obj()?.get("nodes") as? JsonArray).orEmpty()
        val similar = nodes.mapNotNull { node ->
            val n = node.obj() ?: return@mapNotNull null
            // Rated down by AniList users: not a recommendation any more.
            if ((n["rating"].str()?.toIntOrNull() ?: 0) < 0) return@mapNotNull null
            val rec = n["mediaRecommendation"].obj() ?: return@mapNotNull null
            val name = aniListNames(rec["title"].obj()).firstOrNull() ?: return@mapNotNull null
            SimilarTitle(
                title = name,
                coverUrl = rec["coverImage"].obj()?.get("large").str(),
                url = rec["siteUrl"].str(),
                adult = rec["isAdult"].str() == "true",
            )
        }
        SimilarCandidate(id, names.distinct(), similar)
    }
}

/** English first: it is the name most sources know a title by. */
private fun aniListNames(title: JsonObject?): List<String> =
    listOf("english", "romaji", "native").mapNotNull { title?.get(it).str()?.trim()?.takeIf(String::isNotEmpty) }

// ---- MyAnimeList ----------------------------------------------------------------------------

/** Recommendations from `GET /v2/manga/{id}?fields=recommendations`. */
fun parseMalRecommendations(json: Json, text: String): List<SimilarTitle> {
    val root = runCatching { json.parseToJsonElement(text) }.getOrNull().obj() ?: return emptyList()
    return (root["recommendations"] as? JsonArray).orEmpty().mapNotNull { el ->
        val node = el.obj()?.get("node").obj() ?: return@mapNotNull null
        val id = node["id"].str() ?: return@mapNotNull null
        val title = node["title"].str()?.trim()?.takeIf(String::isNotEmpty) ?: return@mapNotNull null
        val picture = node["main_picture"].obj()
        SimilarTitle(
            title = title,
            coverUrl = picture?.get("large").str() ?: picture?.get("medium").str(),
            url = "https://myanimelist.net/manga/$id",
        )
    }
}

private fun JsonElement?.obj(): JsonObject? = this as? JsonObject

private fun JsonElement?.str(): String? = (this as? JsonPrimitive)?.contentOrNull
