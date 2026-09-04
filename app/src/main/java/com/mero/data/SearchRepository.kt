package com.mero.data

import com.mero.domain.Song
import com.zionhuang.innertube.YouTube

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

class SearchRepository(private val api: SearchApi) {

    suspend fun search(query: String): Result<List<Song>> {
        if (query.isBlank()) return Result.success(emptyList())
        return runCatching { api.searchSongs(query.trim()) }
    }
}

/**
 * Backed by innertube's anonymous [YouTube.search]. Never sets [YouTube.cookie]
 * — see CLAUDE.md constraint 1, no Google sign-in anywhere in Mero.
 */
object InnerTubeSearchApi : SearchApi {
    override suspend fun searchSongs(query: String): List<Song> {
        val result = YouTube.search(query, YouTube.SearchFilter.FILTER_SONG).getOrThrow()
        return result.items
            .filterIsInstance<com.zionhuang.innertube.models.SongItem>()
            .filter { it.id.isNotBlank() }
            .map { it.toDomain() }
    }
}
