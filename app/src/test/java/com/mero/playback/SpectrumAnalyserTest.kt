package com.mero.playback

import androidx.media3.common.C
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.sin

/**
 * The spectrum, checked against tones whose answer is known.
 *
 * An analyser that is merely "lively" is easy to write and impossible to
 * trust — the first version pinned every bar to full height because
 * JTransforms does not normalise its output, and it looked plausible while
 * being wrong. A sine at a band's centre is the only way to tell.
 */
class SpectrumAnalyserTest {

    private val sampleRate = 48_000
    private val channels = 2

    private fun analyser() = SpectrumAnalyser().apply {
        listening = true
        flush(sampleRate, channels, C.ENCODING_PCM_16BIT)
    }

    /** Enough frames to fill the analyser's window several times over. */
    private fun feed(a: SpectrumAnalyser, freq: Double, amplitude: Double, frames: Int = 8_192) {
        val buffer = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val v = (sin(2.0 * PI * freq * i / sampleRate) * amplitude * 32_767).toInt().toShort()
            repeat(channels) { buffer.putShort(v) }
        }
        buffer.flip()
        a.handleBuffer(buffer)
    }

    @Test
    fun `silence reads as nothing`() {
        val a = analyser()
        feed(a, freq = 1_000.0, amplitude = 0.0)
        for (level in a.bands.value) assertEquals(0f, level, 1e-3f)
    }

    @Test
    fun `a tone lights its own band loudest`() {
        val a = analyser()
        feed(a, freq = 1_000.0, amplitude = 0.5)
        val levels = a.bands.value
        val loudest = levels.indices.maxByOrNull { levels[it] }
        // Band 5 is 1 kHz.
        assertEquals("levels were ${levels.toList()}", 5, loudest)
    }

    @Test
    fun `a bass tone lights a low band, not a high one`() {
        val a = analyser()
        feed(a, freq = 62.0, amplitude = 0.5)
        val levels = a.bands.value
        assertTrue("62 Hz should read low bands: ${levels.toList()}", levels[1] > levels[8])
    }

    /**
     * The one that caught the missing normalisation: a full-scale tone must
     * approach the top of the scale, and a quiet one must not.
     */
    @Test
    fun `level tracks amplitude instead of pinning to the ceiling`() {
        val loud = analyser().also { feed(it, 1_000.0, amplitude = 0.9) }.bands.value[5]
        val quiet = analyser().also { feed(it, 1_000.0, amplitude = 0.02) }.bands.value[5]

        assertTrue("a loud tone should read high, got $loud", loud > 0.7f)
        assertTrue("a quiet tone should read low, got $quiet", quiet < 0.5f)
        assertTrue("loud ($loud) must exceed quiet ($quiet)", loud > quiet + 0.2f)
    }

    @Test
    fun `nothing is reported while not listening`() {
        val a = SpectrumAnalyser().apply { flush(sampleRate, channels, C.ENCODING_PCM_16BIT) }
        feed(a, 1_000.0, amplitude = 0.9)
        for (level in a.bands.value) assertEquals(0f, level, 1e-6f)
    }
}
