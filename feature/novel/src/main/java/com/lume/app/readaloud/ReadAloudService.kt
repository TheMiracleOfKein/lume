package com.lume.app.readaloud

import android.app.PendingIntent
import androidx.annotation.OptIn
import androidx.media3.common.util.UnstableApi
import androidx.media3.session.DefaultMediaNotificationProvider
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.lume.app.R
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.launch

/**
 * Keeps read aloud going with the screen off or the app closed: a media session with its
 * notification and lock-screen controls. It follows [ReadAloud.state] and stops itself when read
 * aloud is turned off.
 */
@OptIn(UnstableApi::class)
class ReadAloudService : MediaSessionService() {
    private var session: MediaSession? = null
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)

    override fun onCreate() {
        super.onCreate()
        setMediaNotificationProvider(
            DefaultMediaNotificationProvider.Builder(this)
                .setChannelName(R.string.read_aloud_channel)
                .build()
                .apply { setSmallIcon(R.drawable.ic_stat_lume) },
        )
        val player = TtsPlayer()
        val open = packageManager.getLaunchIntentForPackage(packageName)?.let {
            PendingIntent.getActivity(this, 0, it, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
        }
        val s = MediaSession.Builder(this, player)
            .setId("read_aloud")
            .apply { open?.let(::setSessionActivity) }
            .build()
        session = s
        // Shown without waiting for a controller to connect.
        addSession(s)
        scope.launch {
            ReadAloud.state.collect { state ->
                player.refresh()
                if (state == null) stopSelf()
            }
        }
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo): MediaSession? = session

    override fun onDestroy() {
        scope.cancel()
        session?.let {
            it.player.release()
            it.release()
        }
        session = null
        super.onDestroy()
    }
}
