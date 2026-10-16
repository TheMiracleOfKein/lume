package com.lume.app.ui.nav

import android.content.Context
import android.content.Intent
import com.lume.app.MainActivity
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * Routes requested from outside the UI (a notification tap), handed from [MainActivity] to the
 * nav host. The shell navigates and then calls [consumed].
 */
object AppLinks {
    private const val EXTRA_OPEN_ROUTE = "com.lume.app.OPEN_ROUTE"

    private val _pendingRoute = MutableStateFlow<String?>(null)
    val pendingRoute: StateFlow<String?> = _pendingRoute.asStateFlow()

    fun intentFor(context: Context, route: String): Intent =
        Intent(context, MainActivity::class.java)
            .putExtra(EXTRA_OPEN_ROUTE, route)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP or Intent.FLAG_ACTIVITY_SINGLE_TOP)

    fun handle(intent: Intent?) {
        val route = intent?.getStringExtra(EXTRA_OPEN_ROUTE) ?: return
        intent.removeExtra(EXTRA_OPEN_ROUTE)
        _pendingRoute.value = route
    }

    fun consumed() {
        _pendingRoute.value = null
    }
}
