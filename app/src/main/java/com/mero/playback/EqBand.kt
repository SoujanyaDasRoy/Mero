package com.mero.playback

import kotlin.math.ln
import kotlin.math.pow

/**
 * One band of the equalizer, as three numbers rather than one.
 *
 * A graphic equalizer fixes frequency and width and lets you move only the
 * gain, which is why ten of them can never quite sit on the problem: the
 * boxiness in a recording is at 340 Hz, not 250 or 500, and the sibilance is
 * narrow while the warmth is broad. Making frequency and Q editable turns the
 * same ten filters into something that can actually be aimed.
 *
 * The defaults are the old octave-spaced graphic layout, so a curve dialled in
 * before any of this existed still means what it meant.
 */
data class EqBand(
    val frequencyHz: Float,
    val gainDb: Float,
    val q: Float = DEFAULT_Q,
) {
    companion object {
        const val DEFAULT_Q = 1.414f
        const val MIN_Q = 0.3f
        const val MAX_Q = 6f
        const val MIN_FREQ = 20f
        const val MAX_FREQ = 20_000f
        const val MIN_GAIN_DB = -12f
        const val MAX_GAIN_DB = 12f
    }
}

/** The layout a new profile starts from. */
fun defaultBands(): List<EqBand> =
    EqBands.frequencies.map { EqBand(frequencyHz = it, gainDb = 0f) }

/** Applies gain-only preset values over the current frequencies and widths. */
fun applyGains(bands: List<EqBand>, gainsDb: List<Int>): List<EqBand> =
    bands.mapIndexed { index, band -> band.copy(gainDb = gainsDb.getOrElse(index) { 0 }.toFloat()) }

/** True when nothing is being asked of the equalizer. */
val List<EqBand>.isFlat: Boolean get() = all { it.gainDb == 0f }

/**
 * Position of a frequency on a log axis from 20 Hz to 20 kHz, as 0f..1f.
 *
 * Frequency is perceived logarithmically — the step from 100 to 200 Hz is the
 * same musical distance as 1 to 2 kHz — so a linear axis would spend most of
 * its width on treble nobody adjusts.
 */
fun frequencyToFraction(hz: Float): Float =
    (ln(hz / EqBand.MIN_FREQ) / ln(EqBand.MAX_FREQ / EqBand.MIN_FREQ)).coerceIn(0f, 1f)

/** The inverse, for turning a drag back into a frequency. */
fun fractionToFrequency(fraction: Float): Float =
    EqBand.MIN_FREQ * (EqBand.MAX_FREQ / EqBand.MIN_FREQ).pow(fraction.coerceIn(0f, 1f))

/** Serialised as `freq:gain:q` triples so a profile survives a restart. */
fun encodeBands(bands: List<EqBand>): String =
    bands.joinToString(",") { "${it.frequencyHz}:${it.gainDb}:${it.q}" }

fun decodeBands(raw: String?): List<EqBand>? {
    if (raw.isNullOrBlank()) return null
    val bands = raw.split(",").mapNotNull { entry ->
        val parts = entry.split(":")
        if (parts.size != 3) return@mapNotNull null
        val freq = parts[0].toFloatOrNull() ?: return@mapNotNull null
        val gain = parts[1].toFloatOrNull() ?: return@mapNotNull null
        val q = parts[2].toFloatOrNull() ?: return@mapNotNull null
        EqBand(
            frequencyHz = freq.coerceIn(EqBand.MIN_FREQ, EqBand.MAX_FREQ),
            gainDb = gain.coerceIn(EqBand.MIN_GAIN_DB, EqBand.MAX_GAIN_DB),
            q = q.coerceIn(EqBand.MIN_Q, EqBand.MAX_Q),
        )
    }
    return bands.takeIf { it.size == EqBands.count }
}
