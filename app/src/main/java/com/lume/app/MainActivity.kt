package com.lume.app

import android.content.Intent
import com.lume.app.data.library.ReadingLists
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
import androidx.compose.ui.semantics.clearAndSetSemantics
import androidx.lifecycle.lifecycleScope
import android.view.WindowManager
import androidx.biometric.BiometricManager
import androidx.biometric.BiometricPrompt
import androidx.compose.runtime.mutableStateOf
import androidx.core.content.ContextCompat
import com.lume.app.ui.components.LockScreen
import com.lume.app.ui.theme.ThemeMode
import com.lume.app.ui.theme.isDark
import com.lume.app.data.continuity.ContinueReading
import com.lume.app.domain.SourceLinks
import com.lume.app.ui.nav.AppLinks
import com.lume.app.ui.nav.Routes
import com.lume.app.ui.shell.LumeRoot
import com.lume.app.ui.theme.AppColors
import com.lume.app.ui.theme.LumeTheme
import kotlinx.coroutines.launch

class MainActivity : AppCompatActivity() {
    /** App lock: true until the screen lock is confirmed. */
    private val locked = mutableStateOf(false)
    private var stoppedAt = 0L
    private var prompting = false

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        val prefs = (application as LumeApp).container.prefs
        locked.value = prefs.appLock.value && savedInstanceState?.getBoolean(KEY_UNLOCKED) != true
        // "Hide content in recent apps": the window is secure (blank thumbnail, no screenshots).
        lifecycleScope.launch {
            prefs.secureScreen.collect { secure ->
                if (secure) window.addFlags(WindowManager.LayoutParams.FLAG_SECURE)
                else window.clearFlags(WindowManager.LayoutParams.FLAG_SECURE)
            }
        }
        handleOAuthIntent(intent)
        // After rotation the same intent comes back; only a fresh launch opens its route.
        if (savedInstanceState == null) {
            ContinueReading.reportShortcutUsed(this, intent)
            AppLinks.handle(intent)
            handleReadingList(intent)
            handleSiteLink(intent)
        }
        val container = (application as LumeApp).container
        setContent {
            val themeMode by container.prefs.themeMode.collectAsState()
            val pureBlack by container.prefs.pureBlack.collectAsState()
            val dynamicColor by container.prefs.dynamicColor.collectAsState()
            CompositionLocalProvider(LocalAppContainer provides container) {
                LumeTheme(
                    darkTheme = isDark(ThemeMode.fromKey(themeMode) ?: ThemeMode.Dark),
                    pureBlack = pureBlack,
                    dynamicColor = dynamicColor,
                ) {
                    Surface(
                        modifier = Modifier.fillMaxSize(),
                        color = AppColors.background,
                    ) {
                        // While locked, screen readers and other accessibility services must not
                        // read the library behind the lock screen.
                        androidx.compose.foundation.layout.Box(
                            if (locked.value) Modifier.clearAndSetSemantics {} else Modifier,
                        ) { LumeRoot() }
                        if (locked.value) LockScreen(onUnlock = ::promptUnlock)
                    }
                }
            }
        }
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)
        outState.putBoolean(KEY_UNLOCKED, !locked.value)
    }

    override fun onStop() {
        super.onStop()
        if (!isChangingConfigurations) stoppedAt = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        // Back after a while in the background (a file picker or a sign-in page is quicker).
        val prefs = (application as LumeApp).container.prefs
        if (prefs.appLock.value && stoppedAt > 0 && System.currentTimeMillis() - stoppedAt > LOCK_AFTER_MS) {
            locked.value = true
        }
        if (!prefs.appLock.value) locked.value = false
    }

    override fun onResume() {
        super.onResume()
        if (locked.value) promptUnlock()
    }

    /** The system screen-lock prompt: fingerprint, face, PIN, pattern or password. */
    private fun promptUnlock() {
        if (prompting) return
        val authenticators = BiometricManager.Authenticators.BIOMETRIC_WEAK or BiometricManager.Authenticators.DEVICE_CREDENTIAL
        if (BiometricManager.from(this).canAuthenticate(authenticators) != BiometricManager.BIOMETRIC_SUCCESS) {
            // No screen lock set on the phone: nothing to check against.
            locked.value = false
            return
        }
        prompting = true
        val prompt = BiometricPrompt(
            this,
            ContextCompat.getMainExecutor(this),
            object : BiometricPrompt.AuthenticationCallback() {
                override fun onAuthenticationSucceeded(result: BiometricPrompt.AuthenticationResult) {
                    prompting = false
                    locked.value = false
                }

                override fun onAuthenticationError(errorCode: Int, errString: CharSequence) {
                    prompting = false
                }
            },
        )
        prompt.authenticate(
            BiometricPrompt.PromptInfo.Builder()
                .setTitle(getString(R.string.app_lock_title))
                .setAllowedAuthenticators(authenticators)
                .build(),
        )
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        handleOAuthIntent(intent)
        ContinueReading.reportShortcutUsed(this, intent)
        AppLinks.handle(intent)
        handleReadingList(intent)
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

    /** A reading list file shared to Lume (SEND) or opened from a file manager (VIEW). */
    private fun handleReadingList(intent: Intent?) {
        if (intent == null) return
        val uri: Uri = when (intent.action) {
            Intent.ACTION_SEND -> if (intent.type == ReadingLists.MIME) {
                androidx.core.content.IntentCompat.getParcelableExtra(intent, Intent.EXTRA_STREAM, Uri::class.java)
            } else {
                null
            }
            Intent.ACTION_VIEW -> intent.data?.takeIf { it.scheme == "content" }
            else -> null
        } ?: return
        // Handled once, like the notification route.
        intent.action = Intent.ACTION_MAIN
        val container = (application as LumeApp).container
        lifecycleScope.launch {
            runCatching { container.readingLists.open(uri) }
                .onSuccess { AppLinks.open(Routes.ReadingList) }
                .onFailure { Toast.makeText(this@MainActivity, R.string.reading_list_not_a_list, Toast.LENGTH_LONG).show() }
        }
    }

    private companion object {
        const val KEY_UNLOCKED = "lume.unlocked"
        const val LOCK_AFTER_MS = 30_000L
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
                "myanimelist" -> trackers.myAnimeList.exchangeCode(code)
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
