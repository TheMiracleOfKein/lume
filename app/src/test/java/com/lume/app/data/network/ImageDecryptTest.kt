package com.lume.app.data.network

import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.Protocol
import okhttp3.Request
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test

class ImageDecryptTest {
    private val key = "UySkp0BzPhwlvP2V"

    /** The first bytes of a PNG, padded to a few key lengths. */
    private val png = byteArrayOf(0x89.toByte(), 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A) + ByteArray(40) { it.toByte() }

    @Test
    fun xorUndoesItself() {
        val masked = ImageDecrypt.xor(png, key.toByteArray())
        // The first bytes of a real masked page: dc 29 1d 2c.
        assertArrayEquals(byteArrayOf(0xDC.toByte(), 0x29, 0x1D, 0x2C), masked.copyOf(4))
        assertArrayEquals(png, ImageDecrypt.xor(masked, key.toByteArray()))
    }

    @Test
    fun tagAddsTheKeyOnce() {
        assertEquals("https://h/a.png?lume_xor=k", ImageDecrypt.tag("https://h/a.png", "k"))
        assertEquals("https://h/a.png?x=1&lume_xor=k", ImageDecrypt.tag("https://h/a.png?x=1", "k"))
        assertEquals("https://h/a.png", ImageDecrypt.tag("https://h/a.png", null))
    }

    private fun client(answer: (Request) -> ByteArray, seen: MutableList<Request> = mutableListOf()) = OkHttpClient.Builder()
        .addInterceptor(ImageDecryptInterceptor())
        .addInterceptor(Interceptor { chain ->
            val request = chain.request()
            seen += request
            Response.Builder()
                .request(request)
                .protocol(Protocol.HTTP_1_1)
                .code(200)
                .message("OK")
                .header("Content-Type", "application/octet-stream")
                .body(answer(request).toResponseBody("application/octet-stream".toMediaType()))
                .build()
        })
        .build()

    @Test
    fun unmasksTaggedPagesAndDropsTheKeyFromTheRequest() {
        val seen = mutableListOf<Request>()
        val masked = ImageDecrypt.xor(png, key.toByteArray())
        val url = ImageDecrypt.tag("https://static.example/chapters/c/p.png", key)
        client({ masked }, seen).newCall(Request.Builder().url(url).build()).execute().use { response ->
            assertArrayEquals(png, response.body.bytes())
            assertEquals("image/png", response.header("Content-Type"))
        }
        assertEquals("https://static.example/chapters/c/p.png", seen.single().url.toString())
    }

    @Test
    fun plainImagesAndUntaggedUrlsPassThrough() {
        client({ png }).newCall(Request.Builder().url(ImageDecrypt.tag("https://static.example/p.png", key)).build()).execute().use {
            assertArrayEquals(png, it.body.bytes())
        }
        val masked = ImageDecrypt.xor(png, key.toByteArray())
        client({ masked }).newCall(Request.Builder().url("https://static.example/p.png").build()).execute().use {
            assertArrayEquals(masked, it.body.bytes())
        }
    }
}
