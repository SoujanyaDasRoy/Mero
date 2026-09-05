package com.mero.playback

import android.media.audiofx.DynamicsProcessing
import android.media.audiofx.EnvironmentalReverb
import android.media.audiofx.LoudnessEnhancer
import android.media.audiofx.Virtualizer
import android.util.Log
import com.mero.data.EqPresets
import kotlin.math.max
import kotlin.math.roundToInt

private const val BANDS = 10
private const val CHANNELS = 2
private const val TAG = "MeroAudioFx"

/**
 * The real DSP behind the equalizer screen.
 *
 * `DynamicsProcessing` (API 28+) rather than the legacy `Equalizer` effect — it
 * is the reason minSdk is 28 (CLAUDE.md). Attached to ExoPlayer's audio session,
 * so it processes Mero's output only, not system-wide audio.
 *
 * Lives in AppContainer and is mutated from the UI: the equalizer screen and the
 * player service share a process, so this object is the point between them.
 */
class AudioEffects {

    private var sessionId: Int? = null
    private var processing: DynamicsProcessing? = null
    private var loudness: LoudnessEnhancer? = null
    private var virtualizer: Virtualizer? = null
    private var reverb: EnvironmentalReverb? = null

    var enabled: Boolean = true
        private set
    var bands: List<Int> = EqPresets.presets.getValue("Flat")
        private set
    /** 0f..1f from the UI slider, mapped to −12..+12 dB. */
    var preamp: Float = 0.5f
        private set
    /** 0f..1f from the UI slider, mapped to 0..12 dB of safe output lift. */
    var booster: Float = 0f
        private set
    /** 0f..1f room ambience intensity. */
    var reverbIntensity: Float = 0f
        private set
    var normalization: Boolean = false
        private set
    var spatial: Boolean = false
        private set

    /** True when the device can actually widen the stereo image. */
    var spatialSupported: Boolean = false
        private set

    /**
     * True when the settings ask for nothing: flat bands, unity preamp, no
     * normalization, no spatial widening.
     *
     * Worth its own concept because "enabled but transparent" is the common
     * case — most listening happens on the Flat preset — and running audio
     * through a 10-band processor and a 20:1 limiter to achieve nothing is
     * strictly worse than not running it at all.
     */
    private val isTransparent: Boolean
        get() = !enabled || (
            bands.all { it == 0 } &&
                preampDb() == 0f &&
                boosterDb() == 0f &&
                reverbIntensity == 0f &&
                !normalization &&
                !(spatial && spatialSupported)
            )

    private fun preampDb(): Float = (preamp * 24f) - 12f
    private fun boosterDb(): Float = booster * 12f

    /** Called by the playback service once ExoPlayer has an audio session. */
    fun attach(audioSessionId: Int) {
        if (audioSessionId == 0 || audioSessionId == sessionId) return
        release()
        sessionId = audioSessionId
        runCatching {
            processing = DynamicsProcessing(0, audioSessionId, buildConfig())
            loudness = LoudnessEnhancer(audioSessionId)
            virtualizer = Virtualizer(0, audioSessionId).also {
                spatialSupported = it.strengthSupported
            }
            reverb = EnvironmentalReverb(0, audioSessionId)
        }.onFailure { Log.e(TAG, "failed to attach audio effects", it) }
        apply()
    }

    fun release() {
        runCatching { processing?.release() }
        runCatching { loudness?.release() }
        runCatching { virtualizer?.release() }
        runCatching { reverb?.release() }
        processing = null
        loudness = null
        virtualizer = null
        sessionId = null
    }

    fun setEnabled(value: Boolean) { enabled = value; apply() }
    fun setBands(value: List<Int>) { bands = value; apply() }
    fun setBand(index: Int, gainDb: Int) {
        bands = bands.toMutableList().also { it[index] = gainDb }
        apply()
    }
    fun setPreamp(value: Float) { preamp = value; apply() }
    fun setBooster(value: Float) { booster = value.coerceIn(0f, 1f); apply() }
    fun setReverb(value: Float) { reverbIntensity = value.coerceIn(0f, 1f); apply() }
    fun setNormalization(value: Boolean) { normalization = value; apply() }
    fun setSpatial(value: Boolean) { spatial = value; apply() }

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

