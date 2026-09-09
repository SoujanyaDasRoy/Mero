package com.mero.ui.search

import androidx.compose.foundation.background
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
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.GridItemSpan
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.offset
import androidx.compose.ui.draw.rotate
import androidx.compose.ui.layout.ContentScale
import coil3.compose.AsyncImage
import androidx.compose.foundation.clickable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.BasicTextField
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Close
import androidx.compose.material.icons.rounded.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.derivedStateOf
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.SolidColor
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.domain.SearchItem
import com.mero.domain.Song
import com.mero.ui.components.Artwork
import com.mero.ui.components.MeroChip
import com.mero.ui.components.SongRow

private val SEARCH_TABS = listOf("Songs", "Albums", "Artists", "Playlists")

@Composable
fun SearchScreen(
    genres: List<GenreCardData>,
    onGenreClick: (String) -> Unit,
    suggestedArtists: List<SearchItem>,
    recentlyPlayed: List<Song>,
    onSongClick: (Song) -> Unit,
    query: String,
    onQueryChange: (String) -> Unit,
    onSearch: () -> Unit,
    selectedTab: String,
    onTabChange: (String) -> Unit,
    results: List<SearchItem>,
    isSearching: Boolean,
    isLoadingMore: Boolean,
    hasMoreResults: Boolean,
    onLoadMore: () -> Unit,
    nowPlayingId: String?,
    onResultClick: (SearchItem) -> Unit,
    onSongMore: (Song) -> Unit,
    contentPadding: PaddingValues,
    modifier: Modifier = Modifier,
) {
    val scheme = MaterialTheme.colorScheme
    val focusManager = LocalFocusManager.current
    val keyboard = LocalSoftwareKeyboardController.current

    Column(modifier.fillMaxSize()) {
        Box(Modifier.padding(start = 16.dp, end = 16.dp, top = 12.dp, bottom = 4.dp)) {
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
                            "Search songs, artists, albums...",
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
                        keyboardActions = KeyboardActions(onSearch = {
                            focusManager.clearFocus()
                            onSearch()
                        }),
                        modifier = Modifier.fillMaxWidth(),
                    )
                }
                if (query.isNotEmpty()) {
                    IconButton(onClick = { onQueryChange("") }, modifier = Modifier.size(40.dp)) {
                        Icon(
                            Icons.Rounded.Close,
                            contentDescription = "Clear",
                            tint = scheme.onSurfaceVariant,
                        )
                    }
                }
            }
        }

        Box(Modifier.fillMaxWidth().height(4.dp)) {
            if (isSearching) {
                LinearProgressIndicator(
                    modifier = Modifier.fillMaxWidth().height(2.dp),
                    color = scheme.primary,
                    trackColor = scheme.surfaceContainerHigh,
                )
            }
        }

        if (query.isBlank()) {
            IdleSuggestions(
                artists = suggestedArtists,
                recentlyPlayed = recentlyPlayed,
                genres = genres,
                onArtistClick = onResultClick,
                onSongClick = onSongClick,
                onGenreClick = onGenreClick,
                contentPadding = contentPadding,
            )
        } else {
            Row(
                Modifier
                    .horizontalScroll(rememberScrollState())
                    .padding(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                SEARCH_TABS.forEach { tab ->
                    MeroChip(tab, selected = tab == selectedTab, onClick = { onTabChange(tab) })
                }
            }

            val listState = rememberLazyListState()

            val shouldLoadMore = remember {
                derivedStateOf {
                    val totalItems = listState.layoutInfo.totalItemsCount
                    val lastVisibleIndex = listState.layoutInfo.visibleItemsInfo.lastOrNull()?.index ?: 0
                    totalItems > 0 && lastVisibleIndex >= totalItems - 4
                }
            }

            LaunchedEffect(shouldLoadMore.value) {
                if (shouldLoadMore.value && hasMoreResults && !isLoadingMore && !isSearching) {
                    onLoadMore()
                }
            }

            LazyColumn(
                state = listState,
                contentPadding = PaddingValues(bottom = contentPadding.calculateBottomPadding() + 80.dp),
            ) {
                items(results, key = { it.id }) { result ->
                    val rowSong = result.song ?: Song(
                        id = result.id,
                        title = result.title,
                        artist = result.subtitle,
                        thumbnailUrl = result.thumbnailUrl,
                    )
                    SongRow(
                        song = rowSong,
                        subtitle = result.subtitle,
                        highlighted = result.song?.id == nowPlayingId,
                        onClick = {
                            // Picking a result is the end of typing. Without
                            // this the track started but the keyboard stayed
                            // up, covering half the screen and needing a back
                            // press of its own to dismiss.
                            // Both: clearing focus alone leaves the IME up on
                            // some keyboards, and hiding alone leaves the field
                            // focused so the next tap re-opens it.
                            keyboard?.hide()
                            focusManager.clearFocus()
                            onResultClick(result)
                        },
                        onMore = result.song?.let { { onSongMore(it) } },
                    )
                }

                if (isLoadingMore) {
                    item(key = "loading-more") {
                        Box(
                            Modifier
                                .fillMaxWidth()
                                .padding(16.dp),
                            contentAlignment = Alignment.Center,
                        ) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(28.dp),
                                color = scheme.primary,
                                strokeWidth = 2.5.dp,
                            )
                        }
                    }
                }
            }
        }
    }
}

