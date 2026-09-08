package com.mero.playback

import android.app.PendingIntent
import android.content.Intent
import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
import com.mero.MainActivity
import com.mero.MeroApplication

/**
 * Free, with no code of our own: notification, lock-screen controls,
 * Bluetooth/headset buttons, Android Auto. See docs/architecture.md,
 * "Why MediaSessionService rather than a plain foreground service".
 */
class MeroPlaybackService : MediaSessionService() {

    private var mediaSession: MediaSession? = null

    override fun onCreate() {
        super.onCreate()
        val container = (application as MeroApplication).container

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(container.mediaDataSourceFactory(this)))
            .setLoadControl(
                // Defaults wait 2.5s of buffered audio before starting. For a
                // 160 kbps stream that is pure dead time on top of extraction.
                DefaultLoadControl.Builder()
                    .setBufferDurationsMs(
                        /* minBufferMs = */ 30_000,
                        /* maxBufferMs = */ 300_000,
                        /* bufferForPlaybackMs = */ 500,
                        /* bufferForPlaybackAfterRebufferMs = */ 1_500,
                    )
                    .setPrioritizeTimeOverSizeThresholds(true)
                    .build(),
            )
            .setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(C.USAGE_MEDIA)
                    .setContentType(C.AUDIO_CONTENT_TYPE_MUSIC)
                    .build(),
                /* handleAudioFocus = */ true,
            )
            .setRenderersFactory(
                // Mero's own audio stages live in here — see MeroRenderersFactory.
                MeroRenderersFactory(
                    this,
                    container.audioEffects.processor,
                    container.audioEffects.spectrum,
                ),
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        mediaSession = MediaSession.Builder(this, SkipIgnoresRepeatOne(player))
            // Without this the notification is not tappable: Media3 only makes
            // it open something if the session says what to open. Tapping it
            // did nothing at all.
            //
            // FLAG_ACTIVITY_SINGLE_TOP so it returns to the running instance
            // rather than stacking a second one, which would build a fresh
            // MediaController and leave the player mid-track behind it.
            .setSessionActivity(
                PendingIntent.getActivity(
                    this,
                    0,
                    Intent(this, MainActivity::class.java)
                        .setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP),
                    PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
                ),
            )
            .build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        mediaSession?.run {
            player.release()
            release()
        }
        mediaSession = null
        super.onDestroy()
    }
}

/**
 * Makes an explicit skip mean "the next track" even under repeat-one.
 *
 * `Timeline.getNextWindowIndex` returns the *current* index when the repeat
 * mode is ONE, so `seekToNext` replays the track instead of advancing. That is
 * the behaviour auto-advance wants — it is what makes repeat-one repeat — but
 * it is not what a person pressing next means, and it left the next button
 * dead whenever repeat-one was on, in the app and in the notification alike.
 *
 * Wrapping the player rather than fixing the button: the notification, lock
 * screen, headset and car controls all call the player directly and never see
 * the app's own handlers.
 *
 * Repeat-one is restored immediately, so it still governs the thing it should:
 * what happens when the track ends on its own.
 */
private class SkipIgnoresRepeatOne(player: Player) : ForwardingPlayer(player) {

    private inline fun ignoringRepeatOne(block: () -> Unit) {
        val original = repeatMode
        if (original == Player.REPEAT_MODE_ONE) repeatMode = Player.REPEAT_MODE_ALL
        try {
            block()
        } finally {
            if (original == Player.REPEAT_MODE_ONE) repeatMode = original
        }
    }

    override fun seekToNext() = ignoringRepeatOne { super.seekToNext() }

    override fun seekToNextMediaItem() = ignoringRepeatOne { super.seekToNextMediaItem() }

    override fun seekToPrevious() = ignoringRepeatOne { super.seekToPrevious() }

    override fun seekToPreviousMediaItem() = ignoringRepeatOne { super.seekToPreviousMediaItem() }

    // hasNext/hasPrevious are what enable the notification buttons, and they
    // consult the same repeat-aware calculation.
    override fun hasNextMediaItem(): Boolean {
        if (repeatMode == Player.REPEAT_MODE_ONE) return currentMediaItemIndex < mediaItemCount - 1
        return super.hasNextMediaItem()
    }

    override fun hasPreviousMediaItem(): Boolean {
        if (repeatMode == Player.REPEAT_MODE_ONE) return currentMediaItemIndex > 0
        return super.hasPreviousMediaItem()
    }
}
