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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.lifecycle.lifecycleScope
import com.lume.app.ui.nav.AppLinks
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
        if (savedInstanceState == null) AppLinks.handle(intent)
        setContent {
            val darkTheme by LumeApp.instance.prefs.darkTheme.collectAsState()
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

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
        AppLinks.handle(intent)
    }

    private fun handleOAuthIntent(intent: Intent?) {
        val data: Uri = intent?.data ?: return
        if (data.scheme != "lume" || data.host != "oauth") return
        val code = data.getQueryParameter("code") ?: return
        val tracker = data.pathSegments.firstOrNull() ?: return
        lifecycleScope.launch {
            val ok = when (tracker) {
                "anilist" -> LumeApp.instance.trackers.anilist.exchangeCode(code)
                "shikimori" -> LumeApp.instance.trackers.shikimori.exchangeCode(code)
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
