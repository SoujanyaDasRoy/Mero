package com.mero.playback

import kotlin.math.PI
import kotlin.math.log10
import kotlin.math.pow
import kotlin.math.sqrt
import kotlin.math.tan

/**
 * Loudness measured the way broadcast measures it: ITU-R BS.1770 / EBU R128.
 *
 * "Evens out volume between tracks" cannot be done with a fixed gain, which is
 * what Mero shipped before and what the equalizer screen had to apologise for.
 * Peak level is no use either — a compressed pop master and a quiet orchestral
 * recording can peak identically and be twenty decibels apart in how loud they
 * feel. BS.1770 is the standard answer: filter the signal the way a head hears
 * it, then take the mean square.
 *
 * The K-weighting is two filters — a high-shelf standing in for the head, then
 * a high-pass discarding rumble the ear barely registers — and the result is
 * LUFS, on which -14 is roughly what streaming services normalise to.
 */
class LoudnessMeter(private val sampleRate: Int) {

    private val shelf = Array(MAX_CHANNELS) { Biquad() }
    private val highPass = Array(MAX_CHANNELS) { Biquad() }

    /** Mean square accumulated over the current window, per channel. */
    private var squareSum = 0.0
    private var samplesInWindow = 0
    private val windowSamples = (sampleRate * WINDOW_SECONDS).toInt().coerceAtLeast(1)

    /** Integrated loudness so far, in LUFS, or null until a window completes. */
    var lufs: Double? = null
        private set

    init {
        val shelfCoefficients = highShelf1770(sampleRate)
        val highPassCoefficients = highPass1770(sampleRate)
        for (channel in 0 until MAX_CHANNELS) {
            shelf[channel].set(shelfCoefficients)
            highPass[channel].set(highPassCoefficients)
        }
    }

    fun reset() {
        squareSum = 0.0
        samplesInWindow = 0
        lufs = null
        for (channel in 0 until MAX_CHANNELS) {
            shelf[channel].reset()
            highPass[channel].reset()
        }
    }

    /** Feeds one frame. [samples] is one value per channel. */
    fun accept(samples: FloatArray, channelCount: Int) {
        for (channel in 0 until minOf(channelCount, MAX_CHANNELS)) {
            val weighted = highPass[channel].process(shelf[channel].process(samples[channel]))
            squareSum += weighted.toDouble() * weighted
        }
        samplesInWindow++

        if (samplesInWindow >= windowSamples) {
            val meanSquare = squareSum / (samplesInWindow * minOf(channelCount, MAX_CHANNELS))
            val measured = -0.691 + 10.0 * log10(meanSquare.coerceAtLeast(1e-12))
            // Gate near-silence out: a quiet passage is not a quiet track, and
            // without this a fade-out would drag the measurement down with it.
            if (measured > ABSOLUTE_GATE_LUFS) {
                lufs = lufs?.let { it + (measured - it) * INTEGRATION } ?: measured
            }
            squareSum = 0.0
            samplesInWindow = 0
        }
    }

    private companion object {
        const val MAX_CHANNELS = 2

        /** BS.1770's momentary window. */
        const val WINDOW_SECONDS = 0.4

        /** Below this a block is silence and is excluded, per R128 gating. */
        const val ABSOLUTE_GATE_LUFS = -70.0

        /** How fast the integrated figure follows the momentary one. */
        const val INTEGRATION = 0.12
    }
}

/**
 * Stage 1 of K-weighting: a high shelf standing in for the head and torso.
 *
 * Coefficients from BS.1770 are specified at 48 kHz; these are the analogue
 * prototype re-derived at the actual rate, so the weighting is right whatever
 * the track's sample rate happens to be.
 */
fun highShelf1770(sampleRate: Int): BiquadCoefficients {
    val f0 = 1_681.974450955533
    val g = 3.999843853973347
    val q = 0.7071752369554196

    val k = tan(PI * f0 / sampleRate)
    val vh = 10.0.pow(g / 20.0)
    val vb = vh.pow(0.4996667741545416)
    val denominator = 1.0 + k / q + k * k

    val b0 = (vh + vb * k / q + k * k) / denominator
    val b1 = 2.0 * (k * k - vh) / denominator
    val b2 = (vh - vb * k / q + k * k) / denominator
    val a1 = 2.0 * (k * k - 1.0) / denominator
    val a2 = (1.0 - k / q + k * k) / denominator
    return BiquadCoefficients(b0, b1, b2, a1, a2)
}

/** Stage 2 of K-weighting: a high-pass discarding inaudible rumble. */
fun highPass1770(sampleRate: Int): BiquadCoefficients {
    val f0 = 38.13547087602444
    val q = 0.5003270373238773
    val k = tan(PI * f0 / sampleRate)
    val denominator = 1.0 + k / q + k * k
    return BiquadCoefficients(
        b0 = 1.0,
        b1 = -2.0,
        b2 = 1.0,
        a1 = 2.0 * (k * k - 1.0) / denominator,
        a2 = (1.0 - k / q + k * k) / denominator,
    )
}

/**
 * Catches peaks instead of letting them clip.
 *
 * Everything upstream reserves headroom by prediction — the equalizer knows
 * its own curve's peak, crossfeed normalises for the channel it mixes in — but
 * prediction is about the worst case, and being permanently quiet to survive a
 * moment that may never arrive is a poor trade. A limiter lets the level sit
 * where it should and only intervenes when a peak actually arrives.
 *
 * Attack is instant by design: a peak already at the threshold cannot be
 * caught gradually without letting some of it through. Release is slow enough
 * not to be heard pumping.
 */
class Limiter(sampleRate: Int, private val ceiling: Float = 0.98f) {

    private var gain = 1f
    private val releaseCoefficient =
        kotlin.math.exp(-1.0 / (RELEASE_SECONDS * sampleRate)).toFloat()

    fun reset() {
        gain = 1f
    }

    /**
     * Returns the gain to apply to this frame, given its loudest channel.
     *
     * One gain for all channels: attenuating them independently would move the
     * stereo image every time one side peaked.
     */
    fun gainFor(peak: Float): Float {
        val required = if (peak > ceiling) ceiling / peak else 1f
        gain = if (required < gain) required else gain + (required - gain) * (1f - releaseCoefficient)
        return gain
    }

    private companion object {
        const val RELEASE_SECONDS = 0.25
    }
}

/** Linear gain that moves [lufs] to [targetLufs], bounded to something sane. */
fun normalisationGain(lufs: Double?, targetLufs: Double, maxBoostDb: Double = 12.0): Float {
    if (lufs == null) return 1f
    val db = (targetLufs - lufs).coerceIn(-maxBoostDb, maxBoostDb)
    return 10.0.pow(db / 20.0).toFloat()
}

/** RMS of a block, for tests and diagnostics. */
fun rms(values: FloatArray): Double {
    if (values.isEmpty()) return 0.0
    var sum = 0.0
    for (v in values) sum += v.toDouble() * v
    return sqrt(sum / values.size)
}
