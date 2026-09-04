package com.mero.ui.playlist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.material.icons.rounded.DownloadDone
import androidx.compose.material.icons.rounded.DownloadForOffline
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.data.SampleData
import com.mero.domain.Playlist
import com.mero.domain.Song
import com.mero.domain.asClock
import com.mero.ui.components.Artwork

@Composable
fun PlaylistScreen(
    playlist: Playlist,
    nowPlayingId: String?,
    onBack: () -> Unit,
    onSongClick: (Song) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val tracks = SampleData.songs

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
            Spacer(Modifier.weight(1f))
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.Search, "Search in playlist", tint = scheme.onSurfaceVariant)
            }
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.MoreVert, "More", tint = scheme.onSurfaceVariant)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        ) {
            item {
                Row(
                    Modifier.padding(start = 16.dp, end = 16.dp, bottom = 16.dp),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Artwork(playlist.thumbnailUrl, 140, radius = 12)
                    Column(
                        Modifier
                            .weight(1f)
                            .height(140.dp),
                        verticalArrangement = Arrangement.Bottom,
                    ) {
                        Text(
                            playlist.name,
                            fontSize = 24.sp,
                            fontWeight = FontWeight.Medium,
                            lineHeight = 30.sp,
                        )
                        Spacer(Modifier.height(6.dp))
                        Text(
                            "Playlist · ${playlist.trackCount} songs",
                            fontSize = 13.sp,
                            color = scheme.onSurfaceVariant,
                        )
                        Text(
                            "2h 51m · 18 downloaded",
                            fontSize = 13.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    Button(
                        onClick = { tracks.firstOrNull()?.let(onSongClick) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.primary,
                            contentColor = scheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Play", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = { tracks.randomOrNull()?.let(onSongClick) },
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.secondaryContainer,
                            contentColor = scheme.onSecondaryContainer,
                        ),
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle", fontSize = 14.sp, fontWeight = FontWeight.Medium)
                    }
                    Spacer(Modifier.weight(1f))
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Rounded.DownloadForOffline,
                            "Download playlist",
                            tint = scheme.primary,
                        )
                    }
                }
            }

            itemsIndexed(tracks, key = { _, s -> s.id }) { index, song ->
                val playing = song.id == nowPlayingId
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(60.dp)
                        .clickable { onSongClick(song) }
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Text(
                        "${index + 1}",
                        Modifier.width(24.dp),
                        fontSize = 13.sp,
                        textAlign = TextAlign.Center,
                        color = if (playing) scheme.primary else scheme.onSurfaceVariant,
                    )
                    Artwork(song.thumbnailUrl, 44)
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (playing) scheme.primary else scheme.onSurface,
                        )
                        Text(
                            "${song.artist} · ${song.durationSec.asClock()}",
                            fontSize = 12.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (song.downloaded) {
                        Icon(
                            Icons.Rounded.DownloadDone,
                            "Downloaded",
                            Modifier.size(16.dp),
                            tint = scheme.primary,
                        )
                    }
                    IconButton(onClick = {}) {
                        Icon(
                            Icons.Rounded.MoreVert,
                            "More",
                            Modifier.size(20.dp),
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
