package com.mero.data.db

import androidx.room.ColumnInfo
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Embedded
import androidx.room.Entity
import androidx.room.Index
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
    /** Local file or podcast enclosure; null for a YouTube track. See Song.sourceUri. */
    val sourceUri: String? = null,
    /** Times moved on from in its first seconds: the clearest "not this". */
    @ColumnInfo(defaultValue = "0") val skipCount: Int = 0,
)

/** Persisted playback queue; `position` is the order within it. */
@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey val songId: String,
    val position: Int,
    /** Origin name (see com.mero.domain.Origin): who put this song in the queue. */
    @ColumnInfo(defaultValue = "USER_CHOSE") val origin: String = "USER_CHOSE",
)

/** A queued song with the origin it was queued under. */
data class QueuedSong(
    @Embedded val song: SongEntity,
    val origin: String,
)

/**
 * One playback instance of one track: inserted when it starts, updated when it
 * is paused, finalized when it ends or is moved on from. The raw material for
 * learning what this listener finishes, replays and skips.
 */
@Entity(
    tableName = "play_events",
    indices = [Index("trackId"), Index("startedAtMs")],
)
data class PlayEventEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: String,
    /** primaryArtist(song): the artist key used everywhere new code needs an artist id. */
    val artistKey: String,
    val startedAtMs: Long,
    /** Milliseconds of the track actually heard, from position changes while playing. */
    val listenedMs: Long,
    val durationMs: Long,
    val origin: String,
    /** A replay of the track just heard, rather than a first listen. */
    val restarted: Boolean,
    /** False while it is still playing, or if the process died before it ended. */
    val finalized: Boolean,
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
    /**
     * A picture the user chose, as a persisted content:// URI. Null means the
     * cover is borrowed from the first track, which is what every playlist
     * started out doing.
     */
    val coverUri: String? = null,
    /** Their own note about what this playlist is for. */
    val description: String? = null,
)

@Entity(tableName = "playlist_songs", primaryKeys = ["playlistId", "songId"])
data class PlaylistSongEntity(
    val playlistId: String,
    val songId: String,
    val position: Int,
)

@Entity(tableName = "smart_playlists")
data class SmartPlaylistEntity(
    @PrimaryKey val id: String,
    val name: String,
    val rule: String,
    val minPlayCount: Int = 0,
    val artistFilter: String = "",
    val createdAt: Long,
)

/** A playlist plus the count and cover art the list screen needs, in one query. */
data class PlaylistSummary(
    val id: String,
    val name: String,
    val createdAt: Long,
    val trackCount: Int,
    val artworkUrl: String?,
    val coverUri: String? = null,
    val description: String? = null,
) {
    /** The chosen picture if there is one, otherwise the first track's cover. */
    val displayArtwork: String? get() = coverUri ?: artworkUrl
}

data class SmartPlaylistSummary(
    val id: String,
    val name: String,
    val rule: String,
    val minPlayCount: Int,
    val artistFilter: String,
    val trackCount: Int,
)

