package com.mero.playback

import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.cos
import kotlin.math.pow
import kotlin.math.sin

/**
 * One second-order section, normalised so `a0` is 1.
 *
 * Held separately from [Biquad] because the coefficients are a value and the
 * filter is a running state: the audio thread swaps a whole set of these in at
 * once when a slider moves, which it can only do safely if they are immutable.
 */
data class BiquadCoefficients(
    val b0: Double,
    val b1: Double,
    val b2: Double,
    val a1: Double,
    val a2: Double,
) {
    companion object {
        val PASSTHROUGH = BiquadCoefficients(1.0, 0.0, 0.0, 0.0, 0.0)
    }
}

/**
 * Peaking EQ coefficients, from the Audio EQ Cookbook (Robert Bristow-Johnson).
 *
 * The standard formulas rather than anything invented here — a graphic
 * equalizer band is exactly this filter, and the derivation is well travelled
 * enough that writing it out is smaller and less risky than depending on a
 * library to do it.
 *
 * [q] of about 1.41 gives the one-octave bandwidth that the ten octave-spaced
 * bands want; wider and neighbouring bands fight each other, narrower and the
 * gaps between them are audible.
 */
fun peakingEq(freq: Float, gainDb: Float, q: Float, sampleRate: Int): BiquadCoefficients {
    if (gainDb == 0f) return BiquadCoefficients.PASSTHROUGH

    val a = 10.0.pow(gainDb / 40.0)
    val w0 = 2.0 * PI * freq / sampleRate
    val cosW0 = cos(w0)
    val alpha = sin(w0) / (2.0 * q)

    val b0 = 1.0 + alpha * a
    val b1 = -2.0 * cosW0
    val b2 = 1.0 - alpha * a
    val a0 = 1.0 + alpha / a
    val a1 = -2.0 * cosW0
    val a2 = 1.0 - alpha / a

    return BiquadCoefficients(b0 / a0, b1 / a0, b2 / a0, a1 / a0, a2 / a0)
}

/**
 * A running second-order filter, one per channel per band.
 *
 * Direct Form I: it keeps both input and output history, which costs two extra
 * floats over Transposed Direct Form II and in exchange is the form whose
 * numerical behaviour is easiest to reason about when coefficients change
 * underneath it mid-stream — which is what happens every time a slider moves.
 */
class Biquad {

    private var b0 = 1.0
    private var b1 = 0.0
    private var b2 = 0.0
    private var a1 = 0.0
    private var a2 = 0.0

    private var x1 = 0.0
    private var x2 = 0.0
    private var y1 = 0.0
    private var y2 = 0.0

    fun set(c: BiquadCoefficients) {
        b0 = c.b0
        b1 = c.b1
        b2 = c.b2
        a1 = c.a1
        a2 = c.a2
    }

    /** Zeroes the history. Called on a seek or format change, never per sample. */
    fun reset() {
        x1 = 0.0
        x2 = 0.0
        y1 = 0.0
        y2 = 0.0
    }

    fun process(sample: Float): Float {
        val x0 = sample.toDouble()
        val y0 = b0 * x0 + b1 * x1 + b2 * x2 - a1 * y1 - a2 * y2
        x2 = x1
        x1 = x0
        y2 = y1
        y1 = y0
        return y0.toFloat()
    }
}

/**
 * The loudest point of a cascade's response, in dB, or 0 if it only cuts.
 *
 * Needed for headroom. Reserving the largest single band's gain is not enough:
 * one-octave bands overlap, so two neighbours set to +12 dB sum to well above
 * +12 dB between them, and the result clips — which the equalizer tests caught
 * doing exactly that. Evaluating the combined transfer function and reserving
 * its actual peak is both correct and cheap, since it only runs when a setting
 * changes.
 */
fun cascadePeakDb(coefficients: List<BiquadCoefficients>, sampleRate: Int): Float {
    if (coefficients.isEmpty()) return 0f
    var peak = 0.0
    // Log-spaced across the audible range; fine enough that a one-octave band's
    // peak cannot hide between two probes.
    val steps = 240
    val lowHz = 20.0
    val highHz = minOf(20_000.0, sampleRate / 2.0 - 1)
    val ratio = (highHz / lowHz).pow(1.0 / steps)
    var freq = lowHz
    repeat(steps + 1) {
        val w = 2.0 * PI * freq / sampleRate
        val cos1 = cos(-w); val sin1 = sin(-w)
        val cos2 = cos(-2 * w); val sin2 = sin(-2 * w)
        var db = 0.0
        for (c in coefficients) {
            val numRe = c.b0 + c.b1 * cos1 + c.b2 * cos2
            val numIm = c.b1 * sin1 + c.b2 * sin2
            val denRe = 1.0 + c.a1 * cos1 + c.a2 * cos2
            val denIm = c.a1 * sin1 + c.a2 * sin2
            val mag = hypot(numRe, numIm) / hypot(denRe, denIm)
            db += 20.0 * log10(mag)
        }
        if (db > peak) peak = db
        freq *= ratio
    }
    return peak.toFloat()
}

/** The ten octave-spaced bands the equalizer screen draws. */
object EqBands {
    val frequencies = floatArrayOf(
        31f, 62f, 125f, 250f, 500f, 1_000f, 2_000f, 4_000f, 8_000f, 16_000f,
    )

    /** One octave wide, so the bands meet without overlapping much. */
    const val Q = 1.414f

    val count get() = frequencies.size
}
