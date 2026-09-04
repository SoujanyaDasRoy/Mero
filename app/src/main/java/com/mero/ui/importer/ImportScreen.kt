package com.mero.ui.importer

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.CheckCircle
import androidx.compose.material.icons.rounded.Link
import androidx.compose.material.icons.rounded.Lock
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.RadioButtonUnchecked
import androidx.compose.material.icons.rounded.Warning
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.ui.components.Artwork

private val STEPS = listOf("Connect", "Choose", "Resolve", "Review")

@Composable
fun ImportScreen(
    step: Int,
    onStepChange: (Int) -> Unit,
    picked: Set<Int>,
    onTogglePick: (Int) -> Unit,
    onBack: () -> Unit,
    onDone: () -> Unit,
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
            IconButton(onClick = { if (step > 1) onStepChange(step - 1) else onBack() }) {
                Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back")
            }
            Text(
                "Import playlists",
                Modifier.weight(1f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp),
        ) {
            STEPS.forEachIndexed { index, label ->
                val active = index + 1 <= step
                Column(Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Box(
                        Modifier
                            .fillMaxWidth()
                            .height(4.dp)
                            .clip(RoundedCornerShape(2.dp))
                            .background(if (active) scheme.primary else scheme.outlineVariant),
                    )
                    Text(
                        label,
                        fontSize = 11.sp,
                        color = if (active) scheme.onSurface else scheme.onSurfaceVariant,
                    )
                }
            }
        }

        when (step) {
            1 -> ConnectStep(onNext = { onStepChange(2) }, contentPadding)
            2 -> ChooseStep(picked, onTogglePick, onNext = { onStepChange(3) }, contentPadding)
            3 -> ResolveStep(onNext = { onStepChange(4) }, contentPadding)
            else -> ReviewStep(onDone, contentPadding)
        }
    }
}

