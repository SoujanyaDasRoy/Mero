package com.mero.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.hypot
import kotlin.math.log10
import kotlin.math.sin

/**
 * The equalizer's arithmetic, checked without a device.
 *
 * The old equalizer ran on `android.media.audiofx`, which only exists against
 * a live audio session, so none of it could be tested at all — its bugs were
 * only ever found by listening on one particular phone. A filter is a pure
 * function of its coefficients, so this asks the two questions that matter:
 * do the coefficients describe the curve we asked for, and does the filter
 * actually apply them.
 */
class BiquadTest {

    private val sampleRate = 48_000

    /** Default bands with some gains applied, expressed the old readable way. */
    private fun bandsWith(edit: (MutableList<Int>) -> Unit): List<EqBand> {
        val gains = MutableList(EqBands.count) { 0 }.also(edit)
        return applyGains(defaultBands(), gains)
    }

    /**
     * Magnitude of the filter at [freq], evaluated straight from the transfer
     * function rather than by pushing a sweep through an FFT.
     *
     * H(z) at z = e^{jw}, so this is exact — no windowing, no bin spacing, no
     * tolerance owed to the measurement itself.
     */
    private fun responseDb(c: BiquadCoefficients, freq: Double): Double {
        val w = 2.0 * PI * freq / sampleRate
        val cos1 = cos(-w); val sin1 = sin(-w)
        val cos2 = cos(-2 * w); val sin2 = sin(-2 * w)
        val numRe = c.b0 + c.b1 * cos1 + c.b2 * cos2
        val numIm = c.b1 * sin1 + c.b2 * sin2
        val denRe = 1.0 + c.a1 * cos1 + c.a2 * cos2
        val denIm = c.a1 * sin1 + c.a2 * sin2
        return 20.0 * log10(hypot(numRe, numIm) / hypot(denRe, denIm))
    }

    @Test
    fun `a peaking filter reaches its gain at the centre frequency`() {
        val c = peakingEq(1_000f, gainDb = 6f, q = 1.414f, sampleRate = sampleRate)
        assertEquals(6.0, responseDb(c, 1_000.0), 0.01)
    }

    @Test
    fun `a peaking filter leaves distant frequencies alone`() {
        val c = peakingEq(1_000f, gainDb = 9f, q = 1.414f, sampleRate = sampleRate)
        // Four octaves either side is far outside a one-octave band.
        assertEquals(0.0, responseDb(c, 62.5), 0.5)
        assertEquals(0.0, responseDb(c, 16_000.0), 0.5)
    }

    @Test
    fun `cut is the mirror of boost`() {
        val boost = peakingEq(2_000f, gainDb = 8f, q = 1.414f, sampleRate = sampleRate)
        val cut = peakingEq(2_000f, gainDb = -8f, q = 1.414f, sampleRate = sampleRate)
        assertEquals(-responseDb(boost, 2_000.0), responseDb(cut, 2_000.0), 0.01)
    }

    @Test
    fun `zero gain is exactly transparent`() {
        val c = peakingEq(4_000f, gainDb = 0f, q = 1.414f, sampleRate = sampleRate)
        for (f in listOf(20.0, 100.0, 1_000.0, 4_000.0, 15_000.0)) {
            assertEquals("at $f Hz", 0.0, responseDb(c, f), 1e-9)
        }
    }

    /**
     * The coefficients being right does not mean the filter applies them, so
     * this measures the implementation: a steady sine in, amplitude out.
     */
    @Test
    fun `the filter applies its coefficients to a real signal`() {
        val freq = 1_000.0
        val c = peakingEq(freq.toFloat(), gainDb = 6f, q = 1.414f, sampleRate = sampleRate)
        val filter = Biquad()
        filter.set(c)

        // Long enough for the transient to settle; measured over the tail only.
        val samples = sampleRate
        var peakOut = 0.0
        for (i in 0 until samples) {
            val input = sin(2.0 * PI * freq * i / sampleRate)
            val output = filter.process(input.toFloat()).toDouble()
            if (i > samples / 2) peakOut = maxOf(peakOut, abs(output))
        }
        // Input peak is 1.0, so the output peak is the gain.
        assertEquals(6.0, 20.0 * log10(peakOut), 0.1)
    }

    @Test
    fun `a flat filter passes the signal through unchanged`() {
        val filter = Biquad()
        filter.set(peakingEq(1_000f, gainDb = 0f, q = 1.414f, sampleRate = sampleRate))
        for (i in 0 until 1_000) {
            val input = sin(2.0 * PI * 440.0 * i / sampleRate).toFloat()
            assertEquals(input, filter.process(input), 1e-6f)
        }
    }

    @Test
    fun `the filter stays stable at the extremes of the band range`() {
        for (freq in EqBands.frequencies) {
            for (gain in listOf(-12f, 12f)) {
                val filter = Biquad()
                filter.set(peakingEq(freq, gain, 1.414f, sampleRate))
                var worst = 0f
                repeat(20_000) { i ->
                    val input = sin(2.0 * PI * freq * i / sampleRate).toFloat()
                    val out = filter.process(input)
                    worst = maxOf(worst, abs(out))
                }
                assertTrue(
                    "band $freq Hz at $gain dB ran away to $worst",
                    worst.isFinite() && worst < 10f,
                )
            }
        }
    }

    @Test
    fun `the drawn response is flat when every band is zero`() {
        val curve = equalizerResponseDb(defaultBands(), sampleRate)
        for (db in curve) assertEquals(0.0, db.toDouble(), 1e-6)
    }

    @Test
    fun `the drawn response rises where a band is boosted`() {
        val curve = equalizerResponseDb(bandsWith { it[5] = 8 }, sampleRate, points = 256)
        // Curve is log-spaced 20 Hz..~20 kHz, so 1 kHz sits near the middle.
        val peak = curve.max()
        assertEquals(8.0, peak.toDouble(), 0.2)
        // and the ends are untouched
        assertEquals(0.0, curve.first().toDouble(), 0.6)
        assertEquals(0.0, curve.last().toDouble(), 0.6)
    }

    @Test
    fun `overlapping boosts sum to more than either alone`() {
        val single = equalizerResponseDb(bandsWith { it[5] = 6 }, sampleRate, points = 256).max()
        val neighbours = equalizerResponseDb(bandsWith { it[5] = 6; it[6] = 6 }, sampleRate, points = 256).max()
        // This is exactly why headroom cannot come from the largest band.
        assertTrue(
            "two neighbours at +6 dB peaked at $neighbours, one alone at $single",
            neighbours > single + 1f,
        )
    }
}
