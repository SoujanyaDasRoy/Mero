package com.mero.playback

import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.audio.TeeAudioProcessor
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import org.jtransforms.fft.FloatFFT_1D
import java.nio.ByteBuffer
import java.nio.ByteOrder
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.log10
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/**
 * Reads the audio as it plays and reports what it looks like, band by band.
 *
 * Taps ExoPlayer's own pipeline through [TeeAudioProcessor], which matters for
 * a reason beyond tidiness: the obvious way to do this on Android is
 * `android.media.audiofx.Visualizer`, and that requires `RECORD_AUDIO`. A music
 * player asking for the microphone to draw a graph is a bad trade, and it is
 * why the beat haptics that used to exist here were deleted rather than fixed.
 * Inside our own pipeline the samples are simply ours.
 *
 * The output is [EqBands.count] magnitudes in dB, one per equalizer band, so
 * the spectrum lines up with the sliders drawn over it.
 */
class SpectrumAnalyser : TeeAudioProcessor.AudioBufferSink {

    private val _bands = MutableStateFlow(FloatArray(EqBands.count))

    /** Normalised 0..1 per band, already smoothed for drawing. */
    val bands: StateFlow<FloatArray> = _bands.asStateFlow()

    @Volatile
    var listening: Boolean = false

    private var sampleRate = 44_100
    private var channelCount = 2

    private val fft = FloatFFT_1D(FFT_SIZE.toLong())
    private val window = FloatArray(FFT_SIZE) {
        // Hann: the cheapest window that stops a tone smearing across every
        // bin, which without one makes the whole display twitch as one.
        0.5f * (1f - cos(2.0 * PI * it / (FFT_SIZE - 1)).toFloat())
    }
    private val samples = FloatArray(FFT_SIZE)
    private var filled = 0
    private val smoothed = FloatArray(EqBands.count)

    override fun flush(sampleRateHz: Int, channelCount: Int, encoding: Int) {
        sampleRate = sampleRateHz
        this.channelCount = max(1, channelCount)
        filled = 0
        smoothed.fill(0f)
    }

    override fun handleBuffer(buffer: ByteBuffer) {
        if (!listening) return
        val view = buffer.duplicate().order(ByteOrder.nativeOrder())
        while (view.remaining() >= 2 * channelCount) {
            // Mono sum: a spectrum of the stereo image is not a thing anyone
            // reads, and it halves the work.
            var frame = 0f
            repeat(channelCount) { frame += view.short / 32_768f }
            samples[filled++] = frame / channelCount
            if (filled == FFT_SIZE) {
                analyse()
                filled = 0
            }
        }
    }

    private fun analyse() {
        val spectrum = FloatArray(FFT_SIZE)
        for (i in 0 until FFT_SIZE) spectrum[i] = samples[i] * window[i]
        fft.realForward(spectrum)

        val binHz = sampleRate.toFloat() / FFT_SIZE
        val out = FloatArray(EqBands.count)

        for (band in 0 until EqBands.count) {
            val centre = EqBands.frequencies[band]
            // One octave either side of centre, matching the filters' width.
            val low = centre / 1.414f
            val high = centre * 1.414f
            val first = max(1, (low / binHz).toInt())
            val last = min(FFT_SIZE / 2 - 1, (high / binHz).toInt())
            if (first > last) continue

            var sum = 0f
            for (bin in first..last) {
                val re = spectrum[2 * bin]
                val im = spectrum[2 * bin + 1]
                sum += re * re + im * im
            }
            // Normalised before it means anything in dB. JTransforms returns
            // an unscaled transform, so bin magnitudes grow with the window
            // size — a full-scale sine peaks near N/4 once the Hann window's
            // coherent gain of 0.5 is accounted for, not near 1. Without this
            // every bar sat pinned at the ceiling and the display looked
            // convincing while carrying no information at all.
            val rms = sqrt(sum / (last - first + 1)) / BIN_SCALE
            val db = 20f * log10(max(rms, 1e-7f))
            // Map a useful window of level onto 0..1 for drawing.
            out[band] = ((db - FLOOR_DB) / (CEILING_DB - FLOOR_DB)).coerceIn(0f, 1f)
        }

        for (i in out.indices) {
            // Rises quickly, falls slowly — bars that track transients without
            // flickering, the way every meter worth reading behaves.
            smoothed[i] = if (out[i] > smoothed[i]) {
                out[i]
            } else {
                smoothed[i] * DECAY + out[i] * (1f - DECAY)
            }
        }
        _bands.value = smoothed.copyOf()
    }

    /** Installed in the player ahead of the equalizer. */
    fun asProcessor(): AudioProcessor = TeeAudioProcessor(this)

    private companion object {
        /** ~23 ms at 44.1 kHz: fine enough for 31 Hz, fast enough to look live. */
        const val FFT_SIZE = 1024
        const val FLOOR_DB = -70f
        const val CEILING_DB = -10f
        const val DECAY = 0.82f

        /** Full-scale bin magnitude: N/2 from the transform, halved by Hann. */
        const val BIN_SCALE = FFT_SIZE / 4f
    }
}
