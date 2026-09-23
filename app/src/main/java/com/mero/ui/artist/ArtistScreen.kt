package com.mero.ui.artist

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.background
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.ui.draw.clip
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.material3.TextButton
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.domain.ArtistAlbum
import com.mero.domain.ArtistPageData
import com.mero.domain.Song
import com.mero.ui.components.Artwork
import com.mero.ui.components.SongRow

@Composable
fun ArtistScreen(
    data: ArtistPageData?,
    loading: Boolean,
    error: String?,
    onBack: () -> Unit,
    onAlbumClick: (ArtistAlbum) -> Unit,
    onPlaylistClick: (ArtistAlbum) -> Unit,
    onSongClick: (Song) -> Unit,
    onSongMore: (Song) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    Column(modifier.fillMaxSize()) {
        Row(
            Modifier.fillMaxWidth().padding(end = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.Rounded.ArrowBack, "Back") }
            Text(
                data?.name ?: "Artist",
                Modifier.weight(1f),
                fontSize = 20.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        when {
            loading -> Column(
                Modifier.fillMaxSize(),
                horizontalAlignment = Alignment.CenterHorizontally,
                verticalArrangement = Arrangement.Center,
            ) { CircularProgressIndicator(color = scheme.primary) }
            error != null -> Text(error, Modifier.padding(24.dp), color = scheme.error)
            data != null -> {
                // Songs first, and every section short until asked: the albums
                // section alone can hold hundreds of singles and features, and
                // it used to sit above the songs, which were then unreachable.
                var allSongs by remember(data.id) { mutableStateOf(false) }
                var allAlbums by remember(data.id) { mutableStateOf(false) }
                var allPlaylists by remember(data.id) { mutableStateOf(false) }
                LazyColumn(
                    contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
                ) {
                    item {
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(16.dp),
                        ) {
                            Artwork(data.thumbnailUrl, size = 88, radius = 44)
                            Text(
                                listOfNotNull(
                                    data.songs.size.takeIf { it > 0 }?.let { countOf(it, "song") },
                                    data.albums.size.takeIf { it > 0 }?.let { countOf(it, "release") },
                                ).joinToString(" · ").ifEmpty { "Artist" },
                                fontSize = 14.sp,
                                color = scheme.onSurfaceVariant,
                            )
                        }
                    }
                    if (data.songs.isNotEmpty()) {
                        item { SectionTitle("Songs") }
                        items(data.songs.take(if (allSongs) Int.MAX_VALUE else SONGS_SHOWN), key = { "s-" + it.id }) { song ->
                            SongRow(song, onClick = { onSongClick(song) }, onMore = { onSongMore(song) })
                        }
                        if (!allSongs && data.songs.size > SONGS_SHOWN) {
                            item { ShowAll(data.songs.size) { allSongs = true } }
                        }
                    }
                    if (data.albums.isNotEmpty()) {
                        item { SectionTitle("Albums & singles") }
                        items(data.albums.take(if (allAlbums) Int.MAX_VALUE else OTHERS_SHOWN), key = { "a-" + it.browseId }) { album ->
                            ReleaseRow(album, album.year?.toString() ?: "Album") { onAlbumClick(album) }
                        }
                        if (!allAlbums && data.albums.size > OTHERS_SHOWN) {
                            item { ShowAll(data.albums.size) { allAlbums = true } }
                        }
                    }
                    if (data.playlists.isNotEmpty()) {
                        item { SectionTitle("Playlists") }
                        items(data.playlists.take(if (allPlaylists) Int.MAX_VALUE else OTHERS_SHOWN), key = { "pl-" + it.browseId }) { playlist ->
                            ReleaseRow(playlist, "Playlist") { onPlaylistClick(playlist) }
                        }
                        if (!allPlaylists && data.playlists.size > OTHERS_SHOWN) {
                            item { ShowAll(data.playlists.size) { allPlaylists = true } }
                        }
                    }
                }
            }
        }
    }
}

private const val SONGS_SHOWN = 10
private const val OTHERS_SHOWN = 8

private fun countOf(n: Int, noun: String) = "$n $noun" + if (n == 1) "" else "s"

@Composable
private fun SectionTitle(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, top = 20.dp, bottom = 8.dp),
        fontSize = 18.sp,
        fontWeight = FontWeight.SemiBold,
    )
}

@Composable
private fun ShowAll(total: Int, onClick: () -> Unit) {
    TextButton(onClick = onClick, modifier = Modifier.padding(start = 8.dp)) {
        Text("Show all $total")
    }
}

@Composable
private fun ReleaseRow(item: ArtistAlbum, subtitle: String, onClick: () -> Unit) {
    val scheme = MaterialTheme.colorScheme
    Row(
        Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(horizontal = 16.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Artwork(item.thumbnailUrl, size = 56, radius = 8)
        Column(Modifier.weight(1f)) {
            Text(item.title, maxLines = 1, overflow = TextOverflow.Ellipsis, fontWeight = FontWeight.Medium)
            Text(subtitle, fontSize = 13.sp, color = scheme.onSurfaceVariant)
        }
        PlayAffordance()
    }
}

/**
 * The end-of-row play mark on albums and playlists.
 *
 * It used to be the word "Play" in accent colour, which read as a button but
 * was not one — the whole row is the tap target, so the word both said
 * something the row already implied and invited a press that was not
 * separately handled. A glyph states the same thing without claiming to be a
 * control of its own, and it survives translation and long titles.
 */
@Composable
private fun PlayAffordance() {
    val scheme = MaterialTheme.colorScheme
    Box(
        Modifier
            .size(34.dp)
            .clip(CircleShape)
            .background(scheme.surfaceContainerHighest),
        contentAlignment = Alignment.Center,
    ) {
        Icon(
            Icons.Rounded.PlayArrow,
            contentDescription = null,
            tint = scheme.primary,
            modifier = Modifier.size(20.dp),
        )
    }
}
