package com.mero.ui.player

import androidx.compose.animation.animateColorAsState
import androidx.compose.animation.core.animateFloatAsState
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.interaction.DragInteraction
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
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.MyLocation
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.ui.input.pointer.PointerEventPass
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.data.Lyrics
import com.mero.data.LyricsRepository
import com.mero.domain.LyricLine
import com.mero.domain.Song
import kotlinx.coroutines.delay

/** How far ahead of a line's timestamp it lights up: singers start a breath early. */
private const val LEAD_MS = 150L

/** After scrolling by hand, how long before the view follows the song again. */
private const val FOLLOW_AGAIN_MS = 4_000L

/** The line being sung at [positionMs], or -1 before the first. */
internal fun activeLine(lines: List<LyricLine>, positionMs: Long): Int =
    lines.indexOfLast { it.atMs <= positionMs + LEAD_MS }

/**
 * Lyrics that follow the song.
 *
 * The position is read from the player ten times a second rather than taken
 * from the half-second, whole-second clock the rest of the player uses —
 * that clock put the highlight up to a second behind the singer. The current
 * line sits in the upper third, where the eye already is; tapping any line
 * jumps the song there. Scrolling by hand stops the following until you tap
 * "Back to current line" or leave it alone for a few seconds. Pinch to change
 * the text size.
 */
@Composable
fun LyricsSheet(
    song: Song,
    positionMs: () -> Long,
    lyrics: Lyrics,
    loading: Boolean,
    textScale: Float,
    onTextScale: (Float) -> Unit,
    onClose: () -> Unit,
    onSeek: (Long) -> Unit,
    onSearch: (String) -> Unit,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val lines = lyrics.lines
    val currentScale by rememberUpdatedState(textScale)
    var nowMs by remember { mutableLongStateOf(positionMs()) }
    LaunchedEffect(Unit) {
        while (true) {
            nowMs = positionMs()
            delay(100)
        }
    }
    val active = if (lyrics.synced) activeLine(lines, nowMs) else -1

    val listState = rememberLazyListState()
    var following by remember { mutableStateOf(true) }
    var lastTouchMs by remember { mutableLongStateOf(0L) }
    LaunchedEffect(listState) {
        listState.interactionSource.interactions.collect {
            if (it is DragInteraction.Start) {
                following = false
                lastTouchMs = System.currentTimeMillis()
            }
        }
    }
    LaunchedEffect(following, lastTouchMs) {
        if (!following) {
            delay(FOLLOW_AGAIN_MS)
            if (!listState.isScrollInProgress) following = true
        }
    }
    LaunchedEffect(active, following) {
        if (following && active >= 0) {
            val viewport = listState.layoutInfo.viewportSize.height
            listState.animateScrollToItem(active, scrollOffset = -viewport / 3)
        }
    }

    var askingQuery by remember { mutableStateOf<String?>(null) }
    askingQuery?.let { initial ->
        LyricsSearchDialog(
            initial = initial,
            onDismiss = { askingQuery = null },
            onSearch = {
                askingQuery = null
                onSearch(it)
            },
        )
    }
    fun ask() {
        askingQuery = LyricsRepository.cleanTitle(song.title) + " " + song.artist.substringBefore(",").trim()
    }

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
                Text(song.title, fontSize = 16.sp, fontWeight = FontWeight.Medium, maxLines = 1, overflow = TextOverflow.Ellipsis)
                Text(song.artist, fontSize = 12.sp, color = scheme.onSurfaceVariant, maxLines = 1, overflow = TextOverflow.Ellipsis)
            }
            IconButton(onClick = ::ask) {
                Icon(Icons.Rounded.Search, "Search for other lyrics", tint = scheme.onSurfaceVariant)
            }
        }

        Box(
            Modifier
                .weight(1f)
                .fillMaxWidth()
                // Two fingers resize the text. Read before the list sees the
                // touch, so a pinch never also scrolls; one finger passes
                // straight through to scrolling and tapping.
                .pointerInput(Unit) {
                    awaitEachGesture {
                        awaitFirstDown(requireUnconsumed = false, pass = PointerEventPass.Initial)
                        var scale = currentScale
                        do {
                            val event = awaitPointerEvent(PointerEventPass.Initial)
                            if (event.changes.count { it.pressed } >= 2) {
                                scale = (scale * event.calculateZoom()).coerceIn(0.7f, 1.8f)
                                onTextScale(scale)
                                event.changes.forEach { it.consume() }
                            }
                        } while (event.changes.any { it.pressed })
                    }
                },
        ) {
            when {
                loading -> CircularProgressIndicator(Modifier.align(Alignment.Center), color = scheme.primary)
                lines.isEmpty() -> Column(
                    Modifier.align(Alignment.Center).padding(32.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Text("No lyrics for this one yet.", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                    Text(
                        "Try searching with different words.",
                        Modifier.padding(top = 6.dp, bottom = 16.dp),
                        color = scheme.onSurfaceVariant,
                    )
                    Button(onClick = ::ask) { Text("Search lyrics") }
                }
                else -> LazyColumn(
                    state = listState,
                    contentPadding = PaddingValues(horizontal = 24.dp, vertical = 120.dp),
                    modifier = Modifier.fillMaxSize(),
                ) {
                    itemsIndexed(lines) { index, line ->
                        LyricRow(
                            line = line,
                            state = when {
                                !lyrics.synced -> LineState.Plain
                                index == active -> LineState.Now
                                index < active -> LineState.Sung
                                else -> LineState.Coming
                            },
                            textScale = textScale,
                            onClick = if (lyrics.synced) {
                                {
                                    following = true
                                    onSeek(line.atMs)
                                }
                            } else {
                                null
                            },
                        )
                    }
                }
            }

            // Fades rather than AnimatedVisibility: inside this Column's scope
            // that resolves to the column-only variant, which cannot align.
            val showPill = !following && lyrics.synced && active >= 0
            val pillAlpha by animateFloatAsState(if (showPill) 1f else 0f, label = "back to line")
            if (pillAlpha > 0f) {
                Row(
                    Modifier
                        .align(Alignment.BottomCenter)
                        .padding(bottom = 16.dp)
                        .graphicsLayer { alpha = pillAlpha }
                        .clip(RoundedCornerShape(percent = 50))
                        .background(scheme.primaryContainer)
                        .clickable(enabled = showPill) { following = true }
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Icon(Icons.Rounded.MyLocation, null, Modifier.size(16.dp), tint = scheme.onPrimaryContainer)
                    Text("Back to current line", fontSize = 13.sp, color = scheme.onPrimaryContainer)
                }
            }
        }

        if (!loading && lines.isNotEmpty()) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(start = 24.dp, end = 16.dp, top = 8.dp, bottom = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    buildString {
                        append(if (lyrics.synced) "Tap a line to jump there · pinch to resize" else "Not timed, so these won't follow along")
                        if (lyrics.source.isNotEmpty()) append("\nLyrics from ").append(lyrics.source)
                    },
                    Modifier.weight(1f),
                    fontSize = 11.sp,
                    lineHeight = 15.sp,
                    color = scheme.onSurfaceVariant,
                )
                OutlinedButton(onClick = ::ask, shape = RoundedCornerShape(percent = 50)) {
                    Text("Wrong lyrics?", fontSize = 12.sp)
                }
            }
        }
    }
}

