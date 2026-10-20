package com.lume.app.data.tracker

import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.TrackerLinkEntity
import com.lume.app.data.network.AppHttp
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.data.similar.SimilarTitle
import com.lume.app.data.similar.parseMalRecommendations
import com.lume.app.util.urlEncode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import okhttp3.FormBody
import okhttp3.Request
import okhttp3.Response
import java.security.SecureRandom

/**
 * MyAnimeList (API v2, OAuth with PKCE "plain"). Needs Lume's own client ID in
 * `local.properties` (`LUME_MAL_CLIENT_ID`); the redirect URL registered with it is
 * [REDIRECT_URI]. Tokens are refreshed when the API answers 401.
 */
class MyAnimeListTracker(
    private val http: AppHttp,
    private val dao: LibraryDao,
    private val prefs: AppPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Tracker {
    override val id: String = ID
    override val displayName: String = "MyAnimeList"
    override val supportsDates: Boolean get() = true
    override val statuses: List<TrackStatus> get() = TrackStatus.entries

    private val clientId get() = TrackerOAuthDefaults.MAL_CLIENT_ID

    override suspend fun isLoggedIn(): Boolean = !prefs.trackerValue(id, "access_token").isNullOrBlank()

    /** Starts a sign-in: a fresh code verifier is stored, the URL opens in the login screen. */
    suspend fun buildAuthorizeUrl(): String? {
        if (clientId.isBlank()) return null
        val alphabet = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789-._~"
        val random = SecureRandom()
        val verifier = (1..96).map { alphabet[random.nextInt(alphabet.length)] }.joinToString("")
        prefs.setTrackerValue(id, "verifier", verifier)
        return "$OAUTH/authorize?response_type=code&client_id=$clientId&code_challenge=$verifier" +
            "&code_challenge_method=plain&redirect_uri=${urlEncode(REDIRECT_URI)}"
    }

    override fun authorizeUrl(): String? = null

    override suspend fun exchangeCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val verifier = prefs.trackerValue(id, "verifier") ?: return@withContext false
        if (clientId.isBlank()) return@withContext false
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "authorization_code")
            .add("code", code)
            .add("code_verifier", verifier)
            .add("redirect_uri", REDIRECT_URI)
            .build()
        storeTokens(Request.Builder().url("$OAUTH/token").post(body).build())
    }

    private suspend fun storeTokens(request: Request): Boolean =
        http.okHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) return@use false
            val obj = json.parseToJsonElement(text).jsonObject
            val token = (obj["access_token"] as? JsonPrimitive)?.contentOrNull ?: return@use false
            prefs.setTrackerValue(id, "access_token", token)
            (obj["refresh_token"] as? JsonPrimitive)?.contentOrNull?.let { prefs.setTrackerValue(id, "refresh_token", it) }
            true
        }

    private suspend fun refresh(): Boolean {
        val refresh = prefs.trackerValue(id, "refresh_token") ?: return false
        val body = FormBody.Builder()
            .add("client_id", clientId)
            .add("grant_type", "refresh_token")
            .add("refresh_token", refresh)
            .build()
        val ok = storeTokens(Request.Builder().url("$OAUTH/token").post(body).build())
        if (!ok) logout()
        return ok
    }

    override suspend fun logout() {
        listOf("access_token", "refresh_token", "verifier").forEach { prefs.setTrackerValue(id, it, null) }
    }

    /** One API call; on 401 the token is refreshed and the call made again. */
    private suspend fun <T> api(build: Request.Builder.() -> Unit, read: (Response) -> T): T? = withContext(Dispatchers.IO) {
        fun request(token: String) = Request.Builder().apply(build).header("Authorization", "Bearer $token").build()
        val token = prefs.trackerValue(id, "access_token") ?: return@withContext null
        val first = http.okHttp.newCall(request(token)).execute()
        if (first.code != 401) return@withContext first.use(read)
        first.close()
        if (!refresh()) return@withContext null
        val fresh = prefs.trackerValue(id, "access_token") ?: return@withContext null
        http.okHttp.newCall(request(fresh)).execute().use(read)
    }

    override suspend fun search(query: String): List<TrackerSearchResult> {
        val q = query.trim().take(64)
        if (q.isEmpty()) return emptyList()
        val url = "$API/manga?q=${urlEncode(q)}&limit=10&nsfw=true&fields=alternative_titles,main_picture"
        return api({ url(url).get() }) { response ->
            if (!response.isSuccessful) return@api emptyList()
            val data = json.parseToJsonElement(response.body.string()).jsonObject["data"] as? JsonArray ?: return@api emptyList()
            data.mapNotNull { el ->
                val node = (el as? JsonObject)?.get("node") as? JsonObject ?: return@mapNotNull null
                val mangaId = (node["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val alt = node["alternative_titles"] as? JsonObject
                TrackerSearchResult(
                    remoteId = mangaId,
                    title = (node["title"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null,
                    url = "https://myanimelist.net/manga/$mangaId",
                    coverUrl = ((node["main_picture"] as? JsonObject)?.get("medium") as? JsonPrimitive)?.contentOrNull,
                    altTitles = listOfNotNull(
                        (alt?.get("en") as? JsonPrimitive)?.contentOrNull,
                        (alt?.get("ja") as? JsonPrimitive)?.contentOrNull,
                    ) + (alt?.get("synonyms") as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                )
            }
        }.orEmpty()
    }

    /** What MyAnimeList users recommend for [remoteId]; null when signed out or on errors. */
    suspend fun recommendations(remoteId: String): List<SimilarTitle>? =
        api({ url("$API/manga/$remoteId?fields=recommendations").get() }) { response ->
            if (!response.isSuccessful) return@api null
            parseMalRecommendations(json, response.body.string())
        }

    override suspend fun link(sourceId: String, titleId: String, remoteId: String, remoteUrl: String?) {
        dao.upsertTrackerLink(
            TrackerLinkEntity(sourceId, titleId, id, remoteId, remoteUrl ?: "https://myanimelist.net/manga/$remoteId"),
        )
    }

    override suspend fun unlink(sourceId: String, titleId: String) = dao.deleteTrackerLink(sourceId, titleId, id)

    override suspend fun fetchEntry(remoteId: String): TrackerEntry? {
        val url = "$API/manga/$remoteId?fields=num_chapters,my_list_status{start_date,finish_date}"
        return api({ url(url).get() }) { response ->
            if (!response.isSuccessful) return@api null
            val obj = json.parseToJsonElement(response.body.string()).jsonObject
            val total = (obj["num_chapters"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()?.takeIf { it > 0 }
            val list = obj["my_list_status"] as? JsonObject ?: return@api TrackerEntry(totalChapters = total)
            fun str(key: String) = (list[key] as? JsonPrimitive)?.contentOrNull
            val rereading = str("is_rereading") == "true"
            TrackerEntry(
                status = if (rereading) TrackStatus.Rereading else STATUS.fromRemote(str("status")),
                score = str("score")?.toFloatOrNull() ?: 0f,
                chaptersRead = str("num_chapters_read")?.toIntOrNull() ?: 0,
                totalChapters = total,
                startDate = cleanDate(str("start_date")),
                finishDate = cleanDate(str("finish_date")),
            )
        }
    }

    override suspend fun updateEntry(remoteId: String, entry: TrackerEntry): Boolean {
        val form = FormBody.Builder()
            .add("num_chapters_read", entry.chaptersRead.toString())
            .add("score", entry.score.toInt().coerceIn(0, 10).toString())
            .add("is_rereading", (entry.status == TrackStatus.Rereading).toString())
        val status = if (entry.status == TrackStatus.Rereading) "reading" else entry.status?.let(STATUS::toRemote)
        status?.let { form.add("status", it) }
        cleanDate(entry.startDate)?.let { form.add("start_date", it) }
        cleanDate(entry.finishDate)?.let { form.add("finish_date", it) }
        val body = form.build()
        return api({ url("$API/manga/$remoteId/my_list_status").put(body) }) { it.isSuccessful } ?: false
    }

    override suspend fun fetchProgress(remoteId: String): TrackerProgress? =
        fetchEntry(remoteId)?.let { TrackerProgress(it.chaptersRead, it.status?.name, it.score) }

    override suspend fun updateProgress(remoteId: String, chaptersRead: Int): Boolean {
        val current = fetchEntry(remoteId) ?: return false
        // Reading moves "planned" (or no entry) to reading; other statuses are the user's choice.
        val status = if (current.status == null || current.status == TrackStatus.Planned) TrackStatus.Reading else current.status
        return updateEntry(remoteId, current.copy(chaptersRead = chaptersRead, status = status))
    }

    companion object {
        const val ID = "myanimelist"
        const val REDIRECT_URI = "lume://oauth/myanimelist"
        private const val OAUTH = "https://myanimelist.net/v1/oauth2"
        private const val API = "https://api.myanimelist.net/v2"
        private val STATUS = StatusMap(
            listOf(
                TrackStatus.Reading to "reading",
                TrackStatus.Completed to "completed",
                TrackStatus.OnHold to "on_hold",
                TrackStatus.Dropped to "dropped",
                TrackStatus.Planned to "plan_to_read",
            ),
        )
    }
}
