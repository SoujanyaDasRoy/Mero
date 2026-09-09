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
    bands: List<com.mero.playback.EqBand>,
    onBandChange: (Int, com.mero.playback.EqBand) -> Unit,
    selectedBand: Int,
    onSelectBand: (Int) -> Unit,
    onResetBands: () -> Unit,
    spectrumLevels: FloatArray,
    responseDb: FloatArray,
    hapticIntensity: Float,
    onHapticIntensityChange: (Float) -> Unit,
    outputRoute: com.mero.playback.OutputRoute,
    detectedRoute: com.mero.playback.OutputRoute,
    routeOverride: com.mero.playback.OutputRoute?,
    onRouteOverrideChange: (com.mero.playback.OutputRoute?) -> Unit,
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
            // Which curve is being edited, and which one is in use.
            //
            // Settings are kept per output, and this screen was given the
            // route but never showed it — so a curve dialled in on earbuds was
            // silently the curve the car got too, with nothing on screen
            // saying so. A car stereo is a Bluetooth device like any other as
            // far as Android is concerned, so it can only be told apart by
            // being told.
            Text(
                "PROFILE",
                Modifier.padding(start = 16.dp, bottom = 8.dp),
                fontSize = 11.sp,
                letterSpacing = 0.6.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.primary,
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                MeroChip(
                    label = "Auto",
                    selected = routeOverride == null,
                    onClick = { onRouteOverrideChange(null) },
                )
                com.mero.playback.OutputRoute.entries.forEach { option ->
                    MeroChip(
                        label = option.label,
                        selected = routeOverride == option,
                        onClick = { onRouteOverrideChange(option) },
                    )
                }
            }
            Text(
                if (routeOverride == null) {
                    "Following the output — currently " + detectedRoute.label.lowercase() +
                        ". Editing the " + outputRoute.label.lowercase() + " curve."
                } else {
                    "Pinned to " + outputRoute.label.lowercase() +
                        ". Cars connect the same way earbuds do, so Mero cannot " +
                        "tell them apart on its own."
                },
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = scheme.onSurfaceVariant,
            )

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
                EqCurve(
                    bands = bands,
                    spectrum = spectrumLevels,
                    selectedIndex = selectedBand,
                    onSelect = onSelectBand,
                    onBandChange = onBandChange,
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(220.dp),
                )
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(top = 6.dp, start = 4.dp, end = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    listOf("20", "100", "500", "2k", "8k", "20k").forEach {
                        Text(it, fontSize = 10.sp, color = scheme.onSurfaceVariant)
                    }
                }
            }

            // The selected band, in numbers. Dragging sets gain and frequency;
            // width has no natural gesture on a curve, so it gets a slider.
            bands.getOrNull(selectedBand)?.let { band ->
                Text(
                    "Band ${selectedBand + 1}",
                    Modifier.padding(start = 16.dp, top = 18.dp, bottom = 2.dp),
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    "${formatHz(band.frequencyHz)}   ${formatDb(band.gainDb)}   Q ${
                        (band.q * 100).roundToInt() / 100f
                    }",
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 6.dp),
                    fontSize = 20.sp,
                    fontWeight = FontWeight.SemiBold,
                )
                LabelledSlider(
                    "Width",
                    if (band.q < 1f) "Wide" else if (band.q > 3f) "Narrow" else "Medium",
                    ((band.q - com.mero.playback.EqBand.MIN_Q) /
                        (com.mero.playback.EqBand.MAX_Q - com.mero.playback.EqBand.MIN_Q)),
                ) { fraction ->
                    onBandChange(
                        selectedBand,
                        band.copy(
                            q = com.mero.playback.EqBand.MIN_Q +
                                fraction * (com.mero.playback.EqBand.MAX_Q - com.mero.playback.EqBand.MIN_Q),
                        ),
                    )
                }
                Row(
                    Modifier.padding(horizontal = 16.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    MeroChip("Reset all bands", selected = false, onClick = onResetBands)
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

/** 1 kHz rather than 1000 Hz — the way anyone adjusting an equalizer reads it. */
private fun formatHz(hz: Float): String =
    if (hz >= 1_000f) {
        val k = hz / 1_000f
        if (k >= 10f) "${k.roundToInt()} kHz" else "${(k * 10).roundToInt() / 10f} kHz"
    } else {
        "${hz.roundToInt()} Hz"
    }

private fun formatDb(db: Float): String {
    val rounded = (db * 10).roundToInt() / 10f
    return if (rounded > 0) "+$rounded dB" else "$rounded dB"
}
