package com.lume.translate.addon

import android.app.Activity
import android.content.Intent
import android.os.Bundle
import android.text.format.Formatter
import android.util.TypedValue
import android.view.View
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.util.Locale

/**
 * The add-on's own screen (its launcher icon): what it is, which language packs are on the phone,
 * a way to delete them, and a button to open Lume. Translation itself is used from Lume's reader.
 * Plain views, no extra libraries, so the add-on stays small.
 */
class AddonActivity : Activity() {
    private lateinit var store: ModelStore
    private lateinit var packs: LinearLayout

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        store = ModelStore(File(filesDir, "models"))
        val pad = dp(20)
        val column = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(pad, pad, pad, pad)
        }
        column.addView(text(getString(R.string.app_name), 22f))
        column.addView(text(getString(R.string.addon_about), 15f).also { it.setPadding(0, dp(12), 0, dp(12)) })
        column.addView(text(getString(R.string.addon_packs), 13f))
        packs = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL }
        column.addView(packs)
        val lume = packageManager.getLaunchIntentForPackage(LUME_PACKAGE)
        column.addView(
            Button(this).apply {
                text = getString(if (lume != null) R.string.addon_open_lume else R.string.addon_no_lume)
                isEnabled = lume != null
                setOnClickListener { lume?.let { startActivity(it.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) } }
            },
        )
        val scroll = ScrollView(this).apply { addView(column) }
        // Edge to edge (target SDK 35+): keep the content clear of the system bars.
        scroll.setOnApplyWindowInsetsListener { v, insets ->
            @Suppress("DEPRECATION")
            v.setPadding(insets.systemWindowInsetLeft, insets.systemWindowInsetTop, insets.systemWindowInsetRight, insets.systemWindowInsetBottom)
            insets
        }
        setContentView(scroll)
    }

    override fun onResume() {
        super.onResume()
        showPacks()
    }

    private fun showPacks() {
        packs.removeAllViews()
        val installed = store.installedPairs()
        if (installed.isEmpty()) {
            packs.addView(text(getString(R.string.addon_no_packs), 15f))
            return
        }
        val locale = Locale.getDefault()
        for (pair in installed) {
            fun name(code: String) = Locale(code).getDisplayLanguage(locale).replaceFirstChar { it.titlecase(locale) }
            val size = Formatter.formatShortFileSize(this, pair.installedSize)
            packs.addView(text("${name(pair.from)} → ${name(pair.to)} · $size", 15f))
            packs.addView(
                Button(this).apply {
                    text = getString(R.string.addon_delete_pack)
                    setOnClickListener {
                        store.delete(pair)
                        showPacks()
                    }
                },
            )
        }
    }

    private fun text(s: String, sp: Float) = TextView(this).apply {
        text = s
        setTextSize(TypedValue.COMPLEX_UNIT_SP, sp)
        textAlignment = View.TEXT_ALIGNMENT_VIEW_START
    }

    private fun dp(v: Int) = TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP, v.toFloat(), resources.displayMetrics).toInt()

    private companion object {
        const val LUME_PACKAGE = "com.lume.app"
    }
}
