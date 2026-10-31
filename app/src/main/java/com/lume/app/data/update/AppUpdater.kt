package com.lume.app.data.update

import android.app.Application
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.provider.Settings
import androidx.core.content.FileProvider
import com.lume.app.BuildConfig
import com.lume.app.data.network.AppHttp
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.ensureActive
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonArray
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.JsonPrimitive
import kotlinx.serialization.json.booleanOrNull
import kotlinx.serialization.json.contentOrNull
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.longOrNull
import okhttp3.Request
import java.io.File
import java.io.IOException

/** A release on GitHub that is newer than the installed app. */
data class AppRelease(
    val version: String,
    val notes: String,
    val pageUrl: String,
    val apkUrl: String?,
    val apkSize: Long,
)

sealed interface UpdateState {
    data object Idle : UpdateState
    data object Checking : UpdateState
    data object UpToDate : UpdateState
    /** [manual]: the user asked, so a skipped version is shown too. */
    data class Available(val release: AppRelease, val manual: Boolean = true) : UpdateState

    /** [fraction] is null while the size is unknown. */
    data class Downloading(val release: AppRelease, val fraction: Float?) : UpdateState
    data class ReadyToInstall(val release: AppRelease, val apk: File) : UpdateState
    data class Failed(val message: String, val release: AppRelease? = null) : UpdateState
}

/**
 * Checks the GitHub releases of Lume for a newer version, downloads its APK and hands it to the
 * system installer. Android shows its own confirmation; nothing is installed silently.
 */
class AppUpdater(
    private val app: Application,
    private val http: AppHttp,
    private val scope: CoroutineScope,
) {
    private val prefs = app.getSharedPreferences("app_updates", Context.MODE_PRIVATE)
    private val json = Json { ignoreUnknownKeys = true }

    private val _state = MutableStateFlow<UpdateState>(UpdateState.Idle)
    val state: StateFlow<UpdateState> = _state.asStateFlow()

    private var job: Job? = null

    /** Whether Lume checks for a new version by itself (at most once every few hours). */
    private val _autoCheck = MutableStateFlow(prefs.getBoolean(KEY_AUTO, true))
    val autoCheck: StateFlow<Boolean> = _autoCheck.asStateFlow()

    fun setAutoCheck(enabled: Boolean) {
        prefs.edit().putBoolean(KEY_AUTO, enabled).apply()
        _autoCheck.value = enabled
    }

    /** The version the user chose to skip; its prompt isn't shown again. */
    fun isSkipped(release: AppRelease): Boolean = prefs.getString(KEY_SKIPPED, null) == release.version

    fun skip(release: AppRelease) {
        prefs.edit().putString(KEY_SKIPPED, release.version).apply()
    }

    /** Called on start: checks when automatic checks are on and the last check is old enough. */
    fun checkOnStart() {
        if (!_autoCheck.value) return
        val last = prefs.getLong(KEY_LAST_CHECK, 0L)
        if (System.currentTimeMillis() - last < AUTO_INTERVAL_MS) return
        check(manual = false)
    }

    fun check(manual: Boolean = true) {
        if (job?.isActive == true) return
        _state.value = UpdateState.Checking
        job = scope.launch {
            _state.value = try {
                val release = withContext(Dispatchers.IO) { fetchLatest() }
                prefs.edit().putLong(KEY_LAST_CHECK, System.currentTimeMillis()).apply()
                if (release != null && isNewer(release.version, BuildConfig.VERSION_NAME)) UpdateState.Available(release, manual)
                else UpdateState.UpToDate
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: e.toString())
            }
        }
    }

    fun download(release: AppRelease) {
        val url = release.apkUrl ?: return
        if (job?.isActive == true) return
        _state.value = UpdateState.Downloading(release, null)
        job = scope.launch {
            _state.value = try {
                val apk = withContext(Dispatchers.IO) { downloadApk(url, release) }
                UpdateState.ReadyToInstall(release, apk)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                UpdateState.Failed(e.message ?: e.toString(), release)
            }
        }
    }

    /** Back to the found release after a prompt was dismissed, or idle. */
    fun dismiss() {
        if (job?.isActive == true) return
        _state.value = UpdateState.Idle
    }

    /**
     * Opens the system installer for [apk]. When Lume may not install apps yet, opens the
     * setting that allows it instead and returns false; the user comes back and taps again.
     */
    fun install(context: Context, apk: File): Boolean {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O && !context.packageManager.canRequestPackageInstalls()) {
            context.startActivity(
                Intent(Settings.ACTION_MANAGE_UNKNOWN_APP_SOURCES, Uri.parse("package:${context.packageName}"))
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
            )
            return false
        }
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.files", apk)
        context.startActivity(
            Intent(Intent.ACTION_VIEW)
                .setDataAndType(uri, APK_MIME)
                .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_ACTIVITY_NEW_TASK),
        )
        return true
    }

    private fun fetchLatest(): AppRelease? {
        val request = Request.Builder()
            .url(LATEST_URL)
            .header("Accept", "application/vnd.github+json")
            .header("User-Agent", "Lume/${BuildConfig.VERSION_NAME}")
            .build()
        http.okHttp.newCall(request).execute().use { response ->
            if (response.code == 404) return null
            if (!response.isSuccessful) throw IOException("GitHub: HTTP ${response.code}")
            return parseRelease(json.parseToJsonElement(response.body.string()).jsonObject)
        }
    }

    private suspend fun downloadApk(url: String, release: AppRelease): File {
        val dir = File(app.cacheDir, "shared/update").apply { mkdirs() }
        dir.listFiles()?.forEach { it.delete() }
        val target = File(dir, "lume-${release.version}.apk")
        val part = File(dir, "${target.name}.part")
        val request = Request.Builder().url(url).header("User-Agent", "Lume/${BuildConfig.VERSION_NAME}").build()
        val ctx = kotlin.coroutines.coroutineContext
        http.okHttp.newCall(request).execute().use { response ->
            if (!response.isSuccessful) throw IOException("HTTP ${response.code}")
            val body = response.body
            val total = body.contentLength().takeIf { it > 0 } ?: release.apkSize.takeIf { it > 0 }
            var read = 0L
            var lastReported = -1
            body.byteStream().use { input ->
                part.outputStream().use { output ->
                    val buffer = ByteArray(64 * 1024)
                    while (true) {
                        ctx.ensureActive()
                        val n = input.read(buffer)
                        if (n < 0) break
                        output.write(buffer, 0, n)
                        read += n
                        if (total != null) {
                            val percent = (read * 100 / total).toInt()
                            if (percent != lastReported) {
                                lastReported = percent
                                _state.value = UpdateState.Downloading(release, (read.toFloat() / total).coerceIn(0f, 1f))
                            }
                        }
                    }
                }
            }
        }
        if (!part.renameTo(target)) throw IOException("Can't save the update")
        return target
    }

    private companion object {
        const val LATEST_URL = "https://api.github.com/repos/TheMiracleOfKein/lume/releases/latest"
        const val APK_MIME = "application/vnd.android.package-archive"
        const val KEY_AUTO = "auto_check"
        const val KEY_LAST_CHECK = "last_check"
        const val KEY_SKIPPED = "skipped_version"
        const val AUTO_INTERVAL_MS = 6 * 60 * 60 * 1000L
    }
}