/** A genre tile: the name, and a cover from that shelf once one has loaded. */
data class GenreCardData(val title: String, val artworkUrl: String?)

/**
 * Somewhere to go before anything is typed.
 *
 * The images come from the home shelves, which are already loaded and already
 * grouped by exactly the thing a genre card wants to be — a name and some
 * music that sounds like it. No extra requests: an empty search box should not
 * cost a round trip.
 *
 * The chips that used to sit here said the same words in the same grey, so
 * nothing drew the eye and nothing suggested what the genre sounded like. Real
 * cover art does both.
 */
@Composable
private fun GenreGrid(
    genres: List<GenreCardData>,
    onGenreClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    if (genres.isEmpty()) return
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            top = 8.dp,
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        items(genres, key = { it.title }) { genre ->
            GenreCard(
                title = genre.title,
                artworkUrl = genre.artworkUrl,
                tint = genreTint(genre.title),
                onClick = { onGenreClick(genre.title) },
            )
        }
    }
}

@Composable
private fun GenreCard(
    title: String,
    artworkUrl: String?,
    tint: Color,
    onClick: () -> Unit,
) {
    Box(
        Modifier
            .fillMaxWidth()
            .aspectRatio(1.6f)
            .clip(RoundedCornerShape(12.dp))
            .background(tint)
            .clickable(onClick = onClick),
    ) {
        Text(
            title,
            Modifier
                .align(Alignment.TopStart)
                .padding(12.dp)
                .fillMaxWidth(0.72f),
            color = Color.White,
            fontSize = 15.sp,
            lineHeight = 19.sp,
            fontWeight = FontWeight.Bold,
            maxLines = 2,
            overflow = TextOverflow.Ellipsis,
        )
        // Tilted and overhanging the corner, the way a record sleeve would sit
        // in a crate. Keeps the name unobstructed at any card size.
        AsyncImage(
            model = artworkUrl,
            contentDescription = null,
            contentScale = ContentScale.Crop,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .offset(x = 12.dp, y = 8.dp)
                .rotate(24f)
                .size(64.dp)
                .clip(RoundedCornerShape(6.dp)),
        )
    }
}

/**
 * A stable colour per genre.
 *
 * Derived from the name so a shelf keeps the same colour between sessions
 * without anyone maintaining a lookup table, and drawn from a fixed palette so
 * the result is always dark enough for white text.
 */
