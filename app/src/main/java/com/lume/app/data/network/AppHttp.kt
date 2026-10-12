package com.lume.app.data.network

import com.lume.app.R
import com.lume.app.data.locale.appString
import com.lume.app.domain.CloudflareException
import com.lume.app.domain.SourceManifest
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharedFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asSharedFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import okhttp3.Call
import okhttp3.Callback
import okhttp3.CookieJar
import okhttp3.FormBody
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Request
import okhttp3.RequestBody
import okhttp3.RequestBody.Companion.toRequestBody
import okhttp3.Response
import java.io.IOException
import java.net.URI
import java.util.concurrent.TimeUnit
import kotlin.coroutines.resumeWithException

data class HttpResult(
    val code: Int,
    val body: String,
    val finalUrl: String,
)

/**
 * @param cookieJar defaults to the WebView-shared jar; JVM tests pass [CookieJar.NO_COOKIES]
 *   (OkHttp's Android artifact cannot parse domain cookies off-device).
 */
class AppHttp(private val cookieJar: CookieJar = WebViewCookieJar()) {
    private val rateLimiter = HostRateLimitInterceptor()

    @Volatile
    var userAgent: String = DEFAULT_UA
        private set

    private val client: OkHttpClient = OkHttpClient.Builder()
        .cookieJar(cookieJar)
        .followRedirects(true)
        .followSslRedirects(true)
        .connectTimeout(20, TimeUnit.SECONDS)
        .readTimeout(45, TimeUnit.SECONDS)
        .writeTimeout(30, TimeUnit.SECONDS)
        .addInterceptor(rateLimiter)
        .addInterceptor(RetryInterceptor())
        .build()

    val okHttp: OkHttpClient get() = client

    private val _challengeRequests = MutableSharedFlow<URI>(extraBufferCapacity = 1)
    val challengeRequests: SharedFlow<URI> = _challengeRequests.asSharedFlow()

    private val _networkEpoch = MutableStateFlow(0)
    val networkEpoch: StateFlow<Int> = _networkEpoch.asStateFlow()

    /**
     * Cloudflare binds `cf_clearance` to the User-Agent that solved the challenge, so the
     * challenge WebView, catalogue requests and image loading must all send this same value.
     */
    fun setUserAgent(ua: String) {
        if (ua.isNotBlank()) userAgent = ua
    }

    fun setRateLimit(host: String, permitsPerSecond: Double) = rateLimiter.setLimit(host, permitsPerSecond)

    fun bumpNetworkEpoch() {
        _networkEpoch.value = _networkEpoch.value + 1
    }

    suspend fun requestChallenge(uri: URI) {
        _challengeRequests.emit(uri)
    }

    /** Cookies already live in the WebView store; persist them and retry screens. */
    fun syncWebViewCookies(@Suppress("UNUSED_PARAMETER") url: String) {
        (cookieJar as? WebViewCookieJar)?.flush()
        bumpNetworkEpoch()
    }

    suspend fun get(
        uri: URI,
        source: SourceManifest? = null,
        headers: Map<String, String> = emptyMap(),
        checkCloudflare: Boolean = true,
    ): HttpResult = execute("GET", uri, source, headers, null, checkCloudflare)

    suspend fun postForm(
        uri: URI,
        source: SourceManifest? = null,
        headers: Map<String, String> = emptyMap(),
        form: Map<String, String>,
        checkCloudflare: Boolean = true,
    ): HttpResult {
        val body = FormBody.Builder().apply {
            form.forEach { (k, v) -> add(k, v) }
        }.build()
        return execute("POST", uri, source, headers, body, checkCloudflare)
    }

