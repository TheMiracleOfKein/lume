package com.lume.app.data.server

import com.lume.app.data.network.ServerAccess
import com.lume.app.data.source.SourceEngines
import com.lume.app.domain.SourceManifest
import okhttp3.Credentials
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import java.util.UUID

/** Creating server manifests and turning a sign-in into request headers. */
object ServerAuth {
    const val ID_PREFIX = "server-"

    fun newId(): String = ID_PREFIX + UUID.randomUUID().toString()

    fun isServerId(id: String): Boolean = id.startsWith(ID_PREFIX)

    /**
     * Cleans what the user typed: adds `https://` when there is no scheme, drops the trailing
     * slash. Returns null when it is not a usable http(s) URL.
     */
    fun normalizeUrl(input: String): String? {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return null
        val withScheme = if (trimmed.contains("://")) trimmed else "https://$trimmed"
        val url = withScheme.toHttpUrlOrNull() ?: return null
        return url.toString().trimEnd('/')
    }

    fun isPlainHttp(input: String): Boolean = input.trim().startsWith("http://", ignoreCase = true)

    fun manifest(id: String, engine: String, name: String, baseUrl: String): SourceManifest =
        SourceManifest(
            id = id,
            name = name.ifBlank { baseUrl.toHttpUrlOrNull()?.host ?: baseUrl },
            engine = engine,
            baseUrl = baseUrl,
            mediaKind = "comics",
        )

    /**
     * Static headers the network interceptor adds. Kavita signs in with a token its engine
     * fetches and refreshes itself, so it starts without any.
     */
    fun access(engine: String, credentials: ServerCredentials?): ServerAccess {
        if (credentials == null || engine == SourceEngines.KAVITA) return ServerAccess()
        return when {
            credentials.hasApiKey -> ServerAccess(headers = mapOf("X-API-Key" to credentials.apiKey))
            credentials.hasPassword -> ServerAccess(
                headers = mapOf("Authorization" to Credentials.basic(credentials.username, credentials.password, Charsets.UTF_8)),
            )
            else -> ServerAccess()
        }
    }
}
