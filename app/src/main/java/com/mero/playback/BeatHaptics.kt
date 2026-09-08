package com.mero.playback

import android.content.Context
import android.os.VibrationEffect
import android.os.Vibrator
import kotlin.math.roundToInt

/**
 * A short pulse on the beat, driven by the bass the player is already
 * producing.
 *
 * This existed once and was deleted, because it read the audio through
 * `android.media.audiofx.Visualizer` — which needs `RECORD_AUDIO`. Asking for
 * the microphone so a music player can buzz was the wrong trade, and the
 * feature had never worked anyway: the permission was missing, so the effect
 * failed to initialise on every device with `error -3`.
 *
 * [SpectrumAnalyser] already reads the same samples from inside ExoPlayer's
 * pipeline, where no permission applies, so the whole thing is a listener on a
 * number that already exists.
 */
class BeatHaptics(context: Context) {

    private val vibrator = context.getSystemService(Vibrator::class.java)

    /** 0f..1f. Zero is off, and off means nothing is computed. */
    @Volatile
    var intensity: Float = 0f
        private set

    private var baseline = 0f
    private var lastPulseAt = 0L

    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0f, 1f)
        if (intensity == 0f) baseline = 0f
    }

    /**
     * Called with the analyser's low-band level.
     *
     * Onset detection rather than a threshold: music that is simply loud would
     * buzz continuously against a fixed level, so what counts is bass rising
     * sharply above where it has recently been sitting.
     */
    fun onLowBandLevel(level: Float, nowMs: Long) {
        if (intensity <= 0f) return
        val vibrator = vibrator ?: return

        val rise = level - baseline
        baseline += (level - baseline) * BASELINE_FOLLOW

        if (rise < ONSET_RISE) return
        if (nowMs - lastPulseAt < MIN_GAP_MS) return
        lastPulseAt = nowMs

        // Scaled by how hard the onset was, so a track's dynamics come through
        // instead of every beat feeling identical.
        val strength = (intensity * (rise / ONSET_RISE).coerceAtMost(2f) / 2f).coerceIn(0.05f, 1f)
        val amplitude = (strength * 255).roundToInt().coerceIn(1, 255)
        runCatching {
            vibrator.vibrate(VibrationEffect.createOneShot(PULSE_MS, amplitude))
        }
    }

    private companion object {
        /** How fast the "recent level" follows the music. */
        const val BASELINE_FOLLOW = 0.25f

        /** Rise, in normalised level, that counts as a beat. */
        const val ONSET_RISE = 0.12f

        /** Faster than this is not a beat, it is a buzz. */
        const val MIN_GAP_MS = 120L
        const val PULSE_MS = 18L
    }
}
