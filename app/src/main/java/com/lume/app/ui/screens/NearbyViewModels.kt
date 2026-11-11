package com.lume.app.ui.screens

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.lume.app.AppContainer
import com.lume.app.data.db.DownloadEntity
import com.lume.app.data.nearby.NearbyAdvertiser
import com.lume.app.data.nearby.NearbyChannel
import com.lume.app.data.nearby.NearbyDiscovery
import com.lume.app.data.nearby.NearbyPeer
import com.lume.app.data.nearby.NearbyReceiver
import com.lume.app.data.nearby.NearbySender
import com.lume.app.data.nearby.NearbyTransfer
import com.lume.app.data.nearby.Offer
import com.lume.app.data.nearby.ReceivePlan
import com.lume.app.data.nearby.SendResult
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.flowOn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.InetSocketAddress
import java.net.ServerSocket
import java.net.Socket

/** How a transfer ended, on either phone. */
sealed interface NearbyOutcome {
    data class Done(val chapters: Int) : NearbyOutcome
    data object Declined : NearbyOutcome
    data object Cancelled : NearbyOutcome
    data class Failed(val message: String?) : NearbyOutcome
}

/** Limits progress updates to about ten a second. */
private class ProgressThrottle {
    private var last = 0L
    fun ready(done: Long, total: Long): Boolean {
        val now = System.nanoTime()
        if (done < total && now - last < 100_000_000L) return false
        last = now
        return true
    }
}

sealed interface SendPhase {
    data object Choose : SendPhase
    data class Waiting(val name: String, val addresses: List<String>, val port: Int) : SendPhase
    data class Confirm(val peer: String, val code: String) : SendPhase
    data class Sending(val peer: String, val sent: Long, val total: Long) : SendPhase
    data class Finished(val outcome: NearbyOutcome) : SendPhase
}

/**
 * The sending phone: choose chapters, then listen (and announce itself on the network) until a
 * receiver connects. Listening stops when the screen closes.
 */
class NearbySendViewModel(private val app: AppContainer) : ViewModel() {
    val groups: StateFlow<List<DownloadTitleGroup>> = app.dao.observeDownloads()
        .map { downloads ->
            downloads.groupBy { it.sourceId to it.titleId }
                .map { (_, items) ->
                    val first = items.first()
                    DownloadTitleGroup(first.sourceId, first.titleId, first.titleName, first.coverUrl, items.sortedBy { it.chapterName })
                }
                .sortedBy { it.titleName.lowercase() }
        }
        .flowOn(Dispatchers.Default)
        .stateIn(viewModelScope, SharingStarted.Eagerly, emptyList())

    /** Bytes per chapter ("sourceId|titleId|chapterId"). */
    var chapterBytes by mutableStateOf<Map<String, Long>>(emptyMap())
        private set

    /** Chosen chapters: "sourceId|titleId" → chapter ids. */
    var selection by mutableStateOf<Map<String, Set<String>>>(emptyMap())
        private set
    var readMarks by mutableStateOf(true)
    var expanded by mutableStateOf<Set<String>>(emptySet())
        private set
    var phase by mutableStateOf<SendPhase>(SendPhase.Choose)
        private set

    private val advertiser = NearbyAdvertiser(app.app)
    private var server: ServerSocket? = null
    private var active: Socket? = null
    private var decision: CompletableDeferred<Boolean>? = null
    private var offer: Offer? = null
    @Volatile private var busy = false
    @Volatile private var cancelled = false

    init {
        viewModelScope.launch {
            app.dao.observeDownloads().collect { rows ->
                chapterBytes = withContext(Dispatchers.IO) { rows.associate { chapterKey(it) to app.downloads.bytesOf(it) } }
            }
        }
    }

    private fun chapterKey(row: DownloadEntity) = "${row.sourceId}|${row.titleId}|${row.chapterId}"

    val selectedCount: Int get() = selection.values.sumOf { it.size }
    val selectedBytes: Long
        get() = selection.entries.sumOf { (title, ids) -> ids.sumOf { chapterBytes["$title|$it"] ?: 0L } }

    fun toggleExpanded(key: String) {
        expanded = if (key in expanded) expanded - key else expanded + key
    }

    fun toggleTitle(group: DownloadTitleGroup) {
        val key = "${group.sourceId}|${group.titleId}"
        val all = group.chapters.mapTo(HashSet()) { it.chapterId }
        selection = if (selection[key]?.size == all.size) selection - key else selection + (key to all)
    }

