package com.lume.app.data.continuity

import android.app.Application
import android.appwidget.AppWidgetManager
import android.content.ComponentName
import android.content.Intent
import android.graphics.Bitmap
import androidx.core.content.pm.ShortcutInfoCompat
import androidx.core.content.pm.ShortcutManagerCompat
import androidx.core.graphics.drawable.IconCompat
import androidx.core.graphics.drawable.toBitmap
import coil.ImageLoader
import coil.request.ImageRequest
import coil.request.SuccessResult
import coil.size.Scale
import com.lume.app.R
import com.lume.app.data.db.LibraryDao
import com.lume.app.data.db.ProgressEntity
import com.lume.app.data.prefs.AppPreferences
import com.lume.app.ui.nav.AppLinks
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.widget.ContinueWidget
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.FlowPreview
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.debounce
import kotlinx.coroutines.flow.distinctUntilChanged
import kotlinx.coroutines.launch

/**
 * "Continue" outside the app: launcher shortcuts ("Continue <title>") and the home-screen
 * widget, both from the last titles read. A tap opens the reader at the exact spot.
 *
 * With app lock or "hide in recent apps" on, neither shows titles or covers.
 */
class ContinueReading(
    private val app: Application,
    private val dao: LibraryDao,
    private val prefs: AppPreferences,
    private val imageLoader: () -> ImageLoader,
    private val scope: CoroutineScope,
) {
    /** One entry: what to show and where a tap goes. */
    data class Entry(
        val sourceId: String,
        val titleId: String,
        val titleName: String,
        val chapterName: String,
        val coverUrl: String?,
        val route: String,
    )

    @OptIn(FlowPreview::class)
    fun start() {
        scope.launch(Dispatchers.IO) {
            combine(dao.observeHistoryLimit(MAX_ENTRIES), prefs.appLock, prefs.secureScreen) { rows, lock, secure ->
                if (lock || secure) null else rows.map(::entryOf)
            }
                // Only when the titles, their chapters or their order change, not on every page.
                .distinctUntilChanged()
                .debounce(UPDATE_DEBOUNCE_MS)
                .collect { entries ->
                    runCatching { publishShortcuts(entries.orEmpty()) }
                    runCatching { updateWidgets(entries) }
                }
        }
    }

    /** The widget asked for content (added, resized, after a reboot). */
    fun refreshWidgets() {
        scope.launch(Dispatchers.IO) {
            val locked = prefs.appLock.value || prefs.secureScreen.value
            runCatching { updateWidgets(if (locked) null else dao.allProgress().take(MAX_ENTRIES).map(::entryOf)) }
        }
    }

    private fun entryOf(p: ProgressEntity) = Entry(
        sourceId = p.sourceId,
        titleId = p.titleId,
        titleName = p.titleName,
        chapterName = p.chapterName,
        coverUrl = p.coverUrl,
        // No page: the reader resumes the chapter's own saved spot, which is always the latest.
        route = Routes.reader(p.sourceId, p.titleId, p.chapterId),
    )

    /** Intent that opens [route] in the app (shortcuts need an action). */
    fun intentFor(route: String): Intent =
        AppLinks.intentFor(app, route).setAction(Intent.ACTION_VIEW)

    private suspend fun publishShortcuts(entries: List<Entry>) {
        if (entries.isEmpty()) {
            ShortcutManagerCompat.removeAllDynamicShortcuts(app)
            return
        }
        val max = ShortcutManagerCompat.getMaxShortcutCountPerActivity(app).coerceAtLeast(1)
        val shortcuts = entries.take(minOf(max, MAX_SHORTCUTS)).mapIndexed { index, e ->
            val id = "continue:${e.sourceId}:${e.titleId}".take(MAX_ID_LENGTH)
            val icon = cover(e.coverUrl, SHORTCUT_ICON_PX, SHORTCUT_ICON_PX)?.let(IconCompat::createWithBitmap)
                ?: IconCompat.createWithResource(app, R.mipmap.ic_launcher)
            ShortcutInfoCompat.Builder(app, id)
                .setShortLabel(e.titleName.take(SHORT_LABEL_LENGTH).ifBlank { app.getString(R.string.app_name) })
                .setLongLabel(app.getString(R.string.shortcut_continue_long, e.titleName).take(LONG_LABEL_LENGTH))
                .setIcon(icon)
                .setRank(index)
                .setIntent(intentFor(e.route).putExtra(EXTRA_SHORTCUT_ID, id))
                .build()
        }
        ShortcutManagerCompat.setDynamicShortcuts(app, shortcuts)
    }

    private suspend fun updateWidgets(entries: List<Entry>?) {
        val manager = AppWidgetManager.getInstance(app) ?: return
        val ids = manager.getAppWidgetIds(ComponentName(app, ContinueWidget::class.java))
        if (ids.isEmpty()) return
        val covers = entries.orEmpty().map { cover(it.coverUrl, WIDGET_COVER_W, WIDGET_COVER_H) }
        val views = ContinueWidget.views(app, entries, covers, ::intentFor)
        manager.updateAppWidget(ids, views)
    }

    private suspend fun cover(url: String?, width: Int, height: Int): Bitmap? {
        if (url.isNullOrBlank()) return null
        val request = ImageRequest.Builder(app)
            .data(url)
            .size(width, height)
            .scale(Scale.FILL)
            .allowHardware(false)
            .build()
        val result = imageLoader().execute(request) as? SuccessResult ?: return null
        return result.drawable.toBitmap()
    }

    companion object {
        /** Tells the launcher a "Continue" shortcut was used, so it ranks and predicts it better. */
        fun reportShortcutUsed(context: android.content.Context, intent: Intent?) {
            val id = intent?.getStringExtra(EXTRA_SHORTCUT_ID) ?: return
            intent.removeExtra(EXTRA_SHORTCUT_ID)
            runCatching { ShortcutManagerCompat.reportShortcutUsed(context, id) }
        }

        const val MAX_ENTRIES = 3
        private const val EXTRA_SHORTCUT_ID = "com.lume.app.SHORTCUT_ID"
        private const val MAX_SHORTCUTS = 3
        private const val UPDATE_DEBOUNCE_MS = 1_500L
        private const val SHORTCUT_ICON_PX = 192
        private const val WIDGET_COVER_W = 150
        private const val WIDGET_COVER_H = 210
        private const val SHORT_LABEL_LENGTH = 24
        private const val LONG_LABEL_LENGTH = 48
        private const val MAX_ID_LENGTH = 512
    }
}
