package com.lume.app.ui.screens

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.os.Looper
import org.robolectric.shadows.ShadowNetworkCapabilities
import androidx.test.core.app.ApplicationProvider
import com.lume.app.AppContainer
import androidx.compose.runtime.snapshots.Snapshot
import org.robolectric.Shadows.shadowOf

/**
 * Robolectric builds one sandbox (one class loader) per distinct configuration. The app's
 * preferences file is a process-wide delegate that keeps the first folder it was opened in, and
 * `PrivatePreferencesTest` needs it untouched. View model tests open the real preferences, so
 * they run in a sandbox of their own: this package name only makes their configuration differ.
 */
internal const val VIEW_MODEL_SANDBOX = "com.lume.app.ui.screens.sandbox"

/**
 * Shared helpers for ViewModel tests: a real [AppContainer] on the Robolectric application (its
 * own empty Room database; background start-up work is not started) and a wait that lets both
 * the main looper and Room's background threads run.
 */
internal fun testContainer(online: Boolean = true): AppContainer {
    val context = ApplicationProvider.getApplicationContext<Application>()
    val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
    val caps = ShadowNetworkCapabilities.newInstance()
    if (online) shadowOf(caps).addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    shadowOf(cm).setNetworkCapabilities(cm.activeNetwork, caps)
    return AppContainer(context)
}

/**
 * Runs the main looper and sleeps in small steps until [condition] holds, failing after
 * [timeoutMs]. ViewModels launch on the main dispatcher while Room answers on its own threads,
 * so neither side can be driven alone.
 */
internal fun awaitUntil(timeoutMs: Long = 10_000, what: String = "condition", condition: () -> Boolean) {
    val end = System.currentTimeMillis() + timeoutMs
    while (true) {
        // Compose does this every frame; without it snapshotFlow never sees a state write.
        Snapshot.sendApplyNotifications()
        shadowOf(Looper.getMainLooper()).idle()
        if (condition()) return
        check(System.currentTimeMillis() < end) { "Timed out waiting for $what" }
        Thread.sleep(10)
    }
}

/** Lets the main looper run pending work, then waits [ms] of real time for background threads. */
internal fun settle(ms: Long = 200) {
    shadowOf(Looper.getMainLooper()).idle()
    Thread.sleep(ms)
    shadowOf(Looper.getMainLooper()).idle()
}
