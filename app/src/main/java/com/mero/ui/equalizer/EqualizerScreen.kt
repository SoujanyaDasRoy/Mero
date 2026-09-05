package com.mero.ui.equalizer

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
import com.mero.playback.SpatialMode
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
    preamp: Float,
    onPreampChange: (Float) -> Unit,
    booster: Float,
    onBoosterChange: (Float) -> Unit,
    reverb: Float,
    onReverbChange: (Float) -> Unit,
    hapticIntensity: Float,
    onHapticIntensityChange: (Float) -> Unit,
    crossfade: Float,
    onCrossfadeChange: (Float) -> Unit,
    toggles: Map<String, Boolean>,
    onToggle: (String, Boolean) -> Unit,
    spatialMode: SpatialMode = SpatialMode.Off,
    onSpatialModeChange: (SpatialMode) -> Unit = {},
    spatialSupported: Boolean = false,
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
                        "10-band · DynamicsProcessing",
                        fontSize = 11.sp,
                        color = scheme.onSurfaceVariant,
                    )
                    Text("−12 dB", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                }
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

            Text(
                "Output",
                Modifier.padding(start = 16.dp, top = 20.dp, bottom = 4.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceVariant,
            )

            LabelledSlider("Preamp", "${(preamp * 24 - 12).roundToInt()} dB", preamp, onPreampChange)
            LabelledSlider(
                "Sound booster",
                "+${(booster * 12).roundToInt()} dB",
                booster,
                onBoosterChange,
            )
            LabelledSlider(
                "Reverb",
                "${(reverb * 100).roundToInt()}%",
                reverb,
                onReverbChange,
            )
            LabelledSlider(
                "Beat haptics",
                if (hapticIntensity == 0f) "Off" else "${(hapticIntensity * 100).roundToInt()}%",
                hapticIntensity,
                onHapticIntensityChange,
            )
            LabelledSlider(
                "Crossfade",
                if (crossfade <= 0.01f) "Off" else "${(crossfade * 12).roundToInt()}s",
                crossfade,
                onCrossfadeChange,
            )

            EqSwitch(
                "Loudness normalization",
                "Evens out volume between tracks using LoudnessEnhancer",
                toggles["norm"] == true,
            ) { onToggle("norm", it) }
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
            EqSwitch(
                "Spatial audio",
                if (spatialSupported) {
                    "Widens the stereo image. Not Dolby Atmos — see the note below."
                } else {
                    "Not supported on this device's audio output"
                },
                toggles["spatial"] == true && spatialSupported,
                enabled = spatialSupported,
            ) { onToggle("spatial", it) }
            Text(
                "Spatial mode",
                Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceVariant,
            )
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SpatialMode.entries.forEach { mode ->
                    MeroChip(
                        mode.label,
                        mode == spatialMode,
                        onClick = { onSpatialModeChange(mode) },
                    )
                }
            }

            Text(
                "Dolby Atmos needs a licence from Dolby, firmware support from the " +
                    "phone maker, and Atmos-encoded source audio. YouTube serves " +
                    "stereo Opus, so none of those hold here. Spatial audio above is " +
                    "the real equivalent Android exposes.",
                Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = scheme.onSurfaceVariant,
            )

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
