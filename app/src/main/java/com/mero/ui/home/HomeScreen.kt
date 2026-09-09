package com.mero.ui.home

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.SystemUpdate
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.R
import com.mero.data.HomeSection
import com.mero.domain.Song
import com.mero.ui.components.Artwork
import java.util.Calendar

/**
 * Home, with a shape instead of a stack.
 *
 * Every shelf used to be rendered identically — same card size, same heading
 * weight, one after another — so the screen had no entry point. Nothing was
 * first, nothing was yours, and the fastest way back to a song you played an
 * hour ago was to search for it again.
 *
 * Now the top of the screen is about this listener (what they were playing,
 * addressed by time of day), the first shelf is given room to be a feature,
 * and discovery continues below it endlessly as before.
 */
@Composable
fun HomeScreen(
    sections: List<HomeSection>,
    loading: Boolean,
    error: String?,
    onRetry: () -> Unit,
    onLoadMore: () -> Unit,
    loadingMore: Boolean,
    onSongClick: (Song, List<Song>) -> Unit,
    displayName: String,
    recentlyPlayed: List<Song>,
    updateAvailableVersion: String?,
    onUpdateClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val listState = rememberLazyListState()

    // Pull in the next batch of shelves before the user hits the bottom, so the
    // feed reads as continuous rather than as a page that ran out.
    val nearEnd by remember {
        derivedStateOf {
            val last = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
            val total = listState.layoutInfo.totalItemsCount
            total > 0 && last >= total - 2
        }
    }
    LaunchedEffect(listState) {
        snapshotFlow { nearEnd }.collect { if (it) onLoadMore() }
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(68.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Image(
                painter = painterResource(R.drawable.mero_logo),
                contentDescription = null,
                modifier = Modifier
                    .size(34.dp)
                    .clip(RoundedCornerShape(9.dp)),
            )
            Column(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                Text(
                    greeting(displayName),
                    fontSize = 19.sp,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    "What are we listening to?",
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
            // No settings gear here: the bottom bar already has one, and two
            // ways into the same screen in one viewport is just noise.
            IconButton(onClick = onRetry) {
                Icon(Icons.Rounded.Refresh, "Refresh", tint = scheme.onSurfaceVariant)
            }
        }

        when {
            loading && sections.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = scheme.primary) }

            error != null && sections.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .clickable(onClick = onRetry),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Couldn't load music.\n$error\n\nTap to retry.",
                    Modifier.padding(32.dp),
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            else -> LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                // Nothing else tells anyone a new build exists, so the one
                // place everybody opens has to.
                item {
                    AnimatedVisibility(updateAvailableVersion != null) {
                        UpdateBanner(updateAvailableVersion.orEmpty(), onUpdateClick)
                    }
                }

                if (recentlyPlayed.isNotEmpty()) {
                    item(key = "jump-back-in") {
                        QuickPicks(
                            songs = recentlyPlayed.take(6),
                            onSongClick = { song -> onSongClick(song, recentlyPlayed) },
                        )
                    }
                }

                sections.forEachIndexed { index, section ->
                    item(key = section.title) {
                        if (index == 0) {
                            FeatureShelf(section, onSongClick)
                        } else {
                            Shelf(section, onSongClick)
                        }
                    }
                }

                item {
                    if (loadingMore) {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(24.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                Modifier.size(28.dp),
                                color = scheme.primary,
                                strokeWidth = 2.dp,
                            )
                        }
                    } else {
                        Spacer(Modifier.height(16.dp))
                    }
                }
            }
        }
    }
}

/**
 * The songs this person actually played, two to a row.
 *
 * A wide short tile rather than another square card: these are known
 * quantities being returned to, not things to browse, so the title matters
 * more than the artwork and the row can be half the height.
 */
