package com.mero.ui.equalizer

import androidx.compose.foundation.Canvas
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.foundation.background
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.SliderDefaults
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.data.EqPresets
import com.mero.ui.components.MeroChip
import com.mero.ui.components.PreferenceRow
import kotlin.math.roundToInt

private const val BAND_TRACK_DP = 150

@Composable
fun EqualizerScreen(
    enabled: Boolean,
    onEnabledChange: (Boolean) -> Unit,
    preset: String,
    onPresetChange: (String) -> Unit,
    bands: List<Int>,
    onBandChange: (Int, Int) -> Unit,
    spectrumLevels: FloatArray,
    responseDb: FloatArray,
    hapticIntensity: Float,
    onHapticIntensityChange: (Float) -> Unit,
    outputRoute: com.mero.playback.OutputRoute,
    crossfeed: Float,
    onCrossfeedChange: (Float) -> Unit,
    speed: Float,
    onSpeedChange: (Float) -> Unit,
    preamp: Float,
    onPreampChange: (Float) -> Unit,
    toggles: Map<String, Boolean>,
    onToggle: (String, Boolean) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            Text(
                "Equalizer",
                Modifier.weight(1f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
            MeroChip(
                label = if (enabled) "On" else "Off",
                selected = enabled,
                onClick = { onEnabledChange(!enabled) },
                modifier = Modifier.padding(end = 8.dp),
            )
        }

        Column(
            Modifier
                .weight(1f)
                .verticalScroll(rememberScrollState())
                .padding(bottom = contentPadding.calculateBottomPadding()),
        ) {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                EqPresets.presets.keys.forEach { name ->
                    MeroChip(name, name == preset, onClick = { onPresetChange(name) })
                }
            }

            Column(
                Modifier
                    .padding(horizontal = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surfaceContainer)
                    .padding(start = 8.dp, end = 8.dp, top = 16.dp, bottom = 12.dp),
            ) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text("+12 dB", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                    Text(
                        "10-band · in-app DSP",
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                    )
                    Text("−12 dB", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                }
                Box(Modifier.fillMaxWidth()) {
                    // Behind the sliders: what is playing, and what the curve
                    // does to it. The sliders show ten numbers; neither of
                    // these can be read off them.
                    SpectrumAndCurve(
                        levels = spectrumLevels,
                        responseDb = responseDb,
                        modifier = Modifier
                            .matchParentSize()
                            .padding(horizontal = 8.dp),
                    )
                    Row(
                        Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.Bottom,
                    ) {
                        bands.forEachIndexed { index, db ->
                            BandSlider(
                                db = db,
                                hz = EqPresets.bandLabels.getOrElse(index) { "" },
                                onChange = { onBandChange(index, it) },
                            )
                        }
                    }
                }
            }

            Text(
                "Output",
                Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceVariant,
            )

            LabelledSlider("Preamp", "${(preamp * 24 - 12).roundToInt()} dB", preamp, onPreampChange)

            EqSwitch(
                "Loudness normalization",
                "Matches every track to the same perceived level (EBU R128), so a quiet recording does not disappear after a loud one.",
                toggles["norm"] == true,
            ) { onToggle("norm", it) }
            LabelledSlider(
                "Crossfeed",
                if (crossfeed == 0f) "Off" else "${(crossfeed * 100).roundToInt()}%",
                crossfeed,
                onCrossfeedChange,
            )
            Text(
                "Bleeds a little of each channel into the other, dulled and " +
                    "delayed the way your head would do it. Pulls hard-panned " +
                    "mixes out of the middle of your skull on headphones.",
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = scheme.onSurfaceVariant,
            )
            LabelledSlider(
                "Beat haptics",
                if (hapticIntensity == 0f) "Off" else "${(hapticIntensity * 100).roundToInt()}%",
                hapticIntensity,
                onHapticIntensityChange,
            )
            Text(
                "Playback speed",
                Modifier.padding(start = 16.dp, top = 12.dp, bottom = 6.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceVariant,
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                listOf(0.75f, 1f, 1.25f, 1.5f, 2f).forEach { option ->
                    MeroChip(
                        if (option == 1f) "Normal" else "${option}×",
                        option == speed,
                        onClick = { onSpeedChange(option) },
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            EqSwitch(
                "Skip silence",
                "Trims silent passages — ExoPlayer handles this natively",
                toggles["silence"] == true,
            ) { onToggle("silence", it) }
            EqSwitch(
                "Gapless playback",
                "No pause between tracks of the same album",
                toggles["gapless"] == true,
            ) { onToggle("gapless", it) }
            Spacer(Modifier.height(16.dp))
        }
    }
}

/**
 * Vertical −12..+12 dB band. Custom because Material 3 has no vertical slider —
 * everything else on this screen uses the stock control.
 */
@Composable
private fun BandSlider(db: Int, hz: String, onChange: (Int) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    val trackPx = with(LocalDensity.current) { BAND_TRACK_DP.dp.toPx() }
    val fraction = (db + 12) / 24f

    fun setFromY(y: Float) {
        val ratio = 1f - (y / trackPx).coerceIn(0f, 1f)
        onChange((ratio * 24f - 12f).roundToInt().coerceIn(-12, 12))
    }

    Column(
        Modifier.width(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.spacedBy(6.dp),
    ) {
        Text(
            if (db > 0) "+$db" else "$db",
            fontSize = 10.sp,
            color = scheme.primary,
            textAlign = TextAlign.Center,
        )
        Box(
            Modifier
                .width(32.dp)
                .height(BAND_TRACK_DP.dp)
                .pointerInput(Unit) {
                    detectTapGestures { setFromY(it.y) }
                }
                .pointerInput(Unit) {
                    detectDragGestures { change, _ -> setFromY(change.position.y) }
                },
            contentAlignment = Alignment.BottomCenter,
        ) {
            Box(
                Modifier
                    .width(4.dp)
                    .height(BAND_TRACK_DP.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.outlineVariant),
            )
            Box(
                Modifier
                    .width(4.dp)
                    .height((BAND_TRACK_DP * fraction).dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.primary),
            )
            Box(
                Modifier
                    .padding(bottom = (BAND_TRACK_DP * fraction).dp - 7.dp)
                    .size(14.dp)
                    .clip(RoundedCornerShape(7.dp))
                    .background(scheme.primary),
            )
        }
        Text(hz, fontSize = 9.sp, color = scheme.onSurfaceVariant)
    }
}

@Composable
private fun LabelledSlider(label: String, value: String, position: Float, onChange: (Float) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 16.dp, vertical = 4.dp)) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(label, fontSize = 14.sp)
            Text(value, fontSize = 14.sp, color = scheme.onSurfaceVariant)
        }
        Slider(
            value = position,
            onValueChange = onChange,
            colors = SliderDefaults.colors(
                thumbColor = scheme.primary,
                activeTrackColor = scheme.primary,
                inactiveTrackColor = scheme.outlineVariant,
            ),
        )
    }
}

