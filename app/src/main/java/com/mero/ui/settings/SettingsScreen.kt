package com.mero.ui.settings

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
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
import androidx.compose.material.icons.rounded.HelpOutline
import androidx.compose.material.icons.rounded.MotionPhotosPause
import androidx.compose.material.icons.rounded.Palette
import androidx.compose.material.icons.rounded.Person
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
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
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
import com.mero.ui.theme.ThemeMode

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
@OptIn(ExperimentalLayoutApi::class)
@Composable
fun SettingsScreen(
    accent: MeroAccent,
    onAccentChange: (MeroAccent) -> Unit,
    themeMode: ThemeMode,
    onThemeModeChange: (ThemeMode) -> Unit,
    darkInEffect: Boolean,
    displayName: String,
    onEditName: () -> Unit,
    downloadFolderLabel: String?,
    downloadFolderError: String?,
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
                SettingsGroup("You") {
                    PreferenceRow(
                        Icons.Rounded.Person,
                        "Your name",
                        if (displayName.isBlank()) {
                            "Mero says hello on the home screen — tell it what to call you"
                        } else {
                            displayName + " · stays on this phone"
                        },
                        onClick = onEditName,
                    ) {
                        Icon(Icons.Rounded.ChevronRight, null, tint = scheme.onSurfaceVariant)
                    }
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
                        "Theme",
                        when (themeMode) {
                            ThemeMode.System -> "Following the phone — currently " +
                                if (darkInEffect) "dark" else "light"
                            ThemeMode.Light -> "Always light"
                            ThemeMode.Dark -> "Always dark"
                        },
                    )
                    SegmentedChoice(
                        options = ThemeMode.entries,
                        selected = themeMode,
                        label = { it.label },
                        onSelect = onThemeModeChange,
                    )
                    PreferenceRow(
                        Icons.Rounded.DarkMode,
                        "Pure black",
                        if (darkInEffect) {
                            "Real battery saving on an AMOLED panel"
                        } else {
                            "Available while the app is dark"
                        },
                    ) {
                        Switch(
                            checked = toggles["amoled"] == true && darkInEffect,
                            // Gated on what the app *is*, not on what was
                            // chosen: under System the app can be light without
                            // anyone having picked light, and a pure-black
                            // switch that does nothing is worse than one that
                            // says why.
                            enabled = darkInEffect,
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
                        playerVariant.label + " · " + playerVariant.description,
                    )
                    LayoutTiles(
                        selected = playerVariant,
                        onSelect = onPlayerVariantChange,
                    )
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
                        when {
                            downloadFolderError != null -> downloadFolderError
                            downloadFolderLabel != null -> "Copied to " + downloadFolderLabel
                            else -> "Choose where downloads are copied on the device"
                        },
                        iconTint = if (downloadFolderError != null) scheme.error else null,
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
                        Icons.Rounded.HelpOutline,
                        "How updating works",
                        "Mero checks on its own. Tap Download, then Install.",
                    )
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
                .clip(RoundedCornerShape(26.dp))
                .background(scheme.surfaceContainerLow),
        ) {
            content()
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun CodecChips(
    options: List<CodecPreference>,
    selected: CodecPreference,
    onSelect: (CodecPreference) -> Unit,
) {
    FlowRow(
        Modifier.padding(start = 54.dp, end = 16.dp, bottom = 14.dp),
        horizontalArrangement = Arrangement.spacedBy(8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        options.forEach { option ->
            MeroChip(option.label, option == selected, onClick = { onSelect(option) })
        }
    }
}

@OptIn(ExperimentalLayoutApi::class)
@Composable
private fun AccentSwatches(
    accent: MeroAccent,
    onAccentChange: (MeroAccent) -> Unit,
    dimmed: Boolean,
) {
    val scheme = MaterialTheme.colorScheme
    // Wraps rather than scrolls. A colour that is off the edge of the screen
    // is a colour nobody knows they can pick, and there is no affordance
    // telling them to swipe a row that looks like it has ended.
    FlowRow(
        Modifier.padding(start = 54.dp, end = 16.dp, bottom = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp),
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
            .clip(RoundedCornerShape(26.dp))
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
            val ready = state as? UpdateState.Downloaded
            Button(
                onClick = if (ready != null) onInstall else onDownload,
                modifier = Modifier.fillMaxWidth(),
            ) {
                Text(
                    if (ready != null) "Install" else "Download · " + megabytes(release.sizeBytes),
                    fontWeight = FontWeight.Medium,
                )
            }
            Text(
                if (ready != null) {
                    "Also saved as Downloads/" + ready.fileName
                } else {
                    "Installs over this version. Nothing is lost."
                },
                Modifier.padding(top = 10.dp),
                fontSize = 12.sp,
                lineHeight = 17.sp,
                color = scheme.onPrimaryContainer.copy(alpha = 0.85f),
            )
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

/**
 * A row of mutually exclusive options that looks like one control.
 *
 * Three separate chips read as three independent things that happen to be
 * near each other. A segment bar reads as one setting with a position, which
 * is what a theme choice is.
 */
@Composable
private fun <T> SegmentedChoice(
    options: List<T>,
    selected: T,
    label: (T) -> String,
    onSelect: (T) -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(start = 54.dp, end = 16.dp, bottom = 14.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(14.dp))
            .background(scheme.surfaceContainerHighest)
            .padding(3.dp),
        horizontalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        options.forEach { option ->
            val isSelected = option == selected
            Box(
                Modifier
                    .weight(1f)
                    .height(36.dp)
                    .clip(RoundedCornerShape(11.dp))
                    .background(if (isSelected) scheme.primary else Color.Transparent)
                    .clickable { onSelect(option) },
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    label(option),
                    fontSize = 13.sp,
                    fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Medium,
                    color = if (isSelected) scheme.onPrimary else scheme.onSurfaceVariant,
                )
            }
        }
    }
}

/**
 * The Now Playing layouts, as things you can see rather than names you have to
 * take on trust.
 *
 * A row of chips reading "Classic / Immersive / Up Next / One-handed" still
 * asks someone to open the player four times to find out what they mean. Each
 * tile draws the shape of its layout — where the cover sits, where the
 * controls are — which is the whole of the difference between them.
 */
@Composable
private fun LayoutTiles(selected: PlayerVariant, onSelect: (PlayerVariant) -> Unit) {
    Column(
        Modifier.padding(start = 54.dp, end = 16.dp, bottom = 16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp),
    ) {
        PlayerVariant.entries.chunked(2).forEach { pair ->
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                pair.forEach { option ->
                    LayoutTile(
                        variant = option,
                        selected = option == selected,
                        onClick = { onSelect(option) },
                        modifier = Modifier.weight(1f),
                    )
                }
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
    }
}

@Composable
private fun LayoutTile(
    variant: PlayerVariant,
    selected: Boolean,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .clip(RoundedCornerShape(16.dp))
            .background(if (selected) scheme.primaryContainer else scheme.surfaceContainerHighest)
            .clickable(onClick = onClick)
            .padding(10.dp),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(74.dp)
                .clip(RoundedCornerShape(10.dp))
                .background(scheme.surface),
            contentAlignment = Alignment.Center,
        ) {
            LayoutSketch(variant, scheme.onSurfaceVariant, if (selected) scheme.primary else scheme.onSurfaceVariant)
        }
        Text(
            variant.label,
            Modifier.padding(top = 8.dp),
            fontSize = 13.sp,
            fontWeight = FontWeight.SemiBold,
            color = if (selected) scheme.onPrimaryContainer else scheme.onSurface,
        )
        Text(
            variant.description,
            fontSize = 11.sp,
            lineHeight = 15.sp,
            color = if (selected) {
                scheme.onPrimaryContainer.copy(alpha = 0.8f)
            } else {
                scheme.onSurfaceVariant
            },
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
    }
}

/** A wireframe of the layout: cover block, text lines, control dots. */
@Composable
private fun LayoutSketch(variant: PlayerVariant, quiet: Color, accent: Color) {
    // No horizontal padding on the canvas itself: Immersive is the layout whose
    // whole point is that the cover touches the edges, and a uniform inset drew
    // it identically to Classic.
    Canvas(Modifier.fillMaxSize().padding(vertical = 9.dp)) {
        val w = size.width
        val h = size.height
        val margin = w * 0.14f

        fun cover(top: Float, height: Float, inset: Float) = drawRoundRect(
            color = quiet.copy(alpha = 0.5f),
            topLeft = Offset(inset, top),
            size = Size(w - inset * 2, height),
            cornerRadius = CornerRadius(if (inset == 0f) 0f else 4f, if (inset == 0f) 0f else 4f),
        )

        fun line(top: Float, widthFraction: Float) = drawRoundRect(
            color = quiet.copy(alpha = 0.32f),
            topLeft = Offset(margin, top),
            size = Size((w - margin * 2) * widthFraction, 3f),
            cornerRadius = CornerRadius(2f, 2f),
        )

        fun dots(centreY: Float) {
            listOf(0.15f, 0.5f, 0.85f).forEachIndexed { index, fraction ->
                drawCircle(
                    color = if (index == 1) accent else quiet.copy(alpha = 0.5f),
                    radius = if (index == 1) 5.5f else 3f,
                    center = Offset(margin + (w - margin * 2) * fraction, centreY),
                )
            }
        }

        when (variant) {
            PlayerVariant.Standard -> {
                cover(0f, h * 0.46f, inset = w * 0.26f)
                line(h * 0.58f, 0.75f)
                line(h * 0.70f, 0.45f)
                dots(h * 0.88f)
            }

            PlayerVariant.FullBleed -> {
                // Edge to edge, square corners — that is the whole idea of it.
                cover(0f, h * 0.52f, inset = 0f)
                line(h * 0.64f, 0.8f)
                dots(h * 0.87f)
            }

            PlayerVariant.QueueForward -> {
                cover(0f, h * 0.26f, inset = w * 0.38f)
                dots(h * 0.4f)
                // The queue is the bulk of this one, so it is most of the sketch.
                line(h * 0.58f, 1f)
                line(h * 0.72f, 1f)
                line(h * 0.86f, 1f)
            }

            PlayerVariant.Compact -> {
                cover(0f, h * 0.38f, inset = w * 0.3f)
                line(h * 0.5f, 0.6f)
                // Everything else pushed to the very bottom, which is the point.
                dots(h * 0.82f)
                line(h * 0.96f, 1f)
            }
        }
    }
}
