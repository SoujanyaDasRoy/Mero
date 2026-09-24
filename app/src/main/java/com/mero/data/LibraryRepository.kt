package com.mero.data

import com.mero.data.db.MeroDao
import com.mero.data.db.PlaylistEntity
import com.mero.data.db.PlaylistSongEntity
import com.mero.data.db.PlaylistSummary
import com.mero.data.db.SmartPlaylistEntity
import com.mero.data.db.SmartPlaylistSummary
import com.mero.data.db.QueueEntity
import com.mero.data.db.SongEntity
import com.mero.domain.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.map

private fun SongEntity.toDomain() = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationSec = durationSec,
    thumbnailUrl = thumbnailUrl,
    downloaded = downloadedAt != null,
    sourceUri = sourceUri,
)

private fun Song.toEntity() = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationSec = durationSec,
    thumbnailUrl = thumbnailUrl,
    sourceUri = sourceUri,
)

/**
 * M2's persistence: liked songs, listening history, and the queue survive
 * process death. Everything the UI reads is a Flow so the DB is the single
 * source of truth rather than in-memory Compose state.
 */
class LibraryRepository(private val dao: MeroDao) {

    val liked: Flow<List<Song>> = dao.likedSongs().map { rows -> rows.map { it.toDomain() } }
    val downloads: Flow<List<Song>> = dao.downloads().map { rows -> rows.map { it.toDomain() } }
    val recentlyPlayed: Flow<List<Song>> = dao.recentlyPlayed().map { rows -> rows.map { it.toDomain() } }
    val mostPlayed: Flow<List<Song>> = dao.mostPlayed().map { rows -> rows.map { it.toDomain() } }
    val queue: Flow<List<Song>> = dao.queue().map { rows -> rows.map { it.toDomain() } }
    val onDevice: Flow<List<Song>> = dao.onDevice().map { rows -> rows.map { it.toDomain() } }

    /**
     * Keeps songs that have no life outside the library — files from the phone
     * — so they are there to find next time rather than only while playing.
     */
    suspend fun saveSongs(songs: List<Song>) = songs.forEach {
        ensure(it)
        // A file first met through "Open with" was stored with an address
        // that stops working once that visit ends. Adding it properly later
        // has to replace that address, not be ignored because the song exists.
        if (it.sourceUri != null) dao.setSourceUri(it.id, it.sourceUri)
    }

    fun isLiked(songId: String): Flow<Boolean?> = dao.isLiked(songId)

    /** One stored song, for callers that only have its id — the car, mostly. */
    suspend fun song(id: String): Song? = dao.song(id)?.toDomain()

    /** Songs only exist in the DB once they're touched, so upsert before mutating. */
    private suspend fun ensure(song: Song) {
        if (dao.song(song.id) == null) dao.upsertSong(song.toEntity())
    }

    suspend fun markDownloaded(song: Song, downloaded: Boolean) {
        ensure(song)
        dao.setDownloaded(song.id, if (downloaded) System.currentTimeMillis() else null)
    }

    suspend fun clearDownloadedMarkers() = dao.clearDownloadedMarkers()

    suspend fun onPlayed(song: Song) {
        ensure(song)
        dao.markPlayed(song.id, System.currentTimeMillis())
    }

    suspend fun onSkipped(song: Song) {
        ensure(song)
        dao.markSkipped(song.id)
    }

    /**
     * Everything the queue needs to know about this listener's taste: likes,
     * the artists behind likes and plays, what was heard lately, and skips.
     */
    suspend fun taste(): Taste {
        val liked = liked.first()
        val played = mostPlayed.first()
        val skipped = dao.skipped()
        val affinity = HashMap<String, Int>()
        liked.forEach { affinity.merge(primaryArtist(it), 2, Int::plus) }
        // Each of the 50 most-played songs counts once for its artist, so an
        // artist is favoured for range rather than one song on repeat.
        played.forEach { affinity.merge(primaryArtist(it), 1, Int::plus) }
        val artistSkips = HashMap<String, Int>()
        skipped.forEach { artistSkips.merge(primaryArtist(it.toDomain()), it.skipCount, Int::plus) }
        return Taste(
            likedIds = liked.mapTo(HashSet()) { it.id },
            artistAffinity = affinity,
            recentIds = recentlyPlayed.first().take(RECENT_WINDOW).mapTo(HashSet()) { it.id },
            skips = skipped.associate { it.id to it.skipCount },
            artistSkips = artistSkips,
        )
    }

