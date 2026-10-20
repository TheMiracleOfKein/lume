package com.lume.app.ui.components

import android.content.ActivityNotFoundException
import android.content.Context
import android.content.Intent
import android.widget.Toast
import androidx.core.net.toUri
import com.lume.app.R

/** Opens [url] in the user's browser (handy for site logins, comments, or a stubborn check page). */
fun openInBrowser(context: Context, url: String) {
    val intent = Intent(Intent.ACTION_VIEW, url.toUri()).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    try {
        context.startActivity(intent)
    } catch (_: ActivityNotFoundException) {
        Toast.makeText(context, R.string.error_no_browser, Toast.LENGTH_SHORT).show()
    }
}

fun shareLink(context: Context, title: String?, url: String) {
    val send = Intent(Intent.ACTION_SEND)
        .setType("text/plain")
        .putExtra(Intent.EXTRA_SUBJECT, title.orEmpty())
        .putExtra(Intent.EXTRA_TEXT, if (title.isNullOrBlank()) url else "$title\n$url")
    context.startActivity(Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
}

/** Hands [file] (under `cache/shared/`, see `res/xml/shared_files.xml`) to the share sheet. */
fun shareFile(context: Context, file: java.io.File, mime: String, subject: String?, text: String? = null) {
    val uri = androidx.core.content.FileProvider.getUriForFile(context, "${context.packageName}.files", file)
    val send = Intent(Intent.ACTION_SEND)
        .setType(mime)
        .putExtra(Intent.EXTRA_STREAM, uri)
        .putExtra(Intent.EXTRA_SUBJECT, subject.orEmpty())
        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
    if (!text.isNullOrBlank()) send.putExtra(Intent.EXTRA_TEXT, text)
    val chooser = Intent.createChooser(send, null).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    context.startActivity(chooser)
}
