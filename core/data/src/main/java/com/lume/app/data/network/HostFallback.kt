package com.lume.app.data.network

import okhttp3.Interceptor
import okhttp3.Response
import java.io.IOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.concurrent.ConcurrentHashMap

/**
 * Addresses to try when a host does not answer (from the hosts file, see `HostsFile.fallbacks`).
 * The first alternative that works is remembered for the rest of the session, so the next
 * request goes there directly.
 */
class HostFallback {
    @Volatile
    private var alternatives: Map<String, List<String>> = emptyMap()
    private val preferred = ConcurrentHashMap<String, String>()

    /** Called after a host failed and an alternative was tried (the hosts file may have changed). */
    @Volatile
    var onHostFailure: (() -> Unit)? = null

    fun set(map: Map<String, List<String>>) {
        alternatives = map
        preferred.clear()
    }

    fun alternativesFor(host: String): List<String> = alternatives[host.lowercase()].orEmpty()

    fun preferredFor(host: String): String? = preferred[host.lowercase()]

    internal fun remember(host: String, working: String) {
        preferred[host.lowercase()] = working
    }
}

/** Retries a request on another address when the host cannot be reached at all. */
class HostFallbackInterceptor(private val fallback: HostFallback) : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val host = request.url.host
        val known = fallback.alternativesFor(host)
        if (known.isEmpty()) return chain.proceed(request)

        fun onHost(target: String) = request.newBuilder().url(request.url.newBuilder().host(target).build()).build()

        // A host that already failed goes straight to the address that worked.
        fallback.preferredFor(host)?.let { target ->
            try {
                return chain.proceed(onHost(target))
            } catch (e: IOException) {
                if (chain.call().isCanceled() || !unreachable(e, request.method)) throw e
            }
        }
        try {
            return chain.proceed(request)
        } catch (first: IOException) {
            if (chain.call().isCanceled() || !unreachable(first, request.method)) throw first
            fallback.onHostFailure?.invoke()
            var last: IOException = first
            for (target in known) {
                try {
                    val response = chain.proceed(onHost(target))
                    fallback.remember(host, target)
                    return response
                } catch (e: IOException) {
                    if (chain.call().isCanceled()) throw e
                    last = e
                }
            }
            first.addSuppressed(last)
            throw first
        }
    }

    /** The host was not reached. A read timeout of a POST is not retried: the server may have acted. */
    private fun unreachable(e: IOException, method: String): Boolean = when (e) {
        is UnknownHostException, is ConnectException, is NoRouteToHostException -> true
        is SocketTimeoutException -> method == "GET" || method == "HEAD"
        else -> false
    }
}
