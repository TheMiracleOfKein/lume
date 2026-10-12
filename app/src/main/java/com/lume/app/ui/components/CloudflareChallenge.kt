package com.lume.app.ui.components

import android.annotation.SuppressLint
import android.view.ViewGroup
import android.webkit.CookieManager
import android.webkit.WebSettings
import android.webkit.WebView
import android.webkit.WebViewClient
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.ui.viewinterop.AndroidView
import com.lume.app.LumeApp
import com.lume.app.R
import com.lume.app.data.network.AntiBot
import com.lume.app.ui.theme.AppColors
import java.net.URI

@SuppressLint("SetJavaScriptEnabled")
@Composable
fun CloudflareChallengeOverlay(
    uri: URI,
    onDismiss: () -> Unit,
    onSolved: () -> Unit,
) {
    val http = LumeApp.instance.http
    val siteReady = remember { mutableStateOf(value = false) }
    val sawChallenge = remember { mutableStateOf(value = false) }
    val finished = remember { mutableStateOf(value = false) }
    fun finish() {
        if (finished.value) return
        finished.value = true
        http.syncWebViewCookies(uri.toString())
        onSolved()
    }
    val hint = if (siteReady.value) {
        stringResource(R.string.cf_ready_hint)
    } else {
        stringResource(R.string.cf_wait_hint)
    }
    val origin = "${uri.scheme}://${uri.host}/"

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(AppColors.background),
    ) {
        Text(
            text = stringResource(R.string.cf_title),
            color = AppColors.textPrimary,
            fontSize = 20.sp,
            modifier = Modifier.padding(16.dp),
        )
        Text(
            text = hint,
            color = AppColors.textSecondary,
            fontSize = 14.sp,
            modifier = Modifier.padding(horizontal = 16.dp),
        )
        AndroidView(
            modifier = Modifier
                .weight(1f)
                .fillMaxWidth()
                .padding(top = 8.dp),
            factory = { context ->
                WebView(context).apply {
                    layoutParams = ViewGroup.LayoutParams(
                        ViewGroup.LayoutParams.MATCH_PARENT,
                        ViewGroup.LayoutParams.MATCH_PARENT,
                    )
                    settings.javaScriptEnabled = true
                    settings.domStorageEnabled = true
                    settings.mixedContentMode = WebSettings.MIXED_CONTENT_COMPATIBILITY_MODE
                    // cf_clearance is bound to the UA that solved it: use the app's UA, not the WebView's.
                    settings.userAgentString = http.userAgent
                    CookieManager.getInstance().setAcceptCookie(true)
                    CookieManager.getInstance().setAcceptThirdPartyCookies(this, true)
                    webViewClient = object : WebViewClient() {
                        override fun onPageFinished(view: WebView?, url: String?) {
                            if (AntiBot.isChallengePageTitle(view?.title)) {
                                sawChallenge.value = true
                                return
                            }
                            siteReady.value = true
                            // The challenge redirected to the real site: clearance cookie is set.
                            if (sawChallenge.value) view?.postDelayed({ finish() }, AUTO_CONTINUE_DELAY_MS)
                        }
                    }
                    loadUrl(origin)
                }
            },
        )
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(16.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.action_cancel), color = AppColors.textSecondary)
            }
            Button(
                onClick = { finish() },
                colors = ButtonDefaults.buttonColors(
                    containerColor = AppColors.accent,
                    contentColor = Color.Black,
                ),
            ) { Text(stringResource(R.string.action_continue)) }
        }
    }
}

private const val AUTO_CONTINUE_DELAY_MS = 800L
