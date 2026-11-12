package com.lume.app.data.nearby

import android.graphics.Bitmap
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.asImageBitmap
import com.google.zxing.BarcodeFormat
import com.google.zxing.EncodeHintType
import com.google.zxing.qrcode.QRCodeWriter
import com.google.zxing.qrcode.decoder.ErrorCorrectionLevel
import java.net.URLDecoder
import java.net.URLEncoder

/**
 * The sender's address as a link in a QR code: `lume-nearby://host:port?name=Device`. IPv6 hosts are
 * written in brackets. It carries no secret: the pairing code is made by the key exchange after the
 * receiver connects and is still checked on both screens, exactly as when the address is typed.
 */
data class NearbyQrTarget(val host: String, val port: Int, val name: String?)

object NearbyQr {
    const val SCHEME = "lume-nearby"
    private const val PREFIX = "$SCHEME://"
    private const val MAX_NAME = 64

    fun encode(host: String, port: Int, name: String): String {
        val h = if (':' in host && !host.startsWith("[")) "[$host]" else host
        val query = URLEncoder.encode(name.take(MAX_NAME), "UTF-8")
        return "$PREFIX$h:$port?name=$query"
    }

    /** Null when [text] isn't a Lume nearby link with a usable host and port. */
    fun parse(text: String): NearbyQrTarget? {
        val trimmed = text.trim()
        if (!trimmed.startsWith(PREFIX, ignoreCase = true)) return null
        val rest = trimmed.substring(PREFIX.length)
        val authority = rest.substringBefore('?').trimEnd('/')
        val query = if ('?' in rest) rest.substringAfter('?') else ""
        val host: String
        val portText: String
        if (authority.startsWith("[")) {
            val close = authority.indexOf(']')
            if (close < 0 || authority.getOrNull(close + 1) != ':') return null
            host = authority.substring(1, close)
            portText = authority.substring(close + 2)
            if (host.isEmpty() || ':' !in host || !host.all { it.isLetterOrDigit() || it == ':' || it == '.' || it == '%' }) return null
        } else {
            host = authority.substringBeforeLast(':', "")
            portText = authority.substringAfterLast(':', "")
            if (host.isEmpty() || ':' in host || host.any { it.isWhitespace() || it == '/' || it == '@' }) return null
        }
        val port = portText.toIntOrNull() ?: return null
        if (port !in 1..65535) return null
        val name = query.split('&')
            .firstOrNull { it.startsWith("name=") }
            ?.substringAfter('=')
            ?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            ?.take(MAX_NAME)
            ?.takeIf { it.isNotBlank() }
        return NearbyQrTarget(host, port, name)
    }

    /** A black-on-white QR code of [text], [size] pixels wide, with the quiet zone zxing adds. */
    fun render(text: String, size: Int = 640): ImageBitmap {
        val hints = mapOf(EncodeHintType.ERROR_CORRECTION to ErrorCorrectionLevel.M, EncodeHintType.MARGIN to 2)
        val matrix = QRCodeWriter().encode(text, BarcodeFormat.QR_CODE, size, size, hints)
        val pixels = IntArray(matrix.width * matrix.height) { i ->
            if (matrix.get(i % matrix.width, i / matrix.width)) 0xFF000000.toInt() else 0xFFFFFFFF.toInt()
        }
        val bitmap = Bitmap.createBitmap(pixels, matrix.width, matrix.height, Bitmap.Config.ARGB_8888)
        return bitmap.asImageBitmap()
    }
}
