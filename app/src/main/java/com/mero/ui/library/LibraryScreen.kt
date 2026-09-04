package com.mero.ui.library

import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
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
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.Download
import androidx.compose.material.icons.rounded.Favorite
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.LibraryAdd
import androidx.compose.material.icons.rounded.QueueMusic
import androidx.compose.material.icons.rounded.Settings
import androidx.compose.material.icons.rounded.Sort
import androidx.compose.material.icons.rounded.TrendingUp
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.data.SampleData
import com.mero.domain.Playlist
import com.mero.ui.components.Artwork
import com.mero.ui.components.MeroChip
import com.mero.ui.components.ShortcutRow

@Composable
fun LibraryScreen(
    selectedTab: String,
    onTabChange: (String) -> Unit,
    onPlaylistClick: (Playlist) -> Unit,
    onImportClick: () -> Unit,
    onSettingsClick: () -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    Column(modifier.fillMaxSize()) {
        Row(
            Modifier
                .fillMaxWidth()
                .height(64.dp)
                .padding(start = 16.dp, end = 4.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Text("Library", Modifier.weight(1f), fontSize = 22.sp, fontWeight = FontWeight.Medium)
            IconButton(onClick = {}) {
                Icon(Icons.Rounded.Sort, "Sort", tint = scheme.onSurfaceVariant)
            }
            IconButton(onClick = onSettingsClick) {
                Icon(Icons.Rounded.Settings, "Settings", tint = scheme.onSurfaceVariant)
            }
        }

        LazyColumn(
            contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
        ) {
            item {
                Row(
                    Modifier
                        .horizontalScroll(rememberScrollState())
                        .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                ) {
                    SampleData.libraryTabs.forEach { tab ->
                        MeroChip(tab, tab == selectedTab, onClick = { onTabChange(tab) })
                    }
                }
            }

            item {
                ShortcutRow(
                    Icons.Rounded.Favorite, "Liked songs", "128 songs",
                    Icons.Rounded.ChevronRight,
                ) {}
                ShortcutRow(
                    Icons.Rounded.Download, "Downloads", "94 songs · 1.1 GB",
                    Icons.Rounded.ChevronRight,
                ) {}
                ShortcutRow(
                    Icons.Rounded.History, "Recently played", "Last 30 days",
                    Icons.Rounded.ChevronRight,
                ) {}
                ShortcutRow(
                    Icons.Rounded.TrendingUp, "Most played", "All time",
                    Icons.Rounded.ChevronRight,
                ) {}
                HorizontalDivider(
                    Modifier.padding(horizontal = 16.dp, vertical = 12.dp),
                    color = scheme.outlineVariant,
                )
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .padding(start = 16.dp, end = 8.dp, top = 4.dp, bottom = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        "Your playlists",
                        Modifier.weight(1f),
                        fontSize = 14.sp,
                        fontWeight = FontWeight.Medium,
                        color = scheme.onSurfaceVariant,
                    )
                    Row(
                        Modifier
                            .height(32.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, scheme.outlineVariant, RoundedCornerShape(8.dp))
                            .clickable {}
                            .padding(horizontal = 12.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                    ) {
                        Icon(Icons.Rounded.Add, null, Modifier.size(18.dp))
                        Text("New", fontSize = 13.sp, fontWeight = FontWeight.Medium)
                    }
                }
            }

            item {
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable(onClick = onImportClick)
                        .padding(horizontal = 16.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Box(
                        Modifier
                            .size(48.dp)
                            .clip(RoundedCornerShape(8.dp))
                            .border(1.dp, scheme.outline, RoundedCornerShape(8.dp)),
                        contentAlignment = Alignment.Center,
                    ) {
                        Icon(Icons.Rounded.LibraryAdd, null, tint = scheme.primary)
                    }
                    Column(Modifier.weight(1f)) {
                        Text("Import from Spotify or YouTube", fontSize = 16.sp, maxLines = 1)
                        Text(
                            "Metadata only · resolves to YT Music",
                            fontSize = 13.sp,
                            color = scheme.onSurfaceVariant,
                            maxLines = 1,
                        )
                    }
                }
            }

            items(SampleData.playlists, key = { it.id }) { playlist ->
                Row(
                    Modifier
                        .fillMaxWidth()
                        .height(64.dp)
                        .clickable { onPlaylistClick(playlist) }
                        .padding(start = 16.dp, end = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    Artwork(playlist.thumbnailUrl, 48, icon = Icons.Rounded.QueueMusic)
                    Column(Modifier.weight(1f)) {
                        Text(
                            playlist.name,
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                        Text(
                            "Playlist · ${playlist.trackCount} songs",
                            fontSize = 13.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    if (playlist.downloaded) {
                        Icon(
                            Icons.Rounded.Download,
                            "Downloaded",
                            Modifier.size(18.dp),
                            tint = scheme.primary,
                        )
                    }
                }
            }
        }
    }
}
