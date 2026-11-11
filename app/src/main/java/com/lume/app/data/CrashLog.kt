package com.lume.app.data

import kotlinx.serialization.Serializable
import kotlinx.serialization.builtins.ListSerializer
import kotlinx.serialization.json.Json
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.TimeZone

/** One crash of the app: when, on which thread, in which version, and the stack trace. */
@Serializable
data class CrashEntry(
    val at: Long,
    val thread: String,
    val version: String,
    val trace: String,
)

/**
 * The last [MAX_ENTRIES] crashes, saved in a private file on the phone by an uncaught-exception
 * handler. Nothing is sent anywhere: About → "Share log" hands the text to an app the user picks.
 *
 * The file is written synchronously from the crashing thread, so it stays small and plain.
 */
class CrashLog(
    private val file: File,
    private val version: String,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private val json = Json { ignoreUnknownKeys = true }
    private val serializer = ListSerializer(CrashEntry.serializer())
    private val lock = Any()

    /** Newest first. Read from the file each time: the list is small and changes only on a crash. */
    fun entries(): List<CrashEntry> = synchronized(lock) { load() }

    fun record(thread: Thread, error: Throwable) {
        val entry = CrashEntry(clock(), thread.name, version, error.stackTraceToString().take(MAX_TRACE))
        synchronized(lock) { save((listOf(entry) + load()).take(MAX_ENTRIES)) }
    }

    fun clear() {
        synchronized(lock) { file.delete() }
    }

    /**
     * Installs a handler that saves the crash and then hands it to the previous handler, so
     * Android still shows its "app stopped" dialog and ends the process as before.
     */
    fun install() {
        val previous = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { thread, error ->
            runCatching { record(thread, error) }
            if (previous != null) previous.uncaughtException(thread, error) else throw error
        }
    }

    private fun load(): List<CrashEntry> = runCatching {
        file.takeIf { it.isFile }?.readText()?.let { json.decodeFromString(serializer, it) }
    }.getOrNull().orEmpty().take(MAX_ENTRIES)

    private fun save(list: List<CrashEntry>) {
        runCatching {
            file.parentFile?.mkdirs()
            val tmp = File(file.parentFile, file.name + ".tmp")
            tmp.writeText(json.encodeToString(serializer, list))
            if (!tmp.renameTo(file)) {
                file.delete()
                tmp.renameTo(file)
            }
        }
    }

    companion object {
        const val MAX_ENTRIES = 10
        private const val MAX_TRACE = 12_000

        fun format(entry: CrashEntry): String = buildString {
            append(timestamp(entry.at)).append(" · Lume ").append(entry.version)
            append(" · thread ").append(entry.thread).append('\n')
            append(entry.trace.trimEnd())
        }

        /**
         * The text "Share log" sends: the device line, the saved crashes, then the recent errors.
         * [device] is passed in so the text can be built in JVM tests.
         */
        fun report(device: String, crashes: List<CrashEntry>, errors: List<ErrorEntry>, maxErrors: Int = 30): String = buildString {
            append(device).append("\n\n")
            append("== Crashes (").append(crashes.size).append(") ==\n")
            if (crashes.isEmpty()) append("none\n")
            crashes.forEach { append(format(it)).append("\n\n") }
            val shown = errors.take(maxErrors)
            append("\n== Recent errors (").append(shown.size).append(" of ").append(errors.size).append(") ==\n")
            if (shown.isEmpty()) append("none\n")
            shown.forEach { append(ErrorLog.format(it)).append("\n\n") }
        }.trimEnd() + "\n"

        private fun timestamp(at: Long): String =
            SimpleDateFormat("yyyy-MM-dd HH:mm:ss z", Locale.US).apply { timeZone = TimeZone.getDefault() }.format(Date(at))
    }
}
