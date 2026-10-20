package com.lume.app.ui.screens

import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.ActivityInfo
import android.graphics.Bitmap
import android.graphics.drawable.BitmapDrawable
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.view.WindowManager
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.ui.graphics.ColorFilter
import androidx.compose.ui.graphics.ColorMatrix
import coil.imageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import com.lume.app.data.prefs.ReaderSettings
import com.lume.app.domain.ImageBytes
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.ByteArrayOutputStream
import java.io.File

// Reader extras: colour filters, e-ink, brightness, orientation lock, and saving or sharing a page.

/** The colour filter over the pages for [settings], or null when pages are shown as they are. */
internal fun readerColorFilter(settings: ReaderSettings): ColorFilter? {
    val matrix = when (settings.colorFilter) {
        ReaderSettings.FILTER_SEPIA -> ColorMatrix(
            floatArrayOf(
                0.393f, 0.769f, 0.189f, 0f, 0f,
                0.349f, 0.686f, 0.168f, 0f, 0f,
                0.272f, 0.534f, 0.131f, 0f, 0f,
                0f, 0f, 0f, 1f, 0f,
            ),
        )
        ReaderSettings.FILTER_GREY -> ColorMatrix().apply { setToSaturation(0f) }
        // Less blue light for night reading.
        ReaderSettings.FILTER_WARM -> ColorMatrix().apply { setToScale(1f, 0.86f, 0.62f, 1f) }
        else -> null
    }
    if (!settings.eInk) return matrix?.let(ColorFilter::colorMatrix)
    // E-ink: grey with more contrast, so text and lines stay crisp on a slow, low-contrast panel.
    val contrast = 1.45f
    val shift = (1f - contrast) * 128f
    val eInk = ColorMatrix().apply { setToSaturation(0f) }
    eInk.timesAssign(
        ColorMatrix(
            floatArrayOf(
                contrast, 0f, 0f, 0f, shift,
                0f, contrast, 0f, 0f, shift,
                0f, 0f, contrast, 0f, shift,
                0f, 0f, 0f, 1f, 0f,
            ),
        ),
    )
    return ColorFilter.colorMatrix(eInk)
}

/** Orientation lock and brightness while the reader is open; both are undone when it closes. */
@Composable
internal fun ReaderWindowEffects(activity: Activity?, settings: ReaderSettings) {
    DisposableEffect(activity, settings.orientation) {
        activity?.requestedOrientation = when (settings.orientation) {
            ReaderSettings.ORIENTATION_PORTRAIT -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_PORTRAIT
            ReaderSettings.ORIENTATION_LANDSCAPE -> ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE
            else -> ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED
        }
        onDispose { activity?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_UNSPECIFIED }
    }
    DisposableEffect(activity, settings.brightness) {
        val window = activity?.window
        window?.attributes = window.attributes.apply {
            screenBrightness = if (settings.brightness < 0f) WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE else settings.brightness
        }
        onDispose {
            window?.attributes = window.attributes.apply { screenBrightness = WindowManager.LayoutParams.BRIGHTNESS_OVERRIDE_NONE }
        }
    }
}

/** A page's original bytes and file extension, from the download, Coil's disk cache or the network. */
internal suspend fun readerPageBytes(context: Context, imageUrl: String): Pair<ByteArray, String> = withContext(Dispatchers.IO) {
    val raw: ByteArray? = if (!imageUrl.startsWith("http")) {
        File(imageUrl).takeIf { it.isFile }?.readBytes()
    } else {
        cached(context, imageUrl) ?: run {
            // Not cached yet: load it once through Coil (with the source's headers), then read the cache.
            val result = context.imageLoader.execute(ImageRequest.Builder(context).data(imageUrl).allowHardware(false).build())
            cached(context, imageUrl) ?: ((result as? SuccessResult)?.drawable as? BitmapDrawable)?.bitmap?.let { bitmap ->
                ByteArrayOutputStream().also { bitmap.compress(Bitmap.CompressFormat.PNG, 100, it) }.toByteArray()
            }
        }
    }
    val bytes = raw ?: error("page not available")
    bytes to (ImageBytes.extension(bytes) ?: "jpg")
}

private fun cached(context: Context, url: String): ByteArray? =
    context.imageLoader.diskCache?.openSnapshot(url)?.use { snapshot -> snapshot.data.toFile().readBytes() }

/** Saves a page to Pictures/Lume (Android 10+, no permission needed). */
internal suspend fun savePageToPictures(context: Context, bytes: ByteArray, ext: String, name: String) = withContext(Dispatchers.IO) {
    check(Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q)
    val values = ContentValues().apply {
        put(MediaStore.Images.Media.DISPLAY_NAME, "$name.$ext")
        put(MediaStore.Images.Media.MIME_TYPE, mimeOf(ext))
        put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/Lume")
    }
    val resolver = context.contentResolver
    val uri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values) ?: error("can't create the picture")
    resolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("can't write the picture")
}

internal fun mimeOf(ext: String): String = when (ext.lowercase()) {
    "png" -> "image/png"
    "webp" -> "image/webp"
    "gif" -> "image/gif"
    "avif" -> "image/avif"
    else -> "image/jpeg"
}

/** A file name from the title, chapter and page, without characters file systems refuse. */
internal fun pageFileName(title: String, chapter: String, page: Int): String =
    "$title - $chapter - ${page + 1}".replace(Regex("""[\\/:*?"<>|\n\r\t]"""), "_").trim().take(120)