@Composable
private fun QuickPicks(songs: List<Song>, onSongClick: (Song) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 4.dp)) {
        ShelfTitle("Jump back in")
        songs.chunked(2).forEach { pair ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
            ) {
                pair.forEach { song ->
                    Row(
                        Modifier
                            .weight(1f)
                            .height(56.dp)
                            .clip(RoundedCornerShape(14.dp))
                            .background(scheme.surfaceContainerHigh)
                            .clickable { onSongClick(song) },
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Artwork(song.thumbnailUrl, size = 56, radius = 14)
                        Text(
                            song.title,
                            Modifier.padding(horizontal = 10.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                // Keeps a lone last item at half width instead of stretching it
                // across the row, where it would read as a different component.
                if (pair.size == 1) Spacer(Modifier.weight(1f))
            }
        }
        Spacer(Modifier.height(8.dp))
    }
}

/** The first shelf, given the room to look like the front of the app. */
@Composable
private fun FeatureShelf(section: HomeSection, onSongClick: (Song, List<Song>) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column(Modifier.padding(top = 8.dp)) {
        ShelfTitle(section.title, subtitle = section.subtitle ?: "Fresh every time you open Mero")
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 14.dp),
            horizontalArrangement = Arrangement.spacedBy(14.dp),
        ) {
            section.songs.forEach { song ->
                Column(
                    Modifier
                        .width(196.dp)
                        .clickable { onSongClick(song, section.songs) },
                ) {
                    Box {
                        Artwork(song.thumbnailUrl, size = 196, radius = 20)
                        Box(
                            Modifier
                                .align(Alignment.BottomEnd)
                                .padding(10.dp)
                                .size(38.dp)
                                .clip(RoundedCornerShape(19.dp))
                                .background(scheme.primary),
                            contentAlignment = Alignment.Center,
                        ) {
                            Icon(
                                Icons.Rounded.PlayArrow,
                                contentDescription = null,
                                tint = scheme.onPrimary,
                                modifier = Modifier.size(22.dp),
                            )
                        }
                    }
                    Text(
                        song.title,
                        Modifier.padding(top = 10.dp),
                        fontSize = 15.sp,
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
            }
        }
    }
}

/** Every shelf after the first. */
@Composable
private fun Shelf(section: HomeSection, onSongClick: (Song, List<Song>) -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Column {
        ShelfTitle(section.title, subtitle = section.subtitle)
        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            section.songs.forEach { song ->
                Column(
                    Modifier
                        .width(146.dp)
                        .clickable { onSongClick(song, section.songs) },
                ) {
                    Artwork(song.thumbnailUrl, size = 146, radius = 18)
                    Text(
                        song.title,
                        Modifier.padding(top = 8.dp),
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
            }
        }
    }
}

@Composable
private fun ShelfTitle(title: String, subtitle: String? = null) {
    Column(Modifier.padding(start = 16.dp, end = 16.dp, top = 14.dp, bottom = 10.dp)) {
        // Apple Music's section headings are the loudest thing on the
        // screen after the art. Ours were barely above body text.
        Text(title, fontSize = 21.sp, fontWeight = FontWeight.Bold, letterSpacing = (-0.3).sp)
        if (subtitle != null) {
            Text(
                subtitle,
                Modifier.padding(top = 2.dp),
                fontSize = 12.sp,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun UpdateBanner(version: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .padding(horizontal = 16.dp, vertical = 8.dp)
            .fillMaxWidth()
            .clip(RoundedCornerShape(18.dp))
            .background(scheme.primaryContainer)
            .clickable(onClick = onClick)
            .padding(horizontal = 14.dp, vertical = 12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Icon(
            Icons.Rounded.SystemUpdate,
            null,
            tint = scheme.onPrimaryContainer,
            modifier = Modifier.size(20.dp),
        )
        Column(
            Modifier
                .weight(1f)
                .padding(start = 12.dp),
        ) {
            Text(
                "Mero $version is out",
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onPrimaryContainer,
            )
            Text(
                "Tap to update",
                fontSize = 12.sp,
                color = scheme.onPrimaryContainer.copy(alpha = 0.8f),
            )
        }
    }
}

/**
 * Time-of-day greeting. Boundaries chosen for when people are awake rather
 * than by the clock's quarters: "good evening" at five, not at six, and
 * anything past ten at night is still night rather than a fresh morning.
 */
internal fun greetingFor(hour: Int): String = when (hour) {
    in 5..11 -> "Good morning"
    in 12..16 -> "Good afternoon"
    in 17..21 -> "Good evening"
    else -> "Still up?"
}

/**
 * The greeting with a name on it, when there is one.
 *
 * The late-night one is a question, so the name goes inside it — "Still up,
 * Ana?" rather than "Still up?, Ana". A greeting nobody has given a name to
 * stays exactly as it was.
 */
internal fun greetingFor(hour: Int, name: String): String {
    val greeting = greetingFor(hour)
    val trimmed = name.trim()
    return when {
        trimmed.isEmpty() -> greeting
        greeting.endsWith("?") -> greeting.dropLast(1) + ", " + trimmed + "?"
        else -> "$greeting, $trimmed"
    }
}

private fun greeting(name: String): String =
    greetingFor(Calendar.getInstance().get(Calendar.HOUR_OF_DAY), name)
