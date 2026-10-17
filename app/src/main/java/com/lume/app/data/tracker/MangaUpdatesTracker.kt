package com.lume.app.data.tracker

import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.TrackerLinkEntity
import com.lume.app.data.network.AppHttp
import com.lume.app.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonArray
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.put
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response

/**
 * MangaUpdates (Baka-Updates, API v1). Signs in with username and password; only the session
 * token is kept. Lists: 0 reading, 1 wish list, 2 complete, 3 unfinished, 4 on hold. No dates.
 */
class MangaUpdatesTracker(
    private val http: AppHttp,
    private val dao: LibraryDao,
    private val prefs: AppPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Tracker {
    override val id: String = ID
    override val displayName: String = "MangaUpdates"
    override val login: TrackerLogin get() = TrackerLogin.Password
    override val statuses: List<TrackStatus> get() = STATUS.keys.toList()

    private val jsonType = "application/json".toMediaType()

    override suspend fun isLoggedIn(): Boolean = !prefs.trackerValue(id, "session_token").isNullOrBlank()

    override fun authorizeUrl(): String? = null
    override suspend fun exchangeCode(code: String): Boolean = false

    override suspend fun login(username: String, password: String): Boolean = withContext(Dispatchers.IO) {
        val body = buildJsonObject {
            put("username", username.trim())
            put("password", password)
        }.toString().toRequestBody(jsonType)
        val request = Request.Builder().url("$API/account/login").put(body).build()
        http.okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) return@use false
            val context = json.parseToJsonElement(response.body.string()).jsonObject["context"] as? JsonObject
            val token = (context?.get("session_token") as? JsonPrimitive)?.contentOrNull ?: return@use false
            prefs.setTrackerValue(id, "session_token", token)
            true
        }
    }

    override suspend fun logout() {
        prefs.setTrackerValue(id, "session_token", null)
    }

    private suspend fun <T> api(authorized: Boolean, build: Request.Builder.() -> Unit, read: (Response) -> T): T? =
        withContext(Dispatchers.IO) {
            val token = prefs.trackerValue(id, "session_token")
            if (authorized && token == null) return@withContext null
            val request = Request.Builder().apply(build).header("Accept", "application/json")
                .apply { if (token != null) header("Authorization", "Bearer $token") }
                .build()
            http.okHttp.newCall(request).execute().use { response ->
                // Sessions end without a refresh token: sign out so the UI asks to sign in again.
                if (response.code == 401 && authorized) {
                    prefs.setTrackerValue(id, "session_token", null)
                    return@use null
                }
                read(response)
            }
        }

    override suspend fun search(query: String): List<TrackerSearchResult> {
        val q = query.trim()
        if (q.isEmpty()) return emptyList()
        val body = buildJsonObject {
            put("search", q)
            put("perpage", 10)
            put("filter_types", buildJsonArray { add(JsonPrimitive("drama cd")); add(JsonPrimitive("novel")) })
        }.toString().toRequestBody(jsonType)
        return api(authorized = false, { url("$API/series/search").post(body) }) { response ->
            if (!response.isSuccessful) return@api emptyList()
            val results = json.parseToJsonElement(response.body.string()).jsonObject["results"] as? JsonArray ?: return@api emptyList()
            results.mapNotNull { el ->
                val record = (el as? JsonObject)?.get("record") as? JsonObject ?: return@mapNotNull null
                val seriesId = (record["series_id"] as? JsonPrimitive)?.contentOrNull ?: return@mapNotNull null
                val image = ((record["image"] as? JsonObject)?.get("url") as? JsonObject)?.get("original") as? JsonPrimitive
                TrackerSearchResult(
                    remoteId = seriesId,
                    title = (record["title"] as? JsonPrimitive)?.contentOrNull?.let(::stripTags) ?: return@mapNotNull null,
                    url = (record["url"] as? JsonPrimitive)?.contentOrNull,
                    coverUrl = image?.contentOrNull,
                    altTitles = listOfNotNull((el["hit_title"] as? JsonPrimitive)?.contentOrNull?.let(::stripTags)),
                )
            }
        }.orEmpty()
    }

    private fun stripTags(text: String) = text.replace(Regex("<[^>]+>"), "").trim()

    override suspend fun link(sourceId: String, titleId: String, remoteId: String, remoteUrl: String?) {
        dao.upsertTrackerLink(
            TrackerLinkEntity(sourceId, titleId, id, remoteId, remoteUrl ?: "https://www.mangaupdates.com/series.html?id=$remoteId"),
        )
    }

    override suspend fun unlink(sourceId: String, titleId: String) = dao.deleteTrackerLink(sourceId, titleId, id)

    override suspend fun fetchEntry(remoteId: String): TrackerEntry? {
        val listed = api(authorized = true, { url("$API/lists/series/$remoteId").get() }) { response ->
            when {
                response.code == 404 -> JsonObject(emptyMap())
                !response.isSuccessful -> null
                else -> json.parseToJsonElement(response.body.string()).jsonObject
            }
        } ?: return null
        val rating = api(authorized = true, { url("$API/series/$remoteId/rating").get() }) { response ->
            if (!response.isSuccessful) return@api 0f
            (json.parseToJsonElement(response.body.string()).jsonObject["rating"] as? JsonPrimitive)?.contentOrNull?.toFloatOrNull() ?: 0f
        } ?: 0f
        if (listed.isEmpty()) return TrackerEntry(score = rating)
        val listId = (listed["list_id"] as? JsonPrimitive)?.contentOrNull?.toIntOrNull()
        val chapter = ((listed["status"] as? JsonObject)?.get("chapter") as? JsonPrimitive)?.contentOrNull?.toIntOrNull() ?: 0
        return TrackerEntry(
            status = STATUS.entries.firstOrNull { it.value == listId }?.key,
            score = rating,
            chaptersRead = chapter,
        )
    }

    override suspend fun updateEntry(remoteId: String, entry: TrackerEntry): Boolean {
        val seriesId = remoteId.toLongOrNull() ?: return false
        val inList = api(authorized = true, { url("$API/lists/series/$remoteId").get() }) { it.isSuccessful } ?: return false
        val listId = STATUS[entry.status ?: TrackStatus.Reading] ?: 0
        val body = buildJsonArray {
            add(
                buildJsonObject {
                    put("series", buildJsonObject { put("id", seriesId) })
                    put("list_id", listId)
                    put("status", buildJsonObject { put("chapter", entry.chaptersRead) })
                },
            )
        }.toString().toRequestBody(jsonType)
        val ok = api(authorized = true, { url(if (inList) "$API/lists/series/update" else "$API/lists/series").post(body) }) { it.isSuccessful } ?: false
        if (!ok) return false
        val score = entry.score.coerceIn(0f, 10f)
        api(authorized = true, {
            if (score > 0f) url("$API/series/$remoteId/rating").put(buildJsonObject { put("rating", score) }.toString().toRequestBody(jsonType))
            else url("$API/series/$remoteId/rating").delete()
        }) { it.isSuccessful }
        return true
    }

    override suspend fun fetchProgress(remoteId: String): TrackerProgress? =
        fetchEntry(remoteId)?.let { TrackerProgress(it.chaptersRead, it.status?.name, it.score) }

    override suspend fun updateProgress(remoteId: String, chaptersRead: Int): Boolean {
        val current = fetchEntry(remoteId) ?: return false
        val status = if (current.status == null || current.status == TrackStatus.Planned) TrackStatus.Reading else current.status
        return updateEntry(remoteId, current.copy(chaptersRead = chaptersRead, status = status))
    }

    companion object {
        const val ID = "mangaupdates"
        private const val API = "https://api.mangaupdates.com/v1"
        private val STATUS = linkedMapOf(
            TrackStatus.Reading to 0,
            TrackStatus.Planned to 1,
            TrackStatus.Completed to 2,
            TrackStatus.Dropped to 3,
            TrackStatus.OnHold to 4,
        )
    }
}
