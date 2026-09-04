package com.mero.ui.player

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.detectHorizontalDragGestures
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.offset
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Bedtime
import androidx.compose.material.icons.rounded.DragHandle
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.FavoriteBorder
import androidx.compose.material.icons.rounded.GraphicEq
import androidx.compose.material.icons.rounded.KeyboardArrowDown
import androidx.compose.material.icons.rounded.Lyrics
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.MusicNote
import androidx.compose.material.icons.rounded.Pause
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Repeat
import androidx.compose.material.icons.rounded.RepeatOne
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material.icons.rounded.SkipNext
import androidx.compose.material.icons.rounded.SkipPrevious
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.domain.RepeatMode
import com.mero.domain.Song
import com.mero.domain.asClock
import com.mero.ui.components.Artwork
import com.mero.ui.theme.LocalMeroExtras

/** The three Now Playing directions from `Player.dc.html`. */
enum class PlayerVariant(val label: String) {
    Standard("A · Standard"),
    FullBleed("B · Full-bleed"),
    QueueForward("C · Queue-forward"),
}

/** Everything a Now Playing variant needs. Mirrors the design component's props. */
data class PlayerUi(
    val song: Song,
    val source: String,
    val positionSec: Int,
    val playing: Boolean,
    val liked: Boolean,
    val shuffle: Boolean,
    val repeat: RepeatMode,
    val upNext: List<Song>,
    /** e.g. "Opus · 160 kbps" — what the stream actually resolved to. */
    val qualityLabel: String? = null,
)

data class PlayerActions(
    val onCollapse: () -> Unit,
    val onPlayPause: () -> Unit,
    val onPrev: () -> Unit,
    val onNext: () -> Unit,
    val onSeek: (Float) -> Unit,
    val onLike: () -> Unit,
    val onShuffle: () -> Unit,
    val onRepeat: () -> Unit,
    val onQueue: () -> Unit,
    val onLyrics: () -> Unit,
    val onEqualizer: () -> Unit = {},
    val onSleepTimer: () -> Unit = {},
)

@Composable
fun NowPlayingScreen(
    variant: PlayerVariant,
    ui: PlayerUi,
    actions: PlayerActions,
    modifier: Modifier = Modifier,
) = when (variant) {
    PlayerVariant.Standard -> StandardPlayer(ui, actions, modifier)
    PlayerVariant.FullBleed -> FullBleedPlayer(ui, actions, modifier)
    PlayerVariant.QueueForward -> QueueForwardPlayer(ui, actions, modifier)
}

/* ------------------------------- A · Standard ------------------------------ */

@Composable
private fun StandardPlayer(ui: PlayerUi, actions: PlayerActions, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme
    val tint = LocalMeroExtras.current.playerTint

    Column(
        modifier
            .fillMaxSize()
            .background(
                // playerTint at the top, settling into surf2 by 46% down.
                Brush.verticalGradient(
                    0f to tint,
                    0.46f to scheme.surfaceContainer,
                    1f to scheme.surfaceContainer,
                ),
            ),
    ) {
        PlayerTopBar(
            source = ui.source,
            centred = true,
            onCollapse = actions.onCollapse,
        )

        // Artwork sits high, not centred — Tidal-style.
        Spacer(Modifier.height(20.dp))
        Box(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 28.dp),
            contentAlignment = Alignment.Center,
        ) {
            Artwork(
                ui.song.thumbnailUrl,
                size = 330,
                radius = 16,
                modifier = Modifier
                    .fillMaxWidth()
                    .aspectRatio(1f),
            )
        }

        // Middle block: what's playing, and where you are in it.
        Spacer(Modifier.weight(1f))
        Column(Modifier.padding(horizontal = 28.dp)) {
            TitleBlock(
                song = ui.song,
                liked = ui.liked,
                onLike = actions.onLike,
                titleSize = 24,
                titleLineHeight = 32,
                artistSize = 16,
            )
            Spacer(Modifier.height(18.dp))
            SeekBar(ui, actions.onSeek, thumb = 16)
        }

        // Transport sits low.
        Spacer(Modifier.weight(1f))
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 24.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            ShuffleButton(ui.shuffle, actions.onShuffle, 48)
            SkipButton(Icons.Rounded.SkipPrevious, "Previous", actions.onPrev, 56, 40)
            PlayButton(ui.playing, actions.onPlayPause, size = 76, icon = 42, CircleShape)
            SkipButton(Icons.Rounded.SkipNext, "Next", actions.onNext, 56, 40)
            RepeatButton(ui.repeat, actions.onRepeat, 48)
        }

        Spacer(Modifier.height(20.dp))
        if (ui.qualityLabel != null) {
            Text(
                ui.qualityLabel,
                Modifier
                    .fillMaxWidth()
                    .padding(bottom = 6.dp),
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
            )
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(horizontal = 20.dp, vertical = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            TextIconButton(Icons.Rounded.Lyrics, "Lyrics", actions.onLyrics)
            IconButton(onClick = actions.onEqualizer) {
                Icon(
                    Icons.Rounded.GraphicEq,
                    "Equalizer",
                    Modifier.size(22.dp),
                    tint = scheme.onSurfaceVariant,
                )
            }
            IconButton(onClick = actions.onSleepTimer) {
                Icon(
                    Icons.Rounded.Bedtime,
                    "Sleep timer",
                    Modifier.size(22.dp),
                    tint = scheme.onSurfaceVariant,
                )
            }
            TextIconButton(Icons.Rounded.QueueMusic, "Queue", actions.onQueue)
        }
        Spacer(Modifier.height(8.dp))
    }
}


