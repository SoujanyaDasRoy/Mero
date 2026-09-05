package com.mero.data

import com.mero.domain.ArtistAlbum
import com.mero.domain.ArtistPageData
import com.mero.domain.Song
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.pages.ArtistItemsPage

class ArtistRepository {
    suspend fun artist(browseId: String): Result<ArtistPageData> = runCatching {
        val page = YouTube.artist(browseId).getOrThrow()
        val albums = page.sections
            .filter { it.title.contains("album", ignoreCase = true) || it.title.contains("discograph", ignoreCase = true) }
            .flatMap { section ->
                val initial = section.items.mapNotNull { it as? AlbumItem }
                val more = section.moreEndpoint?.let { endpoint -> loadAll(endpoint) }.orEmpty()
                initial + more.mapNotNull { it as? AlbumItem }
            }
            .distinctBy { it.browseId }
            .map { ArtistAlbum(it.browseId, it.title, it.year, it.thumbnail.atArtworkSize()) }
        val songs = page.sections
            .flatMap { it.items }
            .mapNotNull { (it as? SongItem)?.toDomain() }
            .distinctBy { it.id }
        ArtistPageData(
            id = page.artist.id,
            name = page.artist.title,
            thumbnailUrl = page.artist.thumbnail.atArtworkSize(),
            albums = albums,
            songs = songs,
        )
    }

    suspend fun albumSongs(browseId: String): Result<List<Song>> = runCatching {
        YouTube.album(browseId).getOrThrow().songs.map { it.toDomain() }
    }

    private suspend fun loadAll(endpoint: com.zionhuang.innertube.models.BrowseEndpoint): List<com.zionhuang.innertube.models.YTItem> {
        val items = mutableListOf<com.zionhuang.innertube.models.YTItem>()
        var page: ArtistItemsPage? = YouTube.artistItems(endpoint).getOrNull()
        var count = 0
        while (page != null && count++ < 20) {
            items += page.items
            page = page.continuation?.let { YouTube.artistItemsContinuation(it).getOrNull() }?.let {
                ArtistItemsPage(page.title, it.items, it.continuation)
            }
        }
        return items
    }
}