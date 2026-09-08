package com.mero.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.PI
import kotlin.math.exp
import kotlin.math.roundToInt

/**
 * Puts a little of each channel into the other, the way a head does.
 *
 * On speakers both ears hear both channels — the far one arriving slightly
 * later and duller, because it has gone around your head. Headphones deliver
 * each channel to exactly one ear, so a hard-panned mix ends up feeling like
 * it is happening inside your skull rather than in front of you. Records from
 * the sixties are the worst of it; whole instruments sit in one ear.
 *
 * This is the Bauer stereophonic-to-binaural idea in its plain form: delay the
 * opposite channel by roughly the time sound takes to travel around a head,
 * dull it with a low-pass because a head absorbs treble, and mix it in. Bass
 * crosses over almost completely, treble barely at all, which is what makes
 * the image move out of the middle of your head without smearing the stereo.
 *
 * Only touches stereo. With one channel there is nothing to cross.
 */
class CrossfeedAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var desired = 0f

    private var applied = -1f
    private var stereo = false

    /** Delay line for each channel, sized for the head-crossing time. */
    private var delayL = FloatArray(0)
    private var delayR = FloatArray(0)
    private var delayIndex = 0
    private var delaySamples = 0

    private var lowpassCoefficient = 0f
    private var lowpassL = 0f
    private var lowpassR = 0f
    private var crossGain = 0f
    private var normalise = 1f

    /** 0f is off — and off means bit-transparent, not merely inaudible. */
    fun setStrength(value: Float) {
        desired = value.coerceIn(0f, 1f)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        stereo = inputAudioFormat.channelCount == 2
        delaySamples = (inputAudioFormat.sampleRate * HEAD_DELAY_SECONDS).roundToInt().coerceAtLeast(1)
        delayL = FloatArray(delaySamples)
        delayR = FloatArray(delaySamples)
        delayIndex = 0

        // One-pole low-pass. The far ear hears treble attenuated by the head,
        // and this is the cheapest shape that does that convincingly.
        lowpassCoefficient = exp(-2.0 * PI * CUTOFF_HZ / inputAudioFormat.sampleRate).toFloat()
        applied = -1f
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return

        val strength = desired
        if (strength != applied) {
            crossGain = strength * MAX_CROSS_GAIN
            // Mixing in a second copy of the signal would otherwise raise the
            // level; this keeps the output where the input was.
            normalise = 1f / (1f + crossGain)
            applied = strength
        }

        val output = replaceOutputBuffer(inputBuffer.remaining())

        if (strength == 0f || !stereo) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        while (inputBuffer.remaining() >= 4) {
            val left = inputBuffer.short / SHORT_SCALE
            val right = inputBuffer.short / SHORT_SCALE

            // What the opposite ear heard a head-width ago.
            val delayedL = delayL[delayIndex]
            val delayedR = delayR[delayIndex]
            delayL[delayIndex] = left
            delayR[delayIndex] = right
            delayIndex = (delayIndex + 1) % delaySamples

            lowpassL = delayedL + lowpassCoefficient * (lowpassL - delayedL)
            lowpassR = delayedR + lowpassCoefficient * (lowpassR - delayedR)

            output.putShort(toPcm((left + crossGain * lowpassR) * normalise))
            output.putShort(toPcm((right + crossGain * lowpassL) * normalise))
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    private fun toPcm(value: Float): Short =
        (value.coerceIn(-1f, 1f) * SHORT_SCALE)
            .toInt()
            .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
            .toShort()

    override fun onFlush() {
        delayL.fill(0f)
        delayR.fill(0f)
        delayIndex = 0
        lowpassL = 0f
        lowpassR = 0f
    }

    override fun onReset() {
        delayL = FloatArray(0)
        delayR = FloatArray(0)
        applied = -1f
    }

    private companion object {
        /** Roughly the time sound takes to travel around a head. */
        const val HEAD_DELAY_SECONDS = 0.00027

        /** Above this the far ear hears very little, so neither does the mix. */
        const val CUTOFF_HZ = 700.0

        /** At full strength the opposite channel arrives about 6 dB down. */
        const val MAX_CROSS_GAIN = 0.5f

        const val SHORT_SCALE = 32_768f
    }
}
