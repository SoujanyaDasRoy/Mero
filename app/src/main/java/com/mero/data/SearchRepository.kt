package com.mero.data

import com.mero.domain.Song
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem

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

    /**
     * Cheap enough to run while typing (debounced by the caller): it hits
     * YouTube's suggestion endpoint rather than performing a full search.
     */
    suspend fun suggest(query: String): Result<Suggestions> {
        if (query.isBlank()) return Result.success(Suggestions(emptyList(), emptyList()))
        return runCatching { suggestApi.suggest(query.trim()) }
    }
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
