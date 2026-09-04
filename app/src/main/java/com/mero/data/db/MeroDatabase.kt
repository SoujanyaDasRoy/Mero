package com.mero.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
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
)

/** Persisted playback queue; `position` is the order within it. */
@Entity(tableName = "queue")
data class QueueEntity(
    @PrimaryKey val songId: String,
    val position: Int,
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

    @Query("SELECT * FROM songs WHERE playCount > 0 ORDER BY playCount DESC LIMIT 50")
    fun mostPlayed(): Flow<List<SongEntity>>

    @Query("UPDATE songs SET liked = :liked, likedAt = :at WHERE id = :id")
    suspend fun setLiked(id: String, liked: Boolean, at: Long?)

    @Query("SELECT liked FROM songs WHERE id = :id")
    fun isLiked(id: String): Flow<Boolean?>

    @Query(
        "UPDATE songs SET lastPlayedAt = :at, playCount = playCount + 1 WHERE id = :id",
    )
    suspend fun markPlayed(id: String, at: Long)

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
}

@Database(entities = [SongEntity::class, QueueEntity::class], version = 1, exportSchema = false)
abstract class MeroDatabase : RoomDatabase() {
    abstract fun dao(): MeroDao
}
