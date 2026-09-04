package com.mero.ui.library

import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.domain.Song
import com.mero.ui.components.MeroChip
import com.mero.ui.components.SongRow

val LIBRARY_TABS = listOf("Liked", "Recent", "Most played")

@Composable
fun LibraryScreen(
    selectedTab: String,
    onTabChange: (String) -> Unit,
    liked: List<Song>,
    recentlyPlayed: List<Song>,
    mostPlayed: List<Song>,
    nowPlayingId: String?,
    onSongClick: (Song) -> Unit,
    onSettingsClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val songs = when (selectedTab) {
        "Recent" -> recentlyPlayed
        "Most played" -> mostPlayed
        else -> liked
    }

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Medium)
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

        if (songs.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                Text(
                    when (selectedTab) {
                        "Recent" -> "Nothing played yet."
                        "Most played" -> "No listening history yet."
                        else -> "No liked songs yet.\nTap the heart on a track to save it here."
                    },
                    Modifier.padding(32.dp),
                    color = scheme.onSurfaceVariant,
                    textAlign = TextAlign.Center,
                )
            }
        } else {
            LazyColumn(
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                items(songs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        highlighted = song.id == nowPlayingId,
                        onClick = { onSongClick(song) },
                    )
                }
            }
        }
    }
}
