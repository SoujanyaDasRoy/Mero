package com.mero.data

import com.mero.domain.SearchItem
import com.mero.domain.SearchResultType
import com.mero.domain.Song
import com.mero.domain.asClock
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.ArtistItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.models.YTItem

/**
 * The only seam that matters for testing here: the network call, not the
 * repository. See docs/architecture.md, "Why there is no SongRepository
 * interface" — a real interface per repository would be indirection with no
 * second implementation; this fun interface exists because the network really
 * is untestable without it.
 */
fun interface SearchApi {
    suspend fun searchSongs(query: String): List<Song>
}

/** What YouTube suggests while the user is still typing. */
data class Suggestions(
    val queries: List<String>,
    val songs: List<Song>,
) {
    val isEmpty: Boolean get() = queries.isEmpty() && songs.isEmpty()
}

data class SearchResultPage(
    val items: List<SearchItem>,
    val continuation: String?,
)

fun interface SuggestApi {
    suspend fun suggest(query: String): Suggestions
}

class SearchRepository(
    private val api: SearchApi,
    private val suggestApi: SuggestApi = InnerTubeSuggestApi,
) {

    suspend fun search(query: String): Result<List<Song>> {
        if (query.isBlank()) return Result.success(emptyList())
        return runCatching { api.searchSongs(query.trim()) }
    }

    suspend fun searchItemsPage(
        query: String,
        type: SearchResultType,
        continuation: String? = null,
    ): Result<SearchResultPage> {
        if (query.isBlank()) return Result.success(SearchResultPage(emptyList(), null))
        return runCatching {
            if (type == SearchResultType.Playlist && continuation.isNullOrBlank()) {
                return@runCatching firstPlaylistPage(query.trim())
            }
            val page = if (continuation.isNullOrBlank()) {
                YouTube.search(query.trim(), type.filter()).getOrThrow()
            } else {
                YouTube.searchContinuation(continuation).getOrThrow()
            }
            val items = page.items.mapNotNull { it.toSearchItem() }
            SearchResultPage(
                items = items,
                continuation = page.continuation,
            )
        }
    }

    /**
     * Playlists come from two different wells and one of them is nearly dry.
     *
     * `FILTER_FEATURED_PLAYLIST` returns only what YouTube Music itself
     * curates — an artist's official "Presenting …" and little else, often a
     * single row. `FILTER_COMMUNITY_PLAYLIST` returns everything people have
     * made, which is where the depth is.
     *
     * Both are fetched for the first page so the official ones lead, then
     * paging continues down the community list, which is the one that actually
     * keeps going.
     */
    private suspend fun firstPlaylistPage(query: String): SearchResultPage {
        val featured = runCatching {
            YouTube.search(query, YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST).getOrThrow()
        }.getOrNull()
        val community = runCatching {
            YouTube.search(query, YouTube.SearchFilter.FILTER_COMMUNITY_PLAYLIST).getOrThrow()
        }.getOrNull()

        val merged = LinkedHashMap<String, SearchItem>()
        featured?.items?.mapNotNull { it.toSearchItem() }?.forEach { merged.putIfAbsent(it.id, it) }
        community?.items?.mapNotNull { it.toSearchItem() }?.forEach { merged.putIfAbsent(it.id, it) }
        return SearchResultPage(
            items = merged.values.toList(),
            continuation = community?.continuation ?: featured?.continuation,
        )
    }

    suspend fun searchItems(query: String, type: SearchResultType): Result<List<SearchItem>> {
        return searchItemsPage(query, type).map { it.items }
    }

    /**
     * Cheap enough to run while typing (debounced by the caller): it hits
     * YouTube's suggestion endpoint rather than performing a full search.
     */
    suspend fun suggest(query: String): Result<Suggestions> {
        if (query.isBlank()) return Result.success(Suggestions(emptyList(), emptyList()))
        return runCatching { suggestApi.suggest(query.trim()) }
    }
}

private fun SearchResultType.filter() = when (this) {
    SearchResultType.Song -> YouTube.SearchFilter.FILTER_SONG
    SearchResultType.Album -> YouTube.SearchFilter.FILTER_ALBUM
    SearchResultType.Artist -> YouTube.SearchFilter.FILTER_ARTIST
    SearchResultType.Playlist -> YouTube.SearchFilter.FILTER_FEATURED_PLAYLIST
}

private fun YTItem.toSearchItem(): SearchItem? = when (this) {
    is SongItem -> {
        val durationText = duration?.asClock()
        val artistsText = artists.joinToString(", ") { it.name }
        val subtitleText = if (!durationText.isNullOrBlank()) {
            "$artistsText · $durationText"
        } else {
            artistsText
        }
        SearchItem(
            id = id,
            title = title,
            subtitle = subtitleText,
            thumbnailUrl = thumbnail.atArtworkSize(),
            type = SearchResultType.Song,
            song = toDomain(),
        )
    }
    is AlbumItem -> SearchItem(
        id = browseId,
        title = title,
        subtitle = listOfNotNull(artists?.joinToString(", ") { it.name }, year?.toString())
            .filter { it.isNotBlank() }
            .joinToString(" · "),
        thumbnailUrl = thumbnail.atArtworkSize(),
        type = SearchResultType.Album,
        browseId = browseId,
    )
    is ArtistItem -> SearchItem(
        id = id,
        title = title,
        subtitle = "Artist",
        thumbnailUrl = thumbnail.atArtworkSize(),
        type = SearchResultType.Artist,
        browseId = id,
    )
    is PlaylistItem -> SearchItem(
        id = id,
        title = title,
        subtitle = listOfNotNull(author?.name, songCountText)
            .filter { it.isNotBlank() }
            .joinToString(" · "),
        thumbnailUrl = thumbnail.atArtworkSize(),
        type = SearchResultType.Playlist,
        browseId = id,
    )
    else -> null
}

/**
 * Backed by innertube's anonymous [YouTube.search]. Never sets [YouTube.cookie]
 * — see CLAUDE.md constraint 1, no Google sign-in anywhere in Mero.
 */
object InnerTubeSearchApi : SearchApi {

    /**
     * One search page is about 20 tracks, which runs out fast. Following the
     * continuation token a few times gets a useful list without turning a
     * search into an open-ended crawl; each extra page is one cheap request.
     */
    private const val TARGET_RESULTS = 80
    private const val MAX_PAGES = 5

    override suspend fun searchSongs(query: String): List<Song> {
        var page = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
        val songs = LinkedHashMap<String, Song>()
        var fetched = 1

        while (true) {
            page.items
                .filterIsInstance<SongItem>()
                .filter { it.id.isNotBlank() }
                .forEach { songs.putIfAbsent(it.id, it.toDomain()) }

            val next = page.continuation
            if (next == null || songs.size >= TARGET_RESULTS || fetched >= MAX_PAGES) break
            // A failed continuation just means fewer results, never no results.
            page = YouTube.searchContinuation(next).getOrNull() ?: break
            fetched++
        }
        return songs.values.toList()
    }
}

/** Autocomplete as you type, also anonymous. */
object InnerTubeSuggestApi : SuggestApi {
    override suspend fun suggest(query: String): Suggestions {
        val result = YouTube.searchSuggestions(query).getOrThrow()
        return Suggestions(
            queries = result.queries,
            songs = result.recommendedItems
                .filterIsInstance<SongItem>()
                .filter { it.id.isNotBlank() }
                .map { it.toDomain() },
        )
    }
}