/* ------------------------------ B · Full-bleed ----------------------------- */

@Composable
private fun FullBleedPlayer(ui: PlayerUi, actions: PlayerActions, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier
            .fillMaxSize()
            .background(scheme.surfaceContainer),
    ) {
        Box(
            Modifier
                .fillMaxWidth()
                .height(430.dp),
        ) {
            Artwork(
                ui.song.thumbnailUrl,
                size = 430,
                radius = 0,
                modifier = Modifier.fillMaxSize(),
            )
            // Fade the bottom 160dp of the art into the surface.
            Box(
                Modifier
                    .align(Alignment.BottomCenter)
                    .fillMaxWidth()
                    .height(160.dp)
                    .background(
                        Brush.verticalGradient(
                            0f to Color.Transparent,
                            0.96f to scheme.surfaceContainer,
                        ),
                    ),
            )
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(horizontal = 4.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ScrimIconButton(Icons.Rounded.KeyboardArrowDown, "Collapse", actions.onCollapse)
                Spacer(Modifier.weight(1f))
                ScrimIconButton(Icons.Rounded.MoreVert, "More", {})
            }
        }

        Column(Modifier.padding(start = 24.dp, end = 24.dp, bottom = 12.dp)) {
            Text(
                ui.source.uppercase(),
                fontSize = 11.sp,
                letterSpacing = 0.5.sp,
                color = scheme.onSurfaceVariant,
            )
            Spacer(Modifier.height(8.dp))
            TitleBlock(
                song = ui.song,
                liked = ui.liked,
                onLike = actions.onLike,
                titleSize = 28,
                titleLineHeight = 36,
                artistSize = 16,
                titleLetterSpacing = (-0.2).sp.value,
            )

            Spacer(Modifier.height(24.dp))
            SeekBar(ui, actions.onSeek, thumb = 16)

            Spacer(Modifier.weight(1f))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                ShuffleButton(ui.shuffle, actions.onShuffle, 48)
                SkipButton(Icons.Rounded.SkipPrevious, "Previous", actions.onPrev, 56, 36)
                // Rounded square, not a circle — the one shape difference between variants.
                PlayButton(
                    ui.playing, actions.onPlayPause,
                    size = 64, icon = 36, shape = RoundedCornerShape(20.dp),
                )
                SkipButton(Icons.Rounded.SkipNext, "Next", actions.onNext, 56, 36)
                RepeatButton(ui.repeat, actions.onRepeat, 48)
            }

            Spacer(Modifier.height(16.dp))
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                FilledPill(Icons.Rounded.Lyrics, "Lyrics", actions.onLyrics, Modifier.weight(1f))
                FilledPill(
                    Icons.Rounded.QueueMusic,
                    "Queue",
                    actions.onQueue,
                    Modifier.weight(1f),
                )
            }
        }
    }
}

/* ---------------------------- C · Queue-forward ---------------------------- */

