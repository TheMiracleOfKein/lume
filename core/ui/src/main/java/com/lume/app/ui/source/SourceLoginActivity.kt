package com.lume.app.ui.source

import android.annotation.SuppressLint
import android.content.ClipboardManager
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Row
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import com.lume.app.data.source.LibBrowserLogin
import com.lume.app.domain.SourceManifest
import android.content.Context
import android.content.Intent
import android.os.Bundle
import android.view.View
import android.webkit.CookieManager
import android.webkit.WebChromeClient
import android.webkit.WebView
import androidx.activity.compose.BackHandler
import androidx.compose.foundation.layout.imePadding
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.ui.text.style.TextOverflow
import com.lume.app.ui.theme.LumeSystemBars
import com.lume.app.ui.theme.ThemeMode
import com.lume.app.ui.theme.isDark
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
import androidx.compose.ui.semantics.LiveRegionMode
import androidx.compose.ui.semantics.liveRegion
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.viewinterop.AndroidView
import androidx.lifecycle.lifecycleScope
import com.lume.app.ContainerOwner
import com.lume.app.R
import com.lume.app.ui.components.GuardedWebViewClient
import com.lume.app.data.source.LibWebSession
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.LumeTheme
import kotlinx.coroutines.launch

/**
 * Opens the Lib site in a WebView so the user can log in.
 * Reads `localStorage.auth` (cdnlibs) after each page load and saves the token.
 */
class SourceLoginActivity : ComponentActivity() {
    private val container get() = (application as ContainerOwner).container

    /** The user has touched the page after it first loaded (typed, tapped a button). */
    private var userTouched = false
    private var firstPageChecked = false

    /** Access tokens of the old site session found on open; never saved. */
    private val staleTokens = mutableSetOf<String>()

    /** The heavy wipe runs once per screen, so a site that keeps the session can't loop it. */
    private var wiped = false

