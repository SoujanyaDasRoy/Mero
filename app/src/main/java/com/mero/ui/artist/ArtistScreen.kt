package com.mero.ui.artist

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ArrowBack
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
    onSongClick: (Song) -> Unit,
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
            data != null -> LazyColumn(
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                item {
                    Row(
                        Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Artwork(data.thumbnailUrl, size = 88, radius = 44)
                        Column {
                            Text("${data.albums.size} albums", fontSize = 14.sp)
                            Text("Full discography", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                        }
                    }
                }
                if (data.albums.isNotEmpty()) {
                    item {
                        Text("Albums", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), fontWeight = FontWeight.Medium)
                    }
                    items(data.albums, key = { it.browseId }) { album ->
                        Row(
                            Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(12.dp),
                        ) {
                            Artwork(album.thumbnailUrl, size = 56, radius = 8)
                            Column(Modifier.weight(1f)) {
                                Text(album.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                Text(album.year?.toString() ?: "Album", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                            }
                            IconButton(onClick = { onAlbumClick(album) }) { Text("Play", fontSize = 12.sp) }
                        }
                    }
                }
                if (data.songs.isNotEmpty()) {
                    item { Text("Songs", Modifier.padding(start = 16.dp, top = 16.dp, bottom = 8.dp), fontWeight = FontWeight.Medium) }
                    items(data.songs, key = { it.id }) { song -> SongRow(song, onClick = { onSongClick(song) }) }
                }
            }
        }
    }
}