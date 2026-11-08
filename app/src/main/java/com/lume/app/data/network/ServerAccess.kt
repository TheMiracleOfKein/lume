package com.lume.app.data.network

import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrlOrNull
import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.util.concurrent.ConcurrentHashMap

/**
 * What a registered server gets on every request: sign-in [headers], and [query] values that
 * replace a placeholder parameter. Kavita wants its API key in the image URL; the engine writes
 * `apiKey=-` there so the real key never lands in the history, the image cache or logs.
 */
data class ServerAccess(
    val headers: Map<String, String> = emptyMap(),
    val query: Map<String, String> = emptyMap(),
) {
    /** Never prints the secrets. */
    override fun toString(): String = "ServerAccess(headers=${headers.keys}, query=${query.keys})"
}

/**
 * The servers the user added (OPDS, Komga, Kavita), keyed by origin (scheme + host + port).
 * Only requests to a registered origin get that server's [ServerAccess], and only these hosts
 * may be reached over plain `http://`.
 */
class ServerEndpoints {
    private val byOrigin = ConcurrentHashMap<String, ServerAccess>()

    /** Registers [baseUrl] (or replaces its [access], for example a refreshed token). False for an unusable URL. */
    fun register(baseUrl: String, access: ServerAccess = ServerAccess()): Boolean {
        val key = originOf(baseUrl.toHttpUrlOrNull() ?: return false)
        byOrigin[key] = access
        return true
    }

    fun unregister(baseUrl: String) {
        val key = originOf(baseUrl.toHttpUrlOrNull() ?: return)
        byOrigin.remove(key)
    }

    fun clear() = byOrigin.clear()

    fun isRegistered(url: HttpUrl): Boolean = byOrigin.containsKey(originOf(url))

    fun accessFor(url: HttpUrl): ServerAccess? = byOrigin[originOf(url)]

    companion object {
        /** `scheme://host:port` in lower case, with the default port written out. */
        fun originOf(url: HttpUrl): String = "${url.scheme}://${url.host.lowercase()}:${url.port}"
    }
}

/**
 * Adds the stored sign-in (Basic, `X-API-Key` or Bearer) to requests for a registered server.
 * Runs as a network interceptor so a redirect to another host never carries it along.
 */
class ServerAuthInterceptor(private val servers: ServerEndpoints) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val access = servers.accessFor(request.url) ?: return chain.proceed(request)
        val builder = request.newBuilder()
        access.headers.forEach { (name, value) -> builder.header(name, value) }
        val names = request.url.queryParameterNames
        if (access.query.keys.any { it in names }) {
            val url = request.url.newBuilder()
            access.query.forEach { (name, value) -> if (name in names) url.setQueryParameter(name, value) }
            builder.url(url.build())
        }
        return chain.proceed(builder.build())
    }
}

/** Refuses `http://` requests to hosts that are not a server the user added. */
class CleartextGuardInterceptor(private val servers: ServerEndpoints) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        if (!request.url.isHttps && !servers.isRegistered(request.url)) {
            throw CleartextBlockedException(request.url.host)
        }
        return chain.proceed(request)
    }
}

/** A plain-HTTP request to a host that is not a registered server; retrying cannot help. */
class CleartextBlockedException(host: String) :
    IOException("Plain HTTP is only allowed for servers you added: $host")

/**
 * Keeps session cookies out of requests to registered servers. Komga answers with a
 * `KOMGA-SESSION` cookie and then accepts it instead of the Basic sign-in, so a wrong or changed
 * password would go unnoticed. Every request to a server signs in with its own headers instead.
 */
class ServerCookieJar(private val delegate: CookieJar, private val servers: ServerEndpoints) : CookieJar {
    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        if (!servers.isRegistered(url)) delegate.saveFromResponse(url, cookies)
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> =
        if (servers.isRegistered(url)) emptyList() else delegate.loadForRequest(url)

    /** Expires the cookies [delegate] holds for [url], for example when its server is edited or removed. */
    fun forget(url: HttpUrl) {
        val stored = runCatching { delegate.loadForRequest(url) }.getOrDefault(emptyList())
        if (stored.isEmpty()) return
        val expired = stored.map { cookie ->
            Cookie.Builder().name(cookie.name).value("").path(cookie.path).expiresAt(0L)
                .let { if (cookie.hostOnly) it.hostOnlyDomain(cookie.domain) else it.domain(cookie.domain) }.build()
        }
        delegate.saveFromResponse(url, expired)
    }
}
