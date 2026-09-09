package com.mero.data

import com.mero.playback.EqBand
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
}
