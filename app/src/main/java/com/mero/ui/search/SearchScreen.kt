package com.mero.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.History
import androidx.compose.material.icons.rounded.Mic
import androidx.compose.material.icons.rounded.Search
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
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.domain.Song
import com.mero.ui.components.MeroChip
import com.mero.ui.components.SongRow

private val SEARCH_TABS = listOf("Songs", "Albums", "Artists", "Playlists")

@Composable
fun SearchScreen(
    recentSearches: List<String>,
    onRemoveRecent: (String) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    selectedTab: String,
    onTabChange: (String) -> Unit,
    results: List<Song>,
    suggestions: List<String>,
    suggestedSongs: List<Song>,
    onSuggestionClick: (String) -> Unit,
    nowPlayingId: String?,
    onSongClick: (Song) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme

    Column(modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 8.dp)) {
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .clip(RoundedCornerShape(28.dp))
                    .background(scheme.surfaceContainerHigh)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                Icon(
                    Icons.Rounded.Search,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                )
                Box(Modifier.weight(1f)) {
                    if (query.isEmpty()) {
                        Text(
                            "Songs, albums, artists",
                            fontSize = 16.sp,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                    BasicTextField(
                        value = query,
                        onValueChange = onQueryChange,
                        singleLine = true,
                        textStyle = TextStyle(color = scheme.onSurface, fontSize = 16.sp),
                        cursorBrush = SolidColor(scheme.primary),
                        keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                        keyboardActions = KeyboardActions(onSearch = { onSearch() }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                    Icon(
                        if (query.isEmpty()) Icons.Rounded.Mic else Icons.Rounded.Close,
                        contentDescription = if (query.isEmpty()) "Voice search" else "Clear",
                        tint = scheme.onSurfaceVariant,
                    )
                }
            }
        }

        val showingSuggestions = suggestions.isNotEmpty() || suggestedSongs.isNotEmpty()

        if (showingSuggestions) {
            // Live suggestions while typing — the user shouldn't have to finish
            // the word and hit search to see what YouTube would match.
            LazyColumn(
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                items(suggestions, key = { "q-$it" }) { term ->
                    Row(
                        Modifier
                            .fillMaxWidth()
                            .height(52.dp)
                            .clickable { onSuggestionClick(term) }
                            .padding(start = 16.dp, end = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(16.dp),
                    ) {
                        Icon(
                            Icons.Rounded.Search,
                            contentDescription = null,
                            tint = scheme.onSurfaceVariant,
                            modifier = Modifier.size(20.dp),
                        )
                        Text(
                            term,
                            Modifier.weight(1f),
                            fontSize = 16.sp,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                }
                if (suggestedSongs.isNotEmpty()) {
                    item {
                        Text(
                            "Songs",
                            Modifier.padding(start = 16.dp, top = 12.dp, bottom = 4.dp),
                            fontSize = 13.sp,
                            fontWeight = FontWeight.Medium,
                            color = scheme.onSurfaceVariant,
                        )
                    }
                }
                items(suggestedSongs, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        highlighted = song.id == nowPlayingId,
                        onClick = { onSongClick(song) },
                    )
                }
            }
        } else if (query.isBlank()) {
            SearchIdle(
                recentSearches = recentSearches,
                onRemoveRecent = onRemoveRecent,
                onQueryChange = { onQueryChange(it); onSearch() },
                contentPadding = contentPadding,
            )
        } else {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, bottom = 12.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SEARCH_TABS.forEach { tab ->
                    MeroChip(tab, selected = tab == selectedTab, onClick = { onTabChange(tab) })
                }
            }
            LazyColumn(
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding()),
            ) {
                items(results, key = { it.id }) { song ->
                    SongRow(
                        song = song,
                        subtitle = when (selectedTab) {
                            "Albums" -> "Album · ${song.artist}"
                            "Artists" -> "Artist"
                            else -> "Song · ${song.artist}"
                        },
                        highlighted = song.id == nowPlayingId,
                        onClick = { onSongClick(song) },
                        onMore = {},
                    )
                }
            }
        }
    }
}

@Composable
private fun SearchIdle(
    recentSearches: List<String>,
    onRemoveRecent: (String) -> Unit,
    onQueryChange: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val scheme = MaterialTheme.colorScheme
    LazyColumn(contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding())) {
        item {
            Text(
                "Recent searches",
                Modifier.padding(start = 16.dp, top = 8.dp, bottom = 4.dp),
                fontSize = 14.sp,
                fontWeight = FontWeight.Medium,
                color = scheme.onSurfaceVariant,
            )
        }
        items(recentSearches) { term ->
            Row(
                Modifier
                    .fillMaxWidth()
                    .height(56.dp)
                    .padding(start = 16.dp, end = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(16.dp),
            ) {
                Icon(
                    Icons.Rounded.History,
                    contentDescription = null,
                    tint = scheme.onSurfaceVariant,
                    modifier = Modifier.size(22.dp),
                )
                Text(
                    term,
                    Modifier
                        .weight(1f)
                        .clickable { onQueryChange(term) },
                    fontSize = 16.sp,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                IconButton(onClick = { onRemoveRecent(term) }) {
                    Icon(
                        Icons.Rounded.Close,
                        contentDescription = "Remove",
                        tint = scheme.onSurfaceVariant,
                        modifier = Modifier.size(20.dp),
                    )
                }
            }
        }
    }
}