/** Reads a GitHub `releases/latest` answer. Null for drafts and pre-releases. */
internal fun parseRelease(obj: JsonObject): AppRelease? {
    fun JsonObject.str(key: String) = (this[key] as? JsonPrimitive)?.contentOrNull
    if ((obj["draft"] as? JsonPrimitive)?.booleanOrNull == true) return null
    if ((obj["prerelease"] as? JsonPrimitive)?.booleanOrNull == true) return null
    val tag = obj.str("tag_name") ?: return null
    val apk = (obj["assets"] as? JsonArray)
        ?.mapNotNull { it as? JsonObject }
        ?.firstOrNull { it.str("name")?.endsWith(".apk", ignoreCase = true) == true }
    return AppRelease(
        version = tag.removePrefix("v").removePrefix("V"),
        notes = obj.str("body").orEmpty(),
        pageUrl = obj.str("html_url").orEmpty(),
        apkUrl = apk?.str("browser_download_url"),
        apkSize = (apk?.get("size") as? JsonPrimitive)?.longOrNull ?: 0L,
    )
}

/** True when [candidate] (like "0.7.5") is a later version than [current]. */
internal fun isNewer(candidate: String, current: String): Boolean {
    fun parts(v: String) = v.trim().removePrefix("v").substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
    val a = parts(candidate)
    val b = parts(current)
    for (i in 0 until maxOf(a.size, b.size)) {
        val x = a.getOrElse(i) { 0 }
        val y = b.getOrElse(i) { 0 }
        if (x != y) return x > y
    }
    return false
}
