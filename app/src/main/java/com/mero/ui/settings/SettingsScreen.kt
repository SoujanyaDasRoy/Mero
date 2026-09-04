package com.mero.ui.settings

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.rememberScrollState
import com.mero.ui.components.GroupHeader
import com.mero.ui.components.MeroChip
import com.mero.ui.components.PreferenceRow
import com.mero.ui.player.PlayerVariant
import com.mero.ui.theme.MeroAccent

@Composable
fun SettingsScreen(
    accent: MeroAccent,
    onAccentChange: (MeroAccent) -> Unit,
    toggles: Map<String, Boolean>,
    onToggle: (String, Boolean) -> Unit,
    onEqualizerClick: () -> Unit,
    onSleepTimerClick: () -> Unit,
    sleepSummary: String,
    onClearCache: () -> Unit,
    playerVariant: PlayerVariant,
    onPlayerVariantChange: (PlayerVariant) -> Unit,
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
            Text("Settings", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        ) {
            item {
                GroupHeader("ACCENT COLOUR")
                PreferenceRow(
                    icon = Icons.Rounded.FormatPaint,
                    label = "Accent",
                    subtitle = "${accent.label} · overridden when Material You is on",
                )
                Row(
                    Modifier.padding(start = 54.dp, top = 12.dp, bottom = 18.dp),
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    MeroAccent.entries.forEach { option ->
                        val selected = option == accent
                        Box(
                            Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .border(
                                    width = 2.dp,
                                    color = if (selected) scheme.primary else Color.Transparent,
                                    shape = CircleShape,
                                )
                                .clickable { onAccentChange(option) },
                            contentAlignment = Alignment.Center,
                        ) {
                            Box(
                                Modifier
                                    .size(32.dp)
                                    .clip(CircleShape)
                                    .background(option.swatch),
                                contentAlignment = Alignment.Center,
                            ) {
                                if (selected) {
                                    Icon(
                                        Icons.Rounded.Check,
                                        contentDescription = "${option.label} selected",
                                        tint = Color.Black.copy(alpha = 0.7f),
                                        modifier = Modifier.size(20.dp),
                                    )
                                }
                            }
                        }
                    }
                }
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp),
                    color = scheme.outlineVariant,
                )
            }

            item {
                GroupHeader("APPEARANCE")
                PreferenceRow(
                    Icons.Rounded.Palette,
                    "Material You",
                    "Take colours from the wallpaper (Android 12+)",
                ) {
                    Switch(
                        checked = toggles["dynamic"] == true,
                        onCheckedChange = { onToggle("dynamic", it) },
                    )
                }
                PreferenceRow(
                    Icons.Rounded.Contrast,
                    "Pure black",
                    "Real battery saving on the AMOLED panels everyone here has",
                ) {
                    Switch(
                        checked = toggles["amoled"] == true,
                        onCheckedChange = { onToggle("amoled", it) },
                    )
                }
                // The design ships three Now Playing directions and no decision.
                // Exposing the switch here is how that decision gets made — by
                // living with each one — rather than by picking from a mockup.
                PreferenceRow(
                    Icons.Rounded.Smartphone,
                    "Now Playing layout",
                    "Three directions from the design — try each",
                )
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 54.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    PlayerVariant.entries.forEach { option ->
                        MeroChip(
                            label = option.label,
                            selected = option == playerVariant,
                            onClick = { onPlayerVariantChange(option) },
                        )
                    }
                }
                HorizontalDivider(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    color = scheme.outlineVariant,
                )
            }

            item {
                GroupHeader("AUDIO")
                PreferenceRow(
                    Icons.Rounded.GraphicEq,
                    "Equalizer",
                    "10-band, normalization, crossfade",
                    onClick = onEqualizerClick,
                ) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant)
                }
                PreferenceRow(
                    Icons.Rounded.Bedtime,
                    "Sleep timer",
                    "Pause playback after a set time",
                    onClick = onSleepTimerClick,
                ) {
                    Text(sleepSummary, fontSize = 13.sp, color = scheme.onSurfaceVariant)
                }
                PreferenceRow(Icons.Rounded.Hd, "Streaming quality", "Opus ~160 kbps") {
                    Text("High", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                }
                PreferenceRow(Icons.Rounded.SdCard, "Download quality", "Opus ~160 kbps") {
                    Text("High", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                }
                HorizontalDivider(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    color = scheme.outlineVariant,
                )
            }

            item {
                GroupHeader("STORAGE")
                PreferenceRow(
                    Icons.Rounded.Wifi,
                    "Download over Wi-Fi only",
                    "Leave on unless you enjoy paying for data",
                ) {
                    Switch(
                        checked = toggles["wifi"] == true,
                        onCheckedChange = { onToggle("wifi", it) },
                    )
                }
                PreferenceRow(
                    Icons.Rounded.Storage,
                    "Clear artwork cache",
                    "Frees space used by cover art. It re-downloads as needed.",
                    onClick = onClearCache,
                ) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant)
                }
                HorizontalDivider(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    color = scheme.outlineVariant,
                )
            }

            item {
                GroupHeader("PLAYBACK RELIABILITY")
                PreferenceRow(
                    icon = Icons.Rounded.BatteryAlert,
                    label = "Battery optimisation",
                    subtitle = "Xiaomi detected — Autostart and No restrictions are separate settings",
                    iconTint = scheme.error,
                    onClick = {},
                ) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant)
                }
                HorizontalDivider(
                    Modifier.padding(start = 16.dp, end = 16.dp, top = 8.dp),
                    color = scheme.outlineVariant,
                )
            }

            item {
                GroupHeader("ABOUT")
                PreferenceRow(
                    Icons.Rounded.Code,
                    "Source",
                    "github.com/SoujanyaDasRoy/Mero · GPL-3.0",
                    onClick = {},
                ) {
                    Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant)
                }
                PreferenceRow(
                    Icons.Rounded.SystemUpdate,
                    "Updates",
                    "Delivered through Obtainium",
                ) {
                    Text("1.2.1", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                }
                Text(
                    "Mero 1.2.1 · GPL-3.0 · updates via Obtainium",
                    Modifier.padding(20.dp),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}
