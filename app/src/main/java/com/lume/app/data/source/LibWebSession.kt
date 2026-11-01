package com.lume.app.data.source

import android.webkit.CookieManager
import android.webkit.WebStorage
import java.net.URI

/**
 * The Lib sites keep their own web session (cookies and `localStorage.auth`) inside the WebView,
 * next to the token Lume stores. Signing out in Lume has to end that session too, or the login
 * screen finds the old account again and closes before another one can be entered.
 *
 * All Lib sites sign in through the shared `lib.social` account service, so its session goes too.
 * Anti-bot clearance cookies stay, so the next visit doesn't have to pass the check again.
 */
object LibWebSession {
    private val authOrigins = listOf("https://lib.social", "https://auth.lib.social")

    /** Must run on the main thread: WebStorage and CookieManager belong to the WebView. */
    fun clear(baseUrl: String) {
        val origins = (listOfNotNull(originOf(baseUrl)) + authOrigins).distinct()
        runCatching {
            val storage = WebStorage.getInstance()
            origins.forEach { storage.deleteOrigin(it) }
        }
        runCatching {
            val cookies = CookieManager.getInstance()
            origins.forEach { origin -> expireCookies(cookies, origin) }
            cookies.flush()
        }
    }

    /**
     * The heavy fallback for when [clear] wasn't enough and the site still hands out the old
     * account: deletes all WebView storage and every cookie, then puts the anti-bot clearance
     * cookies of [keepClearanceFor] (the sources' base URLs) back, so the other sites don't
     * have to pass their checks again. [done] runs on the main thread once the cookies are gone.
     * Must run on the main thread.
     */
    fun wipeAll(keepClearanceFor: List<String>, done: () -> Unit) {
        runCatching { WebStorage.getInstance().deleteAllData() }
        val cookies = runCatching { CookieManager.getInstance() }.getOrNull() ?: return done()
        val kept = keepClearanceFor.mapNotNull { url ->
            val origin = originOf(url) ?: return@mapNotNull null
            val raw = runCatching { cookies.getCookie(origin) }.getOrNull() ?: return@mapNotNull null
            origin to clearancePairs(raw)
        }.filter { it.second.isNotEmpty() }
        runCatching {
            cookies.removeAllCookies {
                kept.forEach { (origin, pairs) ->
                    val host = runCatching { URI(origin).host }.getOrNull()
                    val domain = host?.let { parentDomain(it) ?: it }
                    pairs.forEach { pair ->
                        val attrs = if (domain != null) "; Domain=.$domain; Path=/; Secure" else "; Path=/; Secure"
                        cookies.setCookie(origin, pair + attrs)
                    }
                }
                cookies.flush()
                done()
            }
        }.onFailure { done() }
    }

    /** `name=value` pairs of the anti-bot cookies in a `Cookie` header value. */
    internal fun clearancePairs(raw: String): List<String> =
        raw.split(';')
            .map { it.trim() }
            .filter { it.contains('=') && isClearanceCookie(it.substringBefore('=').trim()) }
            .distinct()

    private fun expireCookies(cookies: CookieManager, origin: String) {
        val host = runCatching { URI(origin).host }.getOrNull() ?: return
        val raw = cookies.getCookie(origin) ?: return
        raw.split(';')
            .mapNotNull { it.substringBefore('=').trim().takeIf { name -> name.isNotEmpty() } }
            .filterNot(::isClearanceCookie)
            .distinct()
            .forEach { name ->
                val expired = "$name=; Max-Age=0; Expires=Thu, 01 Jan 1970 00:00:00 GMT; Path=/"
                // A cookie may be stored for the host or for its parent domain; expire both.
                // Secure / SameSite=None cookies only accept an overwrite that is Secure too.
                listOf(expired, "$expired; Secure; SameSite=None").forEach { base ->
                    cookies.setCookie(origin, base)
                    cookies.setCookie(origin, "$base; Domain=$host")
                    parentDomain(host)?.let { cookies.setCookie(origin, "$base; Domain=.$it") }
                }
            }
    }

    internal fun isClearanceCookie(name: String): Boolean =
        name == "cf_clearance" || name.startsWith("__cf") || name.startsWith("__ddg") ||
            name.startsWith("ddg") || name.startsWith("__ddos")

    internal fun parentDomain(host: String): String? {
        val parts = host.split('.')
        return if (parts.size > 2) parts.takeLast(2).joinToString(".") else null
    }

    private fun originOf(url: String): String? = runCatching {
        val uri = URI(url.trim())
        if (uri.scheme == null || uri.host == null) null else "${uri.scheme}://${uri.host}"
    }.getOrNull()
}
