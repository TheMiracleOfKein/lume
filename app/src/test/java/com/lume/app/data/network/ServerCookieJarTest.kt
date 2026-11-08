package com.lume.app.data.network

import com.lume.app.data.server.ServerAuth
import com.lume.app.data.server.ServerCredentials
import okhttp3.Cookie
import okhttp3.CookieJar
import okhttp3.HttpUrl
import okhttp3.HttpUrl.Companion.toHttpUrl
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ServerCookieJarTest {
    /** Keeps cookies per host, and drops the ones saved already expired. */
    private class FakeJar : CookieJar {
        val store = mutableMapOf<String, MutableList<Cookie>>()
        override fun saveFromResponse(url: HttpUrl, cookies: List<Cookie>) {
            val list = store.getOrPut(url.host) { mutableListOf() }
            cookies.forEach { c ->
                list.removeAll { it.name == c.name }
                if (c.expiresAt > System.currentTimeMillis()) list += c
            }
        }
        override fun loadForRequest(url: HttpUrl): List<Cookie> = store[url.host].orEmpty().toList()
    }

    private val servers = ServerEndpoints()
    private val inner = FakeJar()
    private val jar = ServerCookieJar(inner, servers)

    private fun session(url: HttpUrl) = Cookie.Builder().name("KOMGA-SESSION").value("abc").hostOnlyDomain(url.host).path("/").build()

    @Test
    fun serverSessionCookiesAreNeverStoredOrSent() {
        val url = "http://10.0.2.2:25600/api/v1/series".toHttpUrl()
        servers.register("http://10.0.2.2:25600")
        jar.saveFromResponse(url, listOf(session(url)))
        assertTrue(inner.store.isEmpty())
        inner.saveFromResponse(url, listOf(session(url)))
        assertTrue(jar.loadForRequest(url).isEmpty())
    }

    @Test
    fun otherHostsKeepTheirCookies() {
        val url = "https://example.org/".toHttpUrl()
        jar.saveFromResponse(url, listOf(session(url)))
        assertEquals(1, jar.loadForRequest(url).size)
    }

    @Test
    fun forgetExpiresStoredCookies() {
        val url = "http://10.0.2.2:25600/".toHttpUrl()
        inner.saveFromResponse(url, listOf(session(url)))
        jar.forget(url)
        assertTrue(inner.loadForRequest(url).isEmpty())
    }

    @Test
    fun savedPasswordSurvivesEditingOnlyTheAddress() {
        val stored = ServerCredentials("me", "pw")
        // The dialog shows the user name and an empty password field.
        assertEquals(stored, ServerAuth.mergeCredentials(ServerCredentials("me", ""), stored))
        assertNull(ServerAuth.mergeCredentials(null, stored))
        // A new password, or another user, replaces it.
        assertEquals("new", ServerAuth.mergeCredentials(ServerCredentials("me", "new"), stored)?.password)
        assertEquals("", ServerAuth.mergeCredentials(ServerCredentials("you", ""), stored)?.password)
    }

    @Test
    fun localNetworkAddressesAreRecognised() {
        listOf("http://10.0.2.2:25600", "192.168.1.5:5000", "http://172.20.0.1", "http://nas.local", "http://nas:8080", "localhost:25600")
            .forEach { assertTrue(it, ServerAuth.isLocalNetwork(it)) }
        listOf("https://komga.example.com", "http://8.8.8.8", "http://172.32.0.1", "").forEach { assertFalse(it, ServerAuth.isLocalNetwork(it)) }
    }
}