    /** A sign-in running in the browser, waiting for the copied callback address. */
    private var browserAttempt by mutableStateOf<LibBrowserLogin.Attempt?>(null)
    private var callbackText by mutableStateOf("")
    private var browserBusy by mutableStateOf(false)
    private var browserError by mutableStateOf<String?>(null)
    private var manifest: SourceManifest? = null

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
        manifest = source.manifest
        // The browser may outlive this screen's process; the attempt's secrets come back with it.
        savedInstanceState?.getStringArray(STATE_ATTEMPT)?.takeIf { it.size == 4 }?.let { (url, verifier, state, redirect) ->
            browserAttempt = LibBrowserLogin.Attempt(url, verifier, state, redirect)
        }
        val loginUrl = source.manifest.baseUrl.trimEnd('/') + "/"
        enableEdgeToEdge()
        val prefs = container.prefs
        setContent {
            val themeMode by prefs.themeMode.collectAsState()
            val pureBlack by prefs.pureBlack.collectAsState()
            val dynamicColor by prefs.dynamicColor.collectAsState()
            val dark = isDark(ThemeMode.fromKey(themeMode) ?: ThemeMode.Dark)
            LumeSystemBars(dark)
            LumeTheme(darkTheme = dark, pureBlack = pureBlack, dynamicColor = dynamicColor) {
                var status by remember { mutableStateOf(getString(R.string.source_login_hint)) }
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
                                    stringResource(R.string.source_login_title, source.manifest.name),
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
                    Column(Modifier.fillMaxSize().padding(padding)) {
                    if (progress in 1..99) {
                        LinearProgressIndicator(
                            progress = { progress / 100f },
                            modifier = Modifier.fillMaxWidth(),
                            color = AppColors.accent,
                            trackColor = AppColors.cardHigh,
                        )
                    }
                    Text(
                        status,
                        color = AppColors.textSecondary,
                        fontSize = 13.sp,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp).semantics { liveRegion = LiveRegionMode.Polite },
                    )
                    BrowserLoginPanel(onStart = { startBrowserLogin(source.manifest.baseUrl) })
                    LoginWebView(
                        url = loginUrl,
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f),
                        onCreated = { web = it },
                        onProgress = { progress = it },
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

    // ClickableViewAccessibility: the touch listener only notes that the user touched the page
    // and never consumes the touch, so the WebView's own click handling stays as it is.
    @SuppressLint("SetJavaScriptEnabled", "ClickableViewAccessibility")
    @Composable
    private fun LoginWebView(
        url: String,
        modifier: Modifier,
        onCreated: (WebView?) -> Unit,
        onProgress: (Int) -> Unit,
        onAuthJson: (String, WebView) -> Unit,
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
                    webViewClient = object : GuardedWebViewClient(container.http.servers) {
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

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        browserAttempt?.let { outState.putStringArray(STATE_ATTEMPT, arrayOf(it.url, it.verifier, it.state, it.redirectUri)) }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        // Back from the browser: a copied callback address finishes the sign-in by itself.
        // The clipboard can only be read with focus, hence here and not in onResume.
        if (!hasFocus || browserAttempt == null || browserBusy) return
        val clip = runCatching {
            val clipboard = getSystemService(ClipboardManager::class.java)
            clipboard?.primaryClip?.takeIf { it.itemCount > 0 }?.getItemAt(0)?.coerceToText(this)?.toString()
        }.getOrNull() ?: return
        val callback = LibBrowserLogin.parseCallback(clip) ?: return
        if (callback.state != browserAttempt?.state) return
        callbackText = clip.trim()
        finishBrowserLogin()
    }

    /** Opens the lib.social sign-in in a Custom Tab, where the phone's Google accounts are. */
    private fun startBrowserLogin(baseUrl: String) {
        val attempt = LibBrowserLogin.start(baseUrl)
        browserAttempt = attempt
        browserError = null
        callbackText = ""
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(attempt.url)).apply {
            // The Custom Tabs protocol without the library: a session extra, even an empty one,
            // asks the browser for a tab on top of Lume instead of its own window.
            putExtras(Bundle().apply { putBinder(EXTRA_CUSTOM_TABS_SESSION, null) })
        }
        runCatching { startActivity(intent) }.onFailure {
            browserAttempt = null
            Toast.makeText(this, getString(R.string.source_login_browser_missing), Toast.LENGTH_SHORT).show()
        }
    }

    private fun finishBrowserLogin() {
        val attempt = browserAttempt ?: return
        val source = manifest ?: return
        val callback = LibBrowserLogin.parseCallback(callbackText)
        if (callback == null) {
            browserError = getString(R.string.source_login_browser_bad_link)
            return
        }
        browserBusy = true
        browserError = null
        lifecycleScope.launch {
            val result = runCatching { container.libBrowserLogin.finish(source, attempt, callback) }
            browserBusy = false
            if (result.isSuccess) {
                browserAttempt = null
                Toast.makeText(this@SourceLoginActivity, getString(R.string.source_login_ok), Toast.LENGTH_SHORT).show()
                finish()
            } else {
                // A code works once: a failed one needs a new sign-in.
                browserError = getString(R.string.source_login_browser_failed)
            }
        }
    }

    /** "Sign in through the browser", then the field for the copied address. */
    @Composable
    private fun BrowserLoginPanel(onStart: () -> Unit) {
        val attempt = browserAttempt
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp).padding(bottom = 8.dp)) {
            if (attempt == null) {
                OutlinedButton(onClick = onStart, modifier = Modifier.fillMaxWidth()) {
                    Text(stringResource(R.string.source_login_browser), color = AppColors.textPrimary)
                }
                return@Column
            }
            Text(
                stringResource(R.string.source_login_browser_steps),
                color = AppColors.textSecondary,
                fontSize = 13.sp,
            )
            OutlinedTextField(
                value = callbackText,
                onValueChange = {
                    callbackText = it
                    browserError = null
                },
                placeholder = { Text(stringResource(R.string.source_login_browser_paste)) },
                singleLine = true,
                isError = browserError != null,
                supportingText = browserError?.let { { Text(it) } },
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
            )
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                TextButton(onClick = { browserAttempt = null }, enabled = !browserBusy) {
                    Text(stringResource(R.string.action_cancel), color = AppColors.textSecondary)
                }
                TextButton(onClick = onStart, enabled = !browserBusy) {
                    Text(stringResource(R.string.source_login_browser_again), color = AppColors.textSecondary)
                }
                Button(
                    onClick = ::finishBrowserLogin,
                    enabled = !browserBusy && callbackText.isNotBlank(),
                    colors = ButtonDefaults.buttonColors(containerColor = AppColors.accent, contentColor = AppColors.onAccent),
                ) {
                    Text(stringResource(R.string.source_login_browser_finish))
                }
            }
        }
    }

    companion object {
        private const val EXTRA_SOURCE_ID = "source_id"
        private const val STATE_ATTEMPT = "browser_attempt"

        /** `CustomTabsIntent.EXTRA_SESSION`, the extra that makes a browser open a Custom Tab. */
        private const val EXTRA_CUSTOM_TABS_SESSION = "android.support.customtabs.extra.SESSION"

        fun start(context: Context, sourceId: String) {
            context.startActivity(
                Intent(context, SourceLoginActivity::class.java)
                    .putExtra(EXTRA_SOURCE_ID, sourceId),
            )
        }
    }
}
