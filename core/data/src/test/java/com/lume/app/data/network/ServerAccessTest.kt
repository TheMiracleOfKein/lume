package com.lume.app.data.network

import com.lume.app.data.server.ServerAuth
import com.lume.app.data.server.ServerCredentials
import com.lume.app.data.source.SourceEngines
import okhttp3.Interceptor
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test
import java.io.IOException

class ServerAccessTest {
    private val servers = ServerEndpoints()

    /** The request that reached the "network" (the end of the interceptor chain). */
    private var seen: Request? = null

    private val client = OkHttpClient.Builder()
        .addInterceptor(CleartextGuardInterceptor(servers))
        .addInterceptor(ServerAuthInterceptor(servers))
        .addInterceptor(
            Interceptor { chain ->
                seen = chain.request()
                Response.Builder()
                    .request(chain.request())
                    .protocol(Protocol.HTTP_1_1)
                    .code(200)
                    .message("OK")
                    .body("".toResponseBody())
                    .build()
            },
        )
        .build()

    private fun call(url: String): Request {
        seen = null
        client.newCall(Request.Builder().url(url).build()).execute().close()
        return seen!!
    }

    @Test
    fun sendsSignInOnlyToTheRegisteredOrigin() {
        servers.register("http://10.0.2.2:25600", ServerAccess(headers = mapOf("X-API-Key" to "secret")))
        assertEquals("secret", call("http://10.0.2.2:25600/api/v1/series").header("X-API-Key"))
        // Same host, other port: a different service.
        assertTrue(runCatching { call("http://10.0.2.2:8080/api").header("X-API-Key") }.isFailure)
        // Same host and port, other scheme.
        assertNull(call("https://10.0.2.2:25600/api").header("X-API-Key"))
    }

    @Test
    fun otherHostsNeverGetTheSignIn() {
        servers.register("https://komga.example.com", ServerAccess(headers = mapOf("Authorization" to "Basic abc")))
        assertEquals("Basic abc", call("https://komga.example.com/x").header("Authorization"))
        assertNull(call("https://komga.example.com.evil.net/x").header("Authorization"))
        assertNull(call("https://evil.net/x").header("Authorization"))
        assertNull(call("https://sub.komga.example.com/x").header("Authorization"))
    }

    @Test
    fun defaultPortsMatchTheirWrittenOutForm() {
        servers.register("https://komga.example.com", ServerAccess(headers = mapOf("X-API-Key" to "k")))
        assertEquals("k", call("https://komga.example.com:443/x").header("X-API-Key"))
        assertEquals("k", call("https://KOMGA.example.com/x").header("X-API-Key"))
    }

    @Test
    fun bearerTokenCanBeReplaced() {
        servers.register("https://kavita.example.com", ServerAccess(headers = mapOf("Authorization" to "Bearer old")))
        servers.register("https://kavita.example.com", ServerAccess(headers = mapOf("Authorization" to "Bearer new")))
        assertEquals("Bearer new", call("https://kavita.example.com/api/Series/1").header("Authorization"))
    }

    @Test
    fun placeholderKeyInTheQueryIsReplaced() {
        servers.register("https://kavita.example.com", ServerAccess(query = mapOf("apiKey" to "real-key")))
        val request = call("https://kavita.example.com/api/Reader/image?chapterId=5&page=0&apiKey=-")
        assertEquals("real-key", request.url.queryParameter("apiKey"))
        assertEquals("5", request.url.queryParameter("chapterId"))
        // Another host keeps whatever the URL says.
        assertEquals("-", call("https://other.example.com/image?apiKey=-").url.queryParameter("apiKey"))
        // Nothing is added when the URL has no such parameter.
        assertNull(call("https://kavita.example.com/api/Series/1").url.queryParameter("apiKey"))
    }

    @Test
    fun plainHttpIsBlockedForUnknownHosts() {
        try {
            call("http://example.com/feed")
            fail("expected the guard to block http")
        } catch (e: CleartextBlockedException) {
            assertTrue(e is IOException)
        }
        assertEquals(null, seen)
    }

