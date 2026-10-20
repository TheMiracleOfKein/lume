package com.lume.app.data.similar

import com.lume.app.data.db.LibraryDao
import com.lume.app.data.network.AppHttp
import com.lume.app.data.tracker.AniListTracker
import com.lume.app.data.tracker.MyAnimeListTracker
import com.lume.app.domain.TitleDetails
import com.lume.app.domain.allNames
import com.lume.app.domain.searchNames
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

/**
 * "Similar titles" for the title screen. AniList's public API needs no account: the title is
 * looked up by its AniList link or by name, and its recommendations are read. When AniList has
 * nothing and the user is signed in to MyAnimeList, MyAnimeList is asked the same way.
 *
 * Answers are kept in memory per title for as long as the app runs; nothing is stored.
 */
class SimilarTitlesRepository(
    private val http: AppHttp,
    private val dao: LibraryDao,
    private val anilist: AniListTracker,
    private val mal: MyAnimeListTracker,
    private val json: Json = Json { ignoreUnknownKeys = true },
) {
    private class Failed : Exception()

    private val cache = object : LinkedHashMap<String, SimilarResult>(32, 0.75f, true) {
        override fun removeEldestEntry(eldest: MutableMap.MutableEntry<String, SimilarResult>?) = size > CACHE_SIZE
    }

    private fun key(sourceId: String, titleId: String) = "$sourceId\u0000$titleId"

    /** The answer from earlier in this run, if any. */
    fun peek(sourceId: String, titleId: String, details: TitleDetails, allowAdult: Boolean): SimilarResult? =
        synchronized(cache) { cache[key(sourceId, titleId)] }?.cleaned(details, allowAdult)

    /**
     * Recommendations for the title; an empty result when no tracker knows it, null when a
     * request failed (nothing cached, so the next visit tries again).
     */
    suspend fun find(sourceId: String, titleId: String, details: TitleDetails, allowAdult: Boolean): SimilarResult? {
        peek(sourceId, titleId, details, allowAdult)?.let { return it }
        val result = try {
            lookUp(sourceId, titleId, details)
        } catch (e: CancellationException) {
            throw e
        } catch (_: Exception) {
            return null
        }
        synchronized(cache) { cache[key(sourceId, titleId)] = result }
        return result.cleaned(details, allowAdult)
    }

    private fun SimilarResult.cleaned(details: TitleDetails, allowAdult: Boolean) =
        copy(items = cleanSimilar(items, allNames(details), allowAdult))

    private suspend fun lookUp(sourceId: String, titleId: String, details: TitleDetails): SimilarResult {
        val names = allNames(details)
        val links = dao.getTrackerLinks(sourceId, titleId)
        val aniListId = links.firstOrNull { it.tracker == anilist.id }?.remoteId?.toLongOrNull()
        val fromAniList = if (aniListId != null) {
            aniList(aniListByIdBody(aniListId)).firstOrNull()?.similar.orEmpty()
        } else {
            // The title, then one other name (sources often list the English one there).
            searchNames(details).take(2).firstNotNullOfOrNull { query ->
                pickSimilarCandidate(names, aniList(aniListSearchBody(json, query.take(80))))
            }?.similar.orEmpty()
        }
        if (fromAniList.isNotEmpty() || !mal.isLoggedIn()) return SimilarResult(fromAniList, ANILIST)

        val malId = links.firstOrNull { it.tracker == mal.id }?.remoteId ?: run {
            val results = mal.search(details.title)
            val candidates = results.map { SimilarCandidate(it.remoteId, listOf(it.title) + it.altTitles, emptyList()) }
            pickSimilarCandidate(names, candidates)?.remoteId
        } ?: return SimilarResult(emptyList(), MYANIMELIST)
        val fromMal = mal.recommendations(malId) ?: throw Failed()
        return SimilarResult(fromMal, MYANIMELIST)
    }

    private suspend fun aniList(body: String): List<SimilarCandidate> = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .build()
        http.okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw Failed()
            parseAniListSimilar(json, response.body.string())
        }
    }

    companion object {
        const val ANILIST = "AniList"
        const val MYANIMELIST = "MyAnimeList"
        private const val CACHE_SIZE = 100
    }
}
