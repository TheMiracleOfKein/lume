package com.lume.app.data.nearby

import com.lume.app.domain.Chapter
import com.lume.app.domain.TitleDetails
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.IOException
import java.net.ServerSocket
import java.net.Socket
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import java.util.concurrent.Future
import kotlin.random.Random

class NearbyProtocolTest {
    @get:Rule
    val temp = TemporaryFolder()

    private val pool = Executors.newCachedThreadPool()

    /** Page bytes per "titleIndex/chapterIndex". */
    private val pages = mapOf(
        "0/0" to listOf(Random(1).nextBytes(300_000), Random(2).nextBytes(10)),
        "0/1" to listOf(Random(3).nextBytes(700_000)),
        "1/0" to listOf(Random(4).nextBytes(5), Random(5).nextBytes(6), Random(6).nextBytes(7)),
    )

    private val offer = Offer(
        titles = listOf(
            OfferTitle(
                "src", "t1", TitleDetails(id = "t1", title = "One"),
                chapters = listOf(
                    OfferChapter(Chapter("c1", "Chapter 1", "c1"), 2, 300_010),
                    OfferChapter(Chapter("c2", "Chapter 2", "c2"), 1, 700_000),
                ),
                readChapterIds = listOf("c1"),
            ),
            OfferTitle("src", "t2", TitleDetails(id = "t2", title = "Two"), chapters = listOf(OfferChapter(Chapter("x", "X", "x"), 3, 18))),
        ),
        readMarks = true,
    )

    private fun keyOf(title: OfferTitle, chapter: OfferChapter): String {
        val ti = offer.titles.indexOf(title)
        return Offer.chapterKey(ti, offer.titles[ti].chapters.indexOf(chapter))
    }

    private fun outgoing(title: OfferTitle, chapter: OfferChapter): List<OutgoingPage> =
        pages.getValue(keyOf(title, chapter)).map { bytes -> OutgoingPage(bytes.size.toLong()) { bytes.inputStream() } }

    /** A connected pair: the sender's side runs [senderSide] on another thread. */
    private fun <T> pair(senderSide: (NearbyChannel.Handshake) -> T, receiverSide: (NearbyChannel.Handshake) -> Unit): T {
        ServerSocket(0).use { server ->
            val sender: Future<T> = pool.submit(
                Callable {
                    server.accept().use { s -> senderSide(NearbyChannel.accept(s.getInputStream(), s.getOutputStream(), "Sender")) }
                },
            )
            Socket("127.0.0.1", server.localPort).use { s ->
                runCatching { receiverSide(NearbyChannel.connect(s.getInputStream(), s.getOutputStream(), "Receiver")) }
                    .onFailure { if (it !is IOException) throw it }
            }
            return sender.get()
        }
    }

    @Test
    fun bothPhonesShowTheSameCodeAndEachOthersName() {
        var receiverView: Pair<String, String>? = null
        val senderView = pair({ it.peerName to it.code }) { receiverView = it.peerName to it.code }
        assertEquals("Receiver", senderView.first)
        assertEquals("Sender", receiverView!!.first)
        assertEquals(senderView.second, receiverView!!.second)
        assertTrue(Regex("""\d{6}""").matches(senderView.second))
    }

    @Test
    fun sendsTheChosenChaptersWithTheirPagesAndSkipsTheRest() {
        val received = mutableMapOf<String, List<ByteArray>>()
        var count = 0
        var lastProgress = 0L to 0L
        val result = pair({ hs -> runBlocking { NearbySender(hs).send(offer, { t, c -> outgoing(t, c) }) } }) { hs ->
            val receiver = NearbyReceiver(hs)
            val got = receiver.readOffer()
            assertEquals(offer, got)
            count = runBlocking {
                receiver.receive(got, skip = setOf("0/1"), workDir = temp.newFolder(), save = { t, c, files ->
                    received[keyOf(t, c)] = files.map { it.readBytes() }
                }) { done, total -> lastProgress = done to total }
            }
        }
        assertEquals(SendResult.Sent(2), result)
        assertEquals(2, count)
        assertEquals(setOf("0/0", "1/0"), received.keys)
        for (key in received.keys) {
            val expected = pages.getValue(key)
            assertEquals(expected.size, received.getValue(key).size)
            expected.zip(received.getValue(key)).forEach { (a, b) -> assertArrayEquals(a, b) }
        }
        assertEquals(300_010L + 18L, lastProgress.second)
        assertEquals(lastProgress.second, lastProgress.first)
    }

    @Test
    fun declineSendsNothing() {
        var saved = 0
        val result = pair({ hs -> runBlocking { NearbySender(hs).send(offer, { _, _ -> saved++; emptyList() }) } }) { hs ->
            val receiver = NearbyReceiver(hs)
            receiver.readOffer()
            receiver.decline()
        }
        assertEquals(SendResult.Declined, result)
        assertEquals(0, saved)
    }

    @Test
    fun chaptersBeforeABrokenConnectionAreKept() {
        val received = mutableListOf<String>()
        val result = runCatching {
            pair({ hs ->
                runBlocking {
                    NearbySender(hs).send(offer, { t, c ->
                        if (keyOf(t, c) == "0/1") throw IOException("disk gone")
                        outgoing(t, c)
                    })
                }
            }) { hs ->
                val receiver = NearbyReceiver(hs)
                val got = receiver.readOffer()
                runBlocking { receiver.receive(got, emptySet(), temp.newFolder(), save = { t, c, _ -> received += keyOf(t, c) }) }
                fail("The receiver should see the connection break")
            }
        }
        assertTrue(result.exceptionOrNull()?.cause is IOException)
        assertEquals(listOf("0/0"), received)
    }

    @Test
    fun somethingThatIsNotLumeIsTurnedAway() {
        ServerSocket(0).use { server ->
            val sender = pool.submit(
                Callable {
                    server.accept().use { s -> runCatching { NearbyChannel.accept(s.getInputStream(), s.getOutputStream(), "Sender") } }
                },
            )
            Socket("127.0.0.1", server.localPort).use { s ->
                s.getOutputStream().write("GET / HTTP/1.1\r\nHost: x\r\n\r\n".toByteArray())
                s.getOutputStream().flush()
                assertTrue(sender.get().exceptionOrNull() is IOException)
            }
        }
    }

    @Test
    fun offerKeysLocateChapters() {
        assertEquals(listOf("0/0", "0/1", "1/0"), offer.keys())
        assertEquals(1 to 0, offer.locate("1/0"))
        assertNull(offer.locate("2/0"))
        assertNull(offer.locate("0/5"))
        assertNull(offer.locate("nonsense"))
        assertEquals(700_000L, offer.bytesOf("0/1"))
        assertEquals(3, offer.chapterCount)
    }

    @Test
    fun parsesTypedAddresses() {
        assertEquals("192.168.1.5" to 41234, NearbyTransfer.parseAddress(" 192.168.1.5:41234 "))
        assertNull(NearbyTransfer.parseAddress("192.168.1.5"))
        assertNull(NearbyTransfer.parseAddress("192.168.1.5:99999"))
        assertNull(NearbyTransfer.parseAddress(":80"))
        assertNull(NearbyTransfer.parseAddress("a b:80"))
    }
}
