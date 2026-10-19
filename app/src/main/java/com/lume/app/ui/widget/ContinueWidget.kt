package com.lume.app.ui.widget

import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.view.View
import android.widget.RemoteViews
import com.lume.app.LumeApp
import com.lume.app.MainActivity
import com.lume.app.R
import com.lume.app.data.continuity.ContinueReading

/**
 * Home-screen widget: the last titles you read, a tap opens the reader where you stopped.
 * Content comes from [ContinueReading], which also refreshes it whenever you read.
 */
class ContinueWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, appWidgetIds: IntArray) {
        val continueReading = (context.applicationContext as? LumeApp)?.container?.continueReading
        if (continueReading != null) {
            continueReading.refreshWidgets()
        } else {
            manager.updateAppWidget(appWidgetIds, views(context, emptyList(), emptyList()) { openApp(context, it) })
        }
    }

    companion object {
        private val ITEMS = intArrayOf(R.id.widget_item0, R.id.widget_item1, R.id.widget_item2)
        private val COVERS = intArrayOf(R.id.widget_cover0, R.id.widget_cover1, R.id.widget_cover2)
        private val TITLES = intArrayOf(R.id.widget_title0, R.id.widget_title1, R.id.widget_title2)
        private val CHAPTERS = intArrayOf(R.id.widget_chapter0, R.id.widget_chapter1, R.id.widget_chapter2)

        private fun openApp(context: Context, route: String?): Intent =
            if (route == null) {
                Intent(context, MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
            } else {
                com.lume.app.ui.nav.AppLinks.intentFor(context, route).setAction(Intent.ACTION_VIEW)
            }

        /**
         * The widget's views. [entries] null = hidden (app lock or "hide in recent apps"):
         * no titles, a tap opens the app. Empty = nothing read yet.
         */
        fun views(
            context: Context,
            entries: List<ContinueReading.Entry>?,
            covers: List<Bitmap?>,
            intentFor: (String) -> Intent,
        ): RemoteViews {
            val views = RemoteViews(context.packageName, R.layout.widget_continue)
            val shown = entries.orEmpty().take(ITEMS.size)
            val hidden = entries == null
            views.setViewVisibility(R.id.widget_items, if (shown.isEmpty()) View.GONE else View.VISIBLE)
            views.setViewVisibility(R.id.widget_empty, if (shown.isEmpty()) View.VISIBLE else View.GONE)
            views.setTextViewText(
                R.id.widget_empty,
                context.getString(if (hidden) R.string.widget_continue_hidden else R.string.widget_continue_empty),
            )
            views.setOnClickPendingIntent(R.id.widget_empty, pending(context, REQUEST_APP, openApp(context, null)))
            ITEMS.indices.forEach { i ->
                val entry = shown.getOrNull(i)
                // Fewer titles than places: the empty places stay, so covers keep their size.
                views.setViewVisibility(ITEMS[i], if (entry == null) View.INVISIBLE else View.VISIBLE)
                if (entry == null) return@forEach
                views.setTextViewText(TITLES[i], entry.titleName)
                views.setTextViewText(CHAPTERS[i], entry.chapterName)
                val cover = covers.getOrNull(i)
                if (cover != null) views.setImageViewBitmap(COVERS[i], cover) else views.setImageViewResource(COVERS[i], android.R.color.transparent)
                views.setOnClickPendingIntent(ITEMS[i], pending(context, REQUEST_ITEM + i, intentFor(entry.route)))
            }
            return views
        }

        private fun pending(context: Context, requestCode: Int, intent: Intent): PendingIntent =
            PendingIntent.getActivity(context, requestCode, intent, PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)

        private const val REQUEST_APP = 300
        private const val REQUEST_ITEM = 301
    }
}
