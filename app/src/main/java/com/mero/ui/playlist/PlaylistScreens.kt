package com.mero.ui.playlist

import androidx.compose.foundation.background
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.rounded.ArrowBack
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Delete
import androidx.compose.material.icons.rounded.Refresh
import androidx.compose.material.icons.rounded.Notes
import androidx.compose.material.icons.rounded.Image
import androidx.compose.material.icons.rounded.Edit
import androidx.compose.material.icons.rounded.MoreVert
import androidx.compose.material.icons.rounded.PlayArrow
import androidx.compose.material.icons.rounded.PlaylistAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Shuffle
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.data.db.PlaylistSummary
import com.mero.data.db.SmartPlaylistSummary
import com.mero.domain.Song
import com.mero.domain.asClock
import com.mero.ui.components.Artwork
import com.mero.ui.components.MeroChip

/* ------------------------- list of playlists ------------------------- */

@Composable
fun PlaylistsTab(
    playlists: List<PlaylistSummary>,
    smartPlaylists: List<SmartPlaylistSummary>,
    onOpen: (String) -> Unit,
    onOpenSmart: (String) -> Unit,
    onCreate: (String) -> Unit,
    onCreateSmart: (String, String, Int, String) -> Unit,
    contentPadding: PaddingValues,
) {
    val scheme = MaterialTheme.colorScheme
    var creating by remember { mutableStateOf(false) }
    var creatingSmart by remember { mutableStateOf(false) }

    if (creating) {
        NamePlaylistDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create",
            onDismiss = { creating = false },
            onConfirm = { name -> creating = false; onCreate(name) },
        )
    }
    if (creatingSmart) {
        SmartPlaylistDialog(
            onDismiss = { creatingSmart = false },
            onConfirm = { name, rule, minPlays, artist ->
                creatingSmart = false
                onCreateSmart(name, rule, minPlays, artist)
            },
        )
    }

    LazyColumn(contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
        item {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable { creating = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.primaryContainer),
                    contentAlignment = Alignment.Center,
                ) {
                    Icon(Icons.Rounded.Add, null, tint = scheme.onPrimaryContainer)
                }
                Text("New playlist", fontSize = 16.sp, fontWeight = FontWeight.Medium)
            }
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable { creatingSmart = true }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Box(
                    Modifier
                        .size(48.dp)
                        .clip(RoundedCornerShape(8.dp))
                        .background(scheme.secondaryContainer),
                    contentAlignment = Alignment.Center,
                ) { Icon(Icons.Rounded.Shuffle, null, tint = scheme.onSecondaryContainer) }
                Column {
                    Text("New smart playlist", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                    Text("Auto-updating rules", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                }
            }
        }

        items(smartPlaylists, key = { it.id }) { smart ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable { onOpenSmart(smart.id) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Artwork(null, 48, icon = Icons.Rounded.Shuffle)
                Column(Modifier.weight(1f)) {
                    Text(smart.name, fontSize = 16.sp, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    Text("${smart.trackCount} tracks · ${smartRuleLabel(smart.rule)}", fontSize = 13.sp, color = scheme.onSurfaceVariant)
                }
            }
        }

        if (playlists.isEmpty() && smartPlaylists.isEmpty()) {
            item {
                Text(
                    "No playlists yet.\nCreate one, then add songs from the ⋮ menu on any track.",
                    Modifier
                        .fillMaxWidth()
                        .padding(32.dp),
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        }

        items(playlists, key = { it.id }) { playlist ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(64.dp)
                    .clickable { onOpen(playlist.id) }
                    .padding(horizontal = 16.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Artwork(playlist.displayArtwork, 48, icon = Icons.Rounded.QueueMusic)
                Column(Modifier.weight(1f)) {
                    Text(
                        playlist.name,
                        fontSize = 16.sp,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        "${playlist.trackCount} " +
                            if (playlist.trackCount == 1) "song" else "songs",
                        fontSize = 13.sp,
                        color = scheme.onSurfaceVariant,
                    )
                }
            }
        }
    }
}

private fun smartRuleLabel(rule: String): String = when (rule) {
    "most" -> "Most played"
    "recent" -> "Recently played"
    "liked" -> "Liked"
    "downloads" -> "Downloads"
    else -> "Custom rules"
}

@Composable
private fun SmartPlaylistDialog(
    onDismiss: () -> Unit,
    onConfirm: (String, String, Int, String) -> Unit,
) {
    var name by remember { mutableStateOf("") }
    var rule by remember { mutableStateOf("most") }
    var minPlays by remember { mutableStateOf("3") }
    var artist by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("New smart playlist") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(name, { name = it }, singleLine = true, label = { Text("Name") })
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("most" to "Most played", "recent" to "Recent", "liked" to "Liked", "downloads" to "Downloads", "custom" to "Custom")
                        .forEach { (value, label) ->
                            MeroChip(label, rule == value, onClick = { rule = value })
                        }
                }
                if (rule == "custom") {
                    OutlinedTextField(
                        minPlays,
                        { minPlays = it.filter(Char::isDigit) },
                        singleLine = true,
                        label = { Text("Minimum plays") },
                    )
                    OutlinedTextField(
                        artist,
                        { artist = it },
                        singleLine = true,
                        label = { Text("Artist contains (optional)") },
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(name, rule, minPlays.toIntOrNull() ?: 0, artist) },
                enabled = name.isNotBlank(),
            ) { Text("Create") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}

/* --------------------------- one playlist --------------------------- */

@Composable
fun PlaylistDetailScreen(
    name: String,
    description: String?,
    coverUri: String?,
    songs: List<Song>,
    nowPlayingId: String?,
    onBack: () -> Unit,
    onPlay: (Song) -> Unit,
    onPlayAll: () -> Unit,
    onShuffle: () -> Unit,
    onRemove: (Song) -> Unit,
    onRename: (String) -> Unit,
    onDescriptionChange: (String) -> Unit,
    onPickCover: () -> Unit,
    onClearCover: () -> Unit,
    onDelete: () -> Unit,
    contentPadding: PaddingValues,
) {
    val scheme = MaterialTheme.colorScheme
    var menuOpen by remember { mutableStateOf(false) }
    var renaming by remember { mutableStateOf(false) }
    var describing by remember { mutableStateOf(false) }
    var confirmingDelete by remember { mutableStateOf(false) }

    if (renaming) {
        NamePlaylistDialog(
            title = "Rename playlist",
            initial = name,
            confirmLabel = "Rename",
            onDismiss = { renaming = false },
            onConfirm = { renaming = false; onRename(it) },
        )
    }
    if (describing) {
        NamePlaylistDialog(
            title = "Description",
            initial = description.orEmpty(),
            confirmLabel = "Save",
            onDismiss = { describing = false },
            onConfirm = { describing = false; onDescriptionChange(it) },
            allowEmpty = true,
        )
    }
    if (confirmingDelete) {
        AlertDialog(
            onDismissRequest = { confirmingDelete = false },
            title = { Text("Delete \"$name\"?") },
            text = { Text("The playlist goes away. The songs themselves aren't affected.") },
            confirmButton = {
                TextButton(onClick = { confirmingDelete = false; onDelete() }) { Text("Delete") }
            },
            dismissButton = {
                TextButton(onClick = { confirmingDelete = false }) { Text("Cancel") }
            },
        )
    }

    Column(Modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(56.dp)
                .padding(horizontal = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            IconButton(onClick = onBack) { Icon(Icons.AutoMirrored.Rounded.ArrowBack, "Back") }
            Text(
                name,
                Modifier.weight(1f),
                fontSize = 18.sp,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Rounded.MoreVert, "More", tint = scheme.onSurfaceVariant)
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = { Text("Rename") },
                        leadingIcon = { Icon(Icons.Rounded.Edit, null) },
                        onClick = { menuOpen = false; renaming = true },
                    )
                    DropdownMenuItem(
                        text = { Text(if (description.isNullOrBlank()) "Add a description" else "Edit description") },
                        leadingIcon = { Icon(Icons.Rounded.Notes, null) },
                        onClick = { menuOpen = false; describing = true },
                    )
                    DropdownMenuItem(
                        text = { Text("Change cover") },
                        leadingIcon = { Icon(Icons.Rounded.Image, null) },
                        onClick = { menuOpen = false; onPickCover() },
                    )
                    if (coverUri != null) {
                        DropdownMenuItem(
                            text = { Text("Use the first track's cover") },
                            leadingIcon = { Icon(Icons.Rounded.Refresh, null) },
                            onClick = { menuOpen = false; onClearCover() },
                        )
                    }
                    DropdownMenuItem(
                        text = { Text("Delete playlist") },
                        leadingIcon = { Icon(Icons.Rounded.Delete, null) },
                        onClick = { menuOpen = false; confirmingDelete = true },
                    )
                }
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        ) {
            item {
                // The cover the user chose, or the first track's. Tapping it
                // is the obvious way to change it, so it is also the way.
                Column(
                    Modifier
                        .fillMaxWidth()
                        .padding(bottom = 12.dp),
                    horizontalAlignment = Alignment.CenterHorizontally,
                ) {
                    Artwork(
                        coverUri ?: songs.firstOrNull()?.thumbnailUrl,
                        size = 168,
                        radius = 20,
                        icon = Icons.Rounded.QueueMusic,
                        modifier = Modifier.clickable(onClick = onPickCover),
                    )
                    Text(
                        name,
                        Modifier.padding(start = 24.dp, end = 24.dp, top = 14.dp),
                        fontSize = 20.sp,
                        fontWeight = FontWeight.Bold,
                        textAlign = TextAlign.Center,
                        maxLines = 2,
                        overflow = TextOverflow.Ellipsis,
                    )
                    Text(
                        songs.size.toString() + if (songs.size == 1) " song" else " songs",
                        Modifier.padding(top = 2.dp),
                        fontSize = 12.sp,
                        color = scheme.onSurfaceVariant,
                    )
                    if (!description.isNullOrBlank()) {
                        Text(
                            description,
                            Modifier
                                .padding(start = 28.dp, end = 28.dp, top = 8.dp)
                                .clickable { describing = true },
                            fontSize = 13.sp,
                            lineHeight = 18.sp,
                            color = scheme.onSurfaceVariant,
                            textAlign = TextAlign.Center,
                        )
                    }
                }
            }
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Button(
                        onClick = onPlayAll,
                        enabled = songs.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.primary,
                            contentColor = scheme.onPrimary,
                        ),
                    ) {
                        Icon(Icons.Rounded.PlayArrow, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Play", fontWeight = FontWeight.Medium)
                    }
                    Button(
                        onClick = onShuffle,
                        enabled = songs.isNotEmpty(),
                        colors = ButtonDefaults.buttonColors(
                            containerColor = scheme.secondaryContainer,
                            contentColor = scheme.onSecondaryContainer,
                        ),
                    ) {
                        Icon(Icons.Rounded.Shuffle, null, Modifier.size(20.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Shuffle", fontWeight = FontWeight.Medium)
                    }
                }
            }

            if (songs.isEmpty()) {
                item {
                    Text(
                        "Nothing here yet.\nUse the ⋮ menu on any song to add it.",
                        Modifier
                            .fillMaxWidth()
                            .padding(32.dp),
                        color = scheme.onSurfaceVariant,
                        textAlign = TextAlign.Center,
                    )
                }
            }

            items(songs, key = { it.id }) { song ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { onPlay(song) }
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(14.dp),
                ) {
                    Artwork(song.thumbnailUrl, 48)
                    Column(Modifier.weight(1f)) {
                        Text(
                            song.title,
                            fontSize = 15.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            color = if (song.id == nowPlayingId) scheme.primary else scheme.onSurface,
                        )
                        Text(
                            "${song.artist} · ${song.durationSec.asClock()}",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                    IconButton(onClick = { onRemove(song) }) {
                        Icon(
                            Icons.Rounded.Close,
                            "Remove from playlist",
                            Modifier.size(20.dp),
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

/* ----------------------- add a song to a playlist ----------------------- */

@Composable
fun AddToPlaylistSheet(
    song: Song,
    playlists: List<PlaylistSummary>,
    onAdd: (String) -> Unit,
    onCreateAndAdd: (String) -> Unit,
    onClose: () -> Unit,
) {
    val scheme = MaterialTheme.colorScheme
    var creating by remember { mutableStateOf(false) }

    if (creating) {
        NamePlaylistDialog(
            title = "New playlist",
            initial = "",
            confirmLabel = "Create and add",
            onDismiss = { creating = false },
            onConfirm = { name -> creating = false; onCreateAndAdd(name) },
        )
    }

    Column(
        Modifier
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
                Text("Add to playlist", fontSize = 18.sp, fontWeight = FontWeight.Medium)
                Text(
                    song.title,
                    fontSize = 12.sp,
                    color = scheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        }

        LazyColumn {
            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { creating = true }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        Modifier
                            .size(44.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .background(scheme.primaryContainer),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.PlaylistAdd, null, tint = scheme.onPrimaryContainer)
                    }
                    Text("New playlist", fontSize = 16.sp, fontWeight = FontWeight.Medium)
                }
            }
            items(playlists, key = { it.id }) { playlist ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { onAdd(playlist.id) }
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Artwork(playlist.displayArtwork, 44, icon = Icons.Rounded.QueueMusic)
                    Column(Modifier.weight(1f)) {
                        Text(playlist.name, fontSize = 16.sp, maxLines = 1)
                        Text(
                            "${playlist.trackCount} " +
                                if (playlist.trackCount == 1) "song" else "songs",
                            fontSize = 12.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun NamePlaylistDialog(
    title: String,
    initial: String,
    confirmLabel: String,
    onDismiss: () -> Unit,
    onConfirm: (String) -> Unit,
    /** A description may be cleared; a name may not. */
    allowEmpty: Boolean = false,
) {
    var text by remember { mutableStateOf(initial) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            OutlinedTextField(
                value = text,
                onValueChange = { text = it },
                singleLine = !allowEmpty,
                minLines = if (allowEmpty) 2 else 1,
                label = { Text(if (allowEmpty) "Description" else "Name") },
            )
        },
        confirmButton = {
            TextButton(
                onClick = { onConfirm(text) },
                enabled = allowEmpty || text.isNotBlank(),
            ) { Text(confirmLabel) }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } },
    )
}
