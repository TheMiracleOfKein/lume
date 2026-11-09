package com.lume.app.data.network

import com.lume.app.domain.ImageBytes
import okhttp3.Interceptor
import okhttp3.MediaType.Companion.toMediaTypeOrNull
import okhttp3.Response
import okhttp3.ResponseBody.Companion.toResponseBody

/**
 * Some sites serve chapter pages XOR-masked with a short repeating key (InkStory). The engine
 * appends the key to the page URL as [KEY_PARAM]; this interceptor strips it before the request
 * leaves the app and unmasks the body. Images that already are images pass through untouched,
 * so a URL tagged by mistake still loads. Reader, downloads and the image cache all use this client.
 */
object ImageDecrypt {
    const val KEY_PARAM = "lume_xor"

    /** [url] marked to be unmasked with [key]; unchanged without a key. */
    fun tag(url: String, key: String?): String {
        if (key.isNullOrEmpty()) return url
        val sep = if ('?' in url) '&' else '?'
        return "$url$sep$KEY_PARAM=${java.net.URLEncoder.encode(key, "UTF-8")}"
    }

    /** [bytes] XOR-ed with the repeating [key]; the same call masks and unmasks. */
    fun xor(bytes: ByteArray, key: ByteArray): ByteArray {
        if (key.isEmpty()) return bytes
        return ByteArray(bytes.size) { (bytes[it].toInt() xor key[it % key.size].toInt()).toByte() }
    }
}

class ImageDecryptInterceptor : Interceptor {
    override fun intercept(chain: Interceptor.Chain): Response {
        val request = chain.request()
        val key = request.url.queryParameter(ImageDecrypt.KEY_PARAM) ?: return chain.proceed(request)
        val clean = request.newBuilder()
            .url(request.url.newBuilder().removeAllQueryParameters(ImageDecrypt.KEY_PARAM).build())
            .build()
        val response = chain.proceed(clean)
        if (!response.isSuccessful) return response
        val raw = response.body.use { it.bytes() }
        val plain = if (ImageBytes.extension(raw) != null) {
            raw
        } else {
            ImageDecrypt.xor(raw, key.toByteArray()).takeIf { ImageBytes.extension(it) != null } ?: raw
        }
        val type = when (ImageBytes.extension(plain)) {
            "png" -> "image/png"
            "jpg" -> "image/jpeg"
            "webp" -> "image/webp"
            "gif" -> "image/gif"
            "avif" -> "image/avif"
            else -> response.header("Content-Type") ?: "application/octet-stream"
        }
        return response.newBuilder()
            .removeHeader("Content-Length")
            .removeHeader("Content-Encoding")
            .header("Content-Type", type)
            .body(plain.toResponseBody(type.toMediaTypeOrNull()))
            .build()
    }
}