@Composable
private fun QueueForwardPlayer(ui: PlayerUi, actions: PlayerActions, modifier: Modifier) {
    val scheme = MaterialTheme.colorScheme

    Column(
        modifier
            .fillMaxSize()
            .background(scheme.surfaceContainer),
    ) {
        PlayerTopBar(
            source = ui.source,
            centred = false,
            onCollapse = actions.onCollapse,
            height = 48,
        )

        Spacer(Modifier.height(8.dp))
        Box(Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Artwork(ui.song.thumbnailUrl, size = 200, radius = 12)
        }

        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 20.dp)) {
            Text(
                ui.song.title,
                Modifier.fillMaxWidth(),
                fontSize = 20.sp,
                lineHeight = 26.sp,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                ui.song.artist,
                Modifier.fillMaxWidth(),
                fontSize = 14.sp,
                lineHeight = 20.sp,
                color = scheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(Modifier.padding(start = 24.dp, end = 24.dp, top = 12.dp)) {
            SeekBar(ui, actions.onSeek, thumb = 12, timeSize = 11)
        }

        // Like and Lyrics ride in the transport row here; shuffle and repeat move
        // into the Up-next card header.
        Row(
            Modifier
                .fillMaxWidth()
                .padding(start = 16.dp, end = 16.dp, top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = actions.onLike, modifier = Modifier.size(44.dp)) {
                Icon(
                    if (ui.liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                    contentDescription = if (ui.liked) "Unlike" else "Like",
                    modifier = Modifier.size(22.dp),
                    tint = scheme.primary,
                )
            }
            SkipButton(Icons.Rounded.SkipPrevious, "Previous", actions.onPrev, 48, 32)
            PlayButton(ui.playing, actions.onPlayPause, size = 60, icon = 34, CircleShape)
            SkipButton(Icons.Rounded.SkipNext, "Next", actions.onNext, 48, 32)
            IconButton(onClick = actions.onLyrics, modifier = Modifier.size(44.dp)) {
                Icon(
                    Icons.Rounded.Lyrics,
                    "Lyrics",
                    Modifier.size(22.dp),
                    tint = scheme.onSurfaceVariant,
                )
            }
        }

        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp, end = 12.dp, top = 16.dp, bottom = 12.dp)
                .clip(RoundedCornerShape(16.dp))
                .background(scheme.surfaceContainerHigh),
        ) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(44.dp)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text("Up next", Modifier.weight(1f), fontSize = 14.sp, fontWeight = FontWeight.Medium)
                ShuffleButton(ui.shuffle, actions.onShuffle, 36, icon = 20)
                RepeatButton(ui.repeat, actions.onRepeat, 36, icon = 20)
                IconButton(onClick = actions.onQueue, modifier = Modifier.size(36.dp)) {
                    Icon(
                        Icons.Rounded.QueueMusic,
                        "Open queue",
                        Modifier.size(20.dp),
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
            LazyColumn {
                items(ui.upNext, key = { it.id }) { song ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(56.dp)
                            .padding(horizontal = 16.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(12.dp),
                    ) {
                        Artwork(song.thumbnailUrl, 40)
                        Column(Modifier.weight(1f)) {
                            Text(
                                song.title,
                                fontSize = 14.sp,
                                lineHeight = 20.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                song.artist,
                                fontSize = 12.sp,
                                lineHeight = 16.sp,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        Icon(
                            Icons.Rounded.DragHandle,
                            contentDescription = null,
                            modifier = Modifier.size(20.dp),
                            tint = scheme.outline,
                        )
                    }
                }
            }
        }
    }
}

/* --------------------------------- shared --------------------------------- */

@Composable
private fun PlayerTopBar(
    source: String,
    centred: Boolean,
    onCollapse: () -> Unit,
    height: Int = 56,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .height(height.dp)
            .padding(horizontal = 4.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        IconButton(onClick = onCollapse) {
            Icon(Icons.Rounded.KeyboardArrowDown, "Collapse", tint = scheme.onSurfaceVariant)
        }
        if (centred) {
            Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                Text(
                    "PLAYING FROM",
                    fontSize = 11.sp,
                    letterSpacing = 0.5.sp,
                    color = scheme.onSurfaceVariant,
                )
                Text(
                    source,
                    fontSize = 14.sp,
                    fontWeight = FontWeight.Medium,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        } else {
            Text(
                source,
                Modifier.weight(1f),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = {}) {
            Icon(Icons.Rounded.MoreVert, "More", tint = scheme.onSurfaceVariant)
        }
    }
}

@Composable
private fun TitleBlock(
    song: Song,
    liked: Boolean,
    onLike: () -> Unit,
    titleSize: Int,
    titleLineHeight: Int,
    artistSize: Int,
    titleLetterSpacing: Float = 0f,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.Top,
    ) {
        Column(Modifier.weight(1f)) {
            Text(
                song.title,
                fontSize = titleSize.sp,
                lineHeight = titleLineHeight.sp,
                letterSpacing = titleLetterSpacing.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                song.artist,
                fontSize = artistSize.sp,
                lineHeight = 24.sp,
                color = scheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onLike) {
            Icon(
                if (liked) Icons.Rounded.Favorite else Icons.Rounded.FavoriteBorder,
                contentDescription = if (liked) "Unlike" else "Like",
                tint = scheme.primary,
            )
        }
    }
}

/**
 * The design's seek bar: a 4dp track with a round thumb, tap or drag to scrub.
 * Not [androidx.compose.material3.Slider] — M3's slider draws stop indicators and
 * a gap around the thumb in recent versions, which the design does not have.
 */
@Composable
private fun SeekBar(
    ui: PlayerUi,
    onSeek: (Float) -> Unit,
    thumb: Int,
    timeSize: Int = 12,
) {
    val scheme = MaterialTheme.colorScheme
    val pct = if (ui.song.durationSec == 0) {
        0f
    } else {
        (ui.positionSec.toFloat() / ui.song.durationSec).coerceIn(0f, 1f)
    }
    Column {
        Box(
            Modifier
                .fillMaxWidth()
                .height((thumb + 4).dp)
                .pointerInput(Unit) {
                    detectTapGestures { onSeek((it.x / size.width).coerceIn(0f, 1f)) }
                }
                .pointerInput(Unit) {
                    detectHorizontalDragGestures { change, _ ->
                        onSeek((change.position.x / size.width).coerceIn(0f, 1f))
                    }
                },
            contentAlignment = Alignment.CenterStart,
        ) {
            Box(
                Modifier
                    .fillMaxWidth()
                    .height(4.dp)
                    .clip(RoundedCornerShape(2.dp))
                    .background(scheme.outlineVariant),
            )
            // The thumb rides the end of the filled region, nudged out by half its
            // width so its centre — not its edge — sits on the play position.
            Box(
                Modifier.fillMaxWidth(pct),
                contentAlignment = Alignment.CenterEnd,
            ) {
                Box(
                    Modifier
                        .fillMaxWidth()
                        .height(4.dp)
                        .clip(RoundedCornerShape(2.dp))
                        .background(scheme.primary),
                )
                Box(
                    Modifier
                        .offset(x = (thumb / 2).dp)
                        .size(thumb.dp)
                        .clip(CircleShape)
                        .background(scheme.primary),
                )
            }
        }
        Row(
            Modifier
                .fillMaxWidth()
                .padding(top = 4.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
        ) {
            Text(
                ui.positionSec.asClock(),
                fontSize = timeSize.sp,
                color = scheme.onSurfaceVariant,
            )
            Text(
                ui.song.durationSec.asClock(),
                fontSize = timeSize.sp,
                color = scheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun PlayButton(
    playing: Boolean,
    onClick: () -> Unit,
    size: Int,
    icon: Int,
    shape: androidx.compose.ui.graphics.Shape,
) {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(size.dp)
            .clip(shape)
            .background(scheme.primary)
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            if (playing) Icons.Rounded.Pause else Icons.Rounded.PlayArrow,
            contentDescription = if (playing) "Pause" else "Play",
            modifier = Modifier.size(icon.dp),
            tint = scheme.onPrimary,
        )
    }
}

@Composable
private fun SkipButton(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    box: Int,
    glyph: Int,
) {
    IconButton(onClick = onClick, modifier = Modifier.size(box.dp)) {
        Icon(icon, label, Modifier.size(glyph.dp))
    }
}

@Composable
private fun ShuffleButton(on: Boolean, onClick: () -> Unit, box: Int, icon: Int = 24) {
    val scheme = MaterialTheme.colorScheme
    IconButton(onClick = onClick, modifier = Modifier.size(box.dp)) {
        Icon(
            Icons.Rounded.Shuffle,
            contentDescription = "Shuffle",
            modifier = Modifier.size(icon.dp),
            tint = if (on) scheme.primary else scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun RepeatButton(mode: RepeatMode, onClick: () -> Unit, box: Int, icon: Int = 24) {
    val scheme = MaterialTheme.colorScheme
    IconButton(onClick = onClick, modifier = Modifier.size(box.dp)) {
        Icon(
            if (mode == RepeatMode.One) Icons.Rounded.RepeatOne else Icons.Rounded.Repeat,
            contentDescription = "Repeat",
            modifier = Modifier.size(icon.dp),
            tint = if (mode == RepeatMode.Off) scheme.onSurfaceVariant else scheme.primary,
        )
    }
}

@Composable
private fun TextIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = scheme.onSurfaceVariant)
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSurfaceVariant,
        )
    }
}

@Composable
private fun FilledPill(
    icon: ImageVector,
    label: String,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Row(
        modifier
            .height(40.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(scheme.secondaryContainer)
            .clickable(onClick = onClick),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(8.dp, Alignment.CenterHorizontally),
    ) {
        Icon(icon, null, Modifier.size(20.dp), tint = scheme.onSecondaryContainer)
        Text(
            label,
            fontSize = 14.sp,
            fontWeight = FontWeight.Medium,
            color = scheme.onSecondaryContainer,
        )
    }
}

/** Translucent scrim button for controls sitting on top of full-bleed artwork. */
@Composable
private fun ScrimIconButton(icon: ImageVector, label: String, onClick: () -> Unit) {
    Box(
        Modifier
            .size(48.dp)
            .clip(CircleShape)
            .background(Color(0xFF141218).copy(alpha = 0.42f))
            .clickable(onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Icon(icon, label, tint = Color(0xFFEDE7F0))
    }
}
