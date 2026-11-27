package com.lume.app.data.source

import com.lume.app.data.network.AppHttp
import com.lume.app.domain.SourceManifest
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.buildJsonObject
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.intOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import kotlinx.serialization.json.longOrNull
import kotlinx.serialization.json.put
import kotlinx.serialization.json.putJsonObject
import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

/**
 * Signing in to a Lib site in the phone's browser instead of Lume's login page, so the browser's
 * saved Google accounts and passwords (and an existing lib.social session) can be used.
 *
 * It is the same OAuth sign-in (with PKCE) the sites run themselves: `auth.lib.social` sends the
 * browser back to the site's own callback page with a one-time code. That page can't return to
 * Lume, so the user copies its address and Lume swaps the code for a token.
 */
class LibBrowserLogin(
    private val http: AppHttp,
    private val auth: LibAuthStore,
) {
    /** One sign-in attempt; [verifier] and [state] stay in Lume and never reach the browser. */
    data class Attempt(
        val url: String,
        val verifier: String,
        val state: String,
        val redirectUri: String,
    )

    /** What the copied callback address holds. */
    data class Callback(val code: String?, val state: String?, val error: String?)

    /**
     * Swaps the code in [callback] for a token and saves it for [manifest]'s source.
     * Throws when the site refuses the code or answers with something else.
     */
    suspend fun finish(manifest: SourceManifest, attempt: Attempt, callback: Callback): LibAuthToken {
        callback.error?.let { error(it) }
        require(callback.state == attempt.state) { "state mismatch" }
        val code = requireNotNull(callback.code) { "no code" }
        val apiRoot = (manifest.configString("apiBase") ?: DEFAULT_API).trimEnd('/')
        val origin = manifest.baseUrl.trimEnd('/')
        val body = buildJsonObject {
            put("grant_type", "authorization_code")
            put("client_id", CLIENT_ID)
            put("redirect_uri", attempt.redirectUri)
            put("code_verifier", attempt.verifier)
            put("code", code)
        }
        val tokenAnswer = http.sendJson(
            "POST",
            URI("$apiRoot/auth/oauth/token"),
            body.toString(),
            source = manifest,
            headers = mapOf("Origin" to origin),
        )
        val token = json.parseToJsonElement(tokenAnswer.body).jsonObject
        val accessToken = token.string("access_token") ?: error("no access token")
        val tokenType = token.string("token_type") ?: "Bearer"
        val me = http.get(
            URI("$apiRoot/auth/me"),
            source = manifest,
            headers = mapOf(
                "Accept" to "application/json",
                "Authorization" to "$tokenType $accessToken",
                "Site-Id" to (manifest.configString("siteId") ?: "1"),
                "Origin" to origin,
            ),
            checkCloudflare = false,
        )
        val userId = json.parseToJsonElement(me.body).jsonObject["data"]?.jsonObject
            ?.get("id")?.jsonPrimitive?.intOrNull ?: error("no user id")
        // The same shape the site keeps in localStorage.auth, so it's stored like a page login.
        val raw = buildJsonObject {
            putJsonObject("auth") { put("id", userId) }
            putJsonObject("token") {
                put("timestamp", System.currentTimeMillis())
                put("expires_in", token["expires_in"]?.jsonPrimitive?.longOrNull ?: 0L)
                put("token_type", tokenType)
                put("access_token", accessToken)
            }
        }
        return auth.saveRaw(manifest.id, raw.toString()) ?: error("token not saved")
    }

    companion object {
        /** The lib.social client the sites themselves sign in with. */
        private const val CLIENT_ID = 1
        private const val AUTHORIZE = "https://auth.lib.social/auth/oauth/authorize"
        private const val DEFAULT_API = "https://api.lib.social/api"
        private const val CALLBACK_PATH = "/ru/front/auth/oauth/callback"
        private val json = Json { ignoreUnknownKeys = true; isLenient = true }
        private val random = SecureRandom()

        /** A new attempt for the site at [baseUrl]. */
        fun start(baseUrl: String): Attempt {
            val verifier = randomString(VERIFIER_LENGTH)
            val state = randomString(STATE_LENGTH)
            val redirectUri = baseUrl.trimEnd('/') + CALLBACK_PATH
            val url = AUTHORIZE +
                "?scope=&client_id=$CLIENT_ID&response_type=code" +
                "&redirect_uri=${encode(redirectUri)}&state=$state" +
                "&code_challenge=${challenge(verifier)}&code_challenge_method=S256&prompt=consent"
            return Attempt(url, verifier, state, redirectUri)
        }

        /** PKCE S256: base64url of the SHA-256 of [verifier], without padding. */
        internal fun challenge(verifier: String): String =
            Base64.getUrlEncoder().withoutPadding()
                .encodeToString(MessageDigest.getInstance("SHA-256").digest(verifier.toByteArray(Charsets.US_ASCII)))

        /**
         * The code, state or error in a copied callback address. Text around the address (a
         * shared page title) is ignored. Null when [text] has no callback in it.
         */
        fun parseCallback(text: String): Callback? {
            val url = Regex("""\S*oauth/callback\?\S+""").find(text)?.value ?: return null
            val params = url.substringAfter('?').substringBefore('#').split('&')
                .mapNotNull { part ->
                    val key = part.substringBefore('=', "")
                    if (key.isEmpty()) null else key to decode(part.substringAfter('='))
                }
                .toMap()
            val callback = Callback(params["code"]?.ifEmpty { null }, params["state"], params["error"]?.ifEmpty { null })
            return callback.takeIf { it.code != null || it.error != null }
        }

        private const val VERIFIER_LENGTH = 128
        private const val STATE_LENGTH = 40
        private const val ALPHABET = "ABCDEFGHIJKLMNOPQRSTUVWXYZabcdefghijklmnopqrstuvwxyz0123456789"

        private fun randomString(length: Int): String =
            buildString(length) { repeat(length) { append(ALPHABET[random.nextInt(ALPHABET.length)]) } }

        private fun encode(value: String) = URLEncoder.encode(value, "UTF-8")
        private fun decode(value: String) = runCatching { URLDecoder.decode(value, "UTF-8") }.getOrDefault(value)

        private fun JsonObject.string(key: String): String? = this[key]?.jsonPrimitive?.contentOrNull
    }
}
