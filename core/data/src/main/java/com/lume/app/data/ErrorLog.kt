package com.lume.app.data

import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** One failed operation, with the full message and the stack trace. */
@Serializable
data class ErrorEntry(
    val at: Long,
    /** Where it happened, e.g. a source name. */
    val where: String,
    val message: String,
    val details: String = "",
)

/**
 * The last [MAX_ENTRIES] errors, kept in a private file on the phone so the full text of an error
 * that the screens only show in one line can be read and copied. Never sent anywhere.
 */
class ErrorLog(
    private val file: File?,
    scope: CoroutineScope? = null,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(ErrorEntry.serializer())

    // With a scope the file is read off the main thread; without one (tests) right away.
    private val _entries = MutableStateFlow(if (scope == null) load() else emptyList())

    /** Newest first. */
    val entries: StateFlow<List<ErrorEntry>> = _entries.asStateFlow()

    init {
        if (file != null && scope != null) {
            scope.launch(Dispatchers.IO) {
                val stored = load()
                // Errors recorded while the file was being read stay on top.
                _entries.update { (it + stored).take(MAX_ENTRIES) }
                // A StateFlow conflates, so a burst of errors writes only the latest list.
                var last = stored
                _entries.collect { list ->
                    if (list != last) {
                        save(list)
                        last = list
                    }
                }
            }
        }
    }

    fun record(where: String, error: Throwable) {
        val message = error.message?.takeIf { it.isNotBlank() } ?: error::class.java.simpleName
        record(where, message, error.stackTraceToString().take(MAX_DETAILS))
    }

    fun record(where: String, message: String, details: String = "") {
        val entry = ErrorEntry(clock(), where, message.take(MAX_MESSAGE), details)
        _entries.update { (listOf(entry) + it).take(MAX_ENTRIES) }
    }

    fun clear() {
        _entries.value = emptyList()
    }

    /** Plain text of every entry, for copying or sharing. */
    fun asText(entries: List<ErrorEntry> = _entries.value): String = entries.joinToString("\n\n") { format(it) }

    private fun load(): List<ErrorEntry> = runCatching {
        file?.takeIf { it.isFile }?.readText()?.let { json.decodeFromString(serializer, it) }
    }.getOrNull().orEmpty().take(MAX_ENTRIES)

    private fun save(list: List<ErrorEntry>) {
        val target = file ?: return
        runCatching {
            val tmp = File(target.parentFile, target.name + ".tmp")
            tmp.writeText(json.encodeToString(serializer, list))
            if (!tmp.renameTo(target)) {
                target.delete()
                tmp.renameTo(target)
            }
        }
    }

    companion object {
        const val MAX_ENTRIES = 200
        private const val MAX_MESSAGE = 2_000
        private const val MAX_DETAILS = 6_000

        fun format(entry: ErrorEntry): String = buildString {
            append(SimpleDateFormat("yyyy-MM-dd HH:mm:ss", Locale.US).format(Date(entry.at)))
            append(" · ").append(entry.where).append('\n')
            append(entry.message)
            if (entry.details.isNotBlank()) append('\n').append(entry.details.trimEnd())
        }
    }
}
