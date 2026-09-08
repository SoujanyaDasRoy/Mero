package com.mero.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.abs
import kotlin.math.log10
import kotlin.math.sin

/**
 * The equalizer stage, driven the way ExoPlayer drives it.
 *
 * Buffers in, buffers out — no device, no audio session. The equalizer this
 * replaces could not be tested at all.
 */
class EqualizerAudioProcessorTest {

    private val sampleRate = 48_000
    private val channels = 2

    private fun processor(configure: EqualizerAudioProcessor.() -> Unit = {}) =
        EqualizerAudioProcessor().apply {
            configure()
            configure(AudioProcessor.AudioFormat(sampleRate, channels, C.ENCODING_PCM_16BIT))
            flush()
        }

    /** Interleaved stereo sine, both channels identical. */
    private fun sine(freq: Double, frames: Int, amplitude: Double = 0.5): ByteBuffer {
        val buffer = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val value = (sin(2.0 * PI * freq * i / sampleRate) * amplitude * 32_767).toInt().toShort()
            repeat(channels) { buffer.putShort(value) }
        }
        buffer.flip()
        return buffer
    }

    /** Loudest sample in the second half, after the filter transient settles. */
    private fun peakOfTail(out: ByteBuffer, frames: Int): Double {
        var peak = 0.0
        var frame = 0
        while (out.remaining() >= 2 * channels) {
            repeat(channels) {
                val sample = out.short / 32_768.0
                if (frame > frames / 2) peak = maxOf(peak, abs(sample))
            }
            frame++
        }
        return peak
    }

    private fun run(p: EqualizerAudioProcessor, input: ByteBuffer): ByteBuffer {
        p.queueInput(input)
        return p.output
    }

    @Test
    fun `a flat equalizer returns the samples untouched`() {
        val p = processor()
        val frames = 2_048
        val input = sine(440.0, frames)
        // duplicate() resets byte order to big-endian, so it has to be set again.
        val expected = input.duplicate().order(ByteOrder.nativeOrder())
        val out = run(p, input)

        var compared = 0
        while (out.remaining() >= 2) {
            assertEquals("sample $compared", expected.short, out.short)
            compared++
        }
        assertEquals(frames * channels, compared)
    }

    @Test
    fun `a disabled equalizer is transparent even with bands set`() {
        val p = processor {
            setBands(applyGains(defaultBands(), List(EqBands.count) { 12 }))
            setEnabled(false)
        }
        val input = sine(1_000.0, 1_024)
        // duplicate() resets byte order to big-endian, so it has to be set again.
        val expected = input.duplicate().order(ByteOrder.nativeOrder())
        val out = run(p, input)
        while (out.remaining() >= 2) assertEquals(expected.short, out.short)
    }

    @Test
    fun `boosting a band lifts a tone sitting in it`() {
        // Band 5 is 1 kHz. +6 dB there, everything else flat.
        val bands = MutableList(EqBands.count) { 0 }.also { it[5] = 6 }
        val frames = sampleRate / 2

        val flatPeak = peakOfTail(run(processor(), sine(1_000.0, frames)), frames)
        val boostedPeak = peakOfTail(
            run(processor { setBands(applyGains(defaultBands(), bands)) }, sine(1_000.0, frames)),
            frames,
        )

        val liftDb = 20 * log10(boostedPeak / flatPeak)
        // Headroom pulls the whole signal down by the largest boost, so the
        // band returns to roughly where it started rather than gaining 6 dB —
        // that is the point of it, and it is what stops a boost clipping.
        assertEquals(0.0, liftDb, 1.0)
        assertTrue("boosted output should not clip", boostedPeak <= 1.0)
    }

    @Test
    fun `boosting one band leaves a distant tone quieter, by the headroom`() {
        val bands = MutableList(EqBands.count) { 0 }.also { it[0] = 12 } // 31 Hz
        val frames = sampleRate / 2
        val flat = peakOfTail(run(processor(), sine(4_000.0, frames)), frames)
        val boosted = peakOfTail(run(processor { setBands(applyGains(defaultBands(), bands)) }, sine(4_000.0, frames)), frames)
        // 4 kHz is untouched by a 31 Hz band, so it only sees the -12 dB of
        // headroom the boost reserved.
        assertEquals(-12.0, 20 * log10(boosted / flat), 1.0)
    }

    @Test
    fun `preamp scales the output`() {
        val frames = sampleRate / 2
        val flat = peakOfTail(run(processor(), sine(1_000.0, frames, amplitude = 0.25)), frames)
        val quieter = peakOfTail(
            run(processor { setPreampDb(-6f) }, sine(1_000.0, frames, amplitude = 0.25)),
            frames,
        )
        assertEquals(-6.0, 20 * log10(quieter / flat), 0.2)
    }

    @Test
    fun `channels are filtered independently`() {
        val p = processor { setBands(applyGains(defaultBands(), MutableList(EqBands.count) { 0 }.also { it[5] = 10 })) }
        val frames = 4_096
        // Left carries a tone, right is silent.
        val input = ByteBuffer.allocate(frames * channels * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            input.putShort((sin(2.0 * PI * 1_000.0 * i / sampleRate) * 16_000).toInt().toShort())
            input.putShort(0)
        }
        input.flip()

        val out = run(p, input)
        var rightEnergy = 0L
        while (out.remaining() >= 4) {
            out.short // left
            rightEnergy += abs(out.short.toInt()).toLong()
        }
        assertEquals("a silent channel must stay silent", 0L, rightEnergy)
    }

    @Test
    fun `a loud signal with every band boosted does not clip`() {
        val p = processor { setBands(applyGains(defaultBands(), List(EqBands.count) { 12 })) }
        val frames = sampleRate / 4
        val out = run(p, sine(1_000.0, frames, amplitude = 0.99))
        var clipped = 0
        while (out.remaining() >= 2) {
            val s = out.short.toInt()
            if (s == Short.MAX_VALUE.toInt() || s == Short.MIN_VALUE.toInt()) clipped++
        }
        assertEquals("samples pinned to full scale", 0, clipped)
    }

    @Test
    fun `rejects formats it cannot process`() {
        val p = EqualizerAudioProcessor()
        try {
            p.configure(AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_FLOAT))
            throw AssertionError("expected float PCM to be refused")
        } catch (expected: AudioProcessor.UnhandledAudioFormatException) {
            // The pipeline inserts a converter when a stage refuses a format,
            // so refusing is how this stage asks for 16-bit.
        }
    }
}
