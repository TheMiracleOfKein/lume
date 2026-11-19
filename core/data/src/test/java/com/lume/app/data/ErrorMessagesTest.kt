package com.lume.app.data

import com.lume.app.R
import com.lume.app.data.network.SourceHttpException
import com.lume.app.domain.CloudflareException
import kotlinx.serialization.SerializationException
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Test
import java.io.EOFException
import java.io.IOException
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.URI
import java.net.UnknownHostException
import java.util.regex.PatternSyntaxException
import javax.net.ssl.SSLHandshakeException

class ErrorMessagesTest {
    // Resolves a string to its resource id, so a test can tell which text was chosen.
    private fun of(error: Throwable): String = ErrorMessages.of(error) { id, _ -> "res:$id" }

    private fun res(id: Int) = "res:$id"

    @Test
    fun `network errors`() {
        assertEquals(res(R.string.error_network_offline), of(UnknownHostException("royalroad.com")))
        assertEquals(res(R.string.error_network_offline), of(ConnectException("Failed to connect")))
        assertEquals(res(R.string.error_network_offline), of(EOFException()))
        assertEquals(res(R.string.error_network_timeout), of(SocketTimeoutException("timeout")))
        assertEquals(res(R.string.error_network_ssl), of(SSLHandshakeException("Chain validation failed")))
    }

    @Test
    fun `cloudflare`() {
        assertEquals(res(R.string.error_cloudflare_needed), of(CloudflareException(URI("https://example.com/"))))
    }

    @Test
    fun `http errors keep their own localized text`() {
        // In the app the message is the localized text; without a context it is the raw "HTTP 404: url".
        val e = SourceHttpException(404, "https://example.com/x")
        assertEquals(e.message, of(e))
    }

    @Test
    fun `parse errors become the chapter could not be read text`() {
        val expected = res(R.string.error_chapter_unreadable)
        assertEquals(expected, of(PatternSyntaxException("Unclosed group", "(a", 2)))
        assertEquals(expected, of(SerializationException("Unexpected JSON token at offset 11")))
        assertEquals(expected, of(IllegalStateException("Check failed.")))
        assertEquals(expected, of(IllegalStateException()))
        assertEquals(expected, of(NumberFormatException("For input string: \"x\"")))
        assertEquals(expected, of(IndexOutOfBoundsException("Index 3 out of bounds for length 1")))
        assertEquals(expected, of(NullPointerException()))
        assertEquals(expected, of(org.jsoup.select.Selector.SelectorParseException("Could not parse query")))
    }

    @Test
    fun `the app's own readable messages are shown as they are`() {
        assertEquals("No chapters", of(IllegalStateException("No chapters")))
        assertEquals("This archive is damaged", of(IOException("This archive is damaged")))
    }

    @Test
    fun `obfuscated and empty messages never reach the screen`() {
        val generic = res(R.string.error_generic)
        for (message in listOf("z73", "a", "ab.c", "x_1$", "", "   ", null)) {
            assertEquals("message: $message", generic, of(IOException(message)))
        }
    }

    @Test
    fun `a class name is not a message`() {
        val generic = res(R.string.error_generic)
        assertEquals(generic, of(IOException("IOException")))
        assertEquals(generic, of(IOException("java.io.IOException")))
        assertEquals(generic, of(IOException("java.io.IOException: java.io.IOException")))
        assertEquals(res(R.string.error_chapter_unreadable), of(IllegalStateException("IllegalStateException")))
    }

    @Test
    fun `the real texts are not obfuscated names`() {
        assertFalse(ErrorMessages.of(IllegalStateException("z73")) { id, _ -> "res:$id" }.contains("z73"))
    }
}
