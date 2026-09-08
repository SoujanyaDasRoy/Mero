package com.mero.ui.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.unit.dp
import com.mero.playback.EqBand
import com.mero.playback.equalizerResponseDb
import com.mero.playback.frequencyToFraction
import kotlin.math.abs
import kotlin.math.roundToInt

/**
 * The equalizer, as one thing you touch instead of ten.
 *
 * Ten vertical sliders are a picture of the control surface, not of the sound.
 * They cannot show what the curve actually does — neighbouring bands overlap,
 * so two at +6 dB make more than +6 dB between them — and they leave no room
 * for a band to move anywhere but up and down.
 *
 * Here the curve is the control. Each band is a handle on it: drag up and down
 * for gain, left and right for frequency. The spectrum of what is playing sits
 * behind, so a boost can be aimed at something audible rather than guessed.
 */
@Composable
fun EqCurve(
    bands: List<EqBand>,
    spectrum: FloatArray,
    selectedIndex: Int,
    onSelect: (Int) -> Unit,
    onBandChange: (Int, EqBand) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val density = LocalDensity.current

    BoxWithConstraints(
        modifier
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.surfaceContainer),
    ) {
        val widthPx = with(density) { maxWidth.toPx() }
        val heightPx = with(density) { maxHeight.toPx() }

        var dragging by remember { mutableIntStateOf(-1) }

        fun bandCentre(band: EqBand): Offset = Offset(
            x = frequencyToFraction(band.frequencyHz) * widthPx,
            y = heightPx / 2f - (band.gainDb / EqBand.MAX_GAIN_DB) * (heightPx / 2f - HANDLE_INSET_PX),
        )

        fun nearest(position: Offset): Int {
            var best = -1
            var bestDistance = Float.MAX_VALUE
            bands.forEachIndexed { index, band ->
                val distance = (bandCentre(band) - position).getDistance()
                if (distance < bestDistance) {
                    bestDistance = distance
                    best = index
                }
            }
            return if (bestDistance < TOUCH_SLOP_PX) best else -1
        }

        Canvas(
            Modifier
                .fillMaxSize()
                .pointerInput(bands, widthPx, heightPx) {
                    detectTapGestures { position -> nearest(position).takeIf { it >= 0 }?.let(onSelect) }
                }
                .pointerInput(bands, widthPx, heightPx) {
                    detectDragGestures(
                        onDragStart = { position ->
                            dragging = nearest(position)
                            if (dragging >= 0) onSelect(dragging)
                        },
                        onDragEnd = { dragging = -1 },
                        onDragCancel = { dragging = -1 },
                    ) { change, _ ->
                        val index = dragging
                        if (index < 0) return@detectDragGestures
                        change.consume()

                        val band = bands[index]
                        // Vertical is gain, horizontal is frequency: the two
                        // things a band has, on the two axes it is drawn on.
                        val gain = ((heightPx / 2f - change.position.y) /
                            (heightPx / 2f - HANDLE_INSET_PX) * EqBand.MAX_GAIN_DB)
                            .coerceIn(EqBand.MIN_GAIN_DB, EqBand.MAX_GAIN_DB)
                        val frequency = com.mero.playback.fractionToFrequency(
                            (change.position.x / widthPx).coerceIn(0f, 1f),
                        )
                        onBandChange(
                            index,
                            band.copy(
                                gainDb = (gain * 2).roundToInt() / 2f,
                                frequencyHz = frequency,
                            ),
                        )
                    }
                },
        ) {
            drawGrid(scheme.outlineVariant.copy(alpha = 0.35f))
            drawSpectrum(spectrum, scheme.primary.copy(alpha = 0.22f))
            drawCurve(bands, scheme.primary)
            bands.forEachIndexed { index, band ->
                val centre = bandCentre(band)
                val selected = index == selectedIndex
                drawCircle(
                    color = if (selected) scheme.primary else scheme.primary.copy(alpha = 0.55f),
                    radius = if (selected) 11.dp.toPx() else 7.dp.toPx(),
                    center = centre,
                )
                if (selected) {
                    drawCircle(
                        color = scheme.onPrimary,
                        radius = 4.dp.toPx(),
                        center = centre,
                    )
                }
            }
        }
    }
}

/** Horizontal 0 dB line plus the ±6 dB guides. */
private fun DrawScope.drawGrid(color: Color) {
    val mid = size.height / 2f
    listOf(0f, 0.5f, -0.5f).forEach { fraction ->
        val y = mid - fraction * (mid - HANDLE_INSET_PX)
        drawLine(
            color = if (fraction == 0f) color else color.copy(alpha = color.alpha * 0.5f),
            start = Offset(0f, y),
            end = Offset(size.width, y),
            strokeWidth = 1f,
        )
    }
}

private fun DrawScope.drawSpectrum(levels: FloatArray, color: Color) {
    if (levels.isEmpty()) return
    val slot = size.width / levels.size
    // Half the panel at most. The curve is the subject here; the spectrum is
    // context for aiming it, and full-height bars simply hid the curve.
    val ceiling = size.height * 0.5f
    levels.forEachIndexed { index, level ->
        val barHeight = (ceiling * level).coerceIn(0f, ceiling)
        drawRect(
            color = color,
            topLeft = Offset(index * slot + slot * 0.2f, size.height - barHeight),
            size = Size(slot * 0.6f, barHeight),
        )
    }
}

private fun DrawScope.drawCurve(bands: List<EqBand>, color: Color) {
    val response = equalizerResponseDb(bands, sampleRate = 48_000, points = 160)
    if (response.size < 2) return
    val mid = size.height / 2f
    val path = Path()
    response.forEachIndexed { index, db ->
        val x = size.width * index / (response.size - 1).toFloat()
        val y = mid - (db / EqBand.MAX_GAIN_DB).coerceIn(-1f, 1f) * (mid - HANDLE_INSET_PX)
        if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
    }
    drawPath(path, color = color, style = Stroke(width = 3f))
}

/** Keeps a handle at maximum gain from being clipped by the panel edge. */
private const val HANDLE_INSET_PX = 22f
private const val TOUCH_SLOP_PX = 110f
