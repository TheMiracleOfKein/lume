package com.lume.app.data.source

import com.lume.app.data.prefs.AppPreferences
import com.lume.app.domain.CloudflareException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.MapSerializer
import kotlinx.serialization.builtins.serializer
import kotlinx.serialization.json.Json

/** The last failed call of a source; cleared by its next successful call, dropped after a week. */
@Serializable
data class SourceProblem(
    val message: String,
    val at: Long,
    /** The site asks for its browser check (Cloudflare). */
    val siteCheck: Boolean = false,
)

/** Where [SourceHealth] keeps its entries between app starts. */
interface SourceHealthStore {
    fun load(): Map<String, SourceProblem>
    suspend fun save(problems: Map<String, SourceProblem>)
}

/** Keeps the entries in the app's private preferences, as one JSON map. */
class PrefsSourceHealthStore(private val prefs: AppPreferences) : SourceHealthStore {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = MapSerializer(String.serializer(), SourceProblem.serializer())

    override fun load(): Map<String, SourceProblem> =
        prefs.sourceProblemsRaw()?.let { raw -> runCatching { json.decodeFromString(serializer, raw) }.getOrNull() }.orEmpty()

    override suspend fun save(problems: Map<String, SourceProblem>) {
        prefs.setSourceProblemsRaw(json.encodeToString(serializer, problems))
    }
}

/**
 * How each source's calls went, so the Sources screen and global search can show a broken source
 * before a title is opened. Entries are kept on the phone for [MAX_AGE_MS] and never sent anywhere.
 */
class SourceHealth(
    private val clock: () -> Long = System::currentTimeMillis,
    private val store: SourceHealthStore? = null,
    scope: CoroutineScope? = null,
    /** Also told about every failure, with the full error (the error log). */
    private val onFailure: ((sourceId: String, error: Throwable) -> Unit)? = null,
) {
    private val _problems = MutableStateFlow(
        store?.load().orEmpty().filterValues { clock() - it.at <= MAX_AGE_MS },
    )
    val problems: StateFlow<Map<String, SourceProblem>> = _problems.asStateFlow()

    init {
        // A StateFlow conflates, so a burst of changes writes only the latest map.
        if (store != null && scope != null) scope.launch { _problems.collect { store.save(it) } }
    }

    fun succeeded(sourceId: String) {
        if (sourceId in _problems.value) _problems.update { it - sourceId }
    }

    fun failed(sourceId: String, error: Throwable) {
        val problem = SourceProblem(
            message = error.message?.takeIf { it.isNotBlank() } ?: error::class.simpleName.orEmpty(),
            at = clock(),
            siteCheck = error is CloudflareException,
        )
        _problems.update { it + (sourceId to problem) }
        onFailure?.invoke(sourceId, error)
    }

    /** Runs [block] for [sourceId] and records how it went; a cancelled call records nothing. */
    suspend fun <T> track(sourceId: String, block: suspend () -> T): T {
        val result = try {
            block()
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            failed(sourceId, e)
            throw e
        }
        succeeded(sourceId)
        return result
    }

    companion object {
        const val MAX_AGE_MS = 7L * 24 * 60 * 60 * 1000
    }
}