@Composable
private fun ConnectStep(onNext: () -> Unit, contentPadding: PaddingValues) {
    val scheme = MaterialTheme.colorScheme
    Column(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
            .padding(bottom = contentPadding.calculateBottomPadding()),
        verticalArrangement = Arrangement.spacedBy(20.dp),
    ) {
        Column(
            Modifier
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceContainer)
                .padding(20.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Icon(Icons.Rounded.Link, null, Modifier.size(32.dp), tint = scheme.primary)
            Text("Connect Spotify", fontSize = 20.sp, fontWeight = FontWeight.Medium)
            Text(
                "Mero reads your playlists and saved albums through Spotify's Web API. " +
                    "It never plays Spotify audio — every track is matched to YouTube Music.",
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = scheme.onSurfaceVariant,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                Icon(
                    Icons.Rounded.Lock,
                    null,
                    Modifier.size(18.dp),
                    tint = scheme.onSurfaceVariant,
                )
                Text(
                    "PKCE authorisation. No client secret ships in the APK, and no Google " +
                        "account is involved anywhere in Mero.",
                    fontSize = 12.sp,
                    lineHeight = 17.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        }

        Button(
            onClick = onNext,
            Modifier
                .fillMaxWidth()
                .height(48.dp),
            shape = RoundedCornerShape(24.dp),
        ) {
            Text("Connect Spotify", fontSize = 15.sp, fontWeight = FontWeight.Medium)
        }

        HorizontalDivider(color = scheme.outlineVariant)

        Text("Or paste a YouTube playlist link", fontSize = 14.sp, fontWeight = FontWeight.Medium)
        Box(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .clip(RoundedCornerShape(4.dp))
                .background(scheme.surfaceContainer)
                .padding(horizontal = 16.dp),
            contentAlignment = Alignment.CenterStart,
        ) {
            Text(
                "music.youtube.com/playlist?list=…",
                fontSize = 15.sp,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun ChooseStep(
    picked: Set<Int>,
    onToggle: (Int) -> Unit,
    onNext: () -> Unit,
    contentPadding: PaddingValues,
) {
    val scheme = MaterialTheme.colorScheme
    val items = listOf(
        "Indie Chill" to "Playlist · 42 songs",
        "Monsoon" to "Playlist · 27 songs",
        "Gym" to "Playlist · 63 songs",
        "Bengali Classics" to "Playlist · 34 songs",
        "Snow" to "Album · Angus & Julia Stone",
    )

    LazyColumn(
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
    ) {
        item {
            Text(
                "4 playlists · 2 albums found",
                Modifier.padding(start = 16.dp, bottom = 8.dp),
                fontSize = 14.sp,
                color = scheme.onSurfaceVariant,
            )
        }
        items.forEachIndexed { index, (name, sub) ->
            item(key = name) {
                val on = index in picked
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { onToggle(index) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        if (on) Icons.Rounded.CheckCircle else Icons.Rounded.RadioButtonUnchecked,
                        contentDescription = if (on) "Selected" else "Not selected",
                        tint = if (on) scheme.primary else scheme.outline,
                        modifier = Modifier.size(22.dp),
                    )
                    Artwork(null, 44, icon = Icons.Rounded.QueueMusic)
                    Column(Modifier.weight(1f)) {
                        Text(name, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(sub, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    }
                }
            }
        }
        item {
            Button(
                onClick = onNext,
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text(
                    "Import ${picked.size} selected",
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Medium,
                )
            }
        }
    }
}

@Composable
private fun ResolveStep(onNext: () -> Unit, contentPadding: PaddingValues) {
    val scheme = MaterialTheme.colorScheme
    val rows = listOf(
        Triple("Mango Tree", "Matched · 228s, exact title", true),
        Triple("Big Jet Plane", "Matched · 236s, exact title", true),
        Triple("Riptide", "Matched · 204s", true),
        Triple("Electric Feel", "Live version — needs a look", false),
        Triple("Sunflower", "Matched · 176s", true),
        Triple("Redbone", "Resolving…", true),
    )

    LazyColumn(
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
    ) {
        item {
            Column(
                Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text("Resolving tracks", fontSize = 14.sp)
                    Text("37 / 50", fontSize = 14.sp, color = scheme.onSurfaceVariant)
                }
                LinearProgressIndicator(
                    progress = { 0.74f },
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(4.dp),
                    color = scheme.primary,
                    trackColor = scheme.outlineVariant,
                )
                Text(
                    "Matching on duration, then title, then artist. Cached so re-imports are instant.",
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        rows.forEach { (title, status, ok) ->
            item(key = title) {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Icon(
                        if (ok) Icons.Rounded.CheckCircle else Icons.Rounded.Warning,
                        contentDescription = null,
                        tint = if (ok) scheme.primary else scheme.error,
                        modifier = Modifier.size(20.dp),
                    )
                    Column(Modifier.weight(1f)) {
                        Text(title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            status,
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
            }
        }
        item {
            OutlinedButton(
                onClick = onNext,
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text("Skip to review", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}

@Composable
private fun ReviewStep(onDone: () -> Unit, contentPadding: PaddingValues) {
    val scheme = MaterialTheme.colorScheme
    val reviews = listOf(
        Triple(
            "Duration differs by 41s",
            "Electric Feel — MGMT",
            "Electric Feel (Live at Lollapalooza)",
        ),
        Triple("Title has extra words", "Sunflower — Rex Orange County", "Sunflower (sped up)"),
        Triple("Different artist", "Redbone — Childish Gambino", "Redbone — Karaoke Version"),
    )

    LazyColumn(
        contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
    ) {
        item {
            Column(
                Modifier
                    .padding(start = 16.dp, end = 16.dp, bottom = 16.dp)
                    .clip(RoundedCornerShape(16.dp))
                    .background(scheme.surfaceContainer)
                    .padding(16.dp),
            ) {
                Text("45 of 50 matched confidently", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                Text(
                    "Five need a look. Overrides are sticky — Mero never re-resolves a track you fixed.",
                    Modifier.padding(top = 4.dp),
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
        reviews.forEach { (reason, from, to) ->
            item(key = from) {
                Column(
                    Modifier
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp)
                        .clip(RoundedCornerShape(12.dp))
                        .border(1.dp, scheme.outlineVariant, RoundedCornerShape(12.dp))
                        .padding(horizontal = 14.dp, vertical = 12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp),
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Warning,
                            null,
                            Modifier.size(18.dp),
                            tint = scheme.error,
                        )
                        Text(reason, fontSize = 12.sp, color = scheme.onSurfaceVariant)
                    }
                    Column {
                        Text("Spotify", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        Text(from, fontSize = 14.sp)
                    }
                    Column {
                        Text("Matched to", fontSize = 11.sp, color = scheme.onSurfaceVariant)
                        Text(to, fontSize = 14.sp, color = scheme.error)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = {}, shape = RoundedCornerShape(18.dp)) {
                            Text("Change match", fontSize = 13.sp)
                        }
                        Spacer(Modifier.size(0.dp))
                        OutlinedButton(
                            onClick = {},
                            shape = RoundedCornerShape(18.dp),
                            colors = ButtonDefaults.outlinedButtonColors(
                                contentColor = scheme.onSurfaceVariant,
                            ),
                        ) {
                            Text("Keep", fontSize = 13.sp)
                        }
                    }
                }
            }
        }
        item {
            Button(
                onClick = onDone,
                Modifier
                    .fillMaxWidth()
                    .padding(16.dp)
                    .height(48.dp),
                shape = RoundedCornerShape(24.dp),
            ) {
                Text("Save 3 playlists", fontSize = 15.sp, fontWeight = FontWeight.Medium)
            }
        }
    }
}
