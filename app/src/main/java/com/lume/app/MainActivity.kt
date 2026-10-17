package com.lume.app

import android.content.Intent
import android.net.Uri
import android.os.Bundle
import android.widget.Toast
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.CompositionLocalProvider
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.lume.app.domain.SourceLinks
import com.lume.app.ui.nav.AppLinks
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.shell.LumeRoot
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.LumeTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        handleOAuthIntent(intent)
        // After rotation the same intent comes back; only a fresh launch opens its route.
        if (savedInstanceState == null) {
            AppLinks.handle(intent)
            handleSiteLink(intent)
        }
        val container = (application as LumeApp).container
        setContent {
            val darkTheme by container.prefs.darkTheme.collectAsState()
            CompositionLocalProvider(LocalAppContainer provides container) {
                LumeTheme(darkTheme = darkTheme) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = AppColors.background,
                    ) {
                        LumeRoot()
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
        AppLinks.handle(intent)
        handleSiteLink(intent)
    }

    /**
     * A title link opened from the browser (VIEW) or shared to Lume (SEND): find the source and
     * title it points at and open the title screen.
     */
    private fun handleSiteLink(intent: Intent?) {
        if (intent == null) return
        val link = when (intent.action) {
            Intent.ACTION_VIEW -> intent.dataString?.takeIf { it.startsWith("http") }
            Intent.ACTION_SEND -> intent.getStringExtra(Intent.EXTRA_TEXT)?.let(SourceLinks::firstLink)
            else -> null
        } ?: return
        // Handled once, like the notification route.
        intent.action = Intent.ACTION_MAIN
        val container = (application as LumeApp).container
        lifecycleScope.launch {
            container.sources.awaitLoaded()
            val target = container.sources.resolveLink(link)
            if (target == null) {
                Toast.makeText(this@MainActivity, R.string.link_open_failed, Toast.LENGTH_LONG).show()
                return@launch
            }
            AppLinks.open(Routes.title(target.first, target.second))
        }
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme != "lume" || data.host != "oauth") return
        val code = data.getQueryParameter("code") ?: return
        val tracker = data.pathSegments.firstOrNull() ?: return
        lifecycleScope.launch {
            val trackers = (application as LumeApp).container.trackers
            val ok = when (tracker) {
                "anilist" -> trackers.anilist.exchangeCode(code)
                "shikimori" -> trackers.shikimori.exchangeCode(code)
                else -> false
            }
            Toast.makeText(
                this@MainActivity,
                if (ok) getString(R.string.oauth_success, tracker)
                else getString(R.string.oauth_error, tracker),
                Toast.LENGTH_SHORT,
            ).show()
        }
    }
}