    suspend fun toggleLiked(song: Song): Boolean {
        ensure(song)
        val nowLiked = dao.song(song.id)?.liked != true
        dao.setLiked(song.id, nowLiked, if (nowLiked) System.currentTimeMillis() else null)
        return nowLiked
    }

    suspend fun setQueue(songs: List<Song>) {
        songs.forEach { ensure(it) }
        dao.clearQueue()
        dao.addToQueue(songs.mapIndexed { index, song -> QueueEntity(song.id, index) })
    }

    /**
     * Rewrites queue order only. Unlike [setQueue] it skips the per-song upsert,
     * since reordering can't introduce a song that isn't already stored — which
     * matters because this runs on drop, not once per drag.
     */
    suspend fun reorderQueue(songs: List<Song>) {
        dao.clearQueue()
        dao.addToQueue(songs.mapIndexed { index, song -> QueueEntity(song.id, index) })
    }

    suspend fun removeFromQueue(songId: String) = dao.removeFromQueue(songId)

    suspend fun clearQueue() = dao.clearQueue()

    /* ---------------------------- playlists ---------------------------- */

    val playlists: Flow<List<PlaylistSummary>> = dao.playlists()
    val smartPlaylists: Flow<List<SmartPlaylistSummary>> = dao.smartPlaylists()

    fun smartPlaylistSongs(summary: SmartPlaylistSummary): Flow<List<Song>> =
        dao.smartPlaylistSongs(summary.rule, summary.minPlayCount, summary.artistFilter)
            .map { rows -> rows.map { it.toDomain() } }

    fun playlistSongs(playlistId: String): Flow<List<Song>> =
        dao.playlistSongs(playlistId).map { rows -> rows.map { it.toDomain() } }

    fun playlist(playlistId: String): Flow<PlaylistEntity?> = dao.playlist(playlistId)

    suspend fun createPlaylist(name: String): String {
        val id = "pl-" + System.currentTimeMillis().toString(36)
        dao.insertPlaylist(PlaylistEntity(id, name.trim(), System.currentTimeMillis()))
        return id
    }

    suspend fun createSmartPlaylist(
        name: String,
        rule: String,
        minPlayCount: Int = 0,
        artistFilter: String = "",
    ): String {
        val id = "smart-" + System.currentTimeMillis().toString(36)
        dao.insertSmartPlaylist(
            SmartPlaylistEntity(
                id = id,
                name = name.trim(),
                rule = rule,
                minPlayCount = minPlayCount.coerceAtLeast(0),
                artistFilter = artistFilter.trim(),
                createdAt = System.currentTimeMillis(),
            ),
        )
        return id
    }

    suspend fun deleteSmartPlaylist(id: String) = dao.deleteSmartPlaylist(id)

    suspend fun renamePlaylist(id: String, name: String) = dao.renamePlaylist(id, name.trim())

    /** A picture the user picked, or null to go back to the first track's cover. */
    suspend fun setPlaylistCover(id: String, uri: String?) =
        dao.setPlaylistCover(id, uri?.takeIf { it.isNotBlank() })

    suspend fun setPlaylistDescription(id: String, text: String?) =
        dao.setPlaylistDescription(id, text?.trim()?.takeIf { it.isNotBlank() })

    suspend fun deletePlaylist(id: String) = dao.deletePlaylist(id)

    /** Appends to the end. Re-adding an existing track is a no-op by primary key. */
    suspend fun addToPlaylist(playlistId: String, song: Song) {
        ensure(song)
        dao.insertPlaylistSong(
            PlaylistSongEntity(playlistId, song.id, dao.nextPositionIn(playlistId)),
        )
    }

    suspend fun addToPlaylist(playlistId: String, songs: List<Song>) {
        songs.forEach { addToPlaylist(playlistId, it) }
    }

    suspend fun removeFromPlaylist(playlistId: String, songId: String) =
        dao.removeFromPlaylist(playlistId, songId)

    suspend fun reorderPlaylist(playlistId: String, songs: List<Song>) {
        dao.deletePlaylistSongs(playlistId)
        dao.insertPlaylistSongs(
            songs.mapIndexed { index, song -> PlaylistSongEntity(playlistId, song.id, index) },
        )
    }
}

/** Songs heard this recently are kept back from the queue for a while. */
private const val RECENT_WINDOW = 25