@Composable
private fun EqSwitch(
    label: String,
    subtitle: String,
    checked: Boolean,
    enabled: Boolean = true,
    onChange: (Boolean) -> Unit,
) {
    PreferenceRow(
        icon = null,
        label = label,
        subtitle = subtitle,
        onClick = if (enabled) ({ onChange(!checked) }) else null,
    ) {
        Switch(checked = checked, onCheckedChange = onChange, enabled = enabled)
    }
}

/**
 * The live spectrum, with the equalizer's own response curve over it.
 *
 * Two things the ten sliders cannot say. The bars are what is actually coming
 * out of the player — tapped after the equalizer, so they move when a band
 * moves. The line is the curve those bands add up to, which is not the shape
 * the slider handles trace: neighbouring bands overlap, so two at +6 dB make
 * more than +6 dB between them.
 */
@Composable
private fun SpectrumAndCurve(
    levels: FloatArray,
    responseDb: FloatArray,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val bar = scheme.primary.copy(alpha = 0.13f)
    val line = scheme.primary.copy(alpha = 0.85f)

    Canvas(modifier) {
        val w = size.width
        val h = size.height
        if (w <= 0f || h <= 0f) return@Canvas

        if (levels.isNotEmpty()) {
            val slot = w / levels.size
            val barWidth = slot * 0.34f
            levels.forEachIndexed { index, level ->
                val barHeight = (h * level).coerceIn(0f, h)
                drawRect(
                    color = bar,
                    topLeft = Offset(index * slot + (slot - barWidth) / 2f, h - barHeight),
                    size = Size(barWidth, barHeight),
                )
            }
        }

        if (responseDb.size >= 2) {
            // ±12 dB maps to the full height, matching the scale the sliders
            // and the "+12 dB / −12 dB" labels already use.
            fun y(db: Float) = h / 2f - (db / 12f).coerceIn(-1f, 1f) * (h / 2f)
            val path = Path()
            responseDb.forEachIndexed { index, db ->
                val x = w * index / (responseDb.size - 1).toFloat()
                if (index == 0) path.moveTo(x, y(db)) else path.lineTo(x, y(db))
            }
            drawPath(path, color = line, style = Stroke(width = 2.dp.toPx()))
        }
    }
}
