package com.mero.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.AlertDialog
import androidx.compose.runtime.Composable
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.domain.Song
import com.mero.data.db.SmartPlaylistSummary
import com.mero.ui.components.MeroChip
import com.mero.ui.components.SongRow

val LIBRARY_TABS = listOf("Playlists", "Liked", "Downloads", "Recent", "Most played")

@Composable
fun LibraryScreen(
    selectedTab: String,
    onTabChange: (String) -> Unit,
    liked: List<Song>,
    recentlyPlayed: List<Song>,
    mostPlayed: List<Song>,
    downloads: List<Song>,
    playlists: List<com.mero.data.db.PlaylistSummary>,
    smartPlaylists: List<SmartPlaylistSummary>,
    onOpenPlaylist: (String) -> Unit,
    onOpenSmartPlaylist: (String) -> Unit,
    onCreatePlaylist: (String) -> Unit,
    onCreateSmartPlaylist: (String, String, Int, String) -> Unit,
    onSongMore: (Song) -> Unit,
    nowPlayingId: String?,
    onSongClick: (Song) -> Unit,
    onSettingsClick: () -> Unit,
    onBrowse: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val songs = when (selectedTab) {
        "Recent" -> recentlyPlayed
        "Most played" -> mostPlayed
        "Downloads" -> downloads
        else -> liked
    }

    Column(modifier.fillMaxSize()) {
        var naming by remember { mutableStateOf(false) }
        if (naming) {
            NewPlaylistDialog(
                onDismiss = { naming = false },
                onConfirm = { naming = false; onCreatePlaylist(it) },
            )
        }

        Row(
            Modifier
                .fillMaxWidth()
                .height(72.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(Modifier.weight(1f)) {
                Text("Library", fontSize = 22.sp, fontWeight = FontWeight.Bold)
                // What is actually in here, so the screen says something before
                // a tab is chosen.
                Text(
                    summaryOf(playlists.size + smartPlaylists.size, liked.size, downloads.size),
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
            // Reachable from every tab, not only from inside Playlists: making
            // a playlist is the thing people come to this screen to do.
            IconButton(onClick = { naming = true }) {
                Icon(Icons.Rounded.PlaylistAdd, "New playlist", tint = scheme.primary)
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Rounded.Settings, "Settings", tint = scheme.onSurfaceVariant)
            }
        }

        Row(
            Modifier
                .horizontalScroll(rememberScrollState())
                .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            LIBRARY_TABS.forEach { tab ->
                MeroChip(tab, tab == selectedTab, onClick = { onTabChange(tab) })
            }
        }

        if (selectedTab == "Playlists") {
            com.mero.ui.playlist.PlaylistsTab(
                playlists = playlists,
                smartPlaylists = smartPlaylists,
                onOpen = onOpenPlaylist,
                onOpenSmart = onOpenSmartPlaylist,
                onCreate = onCreatePlaylist,
                onCreateSmart = onCreateSmartPlaylist,
                contentPadding = contentPadding,
            )
        } else if (songs.isEmpty()) {
            // An empty tab used to be a sentence alone in the middle of
            // nothing. It now says what would fill it and offers the way there.
            EmptyTab(
                message = when (selectedTab) {
                    "Recent" -> "Nothing played yet."
                    "Most played" -> "Play a few things and your favourites collect here."
                    "Downloads" -> "Nothing downloaded yet." + NL +
                        "Use the menu on a track to keep it offline."
                    else -> "No liked songs yet." + NL +
                        "Tap the heart on a track to save it here."
                },
                actionLabel = "Find something to play",
                onAction = onBrowse,
            )
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                items(songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        highlighted = song.id == nowPlayingId,
                        onClick = { onSongClick(song) },
                        onMore = { onSongMore(song) },
                    )
                }
            }
        }
    }
}

private const val NL = "\n"

/** One line describing what the library holds, for the header. */
internal fun summaryOf(playlists: Int, liked: Int, downloads: Int): String {
    val parts = buildList {
        if (playlists > 0) add(playlists.toString() + if (playlists == 1) " playlist" else " playlists")
        if (liked > 0) add("$liked liked")
        if (downloads > 0) add("$downloads downloaded")
    }
    return if (parts.isEmpty()) "Nothing saved yet" else parts.joinToString(" · ")
}

@Composable
private fun EmptyTab(message: String, actionLabel: String, onAction: () -> Unit) {
    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
        Column(
            Modifier.padding(32.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Text(
                message,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                lineHeight = 20.sp,
            )
            Spacer(Modifier.height(16.dp))
            FilledTonalButton(onClick = onAction) { Text(actionLabel) }
        }
    }
}

@Composable
private fun NewPlaylistDialog(onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var name by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New playlist") },
        text = {
            OutlinedTextField(
                value = name,
                onValueChange = { name = it },
                singleLine = true,
                label = { Text("Name") },
            )
        },
        confirmButton = {
            TextButton(onClick = { onConfirm(name) }, enabled = name.isNotBlank()) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
