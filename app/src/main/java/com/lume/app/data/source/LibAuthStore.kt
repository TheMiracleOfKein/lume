package com.lume.app.data.source

import android.content.Context
import androidx.datastore.preferences.core.edit
import androidx.datastore.preferences.core.stringPreferencesKey
import androidx.datastore.preferences.preferencesDataStore
import com.lume.app.data.security.SecretSealer
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json

private val Context.libAuthStore by preferencesDataStore("lib_auth")

/**
 * Stores MangaLib-family WebView `localStorage.auth` tokens per source id.
 * Shape matches cdnlibs / LibGroup AuthToken JSON.
 */
class LibAuthStore(
    private val context: Context,
    /** Tokens are encrypted at rest; values from before v0.9.11 are sealed by [sealStored]. */
    private val secrets: SecretSealer = SecretSealer(),
) {
    private val json = Json { ignoreUnknownKeys = true; isLenient = true }

    fun tokenFlow(sourceId: String): Flow<LibAuthToken?> =
        context.libAuthStore.data.map { prefs ->
            val raw = secrets.open(prefs[key(sourceId)]) ?: return@map null
            runCatching { json.decodeFromString(LibAuthToken.serializer(), raw) }.getOrNull()
        }

    /** Encrypts tokens saved as plain text by older versions. Runs once on start. */
    suspend fun sealStored() {
        val plain = context.libAuthStore.data.first().asMap().mapNotNull { (key, value) ->
            if (value !is String || secrets.isSealed(value)) return@mapNotNull null
            secrets.seal(value).takeIf(secrets::isSealed)?.let { Pair(stringPreferencesKey(key.name), it) }
        }
        if (plain.isEmpty()) return
        context.libAuthStore.edit { target -> plain.forEach { (key, sealed) -> target[key] = sealed } }
    }

    suspend fun getToken(sourceId: String): LibAuthToken? = tokenFlow(sourceId).first()

    suspend fun authorizationHeader(sourceId: String): String? {
        val token = getToken(sourceId) ?: return null
        if (!token.isValid()) return null
        return token.bearerHeader()
    }

    /** The token in a raw `localStorage.auth` value, or null when it isn't a valid one. */
    fun parse(rawJson: String): LibAuthToken? {
        val cleaned = rawJson.trim().removeSurrounding("\"").replace("\\\"", "\"")
        return runCatching { json.decodeFromString(LibAuthToken.serializer(), cleaned) }.getOrNull()
            ?.takeIf { it.isValid() }
    }

    suspend fun saveRaw(sourceId: String, rawJson: String): LibAuthToken? {
        val token = parse(rawJson) ?: return null
        val sealed = secrets.seal(json.encodeToString(LibAuthToken.serializer(), token))
        context.libAuthStore.edit { it[key(sourceId)] = sealed }
        return token
    }

    suspend fun clear(sourceId: String) {
        context.libAuthStore.edit { it.remove(key(sourceId)) }
    }

    private fun key(sourceId: String) = stringPreferencesKey("token_$sourceId")
}

@Serializable
data class LibAuthToken(
    val auth: LibAuthUser? = null,
    val token: LibAuthAccess? = null,
) {
    fun isValid(): Boolean = auth != null && token != null && !token.accessToken.isNullOrBlank()

    fun bearerHeader(): String {
        val type = token?.tokenType?.ifBlank { "Bearer" } ?: "Bearer"
        return "$type ${token?.accessToken.orEmpty()}"
    }
}

@Serializable
data class LibAuthUser(
    val id: Int = 0,
)

@Serializable
data class LibAuthAccess(
    val timestamp: Long = 0,
    @SerialName("expires_in") val expiresIn: Long = 0,
    @SerialName("token_type") val tokenType: String? = "Bearer",
    @SerialName("access_token") val accessToken: String? = null,
)