    fun toggleChapter(group: DownloadTitleGroup, chapterId: String) {
        val key = "${group.sourceId}|${group.titleId}"
        val now = selection[key].orEmpty()
        val next = if (chapterId in now) now - chapterId else now + chapterId
        selection = if (next.isEmpty()) selection - key else selection + (key to next)
    }

    fun selectAll() {
        selection = groups.value.associate { g -> "${g.sourceId}|${g.titleId}" to g.chapters.mapTo(HashSet()) { it.chapterId } }
    }

    fun clearSelection() {
        selection = emptyMap()
    }

    /** Builds the offer and starts listening. */
    fun start() {
        if (selection.isEmpty() || server != null) return
        viewModelScope.launch {
            offer = app.nearby.buildOffer(selection, readMarks)
            val socket = withContext(Dispatchers.IO) { runCatching { ServerSocket(0) }.getOrNull() }
            if (socket == null) {
                phase = SendPhase.Finished(NearbyOutcome.Failed(null))
                return@launch
            }
            server = socket
            val name = app.nearby.deviceName()
            advertiser.start(name, socket.localPort)
            phase = waitingPhase()
            launch(Dispatchers.IO) { acceptLoop(socket) }
        }
    }

    private fun waitingPhase(): SendPhase.Waiting {
        val port = server?.localPort ?: 0
        return SendPhase.Waiting(app.nearby.deviceName(), NearbyTransfer.localAddresses(), port)
    }

    private suspend fun acceptLoop(socket: ServerSocket) {
        while (viewModelScope.isActive && !socket.isClosed) {
            val client = try {
                socket.accept()
            } catch (_: IOException) {
                break
            }
            if (busy) {
                runCatching { client.close() }
                continue
            }
            busy = true
            viewModelScope.launch(Dispatchers.IO) { handle(client) }
        }
    }

    private suspend fun handle(client: Socket) {
        active = client
        cancelled = false
        var paired = false
        try {
            client.soTimeout = HANDSHAKE_TIMEOUT
            val handshake = NearbyChannel.accept(client.getInputStream(), client.getOutputStream(), app.nearby.deviceName())
            paired = true
            client.soTimeout = 0
            val choice = CompletableDeferred<Boolean>().also { decision = it }
            phase = SendPhase.Confirm(handshake.peerName, handshake.code)
            if (!choice.await()) {
                phase = waitingPhase()
                return
            }
            val offer = offer ?: return
            phase = SendPhase.Sending(handshake.peerName, 0, offer.totalBytes)
            client.soTimeout = TRANSFER_TIMEOUT
            val throttle = ProgressThrottle()
            val result = NearbySender(handshake).send(offer, app.nearby::pagesOf) { sent, total ->
                if (throttle.ready(sent, total)) phase = SendPhase.Sending(handshake.peerName, sent, total)
            }
            phase = SendPhase.Finished(
                when (result) {
                    is SendResult.Sent -> NearbyOutcome.Done(result.chapters)
                    SendResult.Declined -> NearbyOutcome.Declined
                },
            )
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            // A stray connection that never paired just lets the screen keep waiting.
            phase = when {
                !paired -> phase
                cancelled -> SendPhase.Finished(NearbyOutcome.Cancelled)
                else -> SendPhase.Finished(NearbyOutcome.Failed(e.message))
            }
        } finally {
            decision = null
            runCatching { client.close() }
            active = null
            busy = false
        }
    }

    /** The user checked the code ([send]) or turned the receiver away. */
    fun confirm(send: Boolean) {
        decision?.complete(send)
    }

    fun cancel() {
        cancelled = true
        decision?.complete(false)
        runCatching { active?.close() }
    }

    /** After a transfer: keep listening for the next phone (or the same one, to resume). */
    fun waitAgain() {
        if (server != null) phase = waitingPhase()
    }

    override fun onCleared() {
        advertiser.stop()
        decision?.complete(false)
        runCatching { active?.close() }
        runCatching { server?.close() }
    }

    private companion object {
        const val HANDSHAKE_TIMEOUT = 20_000
        const val TRANSFER_TIMEOUT = 120_000
    }
}

