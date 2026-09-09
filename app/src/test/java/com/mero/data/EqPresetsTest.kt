package com.mero.data

import com.mero.playback.EqBand
import com.mero.playback.cascadeMagnitudeDb
import com.mero.playback.peakingEq
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class EqPresetsTest {

    @Test
    fun `every preset has one gain per band`() {
        EqPresets.presets.forEach { (name, gains) ->
            assertEquals(name, EqPresets.bandFrequencies.size, gains.size)
        }
    }

    /**
     * A preset that asked for more than the band range allows would be clamped
     * on the way in and silently mean something other than what it says.
     */
    @Test
    fun `no preset exceeds the range a band can hold`() {
        EqPresets.presets.forEach { (name, gains) ->
            gains.forEach { gain ->
                assertTrue(
                    "$name asks for $gain dB",
                    gain >= EqBand.MIN_GAIN_DB && gain <= EqBand.MAX_GAIN_DB,
                )
            }
        }
    }

    @Test
    fun `Flat asks for nothing`() {
        assertTrue(EqPresets.presets.getValue("Flat").all { it == 0 })
    }

    /**
     * The one a car usually wants. Cutting has to happen where the boom is —
     * the bottom three bands — and leave the voice alone, or it just sounds
     * like the volume went down.
     */
    @Test
    fun `Less Bass cuts the low end and leaves the rest`() {
        val gains = EqPresets.presets.getValue("Less Bass")
        val lowBands = gains.take(3)
        val midAndUp = gains.drop(4)

        assertTrue("expected a real cut, got $lowBands", lowBands.all { it <= -5 })
        assertTrue("mids and treble should be untouched", midAndUp.all { it == 0 })
    }

    @Test
    fun `Bass Boost and Less Bass pull in opposite directions`() {
        val boost = EqPresets.presets.getValue("Bass Boost")
        val cut = EqPresets.presets.getValue("Less Bass")
        boost.zip(cut).take(3).forEach { (up, down) ->
            assertTrue(up > 0 && down < 0)
        }
    }

    /**
     * The whole point of this one: shoulders, not a V.
     *
     * A V-shape lifts both ends *and cuts the middle*, which exaggerates the
     * difference and puts every voice and guitar behind the music. Measured on
     * the real filter cascade rather than read off the numbers, because
     * neighbouring bands overlap — a curve can be non-negative at every centre
     * frequency and still dip between two of them.
     */
    @Test
    fun `Extended lifts both ends without dipping anywhere`() {
        val bands = EqPresets.bandFrequencies.mapIndexed { index, freq ->
            EqBand(freq, EqPresets.presets.getValue("Extended")[index].toFloat())
        }
        val cascade = bands.map { peakingEq(it.frequencyHz, it.gainDb, it.q, SAMPLE_RATE) }
        fun at(freq: Double) = cascadeMagnitudeDb(cascade, freq, SAMPLE_RATE)

        // Nothing anywhere in the audible band is turned down.
        var freq = 20.0
        while (freq < 20_000.0) {
            assertTrue("dip of ${at(freq)} dB at $freq Hz", at(freq) > -0.05)
            freq *= 1.05
        }

        // The midrange — where voices and guitars are — is left alone.
        listOf(400.0, 700.0, 1_000.0, 1_500.0).forEach { mid ->
            assertTrue("$mid Hz moved by ${at(mid)} dB", at(mid) < 1.0)
        }

        // And both ends actually rise, or it is just Flat with extra steps.
        assertTrue("sub-bass only rose ${at(35.0)} dB", at(35.0) > 3.0)
        assertTrue("air only rose ${at(14_000.0)} dB", at(14_000.0) > 2.0)
    }

    /** The V-shaped genre presets are meant to be that; this one is not. */
    @Test
    fun `Extended never cuts a band`() {
        assertTrue(EqPresets.presets.getValue("Extended").all { it >= 0 })
    }

    private companion object {
        const val SAMPLE_RATE = 48_000
    }
}