private fun genreTint(title: String): Color {
    val palette = listOf(
        Color(0xFF1E3264), Color(0xFF8D67AB), Color(0xFFE8115B), Color(0xFF148A08),
        Color(0xFFB95700), Color(0xFF503750), Color(0xFF477D95), Color(0xFF7358FF),
        Color(0xFFAF2896), Color(0xFF0D73EC), Color(0xFFDC148C), Color(0xFF056952),
    )
    val index = ((title.hashCode() % palette.size) + palette.size) % palette.size
    return palette[index]
}

/**
 * What the search screen shows before anything has been typed.
 *
 * It was a grid of coloured rectangles with a word in each. That is a fine way
 * to browse genres and a poor way to start, because none of it is about the
 * person looking at it: everything they have played is already on the device
 * and none of it was here. Faces and covers first, words after.
 *
 * One grid, with the rows as full-width items inside it — not a LazyColumn
 * wrapped around a grid, which measures the grid against an infinite height
 * and crashes.
 */
@Composable
private fun IdleSuggestions(
    artists: List<SearchItem>,
    recentlyPlayed: List<Song>,
    genres: List<GenreCardData>,
    onArtistClick: (SearchItem) -> Unit,
    onSongClick: (Song) -> Unit,
    onGenreClick: (String) -> Unit,
    contentPadding: PaddingValues,
) {
    val scheme = MaterialTheme.colorScheme
    LazyVerticalGrid(
        columns = GridCells.Fixed(2),
        contentPadding = PaddingValues(
            start = 16.dp,
            end = 16.dp,
            bottom = contentPadding.calculateBottomPadding() + 80.dp,
        ),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        if (artists.isNotEmpty()) {
            item(key = "artists-heading", span = { GridItemSpan(maxLineSpan) }) {
                IdleHeading("Artists you play")
            }
            item(key = "artists", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(16.dp),
                ) {
                    artists.forEach { artist ->
                        Column(
                            Modifier
                                .width(96.dp)
                                .clickable { onArtistClick(artist) },
                            horizontalAlignment = Alignment.CenterHorizontally,
                        ) {
                            // Circular, the way every music app draws a person
                            // rather than a record.
                            Artwork(artist.thumbnailUrl, size = 96, radius = 48)
                            Text(
                                artist.title,
                                Modifier.padding(top = 8.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                textAlign = TextAlign.Center,
                                maxLines = 2,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        if (recentlyPlayed.isNotEmpty()) {
            item(key = "recent-heading", span = { GridItemSpan(maxLineSpan) }) {
                IdleHeading("Pick up where you left off")
            }
            item(key = "recent", span = { GridItemSpan(maxLineSpan) }) {
                Row(
                    Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp),
                ) {
                    recentlyPlayed.take(10).forEach { song ->
                        Column(
                            Modifier
                                .width(124.dp)
                                .clickable { onSongClick(song) },
                        ) {
                            Artwork(song.thumbnailUrl, size = 124, radius = 14)
                            Text(
                                song.title,
                                Modifier.padding(top = 8.dp),
                                fontSize = 13.sp,
                                fontWeight = FontWeight.Medium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                            Text(
                                song.artist,
                                fontSize = 11.sp,
                                color = scheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                            )
                        }
                    }
                }
            }
        }

        item(key = "browse-heading", span = { GridItemSpan(maxLineSpan) }) {
            IdleHeading("Browse")
        }
        items(genres, key = { it.title }) { genre ->
            GenreCard(
                title = genre.title,
                artworkUrl = genre.artworkUrl,
                tint = genreTint(genre.title),
                onClick = { onGenreClick(genre.title) },
            )
        }
    }
}

@Composable
private fun IdleHeading(text: String) {
    Text(
        text,
        Modifier.padding(start = 16.dp, end = 16.dp, top = 16.dp, bottom = 10.dp),
        fontSize = 18.sp,
        fontWeight = FontWeight.Bold,
    )
}