    suspend fun getBytes(
        uri: URI,
        source: SourceManifest? = null,
        headers: Map<String, String> = emptyMap(),
    ): ByteArray = withContext(Dispatchers.IO) {
        val request = buildRequest("GET", uri, source, headers, null)
        client.newCall(request).await().use { response ->
            if (!response.isSuccessful) {
                val text = runCatching { response.peekBody(16_384).string() }.getOrDefault("")
                validate(response, text, uri, checkCloudflare = true)
            }
            response.body.bytes()
        }
    }

    private suspend fun execute(
        method: String,
        uri: URI,
        source: SourceManifest?,
        headers: Map<String, String>,
        body: RequestBody?,
        checkCloudflare: Boolean,
    ): HttpResult = withContext(Dispatchers.IO) {
        val request = buildRequest(method, uri, source, headers, body)
        client.newCall(request).await().use { response ->
            val text = response.body.string()
            validate(response, text, uri, checkCloudflare)
            HttpResult(response.code, text, response.request.url.toString())
        }
    }

    private fun buildRequest(
        method: String,
        uri: URI,
        source: SourceManifest?,
        headers: Map<String, String>,
        body: RequestBody?,
    ): Request {
        val builder = Request.Builder().url(uri.toString()).method(
            method,
            if (method == "GET" || method == "HEAD") null else body
                ?: "".toRequestBody("application/x-www-form-urlencoded".toMediaType()),
        )
        builder.header("User-Agent", userAgent)
        builder.header("Accept", "text/html,application/xhtml+xml,application/xml;q=0.9,*/*;q=0.8")
        builder.header("Accept-Language", "en-US,en;q=0.9,ru;q=0.8")
        source?.headers?.forEach { (k, v) -> builder.header(k, v) }
        headers.forEach { (k, v) -> builder.header(k, v) }
        return builder.build()
    }

    /**
     * Only real interactive challenges become [CloudflareException] (→ WebView prompt).
     * Region blocks and auth failures are reported as [SourceHttpException] so the user sees
     * the actual reason instead of a challenge page that can never be solved.
     */
    private fun validate(response: Response, body: String, uri: URI, checkCloudflare: Boolean) {
        val status = response.code
        val server = response.header("Server")
        val mitigated = response.header("cf-mitigated")
        if (mitigated.equals("challenge", ignoreCase = true) ||
            (checkCloudflare && AntiBot.isChallenge(status, server, mitigated, body))
        ) {
            throw CloudflareException(uri)
        }
        if (status !in 200..299) {
            throw SourceHttpException(status, uri.toString(), geoBlocked = AntiBot.isGeoBlocked(server, body))
        }
    }

    companion object {
        const val DEFAULT_UA =
            "Mozilla/5.0 (Linux; Android 14; Pixel 8) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/140.0.0.0 Mobile Safari/537.36"
    }
}

/** Suspends on OkHttp's async API so coroutine cancellation cancels the network call. */
private suspend fun Call.await(): Response = suspendCancellableCoroutine { cont ->
    enqueue(
        object : Callback {
            override fun onResponse(call: Call, response: Response) {
                cont.resume(response) { _, _, _ -> response.close() }
            }

            override fun onFailure(call: Call, e: IOException) {
                if (!cont.isCancelled) cont.resumeWithException(e)
            }
        },
    )
    cont.invokeOnCancellation { runCatching { cancel() } }
}

class SourceHttpException(
    val code: Int,
    val url: String,
    val geoBlocked: Boolean = false,
) : Exception("HTTP $code: $url") {
    private val host: String get() = runCatching { URI(url).host }.getOrNull() ?: url

    override val message: String
        get() = when {
            geoBlocked -> appString(R.string.error_http_geoblocked, host)
            code == 401 || code == 403 -> appString(R.string.error_http_forbidden, host, code)
            code == 404 -> appString(R.string.error_http_not_found, host)
            code == 429 -> appString(R.string.error_http_rate_limited, host)
            code >= 500 -> appString(R.string.error_http_server, host, code)
            else -> appString(R.string.error_http_generic, code, host)
        }.ifBlank { "HTTP $code: $url" }
}
