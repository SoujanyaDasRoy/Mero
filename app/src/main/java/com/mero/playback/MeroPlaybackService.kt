package com.mero.playback

import androidx.media3.common.AudioAttributes
import androidx.media3.common.Player
import androidx.media3.common.C
import androidx.media3.datasource.DefaultHttpDataSource
import androidx.media3.datasource.ResolvingDataSource
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

        val dataSourceFactory = ResolvingDataSource.Factory(
            DefaultHttpDataSource.Factory().setAllowCrossProtocolRedirects(true),
            StreamResolver(container.streamRepository),
        )

        val player = ExoPlayer.Builder(this)
            .setMediaSourceFactory(DefaultMediaSourceFactory(dataSourceFactory))
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
