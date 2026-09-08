package com.mero.data

import com.mero.domain.ArtistAlbum
import com.mero.domain.ArtistPageData
import com.mero.domain.Song
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.AlbumItem
import com.zionhuang.innertube.models.PlaylistItem
import com.zionhuang.innertube.models.SongItem
import com.zionhuang.innertube.pages.ArtistItemsPage

class ArtistRepository {
    suspend fun artist(browseId: String): Result<ArtistPageData> = runCatching {
        val page = YouTube.artist(browseId).getOrThrow()

        // Each section is expanded once and then partitioned. Expanding per
        // result type instead meant every "show more" endpoint was crawled
        // twice — up to twenty continuation requests each — to build two lists
        // out of the same items.
        val everything = page.sections.flatMap { section ->
            section.items + section.moreEndpoint?.let { loadAll(it) }.orEmpty()
        }

        val albums = everything
            .mapNotNull { it as? AlbumItem }
            .distinctBy { it.browseId }
            .map { ArtistAlbum(it.browseId, it.title, it.year, it.thumbnail.atArtworkSize()) }
        val playlists = everything
            .mapNotNull { it as? PlaylistItem }
            .distinctBy { it.id }
            .map { ArtistAlbum(it.id, it.title, null, it.thumbnail.atArtworkSize()) }
        val songs = everything
            .mapNotNull { (it as? SongItem)?.toDomain() }
            .distinctBy { it.id }

        ArtistPageData(
            id = page.artist.id,
            name = page.artist.title,
            thumbnailUrl = page.artist.thumbnail.atArtworkSize(),
            albums = albums,
            playlists = playlists,
            songs = songs,
        )
    }

    suspend fun albumSongs(browseId: String): Result<List<Song>> = runCatching {
        YouTube.album(browseId).getOrThrow().songs.map { it.toDomain() }
    }

    /**
     * Every track of a YouTube playlist, following continuations.
     *
     * A playlist result used to do nothing at all when tapped. Playlists are
     * often the most useful thing a search returns — an artist's "Essentials"
     * is a better answer to their name than any single track.
     */
    suspend fun playlistSongs(playlistId: String): Result<List<Song>> = runCatching {
        val page = YouTube.playlist(playlistId).getOrThrow()
        val songs = LinkedHashMap<String, Song>()
        page.songs.forEach { songs.putIfAbsent(it.id, it.toDomain()) }
        var next = page.songsContinuation
        var fetched = 1
        while (next != null && fetched < MAX_PLAYLIST_PAGES) {
            val more = YouTube.playlistContinuation(next).getOrNull() ?: break
            more.songs.forEach { songs.putIfAbsent(it.id, it.toDomain()) }
            next = more.continuation
            fetched++
        }
        songs.values.toList()
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

    private companion object {
        const val MAX_PLAYLIST_PAGES = 10
    }
}