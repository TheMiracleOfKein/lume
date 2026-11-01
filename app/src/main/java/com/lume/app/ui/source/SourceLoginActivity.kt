package com.lume.app.ui.source

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import com.lume.app.ui.components.BackButton
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.lume.app.LumeApp
import com.lume.app.R
import com.lume.app.data.source.LibWebSession
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.LumeTheme
import kotlinx.coroutines.launch

/**
 * Opens the Lib site in a WebView so the user can log in.
 * Reads `localStorage.auth` (cdnlibs) after each page load and saves the token.
 */
class SourceLoginActivity : ComponentActivity() {
    private val container get() = (application as LumeApp).container

    /** The user has touched the page after it first loaded (typed, tapped a button). */
    private var userTouched = false
    private var firstPageChecked = false

    /** Access tokens of the old site session found on open; never saved. */
    private val staleTokens = mutableSetOf<String>()

    /** The heavy wipe runs once per screen, so a site that keeps the session can't loop it. */
    private var wiped = false

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID) ?: run {
            finish()
            return
        }
        val source = container.sources.sourceById(sourceId) ?: run {
            finish()
            return
        }
        val loginUrl = source.manifest.baseUrl.trimEnd('/') + "/"
        enableEdgeToEdge()
        setContent {
            LumeTheme(darkTheme = true) {
                var status by remember { mutableStateOf(getString(R.string.source_login_hint)) }
                Scaffold(
                    containerColor = AppColors.background,
                    topBar = {
                        TopAppBar(
                            title = {
                                Text(
                                    stringResource(R.string.source_login_title, source.manifest.name),
                                    color = AppColors.textPrimary,
                                )
                            },
                            navigationIcon = {
                                BackButton(onClick = { finish() })
                            },
                            actions = {
                                TextButton(onClick = { finish() }) {
                                    Text(stringResource(R.string.action_close), color = AppColors.accent)
                                }
                            },
                        )
                    },
                ) { padding ->
                    Column(Modifier.fillMaxSize().padding(padding)) {
                    Text(
                        status,
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                    )
                    LoginWebView(
                        url = loginUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onAuthJson = { raw, view ->
                            lifecycleScope.launch {
                                val token = container.libAuth.parse(raw) ?: return@launch
                                val accessToken = token.token?.accessToken.orEmpty()
                                if (accessToken in staleTokens) return@launch
                                // Signed out in Lume, yet the site already reports an account
                                // before the user did anything: that's the old session.
                                if (!userTouched && container.libAuth.getToken(sourceId) == null) {
                                    staleTokens += accessToken
                                    if (!wiped) {
                                        wiped = true
                                        status = getString(R.string.source_login_stale)
                                        view.evaluateJavascript(
                                            "(function(){try{localStorage.clear();sessionStorage.clear();}catch(e){}})()",
                                            null,
                                        )
                                        val baseUrls = container.sources.sources.value.map { it.manifest.baseUrl }
                                        LibWebSession.wipeAll(baseUrls) {
                                            view.clearCache(false)
                                            view.loadUrl(loginUrl)
                                        }
                                    }
                                    return@launch
                                }
                                val saved = container.libAuth.saveRaw(sourceId, raw)
                                if (saved != null) {
                                    status = getString(R.string.source_login_ok)
                                    Toast.makeText(
                                        this@SourceLoginActivity,
                                        getString(R.string.source_login_ok),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    // Sync cookies for CDN images / ddos-guard.
                                    CookieManager.getInstance().flush()
                                    container.http.syncWebViewCookies(loginUrl)
                                    finish()
                                }
                            }
                        },
                    )
                    }
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun LoginWebView(
        url: String,
        modifier: Modifier,
        onAuthJson: (String, WebView) -> Unit,
    ) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Share the app UA so cookies set during login (incl. anti-bot clearance) stay valid.
                    settings.userAgentString = container.http.userAgent
                    // Lets the system autofill service (Google Password Manager, the same store
                    // Chrome uses) offer saved logins in the e-mail and password fields.
                    importantForAutofill = View.IMPORTANT_FOR_AUTOFILL_YES
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    setOnTouchListener { _, _ ->
                        if (firstPageChecked) userTouched = true
                        false
                    }
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            view ?: return
                            view.evaluateJavascript(
                                "(function(){try{return localStorage.getItem('auth')||localStorage['auth']||null;}catch(e){return null;}})()",
                            ) { value ->
                                // Report first, then count touches: a session found on open is the old one.
                                val ready = firstPageChecked
                                firstPageChecked = true
                                if (!ready) userTouched = false
                                if (value.isNullOrBlank() || value == "null") return@evaluateJavascript
                                val unquoted = if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
                                    value.substring(1, value.length - 1)
                                        .replace("\\\\", "\\")
                                        .replace("\\\"", "\"")
                                } else {
                                    value
                                }
                                if (unquoted.contains("access_token")) {
                                    onAuthJson(unquoted, view)
                                }
                            }
                        }
                    }
                    loadUrl(url)
                }
            },
        )
    }

    companion object {
        private const val EXTRA_SOURCE_ID = "source_id"

        fun start(context: Context, sourceId: String) {
            context.startActivity(
                Intent(context, SourceLoginActivity::class.java)
                    .putExtra(EXTRA_SOURCE_ID, sourceId),
            )
        }
    }
}
