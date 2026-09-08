package com.mero.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.automirrored.rounded.OpenInNew
import androidx.compose.material.icons.rounded.AllInclusive
import androidx.compose.material.icons.rounded.BatteryAlert
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDownload
import androidx.compose.material.icons.rounded.Code
import androidx.compose.material.icons.rounded.Contrast
import androidx.compose.material.icons.rounded.DarkMode
import androidx.compose.material.icons.rounded.FormatPaint
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Hd
import androidx.compose.material.icons.rounded.MotionPhotosPause
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.SdCard
import androidx.compose.material.icons.rounded.Smartphone
import androidx.compose.material.icons.rounded.Storage
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material.icons.rounded.Wifi
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.data.CodecPreference
import com.mero.data.UpdateState
import com.mero.ui.components.MeroChip
import com.mero.ui.components.PreferenceRow
import com.mero.ui.player.PlayerVariant
import com.mero.ui.theme.MeroAccent

/**
 * Settings as a stack of cards rather than one unbroken list.
 *
 * The screen had grown to twenty-odd rows separated by hairlines, which reads
 * as a single scroll of undifferentiated switches — the eye has nothing to
 * catch on and no sense of where one topic ends. Grouping each topic into its
 * own surface gives the list a shape you can scan without reading, which is
 * how settings screens are actually used: someone arrives looking for one
 * thing and needs to find it, not to read the page.
 */
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
    onClearDownloads: () -> Unit,
    onImportClick: () -> Unit,
    playerVariant: PlayerVariant,
    onPlayerVariantChange: (PlayerVariant) -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    onChooseDownloadFolder: () -> Unit,
    downloadFolderSelected: Boolean,
    streamCodec: CodecPreference,
    onStreamCodecChange: (CodecPreference) -> Unit,
    downloadCodec: CodecPreference,
    onDownloadCodecChange: (CodecPreference) -> Unit,
    onBatterySettingsClick: () -> Unit,
    appVersion: String,
    updateState: UpdateState,
    onSourceClick: () -> Unit,
    onCheckUpdates: () -> Unit,
    onDownloadUpdate: () -> Unit,
    onInstallUpdate: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()
    var confirmClearDownloads by remember { mutableStateOf(false) }
    var cacheCleared by remember { mutableStateOf(false) }

    // Arriving from the home banner lands on whatever row was last scrolled to,
    // which is rarely the card the banner promised. Go to it.
    val showingUpdate = updateState is UpdateState.Available ||
        updateState is UpdateState.Downloading ||
        updateState is UpdateState.Downloaded
    LaunchedEffect(showingUpdate) {
        if (showingUpdate) listState.animateScrollToItem(0)
    }

    if (confirmClearDownloads) {
        AlertDialog(
            onDismissRequest = { confirmClearDownloads = false },
            title = { Text("Remove all downloaded songs?") },
            text = { Text("This removes every song stored on the device. Your playlists and listening history stay intact.") },
            confirmButton = {
                TextButton(onClick = {
                    confirmClearDownloads = false
                    onClearDownloads()
                }) { Text("Remove all") }
            },
            dismissButton = {
                TextButton(onClick = { confirmClearDownloads = false }) { Text("Cancel") }
            },
        )
    }

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
            state = listState,
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 24.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            // An available update goes above everything. It is the one thing on
            // this screen that is time-sensitive, and burying it under twenty
            // switches is how nobody updates.
            item {
                AnimatedVisibility(
                    updateState is UpdateState.Available ||
                        updateState is UpdateState.Downloading ||
                        updateState is UpdateState.Downloaded,
                ) {
                    UpdateCard(
                        state = updateState,
                        onDownload = onDownloadUpdate,
                        onInstall = onInstallUpdate,
                    )
                }
            }

            item {
                SettingsGroup("Appearance") {
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
                        "Light mode",
                        "A bright, warm palette instead of dark",
                    ) {
                        Switch(
                            checked = toggles["dark"] != true,
                            onCheckedChange = { onToggle("dark", !it) },
                        )
                    }
                    PreferenceRow(
                        Icons.Rounded.DarkMode,
                        "Pure black",
                        if (toggles["dark"] == true) {
                            "Real battery saving on an AMOLED panel"
                        } else {
                            "Only applies in dark mode"
                        },
                    ) {
                        Switch(
                            checked = toggles["amoled"] == true,
                            enabled = toggles["dark"] == true,
                            onCheckedChange = { onToggle("amoled", it) },
                        )
                    }
                    PreferenceRow(
                        Icons.Rounded.FormatPaint,
                        "Accent colour",
                        if (toggles["dynamic"] == true) {
                            "Overridden while Material You is on"
                        } else {
                            accent.label
                        },
                    )
                    AccentSwatches(
                        accent = accent,
                        onAccentChange = onAccentChange,
                        dimmed = toggles["dynamic"] == true,
                    )
                    // The design ships three Now Playing directions and no
                    // decision. Exposing the switch is how that decision gets
                    // made — by living with each one, not by picking from a
                    // mockup.
                    PreferenceRow(
                        Icons.Rounded.Smartphone,
                        "Now Playing layout",
                        "Three directions from the design — try each",
                    )
                    Row(
                        Modifier
                            .horizontalScroll(rememberScrollState())
                            .padding(start = 54.dp, end = 16.dp, bottom = 14.dp),
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
                }
            }

            item {
                SettingsGroup("Audio") {
                    PreferenceRow(
                        Icons.Rounded.GraphicEq,
                        "Equalizer",
                        "Ten bands you drag, crossfeed, loudness matching",
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
                    PreferenceRow(
                        Icons.Rounded.AllInclusive,
                        "Infinite playback",
                        "Keep going with similar tracks when the queue runs out",
                    ) {
                        Switch(
                            checked = toggles["infinite"] == true,
                            onCheckedChange = { onToggle("infinite", it) },
                        )
                    }
                    PreferenceRow(
                        Icons.Rounded.MotionPhotosPause,
                        "Pause when idle",
                        if (toggles["infinite"] == true) {
                            "Disabled while Infinite playback is on"
                        } else {
                            "Stops after an hour with no interaction"
                        },
                    ) {
                        Switch(
                            checked = toggles["autopause"] == true,
                            enabled = toggles["infinite"] != true,
                            onCheckedChange = { onToggle("autopause", it) },
                        )
                    }
                    PreferenceRow(
                        Icons.Rounded.Hd,
                        "Streaming quality",
                        "The audio format used while streaming",
                    )
                    CodecChips(CodecPreference.entries, streamCodec, onStreamCodecChange)
                    PreferenceRow(
                        Icons.Rounded.SdCard,
                        "Download quality",
                        "The audio format saved to the device",
                    )
                    CodecChips(CodecPreference.entries, downloadCodec, onDownloadCodecChange)
                }
            }

            item {
                SettingsGroup("Library") {
                    PreferenceRow(
                        Icons.Rounded.CloudDownload,
                        "Import a playlist",
                        "From YouTube Music or Spotify",
                        onClick = onImportClick,
                    ) {
                        Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant)
                    }
                    PreferenceRow(
                        Icons.Rounded.SdCard,
                        "Download folder",
                        if (downloadFolderSelected) {
                            "Downloads are also copied where you chose"
                        } else {
                            "Choose where downloads are copied on the device"
                        },
                        onClick = onChooseDownloadFolder,
                    ) {
                        Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant)
                    }
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
                }
            }

            item {
                SettingsGroup("Storage") {
                    PreferenceRow(
                        Icons.Rounded.Storage,
                        "Clear cache",
                        if (cacheCleared) {
                            "Cleared. Both re-download as needed."
                        } else {
                            "Frees cached audio and cover art"
                        },
                        onClick = {
                            cacheCleared = true
                            onClearCache()
                        },
                    ) {
                        if (cacheCleared) {
                            Icon(Icons.Rounded.Check, null, tint = scheme.primary)
                        } else {
                            Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant)
                        }
                    }
                    PreferenceRow(
                        Icons.Rounded.CloudDownload,
                        "Remove all downloaded songs",
                        "Deletes offline music from this device only",
                        iconTint = scheme.error,
                        onClick = { confirmClearDownloads = true },
                    ) {
                        Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant)
                    }
                }
            }

            item {
                SettingsGroup("Playback reliability") {
                    PreferenceRow(
                        icon = Icons.Rounded.BatteryAlert,
                        label = "Background playback settings",
                        subtitle = "Xiaomi, Oppo, Vivo and OnePlus kill background playback unless Mero is exempt",
                        iconTint = scheme.error,
                        onClick = onBatterySettingsClick,
                    ) {
                        Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant)
                    }
                }
            }

            item {
                SettingsGroup("About") {
                    PreferenceRow(
                        Icons.Rounded.SystemUpdate,
                        "Check for updates",
                        updateSummary(updateState, appVersion),
                        onClick = onCheckUpdates,
                    ) {
                        when (updateState) {
                            is UpdateState.Checking -> CircularProgressIndicator(
                                Modifier.size(18.dp),
                                color = scheme.primary,
                                strokeWidth = 2.dp,
                            )

                            is UpdateState.UpToDate -> Icon(
                                Icons.Rounded.Check,
                                null,
                                tint = scheme.primary,
                            )

                            else -> Icon(
                                Icons.Rounded.ChevronRight,
                                null,
                                tint = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    PreferenceRow(
                        Icons.Rounded.Code,
                        "Source code",
                        "github.com/SoujanyaDasRoy/Mero · GPL-3.0",
                        onClick = onSourceClick,
                    ) {
                        Icon(Icons.AutoMirrored.Rounded.OpenInNew, null, tint = scheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Text(
                    "Mero $appVersion · free and ad-free · GPL-3.0\n" +
                        "Built on InnerTune, yt-dlp and LRCLIB.",
                    Modifier.padding(horizontal = 28.dp, vertical = 20.dp),
                    fontSize = 12.sp,
                    lineHeight = 18.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/** One topic, on its own surface, with a label above it. */
@Composable
private fun SettingsGroup(title: String, content: @Composable () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(horizontal = 12.dp)) {
        Text(
            title,
            Modifier.padding(start = 16.dp, top = 14.dp, bottom = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            letterSpacing = 0.3.sp,
            color = scheme.primary,
        )
        Column(
            Modifier
                .fillMaxWidth()
                .clip(RoundedCornerShape(20.dp))
                .background(scheme.surfaceContainerLow),
        ) {
            content()
        }
    }
}

@Composable
private fun CodecChips(
    options: List<CodecPreference>,
    selected: CodecPreference,
    onSelect: (CodecPreference) -> Unit,
) {
    Row(
        Modifier.padding(start = 54.dp, end = 16.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            MeroChip(option.label, option == selected, onClick = { onSelect(option) })
        }
    }
}

@Composable
private fun AccentSwatches(
    accent: MeroAccent,
    onAccentChange: (MeroAccent) -> Unit,
    dimmed: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.padding(start = 54.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        MeroAccent.entries.forEach { option ->
            val selected = option == accent
            Box(
                Modifier
                    .size(38.dp)
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
                        .size(28.dp)
                        .clip(CircleShape)
                        .background(option.swatch.copy(alpha = if (dimmed) 0.35f else 1f)),
                    contentAlignment = Alignment.Center,
                ) {
                    if (selected) {
                        Icon(
                            Icons.Rounded.Check,
                            contentDescription = option.label + " selected",
                            tint = Color.Black.copy(alpha = 0.7f),
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }
        }
    }
}

/**
 * The update, once there is one: what it is, how big, and one button.
 *
 * Deliberately not a dialog. A dialog interrupts whatever someone opened
 * Settings to do and gets dismissed reflexively; a card waits.
 */
@Composable
private fun UpdateCard(
    state: UpdateState,
    onDownload: () -> Unit,
    onInstall: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    val release = when (state) {
        is UpdateState.Available -> state.release
        is UpdateState.Downloading -> state.release
        is UpdateState.Downloaded -> state.release
        else -> null
    } ?: return

    Column(
        Modifier
            .padding(horizontal = 12.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.primaryContainer)
            .padding(18.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            Icon(
                Icons.Rounded.SystemUpdate,
                null,
                tint = scheme.onPrimaryContainer,
                modifier = Modifier.size(20.dp),
            )
            Text(
                "Mero " + release.versionName + " is out",
                Modifier.padding(start = 10.dp),
                fontSize = 16.sp,
                fontWeight = FontWeight.SemiBold,
                color = scheme.onPrimaryContainer,
            )
        }
        if (release.notes.isNotBlank()) {
            Text(
                release.notes.lineSequence().firstOrNull { it.isNotBlank() }.orEmpty()
                    .removePrefix("**").removeSuffix("**"),
                Modifier.padding(top = 8.dp),
                fontSize = 13.sp,
                lineHeight = 18.sp,
                color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
                maxLines = 3,
                overflow = TextOverflow.Ellipsis,
            )
        }
        Spacer(Modifier.height(14.dp))
        if (state is UpdateState.Downloading) {
            LinearProgressIndicator(
                progress = { state.fraction },
                modifier = Modifier.fillMaxWidth(),
                color = scheme.onPrimaryContainer,
                trackColor = scheme.onPrimaryContainer.copy(alpha = 0.2f),
            )
            Text(
                (state.fraction * 100).toInt().toString() + "% of " + megabytes(release.sizeBytes),
                Modifier.padding(top = 8.dp),
                fontSize = 12.sp,
                color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
        } else {
            val ready = state is UpdateState.Downloaded
            Button(
                onClick = if (ready) onInstall else onDownload,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (ready) "Install" else "Download · " + megabytes(release.sizeBytes),
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

/** The line under "Check for updates", which is where every state ends up. */
private fun updateSummary(state: UpdateState, appVersion: String): String = when (state) {
    is UpdateState.Checking -> "Asking GitHub…"
    is UpdateState.UpToDate -> appVersion + " is the latest version"
    is UpdateState.Available -> state.release.versionName + " is available"
    is UpdateState.Downloading -> "Downloading… " + (state.fraction * 100).toInt() + "%"
    is UpdateState.Downloaded -> "Ready to install"
    is UpdateState.Failed -> state.message
    is UpdateState.Idle -> "Version " + appVersion
}

private fun megabytes(bytes: Long): String =
    if (bytes <= 0) "download" else (bytes / 1_000_000).toString() + " MB"
