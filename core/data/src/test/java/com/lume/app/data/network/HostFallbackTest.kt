package com.lume.app.data.network

import okhttp3.FormBody
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.net.UnknownHostException

class HostFallbackTest {
    private val seen = mutableListOf<String>()

    /** Stands in for the network: down.example.com does not resolve, everything else answers 200. */
    private fun client(fallback: HostFallback) = OkHttpClient.Builder()
        .addInterceptor(HostFallbackInterceptor(fallback))
        .addInterceptor(
            Interceptor { chain ->
                val request = chain.request()
                seen += request.url.host
                if (request.url.host == "down.example.com") throw UnknownHostException(request.url.host)
                Response.Builder().request(request).protocol(Protocol.HTTP_1_1).code(200).message("OK")
                    .body("ok".toResponseBody()).build()
            },
        )
        .build()

    private fun get(client: OkHttpClient, url: String) =
        client.newCall(Request.Builder().url(url).build()).execute()

    @Test
    fun aHostThatDoesNotResolveGoesToTheNextAddress() {
        val fallback = HostFallback().apply { set(mapOf("down.example.com" to listOf("up.example.com"))) }
        val response = get(client(fallback), "https://down.example.com/api/x?q=1")
        assertEquals(200, response.code)
        assertEquals("up.example.com", response.request.url.host)
        assertEquals("/api/x", response.request.url.encodedPath)
        assertEquals("q=1", response.request.url.query)
    }

    @Test
    fun theAddressThatWorkedIsUsedFirstNextTime() {
        val fallback = HostFallback().apply { set(mapOf("down.example.com" to listOf("up.example.com"))) }
        val client = client(fallback)
        get(client, "https://down.example.com/a")
        seen.clear()
        get(client, "https://down.example.com/b")
        assertEquals(listOf("up.example.com"), seen)
    }

    @Test
    fun aHostWithoutAlternativesFailsAsBefore() {
        val failed = runCatching { get(client(HostFallback()), "https://down.example.com/a") }
        assertTrue(failed.exceptionOrNull() is UnknownHostException)
    }

    @Test
    fun theFailureCallbackFiresWhenAnAlternativeIsTried() {
        var fired = 0
        val fallback = HostFallback().apply {
            set(mapOf("down.example.com" to listOf("up.example.com")))
            onHostFailure = { fired++ }
        }
        get(client(fallback), "https://down.example.com/a")
        assertEquals(1, fired)
    }

    @Test
    fun anAnsweringHostIsLeftAlone() {
        val fallback = HostFallback().apply { set(mapOf("up.example.com" to listOf("other.example.com"))) }
        get(client(fallback), "https://up.example.com/a")
        assertEquals(listOf("up.example.com"), seen)
    }

    @Test
    fun aPostIsRetriedOnlyWhenTheHostDidNotResolve() {
        val fallback = HostFallback().apply { set(mapOf("down.example.com" to listOf("up.example.com"))) }
        val request = Request.Builder().url("https://down.example.com/a").post(FormBody.Builder().build()).build()
        assertEquals(200, client(fallback).newCall(request).execute().code)
    }
}
