package com.lume.app.data.tracker

import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.TrackerLinkEntity
import com.lume.app.data.network.AppHttp
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.util.urlEncode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonNull
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * Kitsu (JSON:API at kitsu.app). Signs in with the account's e-mail and password through
 * Kitsu's public app client (the same one its own apps and Mihon use); only the tokens are kept.
 */
class KitsuTracker(
    private val http: AppHttp,
    private val dao: LibraryDao,
    private val prefs: AppPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Tracker {
    override val id: String = ID
    override val displayName: String = "Kitsu"
    override val login: TrackerLogin get() = TrackerLogin.Password
    override val supportsDates: Boolean get() = true
    override val statuses: List<TrackStatus> get() = TrackStatus.entries

    override suspend fun isLoggedIn(): Boolean = !prefs.trackerValue(id, "access_token").isNullOrBlank()

    override fun authorizeUrl(): String? = null
    override suspend fun exchangeCode(code: String): Boolean = false

    override suspend fun login(username: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val body = FormBody.Builder()
            .add("grant_type", "password")
            .add("username", username.trim())
            .add("password", password)
            .add("client_id", CLIENT_ID)
            .add("client_secret", CLIENT_SECRET)
            .build()
        if (!storeTokens(body)) return@withContext false
        val userId = api({ url("$API/users?filter[self]=true").get() }) { response ->
            if (!response.isSuccessful) return@api null
            val data = json.parseToJsonElement(response.body.string()).jsonObject["data"] as? JsonArray
            ((data?.firstOrNull() as? JsonObject)?.get("id") as? JsonPrimitive)?.contentOrNull
        }
        if (userId == null) {
            logout()
            return@withContext false
        }
        prefs.setTrackerValue(id, "user_id", userId)
        true
    }

    private suspend fun storeTokens(form: FormBody): Boolean {
        val request = Request.Builder().url(OAUTH).post(form).build()
        return http.okHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) return@use false
            val obj = json.parseToJsonElement(text).jsonObject
            val token = (obj["access_token"] as? JsonPrimitive)?.contentOrNull ?: return@use false
            prefs.setTrackerValue(id, "access_token", token)
            (obj["refresh_token"] as? JsonPrimitive)?.contentOrNull?.let { prefs.setTrackerValue(id, "refresh_token", it) }
            true
        }
    }

    private suspend fun refresh(): Boolean {
        val refresh = prefs.trackerValue(id, "refresh_token") ?: return false
        val ok = storeTokens(
            FormBody.Builder()
                .add("grant_type", "refresh_token")
                .add("refresh_token", refresh)
                .add("client_id", CLIENT_ID)
                .add("client_secret", CLIENT_SECRET)
                .build(),
        )
        if (!ok) logout()
        return ok
    }

    override suspend fun logout() {
        listOf("access_token", "refresh_token", "user_id").forEach { prefs.setTrackerValue(id, it, null) }
    }

    private suspend fun <T> api(build: Request.Builder.() -> Unit, read: (Response) -> T): T? = withContext(Dispatchers.IO) {
        fun request(token: String?) = Request.Builder().apply(build)
            .header("Accept", JSON_API)
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .build()
        val token = prefs.trackerValue(id, "access_token")
        val first = http.okHttp.newCall(request(token)).execute()
        if (first.code != 401 || token == null) return@withContext first.use(read)
        first.close()
        if (!refresh()) return@withContext null
        http.okHttp.newCall(request(prefs.trackerValue(id, "access_token"))).execute().use(read)
    }

    override suspend fun search(query: String): List<TrackerSearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        return api({ url("$API/manga?filter[text]=${urlEncode(q)}&page[limit]=10").get() }) { response ->
            if (!response.isSuccessful) return@api emptyList()
            val data = json.parseToJsonElement(response.body.string()).jsonObject["data"] as? JsonArray ?: return@api emptyList()
            data.mapNotNull { el ->
                val obj = el as? JsonObject ?: return@mapNotNull null
                val mangaId = (obj["id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val attrs = obj["attributes"] as? JsonObject ?: return@mapNotNull null
                val titles = (attrs["titles"] as? JsonObject)?.values?.mapNotNull { (it as? JsonPrimitive)?.contentOrNull }.orEmpty()
                val slug = (attrs["slug"] as? JsonPrimitive)?.contentOrNull
                TrackerSearchResult(
                    remoteId = mangaId,
                    title = (attrs["canonicalTitle"] as? JsonPrimitive)?.contentOrNull ?: titles.firstOrNull() ?: return@mapNotNull null,
                    url = "https://kitsu.app/manga/${slug ?: mangaId}",
                    coverUrl = ((attrs["posterImage"] as? JsonObject)?.get("small") as? JsonPrimitive)?.contentOrNull,
                    altTitles = titles + (attrs["abbreviatedTitles"] as? JsonArray).orEmpty().mapNotNull { (it as? JsonPrimitive)?.contentOrNull },
                )
            }
        }.orEmpty()
    }

    override suspend fun link(sourceId: String, titleId: String, remoteId: String, remoteUrl: String?) {
        dao.upsertTrackerLink(TrackerLinkEntity(sourceId, titleId, id, remoteId, remoteUrl ?: "https://kitsu.app/manga/$remoteId"))
    }

    override suspend fun unlink(sourceId: String, titleId: String) = dao.deleteTrackerLink(sourceId, titleId, id)

    /** The library entry (id + attributes) for a manga, or null when it is not in the list. */
    private suspend fun libraryEntry(remoteId: String): Pair<String, JsonObject>? {
        val userId = prefs.trackerValue(id, "user_id") ?: return null
        return api({ url("$API/library-entries?filter[userId]=$userId&filter[mangaId]=$remoteId&filter[kind]=manga").get() }) { response ->
            if (!response.isSuccessful) error("HTTP ${response.code}")
            val first = (json.parseToJsonElement(response.body.string()).jsonObject["data"] as? JsonArray)?.firstOrNull() as? JsonObject
                ?: return@api null
            val entryId = (first["id"] as? JsonPrimitive)?.contentOrNull ?: return@api null
            entryId to (first["attributes"] as? JsonObject ?: JsonObject(emptyMap()))
        }
    }

    private suspend fun totalChapters(remoteId: String): Int? =
        api({ url("$API/manga/$remoteId?fields[manga]=chapterCount").get() }) { response ->
            if (!response.isSuccessful) return@api null
            val attrs = (json.parseToJsonElement(response.body.string()).jsonObject["data"] as? JsonObject)?.get("attributes") as? JsonObject
            (attrs?.get("chapterCount") as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        }

    override suspend fun fetchEntry(remoteId: String): TrackerEntry? {
        if (!isLoggedIn()) return null
        val found = runCatching { libraryEntry(remoteId) }.getOrElse { return null }
        val total = runCatching { totalChapters(remoteId) }.getOrNull()
        val attrs = found?.second ?: return TrackerEntry(totalChapters = total)
        fun str(key: String) = (attrs[key] as? JsonPrimitive)?.contentOrNull
        val reconsuming = str("reconsuming") == "true"
        return TrackerEntry(
            status = if (reconsuming) TrackStatus.Rereading else STATUS.fromRemote(str("status")),
            // ratingTwenty is 2–20.
            score = (str("ratingTwenty")?.toFloatOrNull() ?: 0f) / 2f,
            chaptersRead = str("progress")?.toIntOrNull() ?: 0,
            totalChapters = total,
            startDate = cleanDate(str("startedAt")),
            finishDate = cleanDate(str("finishedAt")),
        )
    }

    override suspend fun updateEntry(remoteId: String, entry: TrackerEntry): Boolean {
        val userId = prefs.trackerValue(id, "user_id") ?: return false
        val existing = runCatching { libraryEntry(remoteId) }.getOrElse { return false }
        val status = if (entry.status == TrackStatus.Rereading) "current" else entry.status?.let(STATUS::toRemote) ?: "current"
        val attributes = buildJsonObject {
            put("status", status)
            put("progress", entry.chaptersRead)
            put("reconsuming", entry.status == TrackStatus.Rereading)
            val rating = (entry.score * 2).toInt()
            if (rating in 2..20) put("ratingTwenty", rating) else put("ratingTwenty", JsonNull)
            cleanDate(entry.startDate)?.let { put("startedAt", "${it}T00:00:00.000Z") } ?: put("startedAt", JsonNull)
            cleanDate(entry.finishDate)?.let { put("finishedAt", "${it}T00:00:00.000Z") } ?: put("finishedAt", JsonNull)
        }
        val payload = buildJsonObject {
            put("data", buildJsonObject {
                put("type", "libraryEntries")
                existing?.let { put("id", it.first) }
                put("attributes", attributes)
                if (existing == null) {
                    put("relationships", buildJsonObject {
                        put("user", buildJsonObject { put("data", buildJsonObject { put("type", "users"); put("id", userId) }) })
                        put("media", buildJsonObject { put("data", buildJsonObject { put("type", "manga"); put("id", remoteId) }) })
                    })
                }
            })
        }.toString().toRequestBody(JSON_API.toMediaType())
        return api({
            if (existing != null) url("$API/library-entries/${existing.first}").patch(payload)
            else url("$API/library-entries").post(payload)
        }) { it.isSuccessful } ?: false
    }

    override suspend fun fetchProgress(remoteId: String): TrackerProgress? =
        fetchEntry(remoteId)?.let { TrackerProgress(it.chaptersRead, it.status?.name, it.score) }

    override suspend fun updateProgress(remoteId: String, chaptersRead: Int): Boolean {
        val current = fetchEntry(remoteId) ?: return false
        val status = if (current.status == null || current.status == TrackStatus.Planned) TrackStatus.Reading else current.status
        return updateEntry(remoteId, current.copy(chaptersRead = chaptersRead, status = status))
    }

    companion object {
        const val ID = "kitsu"
        private const val API = "https://kitsu.app/api/edge"
        private const val OAUTH = "https://kitsu.app/api/oauth/token"
        private const val JSON_API = "application/vnd.api+json"

        // Kitsu's public app client (not a user secret).
        private const val CLIENT_ID = "dd031b32d2f56c990b1425efe6c42ad847e7fe3ab46bf1299f05ecd856bdb7dd"
        private const val CLIENT_SECRET = "54d7307928f63414defd96399fc31ba847961ceaecef3a5fd93144e960c0e151"

        private val STATUS = StatusMap(
            listOf(
                TrackStatus.Reading to "current",
                TrackStatus.Completed to "completed",
                TrackStatus.OnHold to "on_hold",
                TrackStatus.Dropped to "dropped",
                TrackStatus.Planned to "planned",
            ),
        )
    }
}
