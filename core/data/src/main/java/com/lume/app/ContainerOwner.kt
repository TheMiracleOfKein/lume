package com.lume.app

import android.content.Context

/** Implemented by the Application so workers, receivers and widgets can reach the [AppContainer]. */
interface ContainerOwner {
    val container: AppContainer
}

/**
 * Process-wide application context, set once by the Application. Only for
 * [com.lume.app.data.locale.appString]; everything else uses [ContainerOwner.container].
 */
object AppContextHolder {
    @Volatile
    lateinit var context: Context
}