    @Test
    fun plainHttpIsAllowedForServersTheUserAdded() {
        servers.register("http://192.168.1.10:25600")
        assertEquals("http://192.168.1.10:25600/api/v1/series", call("http://192.168.1.10:25600/api/v1/series").url.toString())
        // https never needs the allowance.
        assertEquals("https://example.com/x", call("https://example.com/x").url.toString())
    }

    @Test
    fun removingAServerRevokesBoth() {
        servers.register("http://nas.local:5000", ServerAccess(headers = mapOf("X-API-Key" to "k")))
        servers.unregister("http://nas.local:5000")
        assertTrue(runCatching { call("http://nas.local:5000/x") }.isFailure)
        assertFalse(servers.isRegistered(okhttp3.HttpUrl.Builder().scheme("http").host("nas.local").port(5000).build()))
    }

    @Test
    fun retryDoesNotRepeatABlockedRequest() {
        var calls = 0
        val blocked = OkHttpClient.Builder()
            .addInterceptor(RetryInterceptor(maxRetries = 2, baseDelayMs = 1))
            .addInterceptor(Interceptor { chain -> calls++; throw CleartextBlockedException(chain.request().url.host) })
            .build()
        assertTrue(runCatching { blocked.newCall(Request.Builder().url("http://example.com/").build()).execute() }.isFailure)
        assertEquals(1, calls)
    }

    @Test
    fun headersFollowTheSignInKind() {
        val basic = ServerAuth.access(SourceEngines.KOMGA, ServerCredentials(username = "u", password = "p")).headers
        assertEquals("Basic dTpw", basic["Authorization"])
        val key = ServerAuth.access(SourceEngines.KOMGA, ServerCredentials(apiKey = "k")).headers
        assertEquals("k", key["X-API-Key"])
        // Kavita signs in for a token itself; nothing static is added.
        assertTrue(ServerAuth.access(SourceEngines.KAVITA, ServerCredentials(apiKey = "k")).headers.isEmpty())
        assertTrue(ServerAuth.access(SourceEngines.OPDS, null).headers.isEmpty())
    }

    @Test
    fun urlsAreCleanedUp() {
        assertEquals("https://komga.example.com", ServerAuth.normalizeUrl("  komga.example.com/  "))
        assertEquals("http://10.0.2.2:25600", ServerAuth.normalizeUrl("http://10.0.2.2:25600/"))
        assertEquals("https://host/sub/path", ServerAuth.normalizeUrl("https://host/sub/path/"))
        assertNull(ServerAuth.normalizeUrl(""))
        assertNull(ServerAuth.normalizeUrl("ftp://host"))
        assertTrue(ServerAuth.isPlainHttp("http://nas"))
        assertFalse(ServerAuth.isPlainHttp("https://nas"))
        assertTrue(ServerAuth.isServerId(ServerAuth.newId()))
    }

    @Test
    fun secretsStayOutOfToString() {
        val text = ServerCredentials(username = "alice", password = "hunter2", apiKey = "KEY123").toString() +
            ServerAccess(headers = mapOf("Authorization" to "Basic hunter2"), query = mapOf("apiKey" to "KEY123"))
        assertFalse("hunter2" in text)
        assertFalse("KEY123" in text)
        assertFalse("alice" in text)
    }

    @Test
    fun webViewsBlockPlainHttpToUnregisteredHosts() {
        assertTrue(servers.blocksCleartext("http://evil.example.com/x.png"))
        assertTrue(servers.blocksCleartext("HTTP://evil.example.com"))
        assertTrue(servers.blocksCleartext("http://"))
        assertFalse(servers.blocksCleartext("https://example.com"))
        assertFalse(servers.blocksCleartext("about:blank"))
        assertFalse(servers.blocksCleartext("lume://oauth/cb"))
        servers.register("http://10.0.2.2:25600")
        assertFalse(servers.blocksCleartext("http://10.0.2.2:25600/page"))
        // Same host, other port, is another origin.
        assertTrue(servers.blocksCleartext("http://10.0.2.2:8080/page"))
    }
}
