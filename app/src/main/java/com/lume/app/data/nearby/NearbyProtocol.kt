package com.lume.app.data.nearby

import com.lume.app.domain.Chapter
import com.lume.app.domain.TitleDetails
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import java.io.IOException
import java.io.InputStream
import java.security.MessageDigest

/** One downloaded chapter on offer. */
@Serializable
data class OfferChapter(val chapter: Chapter, val pages: Int, val bytes: Long)

/** A title on offer: its details, the chosen chapters and what the receiver needs to show it offline. */
@Serializable
data class OfferTitle(
    val sourceId: String,
    val titleId: String,
    val details: TitleDetails,
    val chapters: List<OfferChapter>,
    /** The title's whole chapter list as the sender knows it, so the receiver's chapter list isn't only the sent ones. */
    val allChapters: List<Chapter> = emptyList(),
    /** Read chapters; sent only when read marks come along. */
    val readChapterIds: List<String> = emptyList(),
)

@Serializable
data class Offer(val titles: List<OfferTitle>, val readMarks: Boolean) {
    val chapterCount: Int get() = titles.sumOf { it.chapters.size }
    val totalBytes: Long get() = titles.sumOf { t -> t.chapters.sumOf { it.bytes } }

    /** Keys of every chapter, in sending order. */
    fun keys(): List<String> = titles.flatMapIndexed { ti, t -> t.chapters.indices.map { chapterKey(ti, it) } }

    fun bytesOf(key: String): Long = locate(key)?.let { (t, c) -> titles[t].chapters[c].bytes } ?: 0L

    /** Title and chapter index of [key], or null when it isn't in this offer. */
    fun locate(key: String): Pair<Int, Int>? {
        val t = key.substringBefore('/').toIntOrNull() ?: return null
        val c = key.substringAfter('/', "").toIntOrNull() ?: return null
        return (t to c).takeIf { t in titles.indices && c in titles[t].chapters.indices }
    }

    companion object {
        fun chapterKey(titleIndex: Int, chapterIndex: Int) = "$titleIndex/$chapterIndex"
    }
}

/** A page to send: its size and a way to read it. */
class OutgoingPage(val size: Long, val open: () -> InputStream)

@Serializable
internal sealed class Control {
    @Serializable @SerialName("offer")
    data class OfferMsg(val offer: Offer) : Control()

    /** [skip]: chapters the receiver already has, or can't keep (its source isn't on that phone). */
    @Serializable @SerialName("answer")
    data class Answer(val accept: Boolean, val skip: List<String> = emptyList()) : Control()

    @Serializable @SerialName("head")
    data class Head(val key: String, val pageSizes: List<Long>) : Control()

    @Serializable @SerialName("tail")
    data class Tail(val sha256: String) : Control()

    @Serializable @SerialName("ack")
    data class Ack(val key: String) : Control()

    @Serializable @SerialName("done")
    data object Done : Control()
}

private val controlJson = Json { ignoreUnknownKeys = true; encodeDefaults = true; classDiscriminator = "kind" }
private const val TYPE_CONTROL = 1
private const val TYPE_DATA = 2
private const val CHUNK = 256 * 1024
private const val MAX_PAGE_BYTES = 128L * 1024 * 1024
private const val MAX_PAGES = 2000
private const val SIZE_SLACK = 16L * 1024 * 1024
private const val SPARE_SPACE = 64L * 1024 * 1024

private fun NearbyChannel.send(message: Control) =
    write(TYPE_CONTROL, controlJson.encodeToString(Control.serializer(), message).toByteArray())

private fun NearbyChannel.receive(): Control {
    val frame = read()
    if (frame.type != TYPE_CONTROL) throw IOException("Expected a message")
    return try {
        controlJson.decodeFromString(Control.serializer(), frame.body.toString(Charsets.UTF_8))
    } catch (e: kotlinx.serialization.SerializationException) {
        throw IOException("Bad message", e)
    } catch (e: IllegalArgumentException) {
        throw IOException("Bad message", e)
    }
}

private inline fun <reified T : Control> NearbyChannel.expect(): T =
    receive() as? T ?: throw IOException("Unexpected message")

private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }

/** Outcome of sending. */
sealed interface SendResult {
    data object Declined : SendResult
    data class Sent(val chapters: Int) : SendResult
}

/** The sending side after pairing. Blocks its thread: run it on Dispatchers.IO; closing the socket stops it. */
class NearbySender(private val handshake: NearbyChannel.Handshake) {
    private val channel get() = handshake.channel

