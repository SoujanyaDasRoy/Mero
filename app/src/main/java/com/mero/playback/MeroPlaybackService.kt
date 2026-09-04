package com.mero.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.exoplayer.DefaultLoadControl
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
                        /* minBufferMs = */ 15_000,
                        /* maxBufferMs = */ 60_000,
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

        mediaSession = MediaSession.Builder(this, player).build()
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
