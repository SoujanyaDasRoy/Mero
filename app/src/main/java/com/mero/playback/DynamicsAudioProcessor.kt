package com.mero.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.abs

/**
 * The output stage: measures how loud the track really is, matches it to a
 * target, and catches whatever peaks that produces.
 *
 * These belong together and last. Normalisation decides the level; the limiter
 * is what makes it safe to choose a level at all. Everything upstream reserves
 * headroom by prediction — the equalizer knows its curve's peak, crossfeed
 * normalises for what it mixes in — and prediction has to assume the worst
 * case, so it leaves the whole track permanently quieter to survive a moment
 * that may never arrive. A limiter only intervenes when a peak actually turns
 * up.
 *
 * Replaces the "fixed lift for quiet tracks" that the equalizer screen used to
 * apologise for. A constant gain cannot even out volume between tracks: a
 * compressed pop master and a quiet orchestral recording can peak identically
 * and still be twenty decibels apart in how loud they feel. [LoudnessMeter]
 * measures the difference the way broadcast does.
 */
class DynamicsAudioProcessor : BaseAudioProcessor() {

    @Volatile
    private var normalise = false

    @Volatile
    private var targetLufs = -14.0

    private var meter: LoudnessMeter? = null
    private var limiter: Limiter? = null
    private var channels = 2
    private var frame = FloatArray(2)

    /** Smoothed so a changing measurement does not audibly ride the level. */
    private var appliedGain = 1f

    fun setNormalisation(enabled: Boolean) {
        normalise = enabled
    }

    /** What the current track measures, in LUFS, once enough has played. */
    val measuredLufs: Double? get() = meter?.lufs

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        channels = inputAudioFormat.channelCount
        frame = FloatArray(channels)
        meter = LoudnessMeter(inputAudioFormat.sampleRate)
        limiter = Limiter(inputAudioFormat.sampleRate)
        appliedGain = 1f
        return inputAudioFormat
    }

    override fun queueInput(inputBuffer: ByteBuffer) {
        if (!inputBuffer.hasRemaining()) return
        val meter = meter ?: return
        val limiter = limiter ?: return

        val output = replaceOutputBuffer(inputBuffer.remaining())
        val wantsNormalisation = normalise

        while (inputBuffer.remaining() >= 2 * channels) {
            var peak = 0f
            for (channel in 0 until channels) {
                val sample = inputBuffer.short / SHORT_SCALE
                frame[channel] = sample
                val magnitude = abs(sample)
                if (magnitude > peak) peak = magnitude
            }

            // Measured from the signal as it arrives — before our own gain, or
            // the meter would be reading its own output and chase itself.
            meter.accept(frame, channels)

            if (wantsNormalisation) {
                val wanted = normalisationGain(meter.lufs, targetLufs)
                appliedGain += (wanted - appliedGain) * GAIN_GLIDE
            } else {
                appliedGain += (1f - appliedGain) * GAIN_GLIDE
            }

            val limiterGain = limiter.gainFor(peak * appliedGain)
            val gain = appliedGain * limiterGain

            for (channel in 0 until channels) {
                output.putShort(
                    (frame[channel] * gain).coerceIn(-1f, 1f)
                        .times(SHORT_SCALE)
                        .toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort(),
                )
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    override fun onFlush() {
        // A new track is a new measurement. Carrying the last one over would
        // apply the previous track's correction to this one.
        meter?.reset()
        limiter?.reset()
        appliedGain = 1f
    }

    override fun onReset() {
        meter = null
        limiter = null
    }

    private companion object {
        const val SHORT_SCALE = 32_768f

        /** Slow enough that the correction settling is never heard moving. */
        const val GAIN_GLIDE = 0.00002f
    }
}
