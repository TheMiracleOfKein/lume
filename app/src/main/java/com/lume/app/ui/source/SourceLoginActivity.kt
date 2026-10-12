package com.lume.app.ui.source

import android.annotation.SuppressLint
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.webkit.CookieManager
import android.webkit.WebView
import android.webkit.WebViewClient
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.outlined.ArrowBack
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
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.LumeTheme
import kotlinx.coroutines.launch

/**
 * Opens the Lib site in a WebView so the user can log in.
 * Reads `localStorage.auth` (cdnlibs) after each page load and saves the token.
 */
class SourceLoginActivity : ComponentActivity() {
    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val sourceId = intent.getStringExtra(EXTRA_SOURCE_ID) ?: run {
            finish()
            return
        }
        val source = LumeApp.instance.sources.sourceById(sourceId) ?: run {
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
                                IconButton(onClick = { finish() }) {
                                    Icon(
                                        Icons.AutoMirrored.Outlined.ArrowBack,
                                        null,
                                        tint = AppColors.textPrimary,
                                    )
                                }
                            },
                            actions = {
                                TextButton(onClick = { finish() }) {
                                    Text(stringResource(R.string.action_close), color = AppColors.accent)
                                }
                            },
                        )
                    },
                ) { padding ->
                    LoginWebView(
                        url = loginUrl,
                        modifier = Modifier
                            .fillMaxSize()
                            .padding(padding),
                        onAuthJson = { raw ->
                            lifecycleScope.launch {
                                val saved = LumeApp.instance.libAuth.saveRaw(sourceId, raw)
                                if (saved != null) {
                                    status = getString(R.string.source_login_ok)
                                    Toast.makeText(
                                        this@SourceLoginActivity,
                                        getString(R.string.source_login_ok),
                                        Toast.LENGTH_SHORT,
                                    ).show()
                                    // Sync cookies for CDN images / ddos-guard.
                                    CookieManager.getInstance().flush()
                                    LumeApp.instance.http.syncWebViewCookies(loginUrl)
                                    finish()
                                }
                            }
                        },
                    )
                }
            }
        }
    }

    @SuppressLint("SetJavaScriptEnabled")
    @Composable
    private fun LoginWebView(
        url: String,
        modifier: Modifier,
        onAuthJson: (String) -> Unit,
    ) {
        AndroidView(
            modifier = modifier,
            factory = { ctx ->
                WebView(ctx).apply {
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    // Share the app UA so cookies set during login (incl. anti-bot clearance) stay valid.
                    settings.userAgentString = LumeApp.instance.http.userAgent
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, finishedUrl: String?) {
                            view?.evaluateJavascript(
                                "(function(){try{return localStorage.getItem('auth')||localStorage['auth']||null;}catch(e){return null;}})()",
                            ) { value ->
                                if (value.isNullOrBlank() || value == "null") return@evaluateJavascript
                                val unquoted = if (value.length >= 2 && value.first() == '"' && value.last() == '"') {
                                    value.substring(1, value.length - 1)
                                        .replace("\\\\", "\\")
                                        .replace("\\\"", "\"")
                                } else {
                                    value
                                }
                                if (unquoted.contains("access_token")) {
                                    onAuthJson(unquoted)
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
