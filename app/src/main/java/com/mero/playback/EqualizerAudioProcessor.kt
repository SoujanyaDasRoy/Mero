package com.mero.playback

import androidx.media3.common.C
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.common.audio.BaseAudioProcessor
import java.nio.ByteBuffer
import kotlin.math.max
import kotlin.math.pow

/**
 * The equalizer, as a stage in ExoPlayer's own audio pipeline.
 *
 * Replaces `android.media.audiofx.DynamicsProcessing`, and the reason is not
 * preference. Attaching effects to the system audio session put the behaviour
 * in the device's hands, and the device kept refusing: the session's effect
 * memory budget rejected the reverb outright (`registerEffect() memory limit
 * exceeded`, status -38), and merely holding an effect chain moved the track
 * off the deep-buffer output (`mismatch between requested flags (00000008) and
 * output flags (00000002)`), which is where the glitching came from. None of
 * it could be tested, either, because those effects only exist against a live
 * session on a device.
 *
 * Here the arithmetic is ours. It is identical on every phone, it cannot be
 * refused, it does not touch the output path, and it is a pure function from
 * one buffer to the next — see BiquadTest and EqualizerAudioProcessorTest.
 */
class EqualizerAudioProcessor : BaseAudioProcessor() {

    /**
     * A whole equalizer setting, swapped in one reference at a time.
     *
     * The audio thread must never see half of a change — bands from before a
     * preset was picked and a preamp from after it — so settings arrive as an
     * immutable value and are adopted between buffers.
     */
    private data class Settings(
        val enabled: Boolean,
        val bandsDb: List<Int>,
        val preampDb: Float,
    ) {
        val isTransparent: Boolean
            get() = !enabled || (bandsDb.all { it == 0 } && preampDb == 0f)
    }

    @Volatile
    private var desired = Settings(enabled = true, bandsDb = List(EqBands.count) { 0 }, preampDb = 0f)

    private var applied: Settings? = null

    /** `[channel][band]`, allocated once per configuration. */
    private var filters: Array<Array<Biquad>> = emptyArray()
    private var gain = 1f

    fun setEnabled(value: Boolean) {
        desired = desired.copy(enabled = value)
    }

    fun setBands(bandsDb: List<Int>) {
        desired = desired.copy(bandsDb = bandsDb.toList())
    }

    fun setPreampDb(value: Float) {
        desired = desired.copy(preampDb = value)
    }

    override fun onConfigure(inputAudioFormat: AudioProcessor.AudioFormat): AudioProcessor.AudioFormat {
        if (inputAudioFormat.encoding != C.ENCODING_PCM_16BIT) {
            throw AudioProcessor.UnhandledAudioFormatException(inputAudioFormat)
        }
        filters = Array(inputAudioFormat.channelCount) { Array(EqBands.count) { Biquad() } }
        applied = null
        return inputAudioFormat
    }

    /**
     * Deliberately always active once configured.
     *
     * Reporting inactive while flat would let the pipeline drop this stage
     * entirely, which is tempting — but the pipeline only re-reads that on a
     * flush or a format change, so the equalizer would then appear dead until
     * the next track. A transparent buffer is copied instead, which costs a
     * memcpy and always responds the moment a slider moves.
     */
    override fun isActive(): Boolean = super.isActive()

    override fun queueInput(inputBuffer: ByteBuffer) {
        // Nothing to do, and not merely an optimisation: the sink hands back
        // this processor's own drained output buffer as an empty input, so
        // without this the transparent path below ends up asking a buffer to
        // copy from itself — `IllegalArgumentException: The source buffer is
        // this buffer`, and playback dies on the first frame.
        if (!inputBuffer.hasRemaining()) return

        val settings = desired
        if (settings !== applied) {
            adopt(settings)
            applied = settings
        }

        val frames = inputBuffer.remaining() / (2 * filters.size)
        val output = replaceOutputBuffer(inputBuffer.remaining())

        if (settings.isTransparent) {
            output.put(inputBuffer)
            output.flip()
            return
        }

        val channels = filters.size
        repeat(frames) {
            for (channel in 0 until channels) {
                var sample = inputBuffer.short / SHORT_SCALE
                val chain = filters[channel]
                for (band in chain) sample = band.process(sample)
                sample *= gain
                output.putShort(
                    (sample.coerceIn(-1f, 1f) * SHORT_SCALE)
                        .toInt()
                        .coerceIn(Short.MIN_VALUE.toInt(), Short.MAX_VALUE.toInt())
                        .toShort(),
                )
            }
        }
        inputBuffer.position(inputBuffer.limit())
        output.flip()
    }

    /**
     * Recomputes coefficients, and the headroom that keeps a boost from
     * clipping.
     *
     * Raising a band raises the summed signal, and anything past full scale
     * clips — audible as crackle, and a real bug in the previous equalizer.
     * The signal is pulled down by however much the curve actually adds, so a
     * boosted setting changes tone without pushing the mix into the ceiling.
     */
    private fun adopt(settings: Settings) {
        val rate = outputAudioFormat.sampleRate
        val coefficients = (0 until EqBands.count).map { index ->
            val db = if (settings.enabled) settings.bandsDb.getOrElse(index) { 0 }.toFloat() else 0f
            peakingEq(EqBands.frequencies[index], db, EqBands.Q, rate)
        }

        // The reserved headroom is the cascade's real peak, not the biggest
        // single band. Neighbouring one-octave bands overlap and sum, so two
        // adjacent boosts exceed either of them on their own.
        val peakDb = max(0f, cascadePeakDb(coefficients, rate))
        gain = 10.0.pow(((settings.preampDb - peakDb) / 20.0)).toFloat()

        for (channel in filters) {
            for (index in 0 until EqBands.count) channel[index].set(coefficients[index])
        }
    }

    override fun onFlush() {
        for (channel in filters) for (band in channel) band.reset()
    }

    override fun onReset() {
        filters = emptyArray()
        applied = null
    }

    private companion object {
        /** 16-bit PCM full scale. */
        const val SHORT_SCALE = 32_768f
    }
}
