package com.mero.playback

import android.content.Context
import android.media.audiofx.Visualizer
import android.os.Build
import android.os.VibrationEffect
import android.os.Vibrator
import android.util.Log
import kotlin.math.sqrt

/** Turns strong waveform attacks into restrained, beat-like haptic pulses. */
class BeatHaptics(context: Context) {

    private val vibrator = context.getSystemService(Vibrator::class.java)
    private var visualizer: Visualizer? = null
    private var lastPulseMs = 0L
    private var recentEnergy = 0.08f
    private var intensity = 0.35f

    val currentIntensity: Float get() = intensity

    fun setIntensity(value: Float) {
        intensity = value.coerceIn(0f, 1f)
        if (intensity == 0f) stop()
    }

    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0 || visualizer != null) return
        runCatching {
            visualizer = Visualizer(audioSessionId).apply {
                val size = Visualizer.getCaptureSizeRange()[1].coerceAtMost(1024)
                captureSize = size
                setDataCaptureListener(
                    object : Visualizer.OnDataCaptureListener {
                        override fun onWaveFormDataCapture(
                            capture: Visualizer,
                            waveform: ByteArray,
                            samplingRate: Int,
                        ) = detectBeat(waveform)

                        override fun onFftDataCapture(
                            capture: Visualizer,
                            fft: ByteArray,
                            samplingRate: Int,
                        ) = Unit
                    },
                    Visualizer.getMaxCaptureRate() / 2,
                    true,
                    false,
                )
                enabled = true
            }
        }.onFailure { Log.w(TAG, "beat haptics unavailable", it) }
    }

    fun release() {
        stop()
        runCatching { visualizer?.release() }
        visualizer = null
    }

    private fun stop() {
        runCatching { vibrator?.cancel() }
    }

    private fun detectBeat(waveform: ByteArray) {
        if (intensity <= 0f || vibrator?.hasVibrator() != true) return
        var energy = 0f
        for (sample in waveform) {
            val centered = sample.toInt() - 128
            energy += centered * centered
        }
        energy = sqrt(energy / waveform.size) / 128f
        val now = System.currentTimeMillis()
        val adaptiveThreshold = (recentEnergy * 1.45f + 0.08f).coerceIn(0.12f, 0.75f)
        val rising = energy > adaptiveThreshold && energy > recentEnergy * 1.08f
        recentEnergy = recentEnergy * 0.88f + energy * 0.12f
        if (!rising || now - lastPulseMs < MIN_PULSE_GAP_MS) return

        lastPulseMs = now
        val strength = ((energy - adaptiveThreshold) / 0.45f).coerceIn(0f, 1f)
        val amplitude = (intensity * (80f + strength * 175f)).toInt().coerceIn(1, 255)
        val duration = (10L + intensity * 22L).toLong()
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            vibrator?.vibrate(VibrationEffect.createOneShot(duration, amplitude))
        } else {
            @Suppress("DEPRECATION")
            vibrator?.vibrate(duration)
        }
    }

    private companion object {
        const val TAG = "MeroBeatHaptics"
        const val MIN_PULSE_GAP_MS = 180L
    }
}
