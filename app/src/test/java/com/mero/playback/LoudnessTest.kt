package com.mero.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.pow
import kotlin.math.sin

/**
 * Loudness and limiting, against figures the standard fixes.
 *
 * BS.1770 has a published calibration: a 1 kHz sine at −20 dBFS on both
 * channels reads −20 LUFS or so, because K-weighting is close to unity there
 * by construction. That gives something to check against rather than "it went
 * up when the music got louder".
 */
class LoudnessTest {

    private val sampleRate = 48_000

    private fun measure(freq: Double, amplitudeDbFs: Double, seconds: Double = 3.0): Double? {
        val meter = LoudnessMeter(sampleRate)
        val amplitude = 10.0.pow(amplitudeDbFs / 20.0)
        val frames = (sampleRate * seconds).toInt()
        val frame = FloatArray(2)
        for (i in 0 until frames) {
            val v = (sin(2.0 * PI * freq * i / sampleRate) * amplitude).toFloat()
            frame[0] = v
            frame[1] = v
            meter.accept(frame, 2)
        }
        return meter.lufs
    }

    @Test
    fun `a 1 kHz tone at minus 20 dBFS measures close to minus 20 LUFS`() {
        val lufs = measure(1_000.0, -20.0)
        assertTrue("no measurement produced", lufs != null)
        // K-weighting is near unity at 1 kHz, and a sine's mean square is half
        // its peak squared — which the -0.691 offset and the standard's
        // calibration together account for.
        assertEquals(-23.0, lufs!!, 1.5)
    }

    @Test
    fun `a louder tone measures louder, by the amount it was raised`() {
        val quiet = measure(1_000.0, -30.0)!!
        val loud = measure(1_000.0, -20.0)!!
        assertEquals(10.0, loud - quiet, 0.5)
    }

    @Test
    fun `silence produces no measurement at all`() {
        val meter = LoudnessMeter(sampleRate)
        val frame = FloatArray(2)
        repeat(sampleRate * 2) { meter.accept(frame, 2) }
        // Gated out rather than reported as extremely quiet, so a silent intro
        // cannot drag a track's figure down.
        assertTrue("silence should not register, got ${meter.lufs}", meter.lufs == null)
    }

    @Test
    fun `k-weighting attenuates rumble and lifts presence`() {
        // The shape is the whole point of K-weighting: bass counts for less
        // than the ear-forward region does.
        val bass = measure(40.0, -20.0)!!
        val presence = measure(3_000.0, -20.0)!!
        assertTrue("40 Hz ($bass) should measure quieter than 3 kHz ($presence)", presence > bass)
    }

    @Test
    fun `normalisation gain moves a quiet track up and a loud one down`() {
        val up = normalisationGain(-24.0, targetLufs = -14.0)
        val down = normalisationGain(-6.0, targetLufs = -14.0)
        assertEquals(10.0.pow(10.0 / 20.0).toFloat(), up, 0.01f)
        assertEquals(10.0.pow(-8.0 / 20.0).toFloat(), down, 0.01f)
    }

    @Test
    fun `normalisation refuses to boost without limit`() {
        // A near-silent recording must not be dragged up 40 dB along with its
        // noise floor.
        val gain = normalisationGain(-70.0, targetLufs = -14.0, maxBoostDb = 12.0)
        assertEquals(10.0.pow(12.0 / 20.0).toFloat(), gain, 0.01f)
    }

    @Test
    fun `no measurement means no change`() {
        assertEquals(1f, normalisationGain(null, targetLufs = -14.0), 0f)
    }

    @Test
    fun `the limiter leaves quiet signal untouched`() {
        val limiter = Limiter(sampleRate)
        repeat(1_000) { assertEquals(1f, limiter.gainFor(0.5f), 1e-6f) }
    }

    @Test
    fun `the limiter pulls a peak under the ceiling`() {
        val limiter = Limiter(sampleRate, ceiling = 0.98f)
        val gain = limiter.gainFor(2.0f)
        assertTrue("2.0 * $gain should land at the ceiling", abs(2.0f * gain - 0.98f) < 1e-3f)
    }

    @Test
    fun `the limiter recovers gradually rather than snapping back`() {
        val limiter = Limiter(sampleRate, ceiling = 0.98f)
        limiter.gainFor(4f)
        val immediatelyAfter = limiter.gainFor(0.1f)
        assertTrue("gain should still be reduced, was $immediatelyAfter", immediatelyAfter < 0.5f)

        // Release is exponential with a 0.25 s time constant, so one of those
        // returns 63% of the way and three returns 95%. Asserting "recovered"
        // after a single time constant was this test being wrong about the
        // arithmetic, not the limiter being slow.
        repeat(sampleRate / 4) { limiter.gainFor(0.1f) }
        val afterOneConstant = limiter.gainFor(0.1f)
        assertEquals(0.72f, afterOneConstant, 0.03f)

        repeat(sampleRate / 2) { limiter.gainFor(0.1f) }
        assertTrue("gain should be back by three constants, was ${limiter.gainFor(0.1f)}", limiter.gainFor(0.1f) > 0.9f)
    }
}
