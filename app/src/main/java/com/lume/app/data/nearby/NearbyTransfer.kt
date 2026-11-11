package com.lume.app.data.nearby

import android.content.Context
import android.net.nsd.NsdManager
import android.net.nsd.NsdServiceInfo
import android.os.Build
import android.provider.Settings
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.downloads.DownloadStore
import com.lume.app.data.library.LibraryRepository
import com.lume.app.data.offline.OfflineTitleCache
import com.lume.app.data.source.SourceRegistry
import com.lume.app.data.title.TitleRepository
import com.lume.app.domain.Chapter
import com.lume.app.domain.TitleDetails
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.withContext
import java.io.File
import java.net.Inet4Address
import java.net.NetworkInterface

/**
 * Sending downloaded chapters to another phone on the same network: what is offered, where the
 * received chapters go, and how the phones find each other (network service discovery).
 */
class NearbyTransfer(
    private val context: Context,
    private val dao: LibraryDao,
    private val downloads: DownloadStore,
    private val offlineCache: OfflineTitleCache,
    private val titles: TitleRepository,
    private val library: LibraryRepository,
    private val sources: SourceRegistry,
) {
    /** The name other phones see: the device name from Android settings, else the model. */
    fun deviceName(): String =
        runCatching { Settings.Global.getString(context.contentResolver, Settings.Global.DEVICE_NAME) }.getOrNull()
            ?.takeIf { it.isNotBlank() } ?: Build.MODEL

    /** Scratch space for chapters while they arrive; emptied when a screen opens. */
    fun workDir(): File = File(context.cacheDir, "nearby").apply { deleteRecursively(); mkdirs() }

    /** The chosen chapters ("sourceId|titleId" → chapter ids) as an offer. */
    suspend fun buildOffer(selection: Map<String, Set<String>>, readMarks: Boolean): Offer = withContext(Dispatchers.IO) {
        val rows = dao.allDownloads().groupBy { "${it.sourceId}|${it.titleId}" }
        val offered = selection.mapNotNull { (key, chapterIds) ->
            val items = rows[key].orEmpty().filter { it.chapterId in chapterIds }
            val first = items.firstOrNull() ?: return@mapNotNull null
            val cached = titles.ownCached(first.sourceId, first.titleId)
            val details = cached?.details
                ?: offlineCache.detailsFromDownloads(first.sourceId, first.titleId)
                ?: TitleDetails(id = first.titleId, title = first.titleName, coverUrl = first.coverUrl)
            val known = cached?.chapters.orEmpty()
            val chapters = items.map { row ->
                val chapter = known.firstOrNull { it.id == row.chapterId }
                    ?: Chapter(id = row.chapterId, title = row.chapterName, url = row.chapterId)
                OfferChapter(chapter, pages = row.pageCount, bytes = downloads.bytesOf(row))
            }.sortedWith(compareBy(nullsLast()) { com.lume.app.domain.chapterSortKey(it.chapter) })
            OfferTitle(
                sourceId = first.sourceId,
                titleId = first.titleId,
                details = details,
                chapters = chapters,
                allChapters = known,
                readChapterIds = if (readMarks) dao.readChapterIds(first.sourceId, first.titleId) else emptyList(),
            )
        }.sortedBy { it.details.title.lowercase() }
        Offer(offered, readMarks)
    }

    /** The pages of an offered chapter, read from this phone's downloads. */
    suspend fun pagesOf(title: OfferTitle, chapter: OfferChapter): List<OutgoingPage> = withContext(Dispatchers.IO) {
        val pages = downloads.localPages(title.sourceId, title.titleId, chapter.chapter.id).orEmpty()
        val outgoing = pages.map { page -> OutgoingPage(downloads.pageSize(page.imageUrl)) { downloads.openPage(page.imageUrl) } }
        // A missing or empty page means the chapter can't be sent whole: the sender skips it.
        if (outgoing.any { it.size <= 0 }) emptyList() else outgoing
    }

    /** The offer's chapter with a [safeChapterUrl], so a sender can't plant a link for this phone to open. */
    private fun cleaned(sourceId: String, chapter: Chapter): Chapter {
        val base = sources.sourceById(sourceId)?.manifest?.baseUrl
        val url = safeChapterUrl(chapter.url, chapter.id, base)
        return if (url == chapter.url) chapter else chapter.copy(url = url)
    }

    private fun cleaned(details: TitleDetails): TitleDetails {
        val cover = safeCoverUrl(details.coverUrl)
        return if (cover == details.coverUrl) details else details.copy(coverUrl = cover)
    }

    /** What the receiver leaves out of [offer]: chapters already here and titles of sources this phone lacks. */
    suspend fun plan(offer: Offer): ReceivePlan = withContext(Dispatchers.IO) {
        val have = mutableSetOf<String>()
        val noSource = mutableSetOf<String>()
        val unsupported = mutableListOf<String>()
        offer.titles.forEachIndexed { ti, title ->
            val known = sources.sourceById(title.sourceId) != null
            if (!known) unsupported += title.details.title
            title.chapters.forEachIndexed { ci, chapter ->
                val key = Offer.chapterKey(ti, ci)
                when {
                    !known -> noSource += key
                    dao.getDownload(title.sourceId, title.titleId, chapter.chapter.id) != null -> have += key
                }
            }
        }
        ReceivePlan(skip = have + noSource, alreadyHere = have.size, unsupportedTitles = unsupported)
    }

    /** Keeps one received chapter; the first one of a title also puts the title in place (see [prepareTitle]). */
    suspend fun saveReceived(offer: Offer, title: OfferTitle, chapter: OfferChapter, pages: List<File>, prepared: MutableSet<String>) {
        val key = "${title.sourceId}|${title.titleId}"
        if (prepared.add(key)) prepareTitle(offer, title)
        downloads.saveReceivedChapter(title.sourceId, title.titleId, cleaned(title.details), cleaned(title.sourceId, chapter.chapter), pages)
    }

    /**
     * Makes a received title readable offline and puts it in the library: its details and chapter
     * list (merged with what this phone already knows) and, when they came along, its read marks.
     */
    private suspend fun prepareTitle(offer: Offer, title: OfferTitle) {
        val existing = offlineCache.get(title.sourceId, title.titleId)
        val details = cleaned(title.details)
        val incoming = title.allChapters.ifEmpty { title.chapters.map { it.chapter } }.map { cleaned(title.sourceId, it) }
        if (existing == null) {
            offlineCache.save(title.sourceId, title.titleId, details, incoming)
        } else {
            val ids = existing.chapters.mapTo(HashSet()) { it.id }
            val added = incoming.filter { it.id !in ids }
            if (added.isNotEmpty()) offlineCache.save(title.sourceId, title.titleId, existing.details, existing.chapters + added)
        }
        if (dao.getBookmark(title.sourceId, title.titleId) == null) {
            library.add(title.sourceId, title.titleId, details.title, details.coverUrl, details.typeLabel)
        }
        if (offer.readMarks && title.readChapterIds.isNotEmpty()) {
            val read = dao.readChapterIds(title.sourceId, title.titleId).toSet()
            library.markRead(title.sourceId, title.titleId, title.readChapterIds.filter { it !in read })
        }
    }

    companion object {
        const val SERVICE_TYPE = "_lume._tcp."

        /** This phone's addresses on the local network (Wi-Fi, hotspot, Ethernet), for typing in by hand. */
        fun localAddresses(): List<String> = runCatching {
            NetworkInterface.getNetworkInterfaces().toList()
                .filter { it.isUp && !it.isLoopback }
                .flatMap { it.inetAddresses.toList() }
                .filterIsInstance<Inet4Address>()
                .filter { it.isSiteLocalAddress }
                .mapNotNull { it.hostAddress }
                .distinct()
        }.getOrDefault(emptyList())

        /** "host:port" typed by hand; null when it can't be one. */
        fun parseAddress(text: String): Pair<String, Int>? {
            val trimmed = text.trim()
            val host = trimmed.substringBeforeLast(':', "").trim()
            val port = trimmed.substringAfterLast(':', "").trim().toIntOrNull() ?: return null
            if (host.isEmpty() || port !in 1..65535 || host.any { it.isWhitespace() }) return null
            return host to port
        }
    }
}

