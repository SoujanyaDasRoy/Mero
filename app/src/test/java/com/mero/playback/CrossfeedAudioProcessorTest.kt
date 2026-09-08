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
import kotlin.math.sin

/**
 * Crossfeed, checked against what it is for.
 *
 * On speakers each ear hears both channels, slightly delayed and dulled by the
 * head. Headphones deliver each channel to one ear only, which is why a
 * hard-panned mix can feel like it is happening inside your skull. Crossfeed
 * puts a little of each channel into the other — low frequencies most, highs
 * least, because that is what a head actually does to sound.
 *
 * So the tests are: does bass cross over, do highs stay put, and is mono left
 * alone.
 */
class CrossfeedAudioProcessorTest {

    private val sampleRate = 48_000

    private fun processor(strength: Float) = CrossfeedAudioProcessor().apply {
        setStrength(strength)
        configure(AudioProcessor.AudioFormat(sampleRate, 2, C.ENCODING_PCM_16BIT))
        flush()
    }

    /** A tone in one channel only. */
    private fun panned(freq: Double, frames: Int, left: Boolean): ByteBuffer {
        val b = ByteBuffer.allocate(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val v = (sin(2.0 * PI * freq * i / sampleRate) * 0.5 * 32_767).toInt().toShort()
            b.putShort(if (left) v else 0)
            b.putShort(if (left) 0 else v)
        }
        b.flip()
        return b
    }

    private fun mono(freq: Double, frames: Int): ByteBuffer {
        val b = ByteBuffer.allocate(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val v = (sin(2.0 * PI * freq * i / sampleRate) * 0.5 * 32_767).toInt().toShort()
            b.putShort(v); b.putShort(v)
        }
        b.flip()
        return b
    }

    /** Peak level of each channel over the settled tail. */
    private fun peaks(out: ByteBuffer, frames: Int): Pair<Double, Double> {
        var l = 0.0
        var r = 0.0
        var i = 0
        while (out.remaining() >= 4) {
            val lv = out.short / 32_768.0
            val rv = out.short / 32_768.0
            if (i > frames / 2) {
                l = maxOf(l, abs(lv))
                r = maxOf(r, abs(rv))
            }
            i++
        }
        return l to r
    }

    private fun run(p: CrossfeedAudioProcessor, input: ByteBuffer): ByteBuffer {
        p.queueInput(input)
        return p.output
    }

    @Test
    fun `off is bit-transparent`() {
        val p = processor(0f)
        val frames = 2_048
        val input = panned(600.0, frames, left = true)
        val expected = input.duplicate().order(ByteOrder.nativeOrder())
        val out = run(p, input)
        while (out.remaining() >= 2) assertEquals(expected.short, out.short)
    }

    @Test
    fun `bass crosses into the other ear`() {
        val frames = sampleRate / 2
        val (l, r) = peaks(run(processor(1f), panned(120.0, frames, left = true)), frames)
        assertTrue("left should still carry the tone, got $l", l > 0.1)
        assertTrue("bass should reach the right channel, got $r", r > 0.1 * l)
    }

    @Test
    fun `treble mostly stays where it was panned`() {
        val frames = sampleRate / 2
        val (l, r) = peaks(run(processor(1f), panned(8_000.0, frames, left = true)), frames)
        assertTrue("treble should stay left: left $l, right $r", r < 0.25 * l)
    }

    @Test
    fun `bass crosses more than treble does`() {
        val frames = sampleRate / 2
        val (bl, br) = peaks(run(processor(1f), panned(120.0, frames, left = true)), frames)
        val (tl, tr) = peaks(run(processor(1f), panned(8_000.0, frames, left = true)), frames)
        val bassRatio = br / bl
        val trebleRatio = tr / tl
        assertTrue(
            "bass should cross more than treble: bass $bassRatio, treble $trebleRatio",
            bassRatio > trebleRatio * 2,
        )
    }

    @Test
    fun `a mono signal stays identical in both channels`() {
        val frames = 8_192
        val out = run(processor(1f), mono(500.0, frames))
        var checked = 0
        while (out.remaining() >= 4) {
            val l = out.short
            val r = out.short
            assertEquals("frame $checked", l, r)
            checked++
        }
        assertTrue(checked > 0)
    }

    @Test
    fun `a full-scale signal does not clip`() {
        val frames = sampleRate / 4
        val b = ByteBuffer.allocate(frames * 4).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            val v = (sin(2.0 * PI * 200.0 * i / sampleRate) * 0.99 * 32_767).toInt().toShort()
            b.putShort(v); b.putShort((-v).toInt().toShort())
        }
        b.flip()
        val out = run(processor(1f), b)
        var clipped = 0
        while (out.remaining() >= 2) {
            val s = out.short.toInt()
            if (s == Short.MAX_VALUE.toInt() || s == Short.MIN_VALUE.toInt()) clipped++
        }
        assertEquals("samples pinned to full scale", 0, clipped)
    }

    @Test
    fun `mono playback is left alone`() {
        // Nothing to cross over with one channel, so it must pass through.
        val p = CrossfeedAudioProcessor().apply {
            setStrength(1f)
            configure(AudioProcessor.AudioFormat(sampleRate, 1, C.ENCODING_PCM_16BIT))
            flush()
        }
        val frames = 1_024
        val b = ByteBuffer.allocate(frames * 2).order(ByteOrder.nativeOrder())
        for (i in 0 until frames) {
            b.putShort((sin(2.0 * PI * 300.0 * i / sampleRate) * 0.5 * 32_767).toInt().toShort())
        }
        b.flip()
        val expected = b.duplicate().order(ByteOrder.nativeOrder())
        val out = run(p, b)
        while (out.remaining() >= 2) assertEquals(expected.short, out.short)
    }
}
