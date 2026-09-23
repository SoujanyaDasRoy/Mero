package com.mero.playback

import android.content.Context
import android.media.AudioManager
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import androidx.media3.common.Player

private const val QUIET_MS = 3_000L
private const val SILENT_TAKEOVER_MS = 8_000L
private const val GIVE_UP_MS = 10 * 60_000L

/**
 * Whether the other app is done. [quietMs] is how long nothing else has been
 * playing; [heardOther] is whether anything else played at all since the loss.
 */
internal fun shouldResumeNow(sinceLossMs: Long, quietMs: Long, heardOther: Boolean): Boolean =
    !shouldGiveUp(sinceLossMs) &&
        quietMs >= QUIET_MS &&
        (heardOther || sinceLossMs >= SILENT_TAKEOVER_MS)

internal fun shouldGiveUp(sinceLossMs: Long): Boolean = sinceLossMs > GIVE_UP_MS

/**
 * Carries on after another app's audio, the way it already does after a call.
 *
 * Android has two kinds of interruption. A *transient* one — a notification,
 * navigation, some voice notes — ExoPlayer already pauses and resumes. A
 * *permanent* one is an app saying it is taking over: Instagram, YouTube,
 * WhatsApp videos. ExoPlayer pauses and, by design, never resumes, so a
 * thirty-second reel ended the music until someone went back and pressed
 * play.
 *
 * After a permanent loss this watches whether anything else is still playing
 * and resumes once it has been quiet for a few seconds. It stands down if the
 * person presses play or pause themselves, if the interruption runs past ten
 * minutes (they have moved on), or if the setting is off.
 */
class ResumeAfterInterruption(
    context: Context,
    private val player: Player,
    private val enabled: () -> Boolean,
) : Player.Listener {

    private val audio = context.getSystemService(AudioManager::class.java)
    private val handler = Handler(Looper.getMainLooper())

    private var lostAt = 0L
    private var quietSince = 0L
    private var heardOther = false
    private var watching = false

    private val tick = object : Runnable {
        override fun run() {
            if (!watching) return
            val now = SystemClock.elapsedRealtime()
            val sinceLoss = now - lostAt
            if (shouldGiveUp(sinceLoss) || player.mediaItemCount == 0) return stop()

            if (audio.isMusicActive) {
                heardOther = true
                quietSince = 0L
            } else if (quietSince == 0L) {
                quietSince = now
            }
            val quiet = if (quietSince == 0L) 0L else now - quietSince
            if (shouldResumeNow(sinceLoss, quiet, heardOther)) {
                stop()
                player.play()
                return
            }
            handler.postDelayed(this, 1_000)
        }
    }

    fun start() = player.addListener(this)

    fun release() {
        stop()
        player.removeListener(this)
    }

    override fun onPlayWhenReadyChanged(playWhenReady: Boolean, reason: Int) {
        if (!playWhenReady && reason == Player.PLAY_WHEN_READY_CHANGE_REASON_AUDIO_FOCUS_LOSS) {
            if (!enabled()) return
            lostAt = SystemClock.elapsedRealtime()
            quietSince = 0L
            heardOther = false
            watching = true
            handler.removeCallbacks(tick)
            handler.postDelayed(tick, 1_000)
        } else {
            // Anything else — play, pause, a headset button — is the person
            // deciding for themselves.
            stop()
        }
    }

    private fun stop() {
        watching = false
        handler.removeCallbacks(tick)
    }
}