    suspend fun send(
        offer: Offer,
        pagesOf: suspend (OfferTitle, OfferChapter) -> List<OutgoingPage>,
        onProgress: (sent: Long, total: Long) -> Unit = { _, _ -> },
    ): SendResult {
        channel.send(Control.OfferMsg(offer))
        val answer = channel.expect<Control.Answer>()
        if (!answer.accept) return SendResult.Declined
        val skip = answer.skip.toSet()
        val wanted = offer.keys().filter { it !in skip }
        var total = wanted.sumOf(offer::bytesOf)
        var sent = 0L
        var count = 0
        val buffer = ByteArray(CHUNK)
        onProgress(0, total)
        for (key in wanted) {
            val (ti, ci) = offer.locate(key) ?: continue
            val title = offer.titles[ti]
            val pages = pagesOf(title, title.chapters[ci])
            if (pages.isEmpty()) {
                // Nothing readable on this phone: leave the chapter out and carry on with the rest.
                total -= offer.bytesOf(key)
                onProgress(minOf(sent, total), total)
                continue
            }
            channel.send(Control.Head(key, pages.map { it.size }))
            val digest = MessageDigest.getInstance("SHA-256")
            for (page in pages) {
                page.open().use { stream ->
                    var remaining = page.size
                    while (remaining > 0) {
                        val n = stream.read(buffer, 0, minOf(buffer.size.toLong(), remaining).toInt())
                        if (n < 0) throw IOException("A page got shorter while sending")
                        digest.update(buffer, 0, n)
                        channel.write(TYPE_DATA, buffer, 0, n)
                        remaining -= n
                        sent += n
                        onProgress(minOf(sent, total), total)
                    }
                }
            }
            channel.send(Control.Tail(digest.digest().hex()))
            if (channel.expect<Control.Ack>().key != key) throw IOException("Wrong chapter confirmed")
            count++
        }
        channel.send(Control.Done)
        return SendResult.Sent(count)
    }
}

/** The receiving side after pairing. Blocking: run it on an IO thread; closing the socket stops it. */
class NearbyReceiver(private val handshake: NearbyChannel.Handshake) {
    private val channel get() = handshake.channel

    /** Waits until the sender confirms the code and sends what it offers. */
    fun readOffer(): Offer = channel.expect<Control.OfferMsg>().offer

    fun decline() = channel.send(Control.Answer(accept = false))

    /**
     * Accepts [offer] minus [skip] and receives chapter after chapter. Each one is written to
     * [workDir], checked against its checksum, handed to [save] and then confirmed, so a broken
     * connection keeps every chapter before it. Returns how many chapters arrived.
     */
    suspend fun receive(
        offer: Offer,
        skip: Set<String>,
        workDir: File,
        save: suspend (OfferTitle, OfferChapter, List<File>) -> Unit,
        onProgress: (received: Long, total: Long) -> Unit = { _, _ -> },
    ): Int {
        channel.send(Control.Answer(accept = true, skip = skip.toList()))
        val total = offer.keys().filter { it !in skip }.sumOf(offer::bytesOf)
        var received = 0L
        var count = 0
        onProgress(0, total)
        while (true) {
            val head = when (val message = channel.receive()) {
                is Control.Done -> {
                    // Chapters the sender had to leave out never arrive; don't leave the bar short.
                    onProgress(total, total)
                    return count
                }
                is Control.Head -> message
                else -> throw IOException("Unexpected message")
            }
            val (ti, ci) = offer.locate(head.key)?.takeIf { head.key !in skip } ?: throw IOException("Chapter not asked for")
            if (head.pageSizes.isEmpty() || head.pageSizes.size > MAX_PAGES || head.pageSizes.any { it !in 1..MAX_PAGE_BYTES }) {
                throw IOException("Bad page sizes")
            }
            val sum = head.pageSizes.sum()
            val declared = offer.bytesOf(head.key)
            if (sum > maxOf(declared * 2, declared + SIZE_SLACK)) throw IOException("Chapter bigger than offered")
            if (workDir.usableSpace < sum + SPARE_SPACE) throw IOException("Not enough space")
            val dir = File(workDir, "chapter").apply { deleteRecursively(); mkdirs() }
            try {
                val digest = MessageDigest.getInstance("SHA-256")
                val files = head.pageSizes.mapIndexed { i, size ->
                    val file = File(dir, "%04d".format(i))
                    file.outputStream().buffered().use { out ->
                        var remaining = size
                        while (remaining > 0) {
                            val frame = channel.read()
                            if (frame.type != TYPE_DATA || frame.body.size > remaining) throw IOException("Bad page data")
                            digest.update(frame.body)
                            out.write(frame.body)
                            remaining -= frame.body.size
                            received += frame.body.size
                            onProgress(minOf(received, total), total)
                        }
                    }
                    file
                }
                val tail = channel.expect<Control.Tail>()
                if (!MessageDigest.isEqual(tail.sha256.toByteArray(), digest.digest().hex().toByteArray())) {
                    throw IOException("Checksum mismatch")
                }
                val title = offer.titles[ti]
                save(title, title.chapters[ci], files)
            } finally {
                dir.deleteRecursively()
            }
            channel.send(Control.Ack(head.key))
            count++
        }
    }
}
