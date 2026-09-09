package com.mero.ui.collection

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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.domain.Song
import com.mero.ui.components.Artwork

/**
 * An album or playlist, as a list you choose from.
 *
 * Tapping an album used to start playing it from track one, which is the one
 * thing a track list is for avoiding: half the reason to open an album is that
 * you want the third song on it. The tap now opens this, and playing is a
 * deliberate second action — either the whole thing from the top, shuffled, or
 * from wherever you point at.
 */
@Composable
fun CollectionScreen(
    title: String,
    subtitle: String,
    artworkUrl: String?,
    songs: List<Song>,
    loading: Boolean,
    error: String?,
    nowPlayingId: String?,
    onPlayFrom: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onSongMore: (Song) -> Unit,
    onRetry: () -> Unit,
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
                title,
                Modifier.weight(1f),
                fontSize = 17.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        when {
            loading && songs.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) { CircularProgressIndicator(color = scheme.primary) }

            error != null && songs.isEmpty() -> Box(
                Modifier
                    .fillMaxSize()
                    .clickable(onClick = onRetry),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Couldn't open this.\n$error\n\nTap to retry.",
                    Modifier.padding(32.dp),
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }

            songs.isEmpty() -> Box(
                Modifier.fillMaxSize(),
                contentAlignment = Alignment.Center,
            ) {
                Text(
                    "Nothing playable in here.",
                    color = scheme.onSurfaceVariant,
                )
            }

            else -> LazyColumn(
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                item {
                    Column(
                        Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 24.dp),
                        horizontalAlignment = Alignment.CenterHorizontally,
                    ) {
                        Artwork(artworkUrl, size = 200, radius = 20)
                        Text(
                            title,
                            Modifier.padding(top = 16.dp),
                            fontSize = 22.sp,
                            fontWeight = FontWeight.Bold,
                            textAlign = TextAlign.Center,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            subtitle,
                            Modifier.padding(top = 4.dp),
                            fontSize = 13.sp,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Row(
                            Modifier.padding(top = 18.dp, bottom = 12.dp),
                            horizontalArrangement = Arrangement.spacedBy(10.dp),
                        ) {
                            Button(onClick = onPlayAll) {
                                Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Play")
                            }
                            OutlinedButton(onClick = onShuffle) {
                                Icon(Icons.Rounded.Shuffle, null, Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Shuffle")
                            }
                        }
                    }
                }

                // itemsIndexed, not indexOf inside the row: a 500-track
                // playlist would otherwise scan the list once per visible row.
                itemsIndexed(songs, key = { _, song -> song.id }) { index, song ->
                    val position = index + 1
                    val playing = song.id == nowPlayingId
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(60.dp)
                            .clickable { onPlayFrom(song) }
                            .padding(start = 16.dp, end = 4.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        // The number, not another copy of the cover: every
                        // track on an album shares one, so a column of
                        // identical thumbnails says nothing and track order is
                        // most of what an album list is for.
                        Text(
                            position.toString(),
                            Modifier.width(28.dp),
                            fontSize = 13.sp,
                            color = if (playing) scheme.primary else scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                        Column(
                            Modifier
                                .weight(1f)
                                .padding(start = 8.dp),
                        ) {
                            Text(
                                song.title,
                                fontSize = 15.sp,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                color = if (playing) scheme.primary else scheme.onSurface,
                            )
                            Text(
                                song.artist,
                                fontSize = 12.sp,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                        IconButton(onClick = { onSongMore(song) }) {
                            Icon(Icons.Rounded.MoreVert, "More", tint = scheme.onSurfaceVariant)
                        }
                    }
                }

                item { Spacer(Modifier.height(16.dp)) }
            }
        }
    }
}
