package com.lume.app.ui.tracker

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.os.Bundle
import android.webkit.WebResourceRequest
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.text.style.TextOverflow
import com.lume.app.ui.theme.LumeSystemBars
import com.lume.app.ui.theme.ThemeMode
import com.lume.app.ui.theme.isDark
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import com.lume.app.ui.components.BackButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.lume.app.LumeApp
import com.lume.app.R
import com.lume.app.data.tracker.AniListTracker
import com.lume.app.data.tracker.TrackerOAuthDefaults
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.LumeTheme
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.launch

/**
 * In-app WebView OAuth so redirect URLs (including #access_token) are captured reliably.
 */
class TrackerAuthActivity : ComponentActivity() {
    private val container get() = (application as LumeApp).container

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val trackerId = intent.getStringExtra(EXTRA_TRACKER) ?: run {
            finish()
            return
        }
        val authUrl = intent.getStringExtra(EXTRA_URL) ?: run {
            finish()
            return
        }
        val title = when (trackerId) {
            "anilist" -> getString(R.string.auth_title_anilist)
            "shikimori" -> getString(R.string.auth_title_shikimori)
            "myanimelist" -> getString(R.string.auth_title_mal)
            else -> getString(R.string.auth_title_generic)
        }
        enableEdgeToEdge()
        setContent {
            val prefs = container.prefs
            val themeMode by prefs.themeMode.collectAsState()
            val pureBlack by prefs.pureBlack.collectAsState()
            val dynamicColor by prefs.dynamicColor.collectAsState()
            val dark = isDark(ThemeMode.fromKey(themeMode) ?: ThemeMode.Dark)
            LumeSystemBars(dark)
            LumeTheme(darkTheme = dark, pureBlack = pureBlack, dynamicColor = dynamicColor) {
                var progress by remember { mutableIntStateOf(0) }
                var web by remember { mutableStateOf<WebView?>(null) }
                BackHandler {
                    val w = web
                    if (w != null && w.canGoBack()) w.goBack() else finish()
                }
                Scaffold(
                    modifier = Modifier.imePadding(),
                    containerColor = AppColors.background,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    title,
                                    color = AppColors.textPrimary,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                )
                            },
                            navigationIcon = {
                                BackButton(onClick = { finish() })
                            },
                            colors = TopAppBarDefaults.topAppBarColors(
                                containerColor = AppColors.background,
                                scrolledContainerColor = AppColors.background,
                                titleContentColor = AppColors.textPrimary,
                                navigationIconContentColor = AppColors.textPrimary,
                                actionIconContentColor = AppColors.textPrimary,
                            ),
                        )
                    },
                ) { padding ->
                    Column(Modifier.padding(padding).fillMaxSize()) {
                    if (progress in 1..99) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = AppColors.accent,
                            trackColor = AppColors.cardHigh,
                        )
                    }
                    AuthWebView(
                        url = authUrl,
                        trackerId = trackerId,
                        modifier = Modifier
                            .weight(1f)
                            .fillMaxWidth(),
                        onCreated = { web = it },
                        onProgress = { progress = it },
                        onHandled = { ok ->
                            Toast.makeText(
                                this@TrackerAuthActivity,
                                if (ok) getString(R.string.auth_success) else getString(R.string.auth_failed),
                                Toast.LENGTH_SHORT,
                            ).show()
                            finish()
                        },
                    )
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun AuthWebView(
        url: String,
        trackerId: String,
        modifier: Modifier,
        onCreated: (WebView?) -> Unit,
        onProgress: (Int) -> Unit,
        onHandled: (Boolean) -> Unit,
    ) {
        AndroidView(
            modifier = modifier,
            onRelease = {
                onCreated(null)
                it.destroy()
            },
            factory = { ctx ->
                WebView(ctx).apply {
                    onCreated(this)
                    webChromeClient = object : WebChromeClient() {
                        override fun onProgressChanged(view: WebView?, newProgress: Int) {
                            onProgress(newProgress)
                        }
                    }
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    webViewClient = object : WebViewClient() {
                        override fun shouldOverrideUrlLoading(
                            view: WebView?,
                            request: WebResourceRequest?,
                        ): Boolean {
                            val u = request?.url?.toString() ?: return false
                            return handleRedirect(u, trackerId, onHandled)
                        }

                        @Deprecated("Deprecated in Java")
                        override fun shouldOverrideUrlLoading(view: WebView?, url: String?): Boolean {
                            val u = url ?: return false
                            return handleRedirect(u, trackerId, onHandled)
                        }

                        override fun onPageStarted(view: WebView?, url: String?, favicon: Bitmap?) {
                            url?.let { handleRedirect(it, trackerId, onHandled) }
                        }
                    }
                    loadUrl(url)
                }
            },
        )
    }

    private fun handleRedirect(url: String, trackerId: String, onHandled: (Boolean) -> Unit): Boolean {
        val isOurs = url.startsWith("lume://oauth/") ||
            url.contains("access_token=") ||
            (url.startsWith("lume://") && url.contains("code="))
        if (!isOurs && !url.startsWith("lume://")) return false

        lifecycleScope.launch {
            val ok = when (trackerId) {
                "anilist" -> completeAniList(url)
                "shikimori" -> completeShikimori(url)
                "myanimelist" -> extractFragmentOrQuery(url, "code")?.let { container.trackers.myAnimeList.exchangeCode(it) } ?: false
                else -> false
            }
            onHandled(ok)
        }
        return true
    }

    private suspend fun completeAniList(url: String): Boolean {
        val token = extractFragmentOrQuery(url, "access_token")
        if (!token.isNullOrBlank()) {
            container.prefs.setAniListAccessToken(token)
            return true
        }
        val code = extractFragmentOrQuery(url, "code") ?: return false
        return container.trackers.anilist.exchangeCode(code)
    }

    private suspend fun completeShikimori(url: String): Boolean {
        val code = extractFragmentOrQuery(url, "code") ?: return false
        return container.trackers.shikimori.exchangeCode(code)
    }

    private fun extractFragmentOrQuery(url: String, key: String): String? {
        val hash = url.substringAfter('#', missingDelimiterValue = "")
        if (hash.isNotEmpty()) {
            hash.split('&').forEach { part ->
                val k = part.substringBefore('=')
                val v = part.substringAfter('=', "")
                if (k == key && v.isNotBlank()) return v
            }
        }
        val query = url.substringAfter('?', missingDelimiterValue = "")
            .substringBefore('#')
        query.split('&').forEach { part ->
            val k = part.substringBefore('=')
            val v = part.substringAfter('=', "")
            if (k == key && v.isNotBlank()) return v
        }
        return null
    }

    companion object {
        private const val EXTRA_TRACKER = "tracker"
        private const val EXTRA_URL = "url"

        suspend fun start(context: Context, trackerId: String): String? {
            val container = (context.applicationContext as LumeApp).container
            val prefs = container.prefs
            val (url, error) = when (trackerId) {
                "anilist" -> {
                    val clientId = TrackerOAuthDefaults.ANILIST_CLIENT_ID
                        .ifBlank { prefs.anilistClientId.first().orEmpty() }
                    if (clientId.isBlank()) {
                        null to context.getString(R.string.auth_missing_anilist_client)
                    } else {
                        val redirect = com.lume.app.util.urlEncode(AniListTracker.REDIRECT_URI)
                        val u =
                            "https://anilist.co/api/v2/oauth/authorize?client_id=$clientId&redirect_uri=$redirect&response_type=token"
                        u to null
                    }
                }
                "shikimori" -> {
                    val clientId = TrackerOAuthDefaults.SHIKIMORI_CLIENT_ID
                        .ifBlank { prefs.shikimoriClientId.first().orEmpty() }
                    if (clientId.isBlank()) {
                        null to context.getString(R.string.auth_missing_shikimori_client)
                    } else {
                        container.trackers.shikimori.buildAuthorizeUrl(clientId) to null
                    }
                }
                "myanimelist" -> {
                    val u = container.trackers.myAnimeList.buildAuthorizeUrl()
                    if (u == null) null to context.getString(R.string.auth_missing_mal_client) else u to null
                }
                else -> null to context.getString(R.string.auth_unknown_tracker)
            }
            if (error != null) return error
            context.startActivity(
                Intent(context, TrackerAuthActivity::class.java)
                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
                    .putExtra(EXTRA_TRACKER, trackerId)
                    .putExtra(EXTRA_URL, url),
            )
            return null
        }
    }
}
