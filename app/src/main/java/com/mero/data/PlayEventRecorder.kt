package com.mero.data

import com.mero.data.db.PlayEventEntity
import com.mero.domain.Origin
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.launch
import kotlin.math.max

/** Where plays are written. [LibraryRepository.playEvents] is the real one. */
interface PlayEventStore {
    suspend fun insert(event: PlayEventEntity): Long
    suspend fun update(id: Long, listenedMs: Long, durationMs: Long, finalized: Boolean)
}

/** The track a playback instance belongs to. */
data class PlayingTrack(val id: String, val artistKey: String, val durationMs: Long, val origin: Origin)

/**
 * Turns what the player reports into one row per playback instance.
 *
 * A playback instance runs from a track starting to it being moved on from. It is
 * inserted when it first plays, updated each time it is paused, and finalized when
 * it ends, is skipped, or is replaced. Pausing and resuming keep the same row: a
 * pause must never be a second play.
 *
 * Time heard is the sum of position changes while playing, so seeking forward
 * skips what was not heard and seeking back counts what was heard again; the total
 * is clamped to the track's duration.
 *
 * Methods are called from the player's thread, one at a time. Database writes go
 * through a queue worked in order on [scope], so an update can never overtake the
 * insert it belongs to and nothing here waits on the database.
 */
class PlayEventRecorder(
    private val store: PlayEventStore,
    scope: CoroutineScope,
    private val clock: () -> Long = System::currentTimeMillis,
) {
    private class Playback(val track: PlayingTrack, val startedAt: Long, val restarted: Boolean) {
        var rowId: Long = NO_ROW
        var listenedMs = 0L
        var durationMs = track.durationMs

        /** Where the playing stretch now in progress began; null while paused. */
        var anchor: Long? = null

        fun closeStretch(endPositionMs: Long) {
            anchor?.let { listenedMs += max(0L, endPositionMs - it) }
            anchor = null
            if (durationMs > 0) listenedMs = listenedMs.coerceAtMost(durationMs)
        }
    }

    private var current: Playback? = null
    private val writes = Channel<suspend () -> Unit>(Channel.UNLIMITED)

    init {
        scope.launch { for (write in writes) runCatching { write() } }
    }

    /** No more events: pending writes still finish, then the worker ends. */
    fun close() {
        writes.close()
    }

    /** The track is playing, from [positionMs]. A resume after a pause carries on the same row. */
    fun onPlaying(track: PlayingTrack, positionMs: Long, restarted: Boolean = false) {
        val open = current
        if (open != null && open.track.id == track.id) {
            if (open.anchor == null) open.anchor = positionMs
            return
        }
        // A different track starting while one is still open: the end was never
        // reported, so keep what was counted rather than lose the row.
        open?.let { end(it) }
        val playback = Playback(track, clock(), restarted).also { it.anchor = positionMs }
        current = playback
        writes.trySend {
            playback.rowId = store.insert(
                PlayEventEntity(
                    trackId = track.id,
                    artistKey = track.artistKey,
                    startedAtMs = playback.startedAt,
                    listenedMs = 0,
                    durationMs = track.durationMs,
                    origin = track.origin.name,
                    restarted = restarted,
                    finalized = false,
                ),
            )
        }
    }

    /** Playback paused or stalled at [positionMs]: remember how much was heard, keep the row open. */
    fun onPaused(positionMs: Long) {
        val playback = current ?: return
        playback.closeStretch(positionMs)
        write(playback, finalized = false)
    }

    /**
     * A seek inside the track. Seeking back to the start after most of it was heard
     * is a replay: that play ends and a new one, marked restarted, begins.
     */
    fun onSeek(fromMs: Long, toMs: Long) {
        val playback = current ?: return
        val wasPlaying = playback.anchor != null
        playback.closeStretch(fromMs)
        val duration = playback.durationMs
        val replay = toMs < REPLAY_START_MS && duration > 0 && playback.listenedMs >= duration / 2
        if (replay) {
            val track = playback.track
            end(playback)
            current = null
            if (wasPlaying) onPlaying(track.copy(durationMs = duration), toMs, restarted = true)
            return
        }
        if (wasPlaying) playback.anchor = toMs
    }

    /** The track ended, was skipped or was replaced, [endPositionMs] into it. */
    fun onTrackEnded(endPositionMs: Long, durationMs: Long = 0) {
        val playback = current ?: return
        if (durationMs > 0) playback.durationMs = durationMs
        playback.closeStretch(endPositionMs)
        end(playback)
        current = null
    }

    private fun end(playback: Playback) = write(playback, finalized = true)

    private fun write(playback: Playback, finalized: Boolean) {
        val listened = playback.listenedMs
        val duration = playback.durationMs
        writes.trySend { if (playback.rowId != NO_ROW) store.update(playback.rowId, listened, duration, finalized) }
    }

    private companion object {
        const val NO_ROW = -1L

        /** Seeking to within this of the start counts as starting over. */
        const val REPLAY_START_MS = 3_000L
    }
}
