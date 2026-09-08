package com.mero.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** The parametric band model: the axis mapping, and surviving a restart. */
class EqBandTest {

    @Test
    fun `the frequency axis is logarithmic, so octaves are evenly spaced`() {
        // 100 to 200 Hz should occupy the same width as 1 to 2 kHz.
        val lowOctave = frequencyToFraction(200f) - frequencyToFraction(100f)
        val highOctave = frequencyToFraction(2_000f) - frequencyToFraction(1_000f)
        assertEquals(lowOctave, highOctave, 1e-4f)
    }

    @Test
    fun `a frequency survives the round trip through the axis`() {
        for (hz in listOf(20f, 63f, 440f, 1_000f, 7_500f, 20_000f)) {
            assertEquals(hz, fractionToFrequency(frequencyToFraction(hz)), hz * 0.001f)
        }
    }

    @Test
    fun `the axis is clamped to the audible range`() {
        assertEquals(0f, frequencyToFraction(5f), 0f)
        assertEquals(1f, frequencyToFraction(30_000f), 0f)
    }

    @Test
    fun `bands survive being written and read back`() {
        val original = listOf(
            EqBand(31f, 4f, 1.2f),
            EqBand(340f, -6.5f, 3f),
        ) + defaultBands().drop(2)
        val restored = decodeBands(encodeBands(original))
        assertEquals(original, restored)
    }

    @Test
    fun `nonsense stored settings fall back rather than crashing`() {
        assertNull(decodeBands("not bands at all"))
        assertNull(decodeBands(""))
        assertNull(decodeBands(null))
        // Right shape, wrong count — a profile from a build with fewer bands.
        assertNull(decodeBands("100:0:1,200:0:1"))
    }

    @Test
    fun `stored values out of range are clamped, not trusted`() {
        val restored = decodeBands(
            (0 until EqBands.count).joinToString(",") { "999999:99:99" },
        )!!
        for (band in restored) {
            assertTrue(band.frequencyHz <= EqBand.MAX_FREQ)
            assertTrue(band.gainDb <= EqBand.MAX_GAIN_DB)
            assertTrue(band.q <= EqBand.MAX_Q)
        }
    }

    @Test
    fun `a preset sets gains and leaves frequency and width alone`() {
        val moved = defaultBands().toMutableList().also {
            it[3] = it[3].copy(frequencyHz = 340f, q = 3f)
        }
        val applied = applyGains(moved, List(EqBands.count) { 5 })
        assertEquals(340f, applied[3].frequencyHz, 0f)
        assertEquals(3f, applied[3].q, 0f)
        assertEquals(5f, applied[3].gainDb, 0f)
    }

    @Test
    fun `default bands are flat`() {
        assertTrue(defaultBands().isFlat)
        assertTrue(!applyGains(defaultBands(), List(EqBands.count) { 1 }).isFlat)
    }
}
