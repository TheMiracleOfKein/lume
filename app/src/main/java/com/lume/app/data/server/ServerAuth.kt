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

    /**
     * Whether [input] points into the local network (private or link-local address, `.local`,
     * a bare host name, the emulator's `10.0.2.2`). Android 17 asks for a permission for those.
     */
    fun isLocalNetwork(input: String): Boolean {
        val host = (normalizeUrl(input) ?: return false).toHttpUrlOrNull()?.host?.lowercase() ?: return false
        if (host == "localhost" || host.endsWith(".local") || host.endsWith(".lan") || host.endsWith(".home.arpa")) return true
        val parts = host.split('.')
        val octets = parts.mapNotNull { it.toIntOrNull()?.takeIf { n -> n in 0..255 } }
        if (parts.size == 4 && octets.size == 4) {
            val (a, b) = octets
            return a == 10 || a == 127 || (a == 172 && b in 16..31) || (a == 192 && b == 168) || (a == 169 && b == 254)
        }
        if (host.startsWith("[") || ':' in host) {
            return host.trim('[', ']').let { it == "::1" || it.startsWith("fe80") || it.startsWith("fc") || it.startsWith("fd") }
        }
        return '.' !in host
    }

    /**
     * The sign-in to store after an edit. Nothing typed keeps [stored]; a user name without a
     * password keeps the stored password when it is the same user (the field says "leave empty to
     * keep the saved sign-in"). Null: nothing to write.
     */
    fun mergeCredentials(typed: ServerCredentials?, stored: ServerCredentials?): ServerCredentials? {
        if (typed == null) return null
        if (typed.hasPassword && typed.password.isEmpty() && stored != null && stored.username == typed.username) {
            return typed.copy(password = stored.password)
        }
        return typed
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