sealed interface ReceivePhase {
    data object Find : ReceivePhase
    data object Connecting : ReceivePhase
    data class Paired(val peer: String, val code: String) : ReceivePhase
    data class Review(val peer: String, val code: String, val offer: Offer, val plan: ReceivePlan) : ReceivePhase
    data class Receiving(val peer: String, val received: Long, val total: Long) : ReceivePhase
    data class Finished(val outcome: NearbyOutcome) : ReceivePhase
}

/** The receiving phone: find a sender (or type its address), check the code, accept, receive. */
class NearbyReceiveViewModel(private val app: AppContainer) : ViewModel() {
    private val discovery = NearbyDiscovery(app.app)
    val peers: StateFlow<List<NearbyPeer>> = discovery.peers

    var phase by mutableStateOf<ReceivePhase>(ReceivePhase.Find)
        private set
    var address by mutableStateOf("")

    private var active: Socket? = null
    private var decision: CompletableDeferred<Boolean>? = null
    private var last: Pair<String, Int>? = null
    @Volatile private var cancelled = false

    init {
        discovery.start()
    }

    fun connectTyped(): Boolean {
        val parsed = NearbyTransfer.parseAddress(address) ?: return false
        connect(parsed.first, parsed.second)
        return true
    }

    fun connect(host: String, port: Int) {
        if (phase !is ReceivePhase.Find && phase !is ReceivePhase.Finished) return
        last = host to port
        cancelled = false
        phase = ReceivePhase.Connecting
        viewModelScope.launch(Dispatchers.IO) { run(host, port) }
    }

    /** Connects to the same sender again; chapters that arrived already are skipped. */
    fun retry() {
        last?.let { (host, port) -> connect(host, port) }
    }

    private suspend fun run(host: String, port: Int) {
        val socket = Socket()
        active = socket
        var paired = false
        try {
            socket.connect(InetSocketAddress(host, port), CONNECT_TIMEOUT)
            socket.soTimeout = HANDSHAKE_TIMEOUT
            val handshake = NearbyChannel.connect(socket.getInputStream(), socket.getOutputStream(), app.nearby.deviceName())
            paired = true
            phase = ReceivePhase.Paired(handshake.peerName, handshake.code)
            socket.soTimeout = 0 // the sender's owner may take a while to confirm
            val receiver = NearbyReceiver(handshake)
            val offer = receiver.readOffer()
            val plan = app.nearby.plan(offer)
            val choice = CompletableDeferred<Boolean>().also { decision = it }
            phase = ReceivePhase.Review(handshake.peerName, handshake.code, offer, plan)
            if (!choice.await()) {
                runCatching { receiver.decline() }
                phase = ReceivePhase.Find
                return
            }
            socket.soTimeout = TRANSFER_TIMEOUT
            val total = offer.keys().filter { it !in plan.skip }.sumOf(offer::bytesOf)
            phase = ReceivePhase.Receiving(handshake.peerName, 0, total)
            val throttle = ProgressThrottle()
            val prepared = mutableSetOf<String>()
            val count = receiver.receive(
                offer = offer,
                skip = plan.skip,
                workDir = app.nearby.workDir(),
                save = { title, chapter, files -> app.nearby.saveReceived(offer, title, chapter, files, prepared) },
            ) { received, all ->
                if (throttle.ready(received, all)) phase = ReceivePhase.Receiving(handshake.peerName, received, all)
            }
            phase = ReceivePhase.Finished(NearbyOutcome.Done(count))
        } catch (e: Exception) {
            if (e is kotlinx.coroutines.CancellationException) throw e
            phase = when {
                cancelled -> if (paired) ReceivePhase.Finished(NearbyOutcome.Cancelled) else ReceivePhase.Find
                // The sender turned this phone away before sending anything.
                paired && phase is ReceivePhase.Paired -> ReceivePhase.Finished(NearbyOutcome.Declined)
                else -> ReceivePhase.Finished(NearbyOutcome.Failed(e.message))
            }
        } finally {
            decision = null
            runCatching { socket.close() }
            active = null
        }
    }

    fun accept(yes: Boolean) {
        decision?.complete(yes)
    }

    fun cancel() {
        cancelled = true
        decision?.complete(false)
        runCatching { active?.close() }
    }

    fun backToFind() {
        phase = ReceivePhase.Find
    }

    override fun onCleared() {
        discovery.close()
        decision?.complete(false)
        runCatching { active?.close() }
    }

    private companion object {
        const val CONNECT_TIMEOUT = 10_000
        const val HANDSHAKE_TIMEOUT = 20_000
        const val TRANSFER_TIMEOUT = 120_000
    }
}
