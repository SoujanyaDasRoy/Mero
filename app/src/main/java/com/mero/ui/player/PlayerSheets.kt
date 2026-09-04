package com.mero.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.DragIndicator
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SyncAlt
import androidx.compose.material3.CircularProgressIndicator
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
import com.mero.domain.LyricLine
import com.mero.domain.Song
import com.mero.domain.asClock
import com.mero.ui.components.Artwork

/** Docked 64dp bar. Tapping anywhere but the buttons expands to Now Playing. */
@Composable
fun MiniPlayer(
    song: Song,
    playing: Boolean,
    buffering: Boolean = false,
    progress: Float,
    onExpand: () -> Unit,
    onPlayPause: () -> Unit,
    onNext: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        modifier
            .fillMaxWidth()
            .height(64.dp)
            .clip(RoundedCornerShape(16.dp))
            .background(scheme.surfaceContainerHigh)
            .clickable(onClick = onExpand),
    ) {
        Row(
            Modifier
                .fillMaxSize()
                .padding(start = 8.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Artwork(song.thumbnailUrl, 48, radius = 8)
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    song.title,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    song.artist,
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            IconButton(onClick = onPlayPause) {
                if (buffering) {
                    CircularProgressIndicator(
                        Modifier.size(20.dp),
                        color = scheme.primary,
                        strokeWidth = 2.dp,
                    )
                } else {
                    Icon(
                        if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
                        contentDescription = if (playing) "Pause" else "Play",
                        modifier = Modifier.size(26.dp),
                    )
                }
            }
            IconButton(onClick = onNext) {
                Icon(Icons.Rounded.SkipNext, "Next", Modifier.size(26.dp))
            }
        }
        LinearProgressIndicator(
            progress = { progress },
            modifier = Modifier
                .align(Alignment.BottomCenter)
                .padding(horizontal = 12.dp)
                .fillMaxWidth()
                .height(2.dp),
            color = scheme.primary,
            trackColor = scheme.outlineVariant,
        )
    }
}

@Composable
fun QueueSheet(
    current: Song,
    positionSec: Int,
    queue: List<Song>,
    onClose: () -> Unit,
    onClear: () -> Unit,
    onPlay: (Song) -> Unit,
    onRemove: (Song) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(
        modifier
            .fillMaxSize()
            .background(scheme.surfaceContainer),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
            Text("Queue", Modifier.weight(1f), fontSize = 18.sp, fontWeight = FontWeight.Medium)
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.PlaylistAdd, "Save queue", tint = scheme.onSurfaceVariant)
            }
            OutlinedButton(
                onClick = onClear,
                Modifier.padding(end = 8.dp),
                shape = RoundedCornerShape(18.dp),
            ) {
                Text("Clear", fontSize = 13.sp)
            }
        }

        Column(Modifier.padding(start = 16.dp, end = 16.dp, bottom = 12.dp)) {
            Text(
                "NOW PLAYING",
                Modifier.padding(bottom = 8.dp),
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = scheme.onSurfaceVariant,
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(12.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(horizontal = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(12.dp),
            ) {
                Icon(Icons.Rounded.GraphicEq, null, Modifier.size(20.dp), tint = scheme.primary)
                Column(Modifier.weight(1f)) {
                    Text(
                        current.title,
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = scheme.primary,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        current.artist,
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                        maxLines = 1,
                    )
                }
                Text(positionSec.asClock(), fontSize = 12.sp, color = scheme.onSurfaceVariant)
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
        ) {
            Text(
                "NEXT UP · ${queue.size}",
                Modifier.weight(1f),
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = scheme.onSurfaceVariant,
            )
            Text("drag to reorder", fontSize = 11.sp, color = scheme.onSurfaceVariant)
        }

        LazyColumn(Modifier.weight(1f)) {
            items(queue, key = { it.id }) { song ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    // ponytail: reorder handle is decorative until the queue is backed
                    // by a real MediaController — drag lands with M2.
                    Icon(
                        Icons.Rounded.DragIndicator,
                        contentDescription = null,
                        tint = scheme.outline,
                    )
                    Row(
                        Modifier
                            .weight(1f)
                            .clickable { onPlay(song) },
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Artwork(song.thumbnailUrl, 44)
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                song.artist,
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                    IconButton(onClick = { onRemove(song) }) {
                        Icon(
                            Icons.Rounded.Close,
                            "Remove",
                            Modifier.size(20.dp),
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
            item {
                Text(
                    "Autoplay radio continues when the queue runs dry.",
                    Modifier.padding(16.dp),
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
fun LyricsSheet(
    song: Song,
    positionSec: Int,
    lines: List<LyricLine>,
    onClose: () -> Unit,
    onSeek: (Int) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val activeIndex = lines.indexOfLast { it.atSec <= positionSec }.coerceAtLeast(0)

    Column(
        modifier
            .fillMaxSize()
            .background(scheme.surfaceContainer),
    ) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onClose) { Icon(Icons.Rounded.Close, "Close") }
            Column(Modifier.weight(1f)) {
                Text(
                    song.title,
                    fontSize = 16.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(song.artist, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1)
            }
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.Search, "Search lyrics", tint = scheme.onSurfaceVariant)
            }
        }

        LazyColumn(
            Modifier
                .weight(1f)
                .padding(horizontal = 24.dp),
        ) {
            itemsIndexed(lines) { index, line ->
                Text(
                    line.text,
                    Modifier
                        .fillMaxWidth()
                        .clickable { onSeek(line.atSec) }
                        .padding(vertical = 11.dp),
                    fontSize = 22.sp,
                    fontWeight = FontWeight.Medium,
                    lineHeight = 28.sp,
                    color = when {
                        index == activeIndex -> scheme.onSurface
                        index < activeIndex -> scheme.onSurfaceVariant.copy(alpha = 0.5f)
                        else -> scheme.onSurfaceVariant
                    },
                )
            }
        }

        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 24.dp, end = 24.dp, top = 12.dp, bottom = 20.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Icon(Icons.Rounded.SyncAlt, null, Modifier.size(16.dp), tint = scheme.onSurfaceVariant)
            Text(
                "Synced lyrics from LRCLIB · tap a line to seek",
                Modifier.weight(1f),
                fontSize = 11.sp,
                color = scheme.onSurfaceVariant,
            )
            OutlinedButton(onClick = {}, shape = RoundedCornerShape(16.dp)) {
                Text("Wrong lyrics?", fontSize = 12.sp)
            }
        }
    }
}
