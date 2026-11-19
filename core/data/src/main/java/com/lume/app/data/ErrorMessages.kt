package com.lume.app.data

import com.lume.app.R
import com.lume.app.data.locale.appString
import com.lume.app.data.network.SourceHttpException
import com.lume.app.domain.CloudflareException
import kotlinx.serialization.SerializationException
import java.io.EOFException
import java.io.InterruptedIOException
import java.net.ConnectException
import java.net.NoRouteToHostException
import java.net.ProtocolException
import java.net.SocketException
import java.net.UnknownHostException
import java.util.regex.PatternSyntaxException
import javax.net.ssl.SSLException

/**
 * Turns a failure into text a reader can act on. A message that is blank, that looks like an
 * obfuscated identifier (R8 renames exception classes, so a bare "z73" can end up on screen), or
 * that only repeats the class name is never shown; the full technical text belongs in [ErrorLog].
 */
object ErrorMessages {
    private val obfuscated = Regex("^[a-zA-Z0-9_$.]{1,4}$")

    /** Kotlin's own texts for check(), requireNotNull() and friends. */
    private val kotlinDefaults = setOf("Check failed.", "Failed requirement.", "Required value was null.")

    /** Readable text for [error], in the app's language. */
    fun of(error: Throwable): String = of(error) { id, args -> appString(id, *args) }

    internal fun of(error: Throwable, string: (Int, Array<Any>) -> String): String {
        fun s(id: Int, vararg args: Any) = string(id, arrayOf(*args))
        return when {
            error is CloudflareException -> s(R.string.error_cloudflare_needed)
            error is SourceHttpException -> error.message.takeIf { isReadable(error, it) }
                ?: s(R.string.error_http_generic, error.code, error.url)
            error is UnknownHostException || error is ConnectException || error is NoRouteToHostException ||
                error is EOFException || error is ProtocolException -> s(R.string.error_network_offline)
            error is InterruptedIOException -> s(R.string.error_network_timeout)
            error is SSLException -> s(R.string.error_network_ssl)
            error is SocketException -> s(R.string.error_network_offline)
            isParseError(error) -> s(R.string.error_chapter_unreadable)
            isReadable(error, error.message) -> error.message!!.trim()
            else -> s(R.string.error_generic)
        }
    }

    /** True for a failure of reading a page or a response, as opposed to getting it. */
    private fun isParseError(error: Throwable): Boolean = when (error) {
        is PatternSyntaxException, is SerializationException, is NumberFormatException,
        is NullPointerException, is IndexOutOfBoundsException, is ClassCastException,
        is NoSuchElementException, is org.jsoup.select.Selector.SelectorParseException,
        -> true
        is IllegalStateException -> error.message.let { it == null || it in kotlinDefaults || !isReadable(error, it) }
        is IllegalArgumentException -> error.javaClass == IllegalArgumentException::class.java && !isReadable(error, error.message)
        else -> false
    }

    private fun isReadable(error: Throwable, message: String?): Boolean {
        val text = message?.trim().orEmpty()
        if (text.isEmpty() || obfuscated.matches(text)) return false
        if (text == error.javaClass.name || text == error.javaClass.simpleName || text == error::class.simpleName) return false
        // "java.lang.Foo" or "java.lang.Foo: text" is toString() of a throwable, not a message.
        if (text.startsWith(error.javaClass.name + ":")) return false
        return true
    }
}

/** Readable text for this failure; see [ErrorMessages]. */
fun Throwable.userMessage(): String = ErrorMessages.of(this)
