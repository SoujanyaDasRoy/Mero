package com.mero.playback

import com.mero.data.EqPresets

/**
 * The equalizer settings the UI edits, and the bridge to the DSP applying them.
 *
 * Holds no audio code of its own any more. It used to drive
 * `android.media.audiofx` effects attached to the system audio session; the
 * arithmetic now lives in [EqualizerAudioProcessor], inside ExoPlayer's own
 * pipeline, and this is the object the equalizer screen and the playback
 * service share to talk about it.
 *
 * Why the move, in short: audiofx put the behaviour in the device's hands and
 * the device kept refusing. The session's effect memory budget rejected
 * effects outright, and merely holding an effect chain moved playback off the
 * deep-buffer output and glitched it. None of it could be tested either. See
 * [EqualizerAudioProcessor] and docs/superpowers/plans/2026-09-08-equalizer.md.
 */
class AudioEffects {

    /** The stage installed in the player's audio pipeline. */
    val processor = EqualizerAudioProcessor()

    /** Widens headphone listening out of the middle of the head. */
    val crossfeedProcessor = CrossfeedAudioProcessor()

    /** Loudness matching and the limiter that makes it safe. */
    val dynamics = DynamicsAudioProcessor()

    /** Reads the finished signal, for the spectrum on the EQ screen. */
    val spectrum = SpectrumAnalyser()

    var enabled: Boolean = true
        private set
    var bands: List<Int> = EqPresets.presets.getValue("Flat")
        private set

    /** 0f..1f from the UI slider, mapped to −12..+12 dB. */
    var preamp: Float = 0.5f
        private set
    var normalization: Boolean = false
        private set

    /** 0f..1f. Off by default: it is a taste, not a correction. */
    var crossfeed: Float = 0f
        private set

    init {
        push()
    }

    fun setEnabled(value: Boolean) {
        enabled = value
        push()
    }

    fun setBands(value: List<Int>) {
        bands = value
        push()
    }

    fun setBand(index: Int, gainDb: Int) {
        bands = bands.toMutableList().also { it[index] = gainDb }
        push()
    }

    fun setPreamp(value: Float) {
        preamp = value
        push()
    }

    fun setNormalization(value: Boolean) {
        normalization = value
        dynamics.setNormalisation(value)
    }

    fun setCrossfeed(value: Float) {
        crossfeed = value.coerceIn(0f, 1f)
        crossfeedProcessor.setStrength(crossfeed)
    }

    private fun preampDb(): Float = (preamp * 24f) - 12f

    private fun push() {
        processor.setEnabled(enabled)
        processor.setBands(bands)
        processor.setPreampDb(preampDb())
    }
}
