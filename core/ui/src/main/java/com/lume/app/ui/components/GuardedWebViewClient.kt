package com.lume.app.ui.components

import android.webkit.WebResourceRequest
import android.webkit.WebResourceResponse
import android.webkit.WebView
import android.webkit.WebViewClient
import com.lume.app.data.network.ServerEndpoints
import java.io.ByteArrayInputStream

/**
 * A [WebViewClient] that refuses plain `http://` pages and subresources unless the host is a
 * server the user added, the same rule the OkHttp client applies. The platform allows cleartext
 * for every host, so the WebViews have to enforce it themselves.
 */
open class GuardedWebViewClient(private val servers: ServerEndpoints) : WebViewClient() {
    override fun shouldOverrideUrlLoading(view: WebView?, request: WebResourceRequest?): Boolean =
        request?.url?.toString()?.let(servers::blocksCleartext) == true

    @Deprecated("Deprecated in Java")
    override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean =
        url?.let(servers::blocksCleartext) == true

    override fun shouldInterceptRequest(view: WebView?, request: WebResourceRequest?): WebResourceResponse? {
        val url = request?.url?.toString() ?: return null
        if (!servers.blocksCleartext(url)) return null
        return WebResourceResponse("text/plain", "utf-8", 403, "Blocked", emptyMap(), ByteArrayInputStream(ByteArray(0)))
    }
}
