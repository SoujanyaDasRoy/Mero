package com.mero.data

import com.mero.data.db.MeroDao
import com.mero.data.db.QueueEntity
import com.mero.data.db.SongEntity
import com.mero.domain.Song
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map

private fun SongEntity.toDomain() = Song(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationSec = durationSec,
    thumbnailUrl = thumbnailUrl,
)

private fun Song.toEntity() = SongEntity(
    id = id,
    title = title,
    artist = artist,
    album = album,
    durationSec = durationSec,
    thumbnailUrl = thumbnailUrl,
)

/**
 * M2's persistence: liked songs, listening history, and the queue survive
 * process death. Everything the UI reads is a Flow so the DB is the single
 * source of truth rather than in-memory Compose state.
 */
class LibraryRepository(private val dao: MeroDao) {

    val liked: Flow<List<Song>> = dao.likedSongs().map { rows -> rows.map { it.toDomain() } }
    val recentlyPlayed: Flow<List<Song>> = dao.recentlyPlayed().map { rows -> rows.map { it.toDomain() } }
    val mostPlayed: Flow<List<Song>> = dao.mostPlayed().map { rows -> rows.map { it.toDomain() } }
    val queue: Flow<List<Song>> = dao.queue().map { rows -> rows.map { it.toDomain() } }

    fun isLiked(songId: String): Flow<Boolean?> = dao.isLiked(songId)

    /** Songs only exist in the DB once they're touched, so upsert before mutating. */
    private suspend fun ensure(song: Song) {
        if (dao.song(song.id) == null) dao.upsertSong(song.toEntity())
    }

    suspend fun onPlayed(song: Song) {
        ensure(song)
        dao.markPlayed(song.id, System.currentTimeMillis())
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

    suspend fun removeFromQueue(songId: String) = dao.removeFromQueue(songId)

    suspend fun clearQueue() = dao.clearQueue()
}
