package com.lume.app.data.tracker

import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.TrackerLinkEntity
import com.lume.app.data.network.AppHttp
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.util.urlEncode
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import java.net.URI

class ShikimoriTracker(
    private val http: AppHttp,
    private val dao: LibraryDao,
    private val prefs: AppPreferences,
    private val json: Json = Json { ignoreUnknownKeys = true },
) : Tracker {
    override val id: String = "shikimori"
    override val displayName: String = "Shikimori"

    override suspend fun isLoggedIn(): Boolean =
        !prefs.shikimoriAccessToken.first().isNullOrBlank()

    override fun authorizeUrl(): String? = null

    fun buildAuthorizeUrl(clientId: String): String {
        val redirect = urlEncode(REDIRECT_URI)
        return "$BASE_URL/oauth/authorize?client_id=$clientId&redirect_uri=$redirect&response_type=code&scope=user_rates"
    }

    override suspend fun exchangeCode(code: String): Boolean = withContext(Dispatchers.IO) {
        val clientId = TrackerOAuthDefaults.SHIKIMORI_CLIENT_ID
            .ifBlank { prefs.shikimoriClientId.first().orEmpty() }
        val clientSecret = TrackerOAuthDefaults.SHIKIMORI_CLIENT_SECRET
            .ifBlank { prefs.shikimoriClientSecret.first().orEmpty() }
        if (clientId.isBlank()) return@withContext false
        val body = FormBody.Builder()
            .add("grant_type", "authorization_code")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("redirect_uri", REDIRECT_URI)
            .add("code", code)
            .build()
        val request = Request.Builder()
            .url("$BASE_URL/oauth/token")
            .post(body)
            .header("User-Agent", USER_AGENT)
            .header("Accept", "application/json")
            .build()
        http.okHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) return@withContext false
            val obj = json.parseToJsonElement(text).jsonObject
            val token = obj["access_token"]?.jsonPrimitive?.content ?: return@withContext false
            val refresh = obj["refresh_token"]?.jsonPrimitive?.content
            prefs.setShikimoriTokens(token, refresh)
            runCatching { fetchAndStoreUserId(token) }
            true
        }
    }

    override suspend fun logout() {
        prefs.setShikimoriTokens(null, null)
        prefs.setShikimoriUserId(null)
    }

    override val statuses: List<TrackStatus> get() = STATUS.statuses

    /**
     * Runs an API call with the access token. Shikimori tokens last a day: on 401 the refresh
     * token gets a new pair and the call is made once more, so the account stays signed in.
     */
    private suspend fun <T> authed(build: (String) -> Request, read: (okhttp3.Response) -> T): T? = withContext(Dispatchers.IO) {
        val token = prefs.shikimoriAccessToken.first() ?: return@withContext null
        val first = http.okHttp.newCall(build(token)).execute()
        if (first.code != 401) return@withContext first.use(read)
        first.close()
        val fresh = refreshLock.withLock {
            // Another call may have refreshed while this one waited: its token is good, and the
            // old refresh token is spent (sending it again would sign the user out).
            prefs.shikimoriAccessToken.first()?.takeIf { it != token } ?: refreshTokens()
        } ?: return@withContext null
        http.okHttp.newCall(build(fresh)).execute().use(read)
    }

    private val refreshLock = kotlinx.coroutines.sync.Mutex()

    /** New tokens from the refresh token; null (and signed out) when Shikimori refuses it. */
    private suspend fun refreshTokens(): String? {
        val refresh = prefs.shikimoriRefreshToken.first() ?: return null
        val clientId = TrackerOAuthDefaults.SHIKIMORI_CLIENT_ID.ifBlank { prefs.shikimoriClientId.first().orEmpty() }
        val clientSecret = TrackerOAuthDefaults.SHIKIMORI_CLIENT_SECRET.ifBlank { prefs.shikimoriClientSecret.first().orEmpty() }
        if (clientId.isBlank()) return null
        val body = FormBody.Builder()
            .add("grant_type", "refresh_token")
            .add("client_id", clientId)
            .add("client_secret", clientSecret)
            .add("refresh_token", refresh)
            .build()
        val request = Request.Builder().url("$BASE_URL/oauth/token").post(body)
            .header("User-Agent", USER_AGENT).header("Accept", "application/json").build()
        return http.okHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) {
                // 400/401: the refresh token is gone too. Other errors may pass; keep the tokens.
                if (response.code in 400..401) prefs.setShikimoriTokens(null, null)
                return@use null
            }
            val obj = json.parseToJsonElement(text).jsonObject
            val token = obj["access_token"]?.jsonPrimitive?.content ?: return@use null
            prefs.setShikimoriTokens(token, obj["refresh_token"]?.jsonPrimitive?.content ?: refresh)
            token
        }
    }

    /** The user's rate for a manga; null when it is not in the list (or signed out). Throws on HTTP errors. */
    private suspend fun userRate(remoteId: String): kotlinx.serialization.json.JsonObject? {
        val token = prefs.shikimoriAccessToken.first() ?: return null
        val userId = ensureUserId(token) ?: return null
        return authed(
            build = { t ->
                Request.Builder()
                    .url("$API_URL/v2/user_rates?user_id=$userId&target_type=Manga&target_id=$remoteId")
                    .get()
                    .header("User-Agent", USER_AGENT)
                    .header("Authorization", "Bearer $t")
                    .header("Accept", "application/json")
                    .build()
            },
            read = { response ->
                val text = response.body.string()
                if (!response.isSuccessful) error("HTTP ${response.code}")
                json.parseToJsonElement(text).jsonArray.firstOrNull()?.jsonObject
            },
        )
    }

    override suspend fun fetchEntry(remoteId: String): TrackerEntry? {
        val rate = runCatching { userRate(remoteId) }.getOrElse { return null }
            ?: return if (prefs.shikimoriAccessToken.first() == null) null else TrackerEntry()
        return TrackerEntry(
            status = STATUS.fromRemote(rate["status"]?.jsonPrimitive?.content),
            score = rate["score"]?.jsonPrimitive?.content?.toFloatOrNull() ?: 0f,
            chaptersRead = rate["chapters"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
        )
    }

    override suspend fun updateEntry(remoteId: String, entry: TrackerEntry): Boolean {
        val token = prefs.shikimoriAccessToken.first() ?: return false
        val userId = ensureUserId(token) ?: return false
        val targetId = remoteId.toLongOrNull() ?: return false
        val rateId = runCatching { userRate(remoteId) }.getOrNull()?.get("id")?.jsonPrimitive?.content
        val payload = buildJsonObject {
            put("user_rate", buildJsonObject {
                put("chapters", JsonPrimitive(entry.chaptersRead))
                put("score", JsonPrimitive(entry.score.toInt().coerceIn(0, 10)))
                entry.status?.let(STATUS::toRemote)?.let { put("status", JsonPrimitive(it)) }
                if (rateId == null) {
                    put("user_id", JsonPrimitive(userId.toLongOrNull() ?: return false))
                    put("target_id", JsonPrimitive(targetId))
                    put("target_type", JsonPrimitive("Manga"))
                }
            })
        }.toString()
        return authed(
            build = { t ->
                Request.Builder()
                    .url(if (rateId != null) "$API_URL/v2/user_rates/$rateId" else "$API_URL/v2/user_rates")
                    .method(if (rateId != null) "PATCH" else "POST", payload.toRequestBody("application/json".toMediaType()))
                    .header("User-Agent", USER_AGENT)
                    .header("Authorization", "Bearer $t")
                    .header("Accept", "application/json")
                    .build()
            },
            read = { it.isSuccessful },
        ) ?: false
    }

    override suspend fun search(query: String): List<TrackerSearchResult> = withContext(Dispatchers.IO) {
        val q = query.trim()
        if (q.isEmpty()) return@withContext emptyList()
        val encoded = urlEncode(q)
        // shikimori.one/api… often 404; Mihon uses shikimori.io
        val result = http.get(
            URI("$API_URL/mangas?search=$encoded&limit=10"),
            checkCloudflare = false,
            headers = mapOf(
                "User-Agent" to USER_AGENT,
                "Accept" to "application/json",
            ),
        )
        val arr = json.parseToJsonElement(result.body).jsonArray
        arr.mapNotNull { el ->
            val obj = el.jsonObject
            val id = obj["id"]?.jsonPrimitive?.content ?: return@mapNotNull null
            val name = obj["russian"]?.jsonPrimitive?.content
                ?.takeIf { it.isNotBlank() }
                ?: obj["name"]?.jsonPrimitive?.content
                ?: return@mapNotNull null
            val image = obj["image"]?.jsonObject?.get("original")?.jsonPrimitive?.content
            TrackerSearchResult(
                remoteId = id,
                title = name,
                url = "$BASE_URL/mangas/$id",
                coverUrl = image?.let { if (it.startsWith("http")) it else "$BASE_URL$it" },
                altTitles = listOfNotNull(
                    obj["name"]?.jsonPrimitive?.content,
                    obj["russian"]?.jsonPrimitive?.content,
                ),
            )
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
                remoteUrl = remoteUrl ?: "$BASE_URL/mangas/$remoteId",
            ),
        )
    }

    override suspend fun unlink(sourceId: String, titleId: String) {
        dao.deleteTrackerLink(sourceId, titleId, id)
    }

    override suspend fun fetchProgress(remoteId: String): TrackerProgress? {
        prefs.shikimoriAccessToken.first() ?: return null
        val obj = runCatching { userRate(remoteId) }.getOrElse { return null } ?: return TrackerProgress(0)
        return TrackerProgress(
            chaptersRead = obj["chapters"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
            status = obj["status"]?.jsonPrimitive?.content,
            score = obj["score"]?.jsonPrimitive?.content?.toFloatOrNull(),
            libraryId = obj["id"]?.jsonPrimitive?.content,
        )
    }

    override suspend fun updateProgress(remoteId: String, chaptersRead: Int): Boolean =
        withContext(Dispatchers.IO) {
            val token = prefs.shikimoriAccessToken.first() ?: return@withContext false
            val userId = ensureUserId(token) ?: return@withContext false
            val existing = runCatching { userRate(remoteId) }.getOrNull()
            val rateId = existing?.get("id")?.jsonPrimitive?.content
            val remoteStatus = existing?.get("status")?.jsonPrimitive?.content
            val targetId = remoteId.toLongOrNull() ?: return@withContext false
            val payload = buildJsonObject {
                put("user_rate", buildJsonObject {
                    put("chapters", JsonPrimitive(chaptersRead))
                    if (rateId == null) {
                        put("user_id", JsonPrimitive(userId.toLongOrNull() ?: return@withContext false))
                        put("target_id", JsonPrimitive(targetId))
                        put("target_type", JsonPrimitive("Manga"))
                        put("status", JsonPrimitive("watching"))
                    } else if (remoteStatus.isNullOrBlank() || remoteStatus == "planned") {
                        // Only bump planned → watching; never clobber completed/on_hold/etc.
                        put("status", JsonPrimitive("watching"))
                    }
                })
            }.toString()
            val url = if (rateId != null) {
                "$API_URL/v2/user_rates/$rateId"
            } else {
                "$API_URL/v2/user_rates"
            }
            authed(
                build = { t ->
                    Request.Builder()
                        .url(url)
                        .method(if (rateId != null) "PATCH" else "POST", payload.toRequestBody("application/json".toMediaType()))
                        .header("User-Agent", USER_AGENT)
                        .header("Authorization", "Bearer $t")
                        .header("Accept", "application/json")
                        .header("Content-Type", "application/json")
                        .build()
                },
                read = { response ->
                    if (!response.isSuccessful) {
                        android.util.Log.w("Shikimori", "updateProgress failed ${response.code}: ${response.body.string()}")
                    }
                    response.isSuccessful
                },
            ) ?: false
        }

    private suspend fun ensureUserId(token: String): String? {
        prefs.shikimoriUserId.first()?.takeIf { it.isNotBlank() }?.let { return it }
        return fetchAndStoreUserId(token)
    }

    private suspend fun fetchAndStoreUserId(token: String): String? {
        val request = Request.Builder()
            .url("$API_URL/users/whoami")
            .get()
            .header("User-Agent", USER_AGENT)
            .header("Authorization", "Bearer $token")
            .header("Accept", "application/json")
            .build()
        return http.okHttp.newCall(request).execute().use { response ->
            val text = response.body.string()
            if (!response.isSuccessful) return@use null
            val id = json.parseToJsonElement(text).jsonObject["id"]?.jsonPrimitive?.content
                ?: return@use null
            prefs.setShikimoriUserId(id)
            id
        }
    }

    companion object {
        const val REDIRECT_URI = "lume://oauth/shikimori"
        /** Canonical host used by Mihon; .one redirects break POSTs with 301. */
        const val BASE_URL = "https://shikimori.io"
        const val API_URL = "$BASE_URL/api"
        const val USER_AGENT = "Lume"
        private val STATUS = StatusMap(
            listOf(
                TrackStatus.Reading to "watching",
                TrackStatus.Completed to "completed",
                TrackStatus.OnHold to "on_hold",
                TrackStatus.Dropped to "dropped",
                TrackStatus.Planned to "planned",
                TrackStatus.Rereading to "rewatching",
            ),
        )
    }
}
