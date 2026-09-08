package com.mero.playback

import android.content.Context
import androidx.media3.common.audio.AudioProcessor
import androidx.media3.exoplayer.DefaultRenderersFactory
import androidx.media3.exoplayer.audio.AudioSink
import androidx.media3.exoplayer.audio.DefaultAudioSink

/**
 * Builds ExoPlayer's audio output with Mero's own processing stages in it.
 *
 * This is the seam that lets the equalizer live inside the player instead of
 * being bolted onto the system audio session. Everything Mero does to the
 * sound now happens between the decoder and the audio track, on samples we
 * own — see [EqualizerAudioProcessor] for why that matters.
 */
class MeroRenderersFactory(
    context: Context,
    private val equalizer: EqualizerAudioProcessor,
) : DefaultRenderersFactory(context) {

    override fun buildAudioSink(
        context: Context,
        enableFloatOutput: Boolean,
        enableAudioTrackPlaybackParams: Boolean,
    ): AudioSink =
        DefaultAudioSink.Builder(context)
            .setAudioProcessors(arrayOf<AudioProcessor>(equalizer))
            // Float output is deliberately off. The equalizer works in 16-bit
            // PCM, and asking the sink for float would only add a conversion
            // either side of it — the arithmetic in between is double
            // precision regardless, which is where the precision that matters
            // actually lives.
            .setEnableFloatOutput(false)
            .setEnableAudioTrackPlaybackParams(enableAudioTrackPlaybackParams)
            .build()
}