private enum class LineState { Plain, Sung, Now, Coming }

@Composable
private fun LyricRow(line: LyricLine, state: LineState, textScale: Float, onClick: (() -> Unit)?) {
    val scheme = MaterialTheme.colorScheme
    val color by animateColorAsState(
        when (state) {
            LineState.Plain, LineState.Now -> scheme.onSurface
            LineState.Sung -> scheme.onSurfaceVariant.copy(alpha = 0.45f)
            LineState.Coming -> scheme.onSurfaceVariant.copy(alpha = 0.8f)
        },
        label = "lyric colour",
    )
    // The current line grows a touch rather than changing font size, which
    // would reflow the list and make everything below it jump.
    val scale by animateFloatAsState(if (state == LineState.Now) 1.06f else 1f, label = "lyric scale")
    Text(
        line.text,
        Modifier
            .fillMaxWidth()
            .clip(RoundedCornerShape(12.dp))
            .then(if (onClick != null) Modifier.clickable(onClick = onClick) else Modifier)
            .padding(vertical = 10.dp, horizontal = 4.dp)
            .graphicsLayer {
                scaleX = scale
                scaleY = scale
                transformOrigin = androidx.compose.ui.graphics.TransformOrigin(0f, 0.5f)
            },
        fontSize = (24 * textScale).sp,
        lineHeight = (31 * textScale).sp,
        fontWeight = if (state == LineState.Now) FontWeight.Bold else FontWeight.SemiBold,
        color = color,
    )
}

@Composable
private fun LyricsSearchDialog(initial: String, onDismiss: () -> Unit, onSearch: (String) -> Unit) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Search lyrics") },
        text = {
            Column {
                Text(
                    "Song name and artist usually works best.",
                    Modifier.padding(bottom = 12.dp),
                    fontSize = 13.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(value = text, onValueChange = { text = it }, singleLine = true)
            }
        },
        confirmButton = {
            TextButton(onClick = { onSearch(text.trim()) }, enabled = text.isNotBlank()) { Text("Search") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
