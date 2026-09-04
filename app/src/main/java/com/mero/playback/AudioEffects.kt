package com.mero.playback

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.LoudnessEnhancer
import android.util.Log
import com.mero.data.EqPresets

private const val BANDS = 10
private const val CHANNELS = 2
private const val TAG = "MeroAudioFx"

/**
 * The real DSP behind the equalizer screen.
 *
 * `DynamicsProcessing` (API 28+) rather than the legacy `Equalizer` effect —
 * it's the reason minSdk is 28 and not lower (CLAUDE.md). Attached to
 * ExoPlayer's audio session, so it processes Mero's output only, not
 * system-wide audio.
 *
 * Lives in AppContainer and is mutated from the UI: the equalizer screen and
 * the player service are in the same process, so this object is the shared
 * point between them.
 */
class AudioEffects {

    private var sessionId: Int? = null
    private var processing: DynamicsProcessing? = null
    private var loudness: LoudnessEnhancer? = null

    var enabled: Boolean = true
        private set
    var bands: List<Int> = EqPresets.presets.getValue("Bass Boost")
        private set
    /** 0f..1f from the UI slider, mapped to −12..+12 dB. */
    var preamp: Float = 0.5f
        private set
    var normalization: Boolean = true
        private set

    /** Called by the playback service once ExoPlayer has an audio session. */
    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == sessionId) return
        release()
        sessionId = audioSessionId
        runCatching {
            processing = DynamicsProcessing(0, audioSessionId, buildConfig())
            loudness = LoudnessEnhancer(audioSessionId)
        }.onFailure { Log.e(TAG, "failed to attach audio effects", it) }
        apply()
    }

    fun release() {
        runCatching { processing?.release() }
        runCatching { loudness?.release() }
        processing = null
        loudness = null
        sessionId = null
    }

    fun setEnabled(value: Boolean) { enabled = value; apply() }
    fun setBands(value: List<Int>) { bands = value; apply() }
    fun setBand(index: Int, gainDb: Int) {
        bands = bands.toMutableList().also { it[index] = gainDb }
        apply()
    }
    fun setPreamp(value: Float) { preamp = value; apply() }
    fun setNormalization(value: Boolean) { normalization = value; apply() }

    private fun buildConfig(): DynamicsProcessing.Config =
        DynamicsProcessing.Config.Builder(
            DynamicsProcessing.VARIANT_FAVOR_FREQUENCY_RESOLUTION,
            CHANNELS,
            /* preEqInUse = */ true,
            /* preEqBandCount = */ BANDS,
            /* mbcInUse = */ false,
            /* mbcBandCount = */ 0,
            /* postEqInUse = */ false,
            /* postEqBandCount = */ 0,
            /* limiterInUse = */ true,
        ).build()

    /** Pushes current state onto the live effects. Safe to call any time. */
    private fun apply() {
        val dp = processing ?: return
        runCatching {
            for (i in 0 until BANDS) {
                val gain = if (enabled) bands.getOrElse(i) { 0 }.toFloat() else 0f
                dp.setPreEqBandAllChannelsTo(
                    i,
                    DynamicsProcessing.EqBand(true, EqPresets.bandFrequencies[i], gain),
                )
            }
            // 0f..1f -> -12..+12 dB
            dp.setInputGainAllChannelsTo(if (enabled) (preamp * 24f) - 12f else 0f)
            dp.enabled = enabled
        }.onFailure { Log.e(TAG, "failed to apply equalizer", it) }

        runCatching {
            loudness?.let {
                it.enabled = enabled && normalization
                // Modest, fixed lift; real per-track ReplayGain needs loudnessDb
                // from the extractor and lands with the rest of M4's polish.
                if (enabled && normalization) it.setTargetGain(300)
            }
        }.onFailure { Log.e(TAG, "failed to apply loudness enhancer", it) }
    }
}
