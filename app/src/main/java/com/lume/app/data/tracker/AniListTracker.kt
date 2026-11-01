package com.lume.app.data.tracker

import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.TrackerLinkEntity
import com.lume.app.data.network.AppHttp
import com.lume.app.data.prefs.AppPreferences
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody

class AniListTracker(
    private val http: AppHttp,
    private val dao: LibraryDao,
    private val prefs: AppPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Tracker {
    override val id: String = "anilist"
    override val displayName: String = "AniList"

    override suspend fun isLoggedIn(): Boolean =
        !prefs.anilistAccessToken.first().isNullOrBlank()

    override fun authorizeUrl(): String? {
        // Client ID is user-configurable; redirect must match app deep link.
        return null // built in Settings with client id
    }

    override suspend fun exchangeCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val clientId = TrackerOAuthDefaults.ANILIST_CLIENT_ID
            .ifBlank { prefs.anilistClientId.first().orEmpty() }
        val clientSecret = TrackerOAuthDefaults.ANILIST_CLIENT_SECRET
            .ifBlank { prefs.anilistClientSecret.first().orEmpty() }
        if (clientId.isBlank()) return@withContext false
        // Built as JSON, not by pasting strings: the code comes from a redirect link.
        val jsonBody = kotlinx.serialization.json.buildJsonObject {
            put("grant_type", JsonPrimitive("authorization_code"))
            put("client_id", JsonPrimitive(clientId))
            put("client_secret", JsonPrimitive(clientSecret))
            put("redirect_uri", JsonPrimitive(REDIRECT_URI))
            put("code", JsonPrimitive(code))
        }.toString()
        val jsonRequest = Request.Builder()
            .url("https://anilist.co/api/v2/oauth/token")
            .post(jsonBody.toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()
        http.okHttp.newCall(jsonRequest).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) return@withContext false
            val obj = json.parseToJsonElement(text).jsonObject
            val token = obj["access_token"]?.jsonPrimitive?.content ?: return@withContext false
            prefs.setAniListAccessToken(token)
            true
        }
    }

    override suspend fun logout() {
        prefs.setAniListAccessToken(null)
    }

    override val supportsDates: Boolean get() = true

    override val statuses: List<TrackStatus> get() = STATUS.statuses

    private suspend fun graphql(body: String, token: String?): kotlinx.serialization.json.JsonObject? = withContext(Dispatchers.IO) {
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(body.toRequestBody("application/json".toMediaType()))
            .apply { if (token != null) header("Authorization", "Bearer $token") }
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()
        http.okHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) return@use null
            json.parseToJsonElement(text).jsonObject["data"]?.jsonObject
        }
    }

    override suspend fun fetchEntry(remoteId: String): TrackerEntry? {
        val token = prefs.anilistAccessToken.first() ?: return null
        val mediaId = remoteId.toLongOrNull() ?: return null
        val body =
            """{"query":"query(${'$'}id:Int){Media(id:${'$'}id,type:MANGA){chapters mediaListEntry{progress status score(format:POINT_10_DECIMAL) startedAt{year month day} completedAt{year month day}}}}","variables":{"id":$mediaId}}"""
        val media = graphql(body, token)?.get("Media")?.jsonObject ?: return null
        val total = media["chapters"]?.jsonPrimitive?.content?.toIntOrNull()
        val entry = media["mediaListEntry"] as? kotlinx.serialization.json.JsonObject
            ?: return TrackerEntry(totalChapters = total)
        fun date(key: String): String? {
            val d = entry[key] as? kotlinx.serialization.json.JsonObject ?: return null
            return fuzzyDate(
                d["year"]?.jsonPrimitive?.content?.toIntOrNull(),
                d["month"]?.jsonPrimitive?.content?.toIntOrNull(),
                d["day"]?.jsonPrimitive?.content?.toIntOrNull(),
            )
        }
        return TrackerEntry(
            status = STATUS.fromRemote(entry["status"]?.jsonPrimitive?.content),
            score = entry["score"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
            chaptersRead = entry["progress"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            totalChapters = total,
            startDate = date("startedAt"),
            finishDate = date("completedAt"),
        )
    }

    override suspend fun updateEntry(remoteId: String, entry: TrackerEntry): Boolean {
        val token = prefs.anilistAccessToken.first() ?: return false
        val mediaId = remoteId.toLongOrNull() ?: return false
        fun fuzzy(date: String?): String {
            val parts = cleanDate(date)?.split('-')?.map { it.toInt() } ?: return "{\"year\":null,\"month\":null,\"day\":null}"
            return "{\"year\":${parts[0]},\"month\":${parts[1]},\"day\":${parts[2]}}"
        }
        val status = entry.status?.let(STATUS::toRemote)?.let { "\"$it\"" } ?: "null"
        // scoreRaw is 0–100 whatever score format the user picked on AniList.
        val body =
            """{"query":"mutation(${'$'}id:Int,${'$'}progress:Int,${'$'}status:MediaListStatus,${'$'}score:Int,${'$'}start:FuzzyDateInput,${'$'}end:FuzzyDateInput){SaveMediaListEntry(mediaId:${'$'}id,progress:${'$'}progress,status:${'$'}status,scoreRaw:${'$'}score,startedAt:${'$'}start,completedAt:${'$'}end){id}}","variables":{"id":$mediaId,"progress":${entry.chaptersRead},"status":$status,"score":${(entry.score * 10).toInt()},"start":${fuzzy(entry.startDate)},"end":${fuzzy(entry.finishDate)}}}"""
        return graphql(body, token)?.get("SaveMediaListEntry") != null
    }

    override suspend fun search(query: String): List<TrackerSearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val escaped = json.encodeToString(q)
        val body =
            """{"query":"query (${'$'}search:String){Page(perPage:10){media(search:${'$'}search,type:MANGA){id title{romaji english native} synonyms siteUrl coverImage{large}}}}","variables":{"search":$escaped}}"""
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()
        http.okHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) return@withContext emptyList()
            val root = json.parseToJsonElement(text).jsonObject
            val media = root["data"]?.jsonObject
                ?.get("Page")?.jsonObject
                ?.get("media")?.jsonArray
                ?: return@withContext emptyList()
            media.mapNotNull { el ->
                val obj = el.jsonObject
                val id = obj["id"]?.jsonPrimitive?.longOrNull?.toString() ?: return@mapNotNull null
                val titles = obj["title"]?.jsonObject
                val name = titles?.get("english")?.jsonPrimitive?.content
                    ?: titles?.get("romaji")?.jsonPrimitive?.content
                    ?: titles?.get("native")?.jsonPrimitive?.content
                    ?: return@mapNotNull null
                TrackerSearchResult(
                    remoteId = id,
                    title = name,
                    url = obj["siteUrl"]?.jsonPrimitive?.content,
                    coverUrl = obj["coverImage"]?.jsonObject?.get("large")?.jsonPrimitive?.content,
                    altTitles = listOf("english", "romaji", "native").mapNotNull { key ->
                        (titles?.get(key) as? JsonPrimitive)?.takeIf { it.isString }?.content
                    } + (obj["synonyms"] as? kotlinx.serialization.json.JsonArray).orEmpty().mapNotNull {
                        (it as? JsonPrimitive)?.takeIf { p -> p.isString }?.content
                    },
                )
            }
        }
    }

    override suspend fun link(
        sourceId: String,
        titleId: String,
        remoteId: String,
        remoteUrl: String?,
    ) {
        dao.upsertTrackerLink(
            TrackerLinkEntity(
                sourceId = sourceId,
                titleId = titleId,
                tracker = id,
                remoteId = remoteId,
                remoteUrl = remoteUrl ?: "https://anilist.co/manga/$remoteId",
            ),
        )
    }

    override suspend fun unlink(sourceId: String, titleId: String) {
        dao.deleteTrackerLink(sourceId, titleId, id)
    }

    override suspend fun fetchProgress(remoteId: String): TrackerProgress? = withContext(Dispatchers.IO) {
        val token = prefs.anilistAccessToken.first() ?: return@withContext null
        val mediaId = remoteId.toLongOrNull() ?: return@withContext null
        val body =
            """{"query":"query(${'$'}id:Int){Media(id:${'$'}id,type:MANGA){mediaListEntry{progress status score}}}","variables":{"id":$mediaId}}"""
        val request = Request.Builder()
            .url("https://graphql.anilist.co")
            .post(body.toRequestBody("application/json".toMediaType()))
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .header("Content-Type", "application/json")
            .build()
        http.okHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) return@withContext null
            val entry = json.parseToJsonElement(text).jsonObject["data"]?.jsonObject
                ?.get("Media")?.jsonObject
                ?.get("mediaListEntry")?.jsonObject
                ?: return@withContext TrackerProgress(0)
            TrackerProgress(
                chaptersRead = entry["progress"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                status = entry["status"]?.jsonPrimitive?.content,
                score = entry["score"]?.jsonPrimitive?.content?.toFloatOrNull(),
            )
        }
    }

    override suspend fun updateProgress(remoteId: String, chaptersRead: Int): Boolean =
        withContext(Dispatchers.IO) {
            val token = prefs.anilistAccessToken.first() ?: return@withContext false
            val mediaId = remoteId.toLongOrNull() ?: return@withContext false
            val body =
                """{"query":"mutation(${'$'}id:Int,${'$'}progress:Int){SaveMediaListEntry(mediaId:${'$'}id,progress:${'$'}progress){id progress}}","variables":{"id":$mediaId,"progress":$chaptersRead}}"""
            val request = Request.Builder()
                .url("https://graphql.anilist.co")
                .post(body.toRequestBody("application/json".toMediaType()))
                .header("Authorization", "Bearer $token")
                .header("Accept", "application/json")
                .header("Content-Type", "application/json")
                .build()
            http.okHttp.newCall(request).execute().use { it.isSuccessful }
        }

    companion object {
        const val REDIRECT_URI = "lume://oauth/anilist"
        private val STATUS = StatusMap(
            listOf(
                TrackStatus.Reading to "CURRENT",
                TrackStatus.Completed to "COMPLETED",
                TrackStatus.OnHold to "PAUSED",
                TrackStatus.Dropped to "DROPPED",
                TrackStatus.Planned to "PLANNING",
                TrackStatus.Rereading to "REPEATING",
            ),
        )
    }
}
