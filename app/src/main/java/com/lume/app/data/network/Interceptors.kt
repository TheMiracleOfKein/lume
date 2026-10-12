package com.lume.app.data.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.io.InterruptedIOException
import java.util.concurrent.ConcurrentHashMap

/**
 * Retries idempotent requests on transient failures: 429, 502/503/504 that are *not*
 * anti-bot challenges, and dropped connections. Honors `Retry-After` (capped).
 */
class RetryInterceptor(
    private val maxRetries: Int = 2,
    private val baseDelayMs: Long = 700,
    private val maxDelayMs: Long = 10_000,
) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val idempotent = request.method == "GET" || request.method == "HEAD"
        var attempt = 0
        while (true) {
            val response = try {
                chain.proceed(request)
            } catch (e: IOException) {
                // Timeouts on a cancelled call surface as InterruptedIOException — never retry those.
                if (!idempotent || attempt >= maxRetries || e is InterruptedIOException && chain.call().isCanceled()) {
                    throw e
                }
                sleep(backoff(attempt++))
                continue
            }
            if (!idempotent || attempt >= maxRetries || !isTransient(response)) return response
            val wait = retryAfterMs(response) ?: backoff(attempt)
            response.close()
            attempt++
            sleep(wait)
        }
    }

    private fun isTransient(response: Response): Boolean = when (response.code) {
        429, 502, 504 -> true
        503 -> !AntiBot.isChallengeServer(response)
        else -> false
    }

    private fun retryAfterMs(response: Response): Long? =
        response.header("Retry-After")?.trim()?.toLongOrNull()?.times(1000)?.coerceIn(0, maxDelayMs)

    private fun backoff(attempt: Int): Long = (baseDelayMs shl attempt).coerceAtMost(maxDelayMs)

    private fun sleep(ms: Long) {
        try {
            Thread.sleep(ms)
        } catch (e: InterruptedException) {
            Thread.currentThread().interrupt()
            throw InterruptedIOException("Retry interrupted")
        }
    }
}

/**
 * Simple per-host request spacing (e.g. MangaDex asks for ≤5 req/s on api.mangadex.org).
 * Hosts are registered at runtime from source manifests (`engineConfig.rateLimit`).
 */
class HostRateLimitInterceptor : Interceptor {
    private val intervals = ConcurrentHashMap<String, Long>()
    private val nextSlot = ConcurrentHashMap<String, Long>()

    fun setLimit(host: String, permitsPerSecond: Double) {
        if (permitsPerSecond <= 0) intervals.remove(host) else intervals[host] = (1000 / permitsPerSecond).toLong()
    }

    override fun intercept(chain: Interceptor.Chain): Response {
        val host = chain.request().url.host
        val interval = intervals[host]
        if (interval != null) {
            val now = System.currentTimeMillis()
            // Reserve the next free slot atomically, then wait for it outside the map lock.
            val slot = nextSlot.compute(host) { _, prev -> maxOf(now, (prev ?: 0L)) + interval }!! - interval
            val wait = slot - now
            if (wait > 0) {
                try {
                    Thread.sleep(wait)
                } catch (e: InterruptedException) {
                    Thread.currentThread().interrupt()
                    throw InterruptedIOException("Rate limit wait interrupted")
                }
            }
        }
        return chain.proceed(chain.request())
    }
}

/** Anti-bot page detection shared by the HTTP client and the challenge WebView. */
object AntiBot {
    fun isChallengeServer(response: Response): Boolean {
        val server = response.header("Server").orEmpty().lowercase()
        return response.header("cf-mitigated") != null || "cloudflare" in server || "ddos-guard" in server
    }

    /**
     * True when the response is an interactive challenge a WebView can solve.
     * Plain 403s (region blocks, missing auth) are deliberately *not* challenges.
     */
    fun isChallenge(code: Int, server: String?, cfMitigated: String?, body: String): Boolean {
        if (cfMitigated.equals("challenge", ignoreCase = true)) return true
        if (code != 403 && code != 503 && code != 429) return false
        val s = server.orEmpty().lowercase()
        val head = body.take(8_000).lowercase()
        return when {
            "cloudflare" in s -> CF_MARKERS.any { it in head }
            "ddos-guard" in s -> "ddos-guard" in head && "geoblock" !in head
            else -> false
        }
    }

    fun isGeoBlocked(server: String?, body: String): Boolean {
        val head = body.take(8_000).lowercase()
        return "geoblock" in head ||
            (server.orEmpty().contains("cloudflare", ignoreCase = true) && "error code: 1009" in head)
    }

    fun isChallengePageTitle(title: String?): Boolean {
        val t = title.orEmpty().lowercase()
        return t.contains("just a moment") || t.contains("attention required") ||
            t.contains("один момент") || t.contains("ddos-guard") || t == "cloudflare"
    }

    private val CF_MARKERS = listOf(
        "just a moment",
        "cf-browser-verification",
        "challenge-platform",
        "cf_chl_",
        "attention required",
    )
}
