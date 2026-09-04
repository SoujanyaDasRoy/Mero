package com.mero.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Transaction
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import kotlinx.coroutines.flow.Flow

/**
 * Natural keys throughout — `videoId` is already a stable global id, so there's
 * no UUID ceremony. See docs/architecture.md.
 */
@Entity(tableName = "songs")
data class SongEntity(
    @PrimaryKey val id: String,
    val title: String,
    val artist: String,
    val album: String,
    val durationSec: Int,
    val thumbnailUrl: String?,
    val liked: Boolean = false,
    val likedAt: Long? = null,
    val lastPlayedAt: Long? = null,
    val playCount: Int = 0,
    /** When the audio was pinned to the download cache; null means streaming only. */
    val downloadedAt: Long? = null,
)

/** Persisted playback queue; `position` is the order within it. */
@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey val songId: String,
    val position: Int,
)

/**
 * User-created playlists. Unlike songs there's no natural key here — the user
 * invents these — so the id is generated.
 */
@Entity(tableName = "playlists")
data class PlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val createdAt: Long,
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongEntity(
    val playlistId: String,
    val songId: String,
    val position: Int,
)

/** A playlist plus the count and cover art the list screen needs, in one query. */
data class PlaylistSummary(
    val id: String,
    val name: String,
    val createdAt: Long,
    val trackCount: Int,
    val artworkUrl: String?,
)

@Dao
interface MeroDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSong(song: SongEntity)

    @Query("SELECT * FROM songs WHERE id = :id")
    suspend fun song(id: String): SongEntity?

    @Query("SELECT * FROM songs WHERE liked = 1 ORDER BY likedAt DESC")
    fun likedSongs(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE lastPlayedAt IS NOT NULL ORDER BY lastPlayedAt DESC LIMIT 50")
    fun recentlyPlayed(): Flow<List<SongEntity>>

    @Query("SELECT * FROM songs WHERE downloadedAt IS NOT NULL ORDER BY downloadedAt DESC")
    fun downloads(): Flow<List<SongEntity>>

    @Query("UPDATE songs SET downloadedAt = :at WHERE id = :id")
    suspend fun setDownloaded(id: String, at: Long?)

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC LIMIT 50")
    fun mostPlayed(): Flow<List<SongEntity>>

    @Query("UPDATE songs SET liked = :liked, likedAt = :at WHERE id = :id")
    suspend fun setLiked(id: String, liked: Boolean, at: Long?)

    @Query("SELECT liked FROM songs WHERE id = :id")
    fun isLiked(id: String): Flow<Boolean?>

    @Query("UPDATE songs SET lastPlayedAt = :at, playCount = playCount + 1 WHERE id = :id")
    suspend fun markPlayed(id: String, at: Long)

    /* ------------------------------ queue ------------------------------ */

    @Query("DELETE FROM queue")
    suspend fun clearQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToQueue(entries: List<QueueEntity>)

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN queue q ON q.songId = s.id
        ORDER BY q.position ASC
        """,
    )
    fun queue(): Flow<List<SongEntity>>

    @Query("DELETE FROM queue WHERE songId = :songId")
    suspend fun removeFromQueue(songId: String)

    /* ---------------------------- playlists ---------------------------- */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: String, name: String)

    @Query("DELETE FROM playlists WHERE id = :id")
    suspend fun deletePlaylistRow(id: String)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :id")
    suspend fun deletePlaylistSongs(id: String)

    @Transaction
    suspend fun deletePlaylist(id: String) {
        deletePlaylistSongs(id)
        deletePlaylistRow(id)
    }

    /**
     * Cover art is borrowed from the first track, so a playlist looks like
     * something without the user doing any work.
     */
    @Query(
        """
        SELECT p.id AS id, p.name AS name, p.createdAt AS createdAt,
               (SELECT COUNT(*) FROM playlist_songs ps WHERE ps.playlistId = p.id) AS trackCount,
               (SELECT s.thumbnailUrl FROM playlist_songs ps
                  INNER JOIN songs s ON s.id = ps.songId
                  WHERE ps.playlistId = p.id
                  ORDER BY ps.position ASC LIMIT 1) AS artworkUrl
        FROM playlists p
        ORDER BY p.createdAt DESC
        """,
    )
    fun playlists(): Flow<List<PlaylistSummary>>

    @Query("SELECT * FROM playlists WHERE id = :id")
    fun playlist(id: String): Flow<PlaylistEntity?>

    @Query(
        """
        SELECT s.* FROM songs s
        INNER JOIN playlist_songs ps ON ps.songId = s.id
        WHERE ps.playlistId = :playlistId
        ORDER BY ps.position ASC
        """,
    )
    fun playlistSongs(playlistId: String): Flow<List<SongEntity>>

    @Query("SELECT COALESCE(MAX(position), -1) + 1 FROM playlist_songs WHERE playlistId = :playlistId")
    suspend fun nextPositionIn(playlistId: String): Int

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSong(entry: PlaylistSongEntity)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylistSongs(entries: List<PlaylistSongEntity>)

    @Query("DELETE FROM playlist_songs WHERE playlistId = :playlistId AND songId = :songId")
    suspend fun removeFromPlaylist(playlistId: String, songId: String)
}

/**
 * Adds playlists without touching the existing tables — liked songs and
 * listening history survive the upgrade.
 */
val MIGRATION_1_2 = object : Migration(1, 2) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlists (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS playlist_songs (
                playlistId TEXT NOT NULL,
                songId TEXT NOT NULL,
                position INTEGER NOT NULL,
                PRIMARY KEY(playlistId, songId)
            )
            """.trimIndent(),
        )
    }
}

val MIGRATION_2_3 = object : Migration(2, 3) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN downloadedAt INTEGER")
    }
}

@Database(
    entities = [
        SongEntity::class,
        QueueEntity::class,
        PlaylistEntity::class,
        PlaylistSongEntity::class,
    ],
    version = 3,
    exportSchema = false,
)
abstract class MeroDatabase : RoomDatabase() {
    abstract fun dao(): MeroDao
}
