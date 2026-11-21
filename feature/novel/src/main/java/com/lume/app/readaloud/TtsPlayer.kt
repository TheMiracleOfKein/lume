package com.lume.app.readaloud

import android.net.Uri
import android.os.Looper
import androidx.annotation.OptIn
import androidx.media3.common.AudioAttributes
import androidx.media3.common.C
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import androidx.media3.common.Player
import androidx.media3.common.SimpleBasePlayer
import androidx.media3.common.util.UnstableApi
import com.google.common.util.concurrent.Futures
import com.google.common.util.concurrent.ListenableFuture

/**
 * [ReadAloud] as a Media3 player, so the media session shows it in the notification and on the
 * lock screen and headset buttons control it. Play / pause, stop, and previous / next paragraph;
 * no seek bar (speech has no known length). Call [refresh] when [ReadAloud.state] changes.
 */
@OptIn(UnstableApi::class)
internal class TtsPlayer : SimpleBasePlayer(Looper.getMainLooper()) {
    private val commands = Player.Commands.Builder()
        .addAll(
            Player.COMMAND_PLAY_PAUSE,
            Player.COMMAND_STOP,
            Player.COMMAND_SEEK_TO_PREVIOUS,
            Player.COMMAND_SEEK_TO_NEXT,
            Player.COMMAND_GET_CURRENT_MEDIA_ITEM,
            Player.COMMAND_GET_METADATA,
            Player.COMMAND_GET_TIMELINE,
            Player.COMMAND_RELEASE,
        )
        .build()

    private val attributes = AudioAttributes.Builder()
        .setUsage(C.USAGE_MEDIA)
        .setContentType(C.AUDIO_CONTENT_TYPE_SPEECH)
        .build()

    /** [ReadAloud.state] changed: the session reads [getState] again. */
    fun refresh() = invalidateState()

    override fun getState(): State {
        val builder = State.Builder()
            .setAvailableCommands(commands)
            .setAudioAttributes(attributes)
        val s = ReadAloud.state.value
            ?: return builder
                .setPlaybackState(Player.STATE_IDLE)
                .setPlayWhenReady(false, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
                .build()
        val metadata = MediaMetadata.Builder()
            .setTitle(s.chapterTitle)
            .setArtist(s.titleName)
            .setAlbumTitle(s.titleName)
            .setArtworkUri(s.coverUrl?.let(Uri::parse))
            .build()
        val item = MediaItem.Builder()
            .setMediaId("${s.sourceId}/${s.titleId}/${s.chapterId}")
            .setMediaMetadata(metadata)
            .build()
        return builder
            .setPlaylist(listOf(MediaItemData.Builder(item.mediaId).setMediaItem(item).setMediaMetadata(metadata).build()))
            .setCurrentMediaItemIndex(0)
            .setContentPositionMs(0)
            .setPlaybackState(if (s.status == ReadAloudStatus.Loading) Player.STATE_BUFFERING else Player.STATE_READY)
            .setPlayWhenReady(s.status != ReadAloudStatus.Paused, Player.PLAY_WHEN_READY_CHANGE_REASON_USER_REQUEST)
            .build()
    }

    override fun handleSetPlayWhenReady(playWhenReady: Boolean): ListenableFuture<*> {
        if (playWhenReady) ReadAloud.resume() else ReadAloud.pause()
        return Futures.immediateVoidFuture()
    }

    override fun handleStop(): ListenableFuture<*> {
        ReadAloud.stop()
        return Futures.immediateVoidFuture()
    }

    override fun handleSeek(mediaItemIndex: Int, positionMs: Long, seekCommand: Int): ListenableFuture<*> {
        when (seekCommand) {
            Player.COMMAND_SEEK_TO_NEXT, Player.COMMAND_SEEK_TO_NEXT_MEDIA_ITEM -> ReadAloud.nextParagraph()
            Player.COMMAND_SEEK_TO_PREVIOUS, Player.COMMAND_SEEK_TO_PREVIOUS_MEDIA_ITEM -> ReadAloud.previousParagraph()
            else -> Unit // not offered: no seek bar, one item
        }
        return Futures.immediateVoidFuture()
    }

    override fun handleRelease(): ListenableFuture<*> = Futures.immediateVoidFuture()
}