/**
 * A chapter link from another phone that is safe to keep: a plain relative path, or a web link on
 * the source's own site (or a subdomain of it). Anything else becomes the chapter id.
 */
internal fun safeChapterUrl(url: String, chapterId: String, sourceBaseUrl: String?): String {
    val relative = "://" !in url && !url.startsWith("/") && !url.startsWith("\\") &&
        url.substringBefore('/').none { it == ':' } &&
        url.split('/', '\\').none { it == ".." }
    if (relative) return url
    val isWeb = url.startsWith("http://", ignoreCase = true) || url.startsWith("https://", ignoreCase = true)
    if (!isWeb || sourceBaseUrl == null) return chapterId
    val baseHost = runCatching { java.net.URI(sourceBaseUrl).host }.getOrNull()?.lowercase()
    val host = runCatching { java.net.URI(url).host }.getOrNull()?.lowercase()
    if (baseHost.isNullOrEmpty() || host.isNullOrEmpty()) return chapterId
    return if (host == baseHost || host.endsWith(".$baseHost")) url else chapterId
}

/** [text] cut to at most [maxBytes] UTF-8 bytes, never in the middle of a character. */
internal fun truncateUtf8(text: String, maxBytes: Int): String {
    var bytes = 0
    var end = 0
    while (end < text.length) {
        val cp = text.codePointAt(end)
        val size = String(Character.toChars(cp)).toByteArray(Charsets.UTF_8).size
        if (bytes + size > maxBytes) break
        bytes += size
        end += Character.charCount(cp)
    }
    return text.substring(0, end)
}

/** [url] when it is a web address, else null. */
internal fun safeCoverUrl(url: String?): String? =
    url?.takeIf { it.startsWith("http://", ignoreCase = true) || it.startsWith("https://", ignoreCase = true) }

/** What the receiver skips; [alreadyHere] chapters are downloaded on this phone already. */
data class ReceivePlan(val skip: Set<String>, val alreadyHere: Int, val unsupportedTitles: List<String>)

