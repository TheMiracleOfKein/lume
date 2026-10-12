package com.lume.app.data.network

import android.webkit.CookieManager
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import java.util.concurrent.ConcurrentHashMap

/**
 * Cookie jar shared with the system WebView.
 *
 * Cloudflare / DDoS-Guard clearance and site logins happen inside a WebView; storing OkHttp
 * cookies in the same [CookieManager] means a solved challenge is visible to the catalogue
 * engines immediately (any subdomain, correct domain matching) and survives app restarts.
 *
 * If the WebView provider is unavailable (being updated, missing on some ROMs) we fall back to
 * an in-memory jar so networking keeps working without persistence.
 */
class WebViewCookieJar : CookieJar {
    private val manager: CookieManager? by lazy {
        runCatching { CookieManager.getInstance().apply { setAcceptCookie(true) } }.getOrNull()
    }
    private val fallback = MemoryCookieJar()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        val cm = manager ?: return fallback.saveFromResponse(url, cookies)
        val target = url.toString()
        cookies.forEach { cm.setCookie(target, it.toString()) }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val cm = manager ?: return fallback.loadForRequest(url)
        val raw = runCatching { cm.getCookie(url.toString()) }.getOrNull() ?: return emptyList()
        return raw.split(';').mapNotNull { part ->
            part.trim().takeIf { it.isNotEmpty() }?.let { Cookie.parse(url, it) }
        }
    }

    fun flush() {
        runCatching { manager?.flush() }
    }
}

/** Process-lifetime cookie store; domain cookies are matched against parent hosts too. */
class MemoryCookieJar : CookieJar {
    private val store = ConcurrentHashMap<String, Cookie>()

    override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
        cookies.forEach { store["${it.domain}|${it.path}|${it.name}"] = it }
    }

    override fun loadForRequest(url: HttpUrl): List<Cookie> {
        val now = System.currentTimeMillis()
        store.entries.removeAll { it.value.expiresAt < now }
        return store.values.filter { it.matches(url) }
    }
}
