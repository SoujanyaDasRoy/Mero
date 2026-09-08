package com.mero.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.ForwardingPlayer
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.exoplayer.source.DefaultMediaSourceFactory
import androidx.media3.session.MediaSession
import androidx.media3.session.MediaSessionService
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
                // 32-bit float through the effects chain instead of 16-bit.
                // With the equalizer active the signal is scaled and summed;
                // doing that in 16-bit quantises at every stage.
                DefaultRenderersFactory(this).setEnableAudioFloatOutput(true),
            )
            .setHandleAudioBecomingNoisy(true)
            .build()

        // The equalizer processes this session only — not system audio.
        val effects = container.audioEffects
        effects.attach(player.audioSessionId)
        player.addListener(object : Player.Listener {
            override fun onAudioSessionIdChanged(audioSessionId: Int) {
                effects.attach(audioSessionId)
            }
        })

        mediaSession = MediaSession.Builder(this, SkipIgnoresRepeatOne(player)).build()
    }

    override fun onGetSession(controllerInfo: MediaSession.ControllerInfo) = mediaSession

    override fun onDestroy() {
        (application as MeroApplication).container.audioEffects.release()
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