/** A sender found on the network. */
data class NearbyPeer(val name: String, val host: String, val port: Int)

/** Announces a listening sender on the local network while it is registered. */
class NearbyAdvertiser(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private var listener: NsdManager.RegistrationListener? = null

    fun start(name: String, port: Int) {
        stop()
        val info = NsdServiceInfo().apply {
            serviceName = truncateUtf8(name, 63)
            serviceType = NearbyTransfer.SERVICE_TYPE
            setPort(port)
        }
        val l = object : NsdManager.RegistrationListener {
            override fun onServiceRegistered(info: NsdServiceInfo) = Unit
            override fun onRegistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
            override fun onServiceUnregistered(info: NsdServiceInfo) = Unit
            override fun onUnregistrationFailed(info: NsdServiceInfo, errorCode: Int) = Unit
        }
        listener = l
        runCatching { nsd?.registerService(info, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stop() {
        listener?.let { l -> runCatching { nsd?.unregisterService(l) } }
        listener = null
    }
}

/** Finds senders on the local network while started. */
class NearbyDiscovery(context: Context) {
    private val nsd = context.getSystemService(NsdManager::class.java)
    private val executor = java.util.concurrent.Executors.newSingleThreadExecutor()
    private var listener: NsdManager.DiscoveryListener? = null
    private val callbacks = mutableListOf<Any>()
    private val _peers = MutableStateFlow<List<NearbyPeer>>(emptyList())
    val peers: StateFlow<List<NearbyPeer>> = _peers.asStateFlow()

    // Android before 14 resolves one service at a time.
    private val pending = ArrayDeque<NsdServiceInfo>()
    private var resolving = false

    fun start() {
        if (listener != null) return
        val l = object : NsdManager.DiscoveryListener {
            override fun onDiscoveryStarted(serviceType: String) = Unit
            override fun onDiscoveryStopped(serviceType: String) = Unit
            override fun onStartDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onStopDiscoveryFailed(serviceType: String, errorCode: Int) = Unit
            override fun onServiceFound(info: NsdServiceInfo) = resolve(info)
            override fun onServiceLost(info: NsdServiceInfo) {
                _peers.update { list -> list.filterNot { it.name == info.serviceName } }
            }
        }
        listener = l
        runCatching { nsd?.discoverServices(NearbyTransfer.SERVICE_TYPE, NsdManager.PROTOCOL_DNS_SD, l) }
    }

    fun stop() {
        listener?.let { l -> runCatching { nsd?.stopServiceDiscovery(l) } }
        listener = null
        if (Build.VERSION.SDK_INT >= 34) {
            synchronized(callbacks) {
                callbacks.forEach { cb -> runCatching { nsd?.unregisterServiceInfoCallback(cb as NsdManager.ServiceInfoCallback) } }
                callbacks.clear()
            }
        }
        synchronized(pending) { pending.clear() }
        _peers.update { emptyList() }
    }

    fun close() {
        stop()
        executor.shutdown()
    }

    private fun found(name: String, host: String?, port: Int) {
        if (host == null || port <= 0) return
        val peer = NearbyPeer(name, host, port)
        _peers.update { list -> (list.filterNot { it.name == name } + peer).sortedBy { it.name.lowercase() } }
    }

    private fun resolve(info: NsdServiceInfo) {
        if (Build.VERSION.SDK_INT >= 34) {
            val cb = object : NsdManager.ServiceInfoCallback {
                override fun onServiceInfoCallbackRegistrationFailed(errorCode: Int) = Unit
                override fun onServiceUpdated(updated: NsdServiceInfo) {
                    val host = updated.hostAddresses.firstOrNull { it is Inet4Address } ?: updated.hostAddresses.firstOrNull()
                    found(updated.serviceName, host?.hostAddress, updated.port)
                }
                override fun onServiceLost() {
                    _peers.update { list -> list.filterNot { it.name == info.serviceName } }
                    runCatching { nsd?.unregisterServiceInfoCallback(this) }
                    synchronized(callbacks) { callbacks.remove(this) }
                }
                override fun onServiceInfoCallbackUnregistered() = Unit
            }
            synchronized(callbacks) { callbacks += cb }
            runCatching { nsd?.registerServiceInfoCallback(info, executor, cb) }
        } else {
            synchronized(pending) {
                pending.addLast(info)
                if (resolving) return
                resolving = true
            }
            resolveNext()
        }
    }

    @Suppress("DEPRECATION")
    private fun resolveNext() {
        val next = synchronized(pending) {
            pending.removeFirstOrNull().also { if (it == null) resolving = false }
        } ?: return
        val started = runCatching {
            nsd?.resolveService(
                next,
                object : NsdManager.ResolveListener {
                    override fun onResolveFailed(info: NsdServiceInfo, errorCode: Int) = resolveNext()
                    override fun onServiceResolved(info: NsdServiceInfo) {
                        found(info.serviceName, info.host?.hostAddress, info.port)
                        resolveNext()
                    }
                },
            )
        }.isSuccess
        if (!started) resolveNext()
    }
}
