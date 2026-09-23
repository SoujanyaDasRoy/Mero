package com.mero.ui.playlist

import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Check
import androidx.compose.material.icons.rounded.PhoneAndroid
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.domain.Song
import com.mero.ui.components.Artwork

/**
 * Filling a playlist from inside it.
 *
 * Adding a song used to mean leaving the playlist, finding the song somewhere
 * else, opening its menu, choosing "Add to playlist" and picking this playlist
 * out of a list — for every song. Here the playlist stays the subject: search,
 * tap +, and it is in, without going anywhere. Before anything is typed it
 * offers what you have been listening to, because that is usually what you
 * are building the playlist from.
 */
@Composable
fun AddSongsScreen(
    playlistName: String,
    query: String,
    onQueryChange: (String) -> Unit,
    results: List<Song>,
    suggestions: List<Song>,
    searching: Boolean,
    alreadyIn: Set<String>,
    onAdd: (Song) -> Unit,
    onAddFromPhone: () -> Unit,
    onBack: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val shown = if (query.isBlank()) suggestions else results

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Done") }
            Column(Modifier.weight(1f)) {
                Text("Add songs", fontSize = 17.sp, fontWeight = FontWeight.Medium)
                Text(
                    "to " + playlistName,
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        Row(
            Modifier
                .padding(horizontal = 16.dp, vertical = 4.dp)
                .fillMaxWidth()
                .height(48.dp)
                .clip(RoundedCornerShape(percent = 50))
                .background(scheme.surfaceContainerHigh)
                .padding(horizontal = 16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Rounded.Search, null, tint = scheme.onSurfaceVariant, modifier = Modifier.size(20.dp))
            Box(
                Modifier
                    .weight(1f)
                    .padding(start = 12.dp),
            ) {
                if (query.isEmpty()) {
                    Text("Search for a song", color = scheme.onSurfaceVariant, fontSize = 15.sp)
                }
                BasicTextField(
                    value = query,
                    onValueChange = onQueryChange,
                    singleLine = true,
                    textStyle = TextStyle(color = scheme.onSurface, fontSize = 15.sp),
                    cursorBrush = SolidColor(scheme.primary),
                    modifier = Modifier.fillMaxWidth(),
                )
            }
            if (searching) {
                CircularProgressIndicator(Modifier.size(18.dp), strokeWidth = 2.dp, color = scheme.primary)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(
                top = 4.dp,
                bottom = contentPadding.calculateBottomPadding() + 16.dp,
            ),
        ) {
            item(key = "from-phone") {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .clickable(onClick = onAddFromPhone)
                        .padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(12.dp))
                            .background(scheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PhoneAndroid, null, tint = scheme.onPrimaryContainer)
                    }
                    Column {
                        Text("From this phone", fontSize = 15.sp, fontWeight = FontWeight.Medium)
                        Text(
                            "MP3s and other audio files you already have",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }

            if (query.isBlank() && suggestions.isNotEmpty()) {
                item(key = "heading") {
                    Text(
                        "Recently played",
                        Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                        fontSize = 13.sp,
                        fontWeight = FontWeight.SemiBold,
                        color = scheme.primary,
                    )
                }
            }

            items(shown, key = { "song-" + it.id }) { song ->
                val added = song.id in alreadyIn
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable(enabled = !added) { onAdd(song) }
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Artwork(song.thumbnailUrl, size = 48)
                    Column(Modifier.weight(1f)) {
                        Text(song.title, fontSize = 15.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                        Text(
                            song.artist,
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    // A tick, not a disappearing row: seeing what was just
                    // added is how you know the tap worked.
                    IconButton(onClick = { onAdd(song) }, enabled = !added) {
                        Icon(
                            if (added) Icons.Rounded.Check else Icons.Rounded.Add,
                            contentDescription = if (added) "Added" else "Add",
                            tint = if (added) scheme.primary else scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}