    /**
     * Pushes current state onto the live effects.
     *
     * Two things here exist specifically to stop the audible crackle that
     * boosting bands used to cause:
     *
     *  - **Headroom compensation.** Raising a band raises the summed signal, and
     *    anything past 0 dBFS clips — which is what the crackle was. Input gain
     *    is pulled down by the largest positive band gain, so a boosted EQ
     *    changes tone without pushing the mix into the ceiling.
     *  - **A configured limiter.** The config always reserved a limiter stage,
     *    but nothing ever set its parameters, so it wasn't catching anything.
     *    It now hard-limits just below full scale as a backstop.
     */
    private fun apply() {
        val dp = processing ?: return
        runCatching {
            var maxBoost = 0f
            for (i in 0 until BANDS) {
                val gain = if (enabled) bands.getOrElse(i) { 0 }.toFloat() else 0f
                maxBoost = max(maxBoost, gain)
                dp.setPreEqBandAllChannelsTo(
                    i,
                    DynamicsProcessing.EqBand(true, EqPresets.bandFrequencies[i], gain),
                )
            }

            val preampDb = if (enabled) preampDb() else 0f
            val outputLiftDb = if (enabled) boosterDb() else 0f
            // Reserve headroom for the post-EQ booster so loudness gains do not
            // turn an already boosted band into hard digital clipping.
            dp.setInputGainAllChannelsTo(preampDb - maxBoost - outputLiftDb)

            dp.setLimiterAllChannelsTo(
                DynamicsProcessing.Limiter(
                    /* inUse = */ true,
                    /* enabled = */ true,
                    /* linkGroup = */ 0,
                    /* attackTime = */ 1f,
                    /* releaseTime = */ 60f,
                    /* ratio = */ 20f,
                    /* threshold = */ -1f,
                    /* postGain = */ 0f,
                ),
            )
            // Bypass rather than process-to-no-effect. A flat pre-EQ still
            // costs a multiband pass and a limiter stage on every sample, and
            // the limiter is not transparent by construction.
            dp.enabled = !isTransparent
        }.onFailure { Log.e(TAG, "failed to apply equalizer", it) }

        runCatching {
            loudness?.let {
                val gainMb = if (enabled) {
                    (boosterDb() * 1000f).roundToInt() + if (normalization) 150 else 0
                } else {
                    0
                }
                it.enabled = enabled && gainMb > 0
                if (it.enabled) it.setTargetGain(gainMb)
            }
        }.onFailure { Log.e(TAG, "failed to apply loudness enhancer", it) }

        runCatching {
            virtualizer?.let {
                it.enabled = spatial && spatialSupported
                if (spatial && spatialSupported) it.setStrength(900)
            }
        }.onFailure { Log.e(TAG, "failed to apply spatial audio", it) }

        runCatching {
            reverb?.let { effect ->
                val amount = if (enabled) reverbIntensity else 0f
                effect.enabled = amount > 0f
                if (amount > 0f) {
                    effect.roomLevel = (-1000f + amount * 850f).toInt().toShort()
                    effect.roomHFLevel = (-1800f + amount * 1200f).toInt().toShort()
                    effect.decayTime = (350f + amount * 900f).toInt()
                    effect.decayHFRatio = (450f + amount * 250f).toInt().toShort()
                    effect.reflectionsLevel = (-1800f + amount * 1300f).toInt().toShort()
                    effect.reverbLevel = (-2200f + amount * 1500f).toInt().toShort()
                    effect.diffusion = (700f + amount * 250f).toInt().toShort()
                    effect.density = (650f + amount * 300f).toInt().toShort()
                }
            }
        }.onFailure { Log.e(TAG, "failed to apply reverb", it) }
    }
}
