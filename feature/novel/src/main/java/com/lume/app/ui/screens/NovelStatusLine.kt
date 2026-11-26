package com.lume.app.ui.screens

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.BatteryManager
import android.text.format.DateFormat
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.BatteryStd
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.produceState
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.ContextCompat
import kotlinx.coroutines.awaitCancellation
import kotlinx.coroutines.delay
import java.util.Date

/**
 * A quiet line under the text while the bars are hidden: the time, the chapter progress and the
 * battery, as in paper-like readers. Screen readers skip it; the bars say the same.
 */
@Composable
internal fun NovelStatusLine(percent: Int, palette: NovelPalette, modifier: Modifier = Modifier) {
    val context = LocalContext.current
    val time by produceState(clockText(context)) {
        while (true) {
            val now = System.currentTimeMillis()
            delay(MINUTE_MS - now % MINUTE_MS + 50)
            value = clockText(context)
        }
    }
    val battery by produceState<Int?>(null) {
        val receiver = object : BroadcastReceiver() {
            override fun onReceive(c: Context, intent: Intent) {
                value = batteryPercent(intent)
            }
        }
        // A sticky broadcast: registering returns the current level at once.
        val sticky = ContextCompat.registerReceiver(
            context, receiver, IntentFilter(Intent.ACTION_BATTERY_CHANGED), ContextCompat.RECEIVER_NOT_EXPORTED,
        )
        sticky?.let { value = batteryPercent(it) }
        try {
            awaitCancellation()
        } finally {
            context.unregisterReceiver(receiver)
        }
    }
    val color = palette.textSecondary
    Row(
        modifier = modifier
            .fillMaxWidth()
            .background(palette.background)
            .padding(horizontal = 20.dp, vertical = 4.dp)
            .clearAndSetSemantics {},
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(time, color = color, fontSize = 11.sp, modifier = Modifier.weight(1f))
        Text("$percent%", color = color, fontSize = 11.sp, textAlign = TextAlign.Center, modifier = Modifier.weight(1f))
        Row(Modifier.weight(1f), horizontalArrangement = Arrangement.End, verticalAlignment = Alignment.CenterVertically) {
            battery?.let {
                Icon(Icons.Outlined.BatteryStd, contentDescription = null, tint = color, modifier = Modifier.size(12.dp))
                Text("$it%", color = color, fontSize = 11.sp)
            }
        }
    }
}

private const val MINUTE_MS = 60_000L

private fun clockText(context: Context): String = DateFormat.getTimeFormat(context).format(Date())

private fun batteryPercent(intent: Intent): Int? {
    val level = intent.getIntExtra(BatteryManager.EXTRA_LEVEL, -1)
    val scale = intent.getIntExtra(BatteryManager.EXTRA_SCALE, -1)
    return if (level < 0 || scale <= 0) null else level * 100 / scale
}