@Dao
interface MeroDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertSong(song: SongEntity)

    @Query("UPDATE songs SET sourceUri = :uri WHERE id = :id")
    suspend fun setSourceUri(id: String, uri: String?)

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

    @Query("UPDATE songs SET downloadedAt = NULL WHERE downloadedAt IS NOT NULL")
    suspend fun clearDownloadedMarkers()

    /** Everything the listener has shown a feeling about, for building taste. */
    @Query("SELECT * FROM songs WHERE liked = 1 OR playCount > 0")
    suspend fun tasteSongs(): List<SongEntity>

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC LIMIT 50")
    fun mostPlayed(): Flow<List<SongEntity>>

    /** Files added from the phone, most recently added first. */
    @Query("SELECT * FROM songs WHERE sourceUri LIKE 'content:%' OR sourceUri LIKE 'file:%' ORDER BY rowid DESC")
    fun onDevice(): Flow<List<SongEntity>>

    @Query("UPDATE songs SET liked = :liked, likedAt = :at WHERE id = :id")
    suspend fun setLiked(id: String, liked: Boolean, at: Long?)

    @Query("SELECT liked FROM songs WHERE id = :id")
    fun isLiked(id: String): Flow<Boolean?>

    @Query("UPDATE songs SET lastPlayedAt = :at, playCount = playCount + 1 WHERE id = :id")
    suspend fun markPlayed(id: String, at: Long)

    @Query("UPDATE songs SET skipCount = skipCount + 1 WHERE id = :id")
    suspend fun markSkipped(id: String)

    @Query("SELECT * FROM songs WHERE skipCount > 0")
    suspend fun skipped(): List<SongEntity>

    /* ------------------------------ queue ------------------------------ */

    @Query("DELETE FROM queue")
    suspend fun clearQueue()

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun addToQueue(entries: List<QueueEntity>)

    @Query(
        """
        SELECT s.*, q.origin AS origin FROM songs s
        INNER JOIN queue q ON q.songId = s.id
        ORDER BY q.position ASC
        """,
    )
    fun queue(): Flow<List<QueuedSong>>

    /* ------------------------------ play events ------------------------------ */

    @Insert
    suspend fun insertPlayEvent(event: PlayEventEntity): Long

    @Query("UPDATE play_events SET listenedMs = :listenedMs, durationMs = :durationMs, finalized = :finalized WHERE id = :id")
    suspend fun updatePlayEvent(id: Long, listenedMs: Long, durationMs: Long, finalized: Boolean)

    @Query("SELECT COUNT(*) FROM play_events")
    suspend fun playEventCount(): Int

    @Query("SELECT * FROM play_events ORDER BY startedAtMs DESC, id DESC LIMIT :limit")
    suspend fun lastPlayEvents(limit: Int): List<PlayEventEntity>

    /** Anything still unfinalized when the app starts belongs to a process that died mid-track. */
    @Query("UPDATE play_events SET finalized = 1 WHERE finalized = 0")
    suspend fun finalizeStalePlayEvents()

    @Query("DELETE FROM play_events WHERE startedAtMs < :cutoffMs")
    suspend fun deletePlayEventsBefore(cutoffMs: Long)

    @Query("DELETE FROM play_events WHERE id NOT IN (SELECT id FROM play_events ORDER BY startedAtMs DESC, id DESC LIMIT :keep)")
    suspend fun keepNewestPlayEvents(keep: Int)

    @Query("DELETE FROM queue WHERE songId = :songId")
    suspend fun removeFromQueue(songId: String)

    /* ---------------------------- playlists ---------------------------- */

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlaylist(playlist: PlaylistEntity)

    @Query("UPDATE playlists SET name = :name WHERE id = :id")
    suspend fun renamePlaylist(id: String, name: String)

    @Query("UPDATE playlists SET coverUri = :uri WHERE id = :id")
    suspend fun setPlaylistCover(id: String, uri: String?)

    @Query("UPDATE playlists SET description = :text WHERE id = :id")
    suspend fun setPlaylistDescription(id: String, text: String?)

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
               p.coverUri AS coverUri, p.description AS description,
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

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertSmartPlaylist(playlist: SmartPlaylistEntity)

    @Query("DELETE FROM smart_playlists WHERE id = :id")
    suspend fun deleteSmartPlaylist(id: String)

    @Query(
        """
        SELECT sp.id AS id, sp.name AS name, sp.rule AS rule,
               sp.minPlayCount AS minPlayCount, sp.artistFilter AS artistFilter,
               (SELECT COUNT(*) FROM songs s
                WHERE (sp.rule = 'most' AND s.playCount > 0)
                   OR (sp.rule = 'recent' AND s.lastPlayedAt IS NOT NULL)
                   OR (sp.rule = 'liked' AND s.liked = 1)
                   OR (sp.rule = 'downloads' AND s.downloadedAt IS NOT NULL)
                   OR (sp.rule = 'custom' AND
                       (sp.minPlayCount = 0 OR s.playCount >= sp.minPlayCount) AND
                       (sp.artistFilter = '' OR s.artist LIKE '%' || sp.artistFilter || '%'))
               ) AS trackCount
        FROM smart_playlists sp
        ORDER BY sp.createdAt DESC
        """,
    )
    fun smartPlaylists(): Flow<List<SmartPlaylistSummary>>

    @Query(
        """
        SELECT s.* FROM songs s
        WHERE (:rule = 'most' AND s.playCount > 0)
           OR (:rule = 'recent' AND s.lastPlayedAt IS NOT NULL)
           OR (:rule = 'liked' AND s.liked = 1)
           OR (:rule = 'downloads' AND s.downloadedAt IS NOT NULL)
           OR (:rule = 'custom' AND
               (:minPlayCount = 0 OR s.playCount >= :minPlayCount) AND
               (:artistFilter = '' OR s.artist LIKE '%' || :artistFilter || '%'))
        ORDER BY
            CASE WHEN :rule = 'most' THEN s.playCount ELSE 0 END DESC,
            CASE WHEN :rule = 'recent' THEN s.lastPlayedAt ELSE 0 END DESC,
            CASE WHEN :rule = 'liked' THEN s.likedAt ELSE 0 END DESC,
            s.lastPlayedAt DESC
        """,
    )
    fun smartPlaylistSongs(
        rule: String,
        minPlayCount: Int,
        artistFilter: String,
    ): Flow<List<SongEntity>>

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
        SmartPlaylistEntity::class,
        PlayEventEntity::class,
    ],
    version = 8,
    exportSchema = true,
)
abstract class MeroDatabase : RoomDatabase() {
    abstract fun dao(): MeroDao
}

