package com.lume.app.data.source

import com.lume.app.domain.CloudflareException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/** The last failed call of a source since the app started; cleared by its next successful call. */
data class SourceProblem(
    val message: String,
    val at: Long,
    /** The site asks for its browser check (Cloudflare). */
    val siteCheck: Boolean = false,
)

/**
 * How each source's calls went in this session, so the Sources screen can show a broken source
 * before a title is opened. Nothing is stored or sent anywhere.
 */
class SourceHealth(private val clock: () -> Long = System::currentTimeMillis) {
    private val _problems = MutableStateFlow<Map<String, SourceProblem>>(emptyMap())
    val problems: StateFlow<Map<String, SourceProblem>> = _problems.asStateFlow()

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
}