/**
 * The listening log (play_events) and where each queued song came from.
 *
 * The queue's origin column defaults to USER_CHOSE, which is what every song in a
 * queue saved before this version was: nothing then marked anything as suggested.
 */
val MIGRATION_7_8 = object : Migration(7, 8) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE queue ADD COLUMN origin TEXT NOT NULL DEFAULT 'USER_CHOSE'")
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS play_events (
                id INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL,
                trackId TEXT NOT NULL,
                artistKey TEXT NOT NULL,
                startedAtMs INTEGER NOT NULL,
                listenedMs INTEGER NOT NULL,
                durationMs INTEGER NOT NULL,
                origin TEXT NOT NULL,
                restarted INTEGER NOT NULL,
                finalized INTEGER NOT NULL
            )
            """.trimIndent(),
        )
        db.execSQL("CREATE INDEX IF NOT EXISTS index_play_events_trackId ON play_events (trackId)")
        db.execSQL("CREATE INDEX IF NOT EXISTS index_play_events_startedAtMs ON play_events (startedAtMs)")
    }
}

/** Skips, so the queue can learn what this listener moves on from. */
val MIGRATION_6_7 = object : Migration(6, 7) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN skipCount INTEGER NOT NULL DEFAULT 0")
    }
}

/** Songs that are not YouTube tracks: files from the phone, podcast episodes. */
val MIGRATION_5_6 = object : Migration(5, 6) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE songs ADD COLUMN sourceUri TEXT")
    }
}

/**
 * Playlists gain a cover and a description.
 *
 * Both nullable with no default: an existing playlist has neither, and null is
 * what "borrow the first track's cover" already means everywhere it is read.
 */
val MIGRATION_4_5 = object : Migration(4, 5) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL("ALTER TABLE playlists ADD COLUMN coverUri TEXT")
        db.execSQL("ALTER TABLE playlists ADD COLUMN description TEXT")
    }
}

val MIGRATION_3_4 = object : Migration(3, 4) {
    override fun migrate(db: SupportSQLiteDatabase) {
        db.execSQL(
            """
            CREATE TABLE IF NOT EXISTS smart_playlists (
                id TEXT NOT NULL PRIMARY KEY,
                name TEXT NOT NULL,
                rule TEXT NOT NULL,
                minPlayCount INTEGER NOT NULL DEFAULT 0,
                artistFilter TEXT NOT NULL DEFAULT '',
                createdAt INTEGER NOT NULL
            )
            """.trimIndent(),
        )
    }
}
