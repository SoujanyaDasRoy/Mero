package com.mero.data

import com.mero.data.db.PlayEventEntity
import com.mero.domain.Origin
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.TestScope
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** One row per playback instance: pauses never add rows, time heard comes from position changes. */
class PlayEventRecorderTest {

    /** Rows kept in memory; [insertDelayMs] makes inserts slow to prove updates cannot overtake them. */
    private class FakeStore(private val insertDelayMs: Long = 0) : PlayEventStore {
        val rows = mutableListOf<PlayEventEntity>()

        override suspend fun insert(event: PlayEventEntity): Long {
            if (insertDelayMs > 0) delay(insertDelayMs)
            val id = rows.size + 1L
            rows += event.copy(id = id)
            return id
        }

        override suspend fun update(id: Long, listenedMs: Long, durationMs: Long, finalized: Boolean) {
            val i = rows.indexOfFirst { it.id == id }
            rows[i] = rows[i].copy(listenedMs = listenedMs, durationMs = durationMs, finalized = finalized)
        }
    }

    private fun track(id: String = "t1", durationMs: Long = 200_000, origin: Origin = Origin.USER_CHOSE) =
        PlayingTrack(id, "artist-$id", durationMs, origin)

    /** The recorder's worker is background work, which advanceUntilIdle leaves alone. */
    private fun TestScope.settle() {
        advanceTimeBy(10_000)
        runCurrent()
    }

    private fun TestScope.recorder(store: PlayEventStore) =
        PlayEventRecorder(store, backgroundScope, clock = { 1_000L })

    @Test
    fun `playing then ending is one finalized row with the time heard`() = runTest {
        val store = FakeStore()
        val r = recorder(store)
        r.onPlaying(track(), 0)
        r.onTrackEnded(200_000)
        settle()
        assertEquals(1, store.rows.size)
        assertEquals(200_000L, store.rows[0].listenedMs)
        assertTrue(store.rows[0].finalized)
    }

    @Test
    fun `a pause never creates a second row`() = runTest {
        val store = FakeStore()
        val r = recorder(store)
        r.onPlaying(track(), 0)
        r.onPaused(30_000)
        r.onPlaying(track(), 30_000)      // resumed
        r.onPaused(50_000)
        r.onPlaying(track(), 50_000)      // resumed again
        r.onTrackEnded(90_000)
        settle()
        assertEquals(1, store.rows.size)
        assertEquals(90_000L, store.rows[0].listenedMs)    // 30 + 20 + 40 seconds
        assertTrue(store.rows[0].finalized)
    }

    @Test
    fun `a pause writes progress but leaves the row open`() = runTest {
        val store = FakeStore()
        val r = recorder(store)
        r.onPlaying(track(), 0)
        r.onPaused(12_000)
        settle()
        assertEquals(12_000L, store.rows.single().listenedMs)
        assertFalse(store.rows.single().finalized)
    }

    @Test
    fun `seeking forward does not count what was skipped over and seeking back counts again`() = runTest {
        val store = FakeStore()
        val r = recorder(store)
        r.onPlaying(track(), 0)
        r.onSeek(10_000, 100_000)         // heard 10s, jumped to 100s
        r.onSeek(120_000, 110_000)        // heard 20 more, went back 10s
        r.onTrackEnded(130_000)           // heard 20 more
        settle()
        assertEquals(50_000L, store.rows.single().listenedMs)
    }

    @Test
    fun `time heard is clamped to the length of the track`() = runTest {
        val store = FakeStore()
        val r = recorder(store)
        r.onPlaying(track(durationMs = 60_000), 0)
        r.onTrackEnded(75_000)
        settle()
        assertEquals(60_000L, store.rows.single().listenedMs)
    }

    @Test
    fun `a track never played leaves no row`() = runTest {
        val store = FakeStore()
        val r = recorder(store)
        r.onTrackEnded(5_000)
        r.onPaused(5_000)
        settle()
        assertTrue(store.rows.isEmpty())
    }

    @Test
    fun `the next track is a new row`() = runTest {
        val store = FakeStore()
        val r = recorder(store)
        r.onPlaying(track("a"), 0)
        r.onTrackEnded(8_000)
        r.onPlaying(track("b"), 0)
        r.onTrackEnded(190_000)
        settle()
        assertEquals(listOf("a", "b"), store.rows.map { it.trackId })
        assertEquals(listOf(8_000L, 190_000L), store.rows.map { it.listenedMs })
    }

    @Test
    fun `starting over after most of a track is a restarted play`() = runTest {
        val store = FakeStore()
        val r = recorder(store)
        r.onPlaying(track(), 0)
        r.onSeek(180_000, 500)            // heard 180s of 200s, then back to the start
        r.onTrackEnded(100_500)
        settle()
        assertEquals(2, store.rows.size)
        assertFalse(store.rows[0].restarted)
        assertTrue(store.rows[0].finalized)
        assertTrue(store.rows[1].restarted)
        assertEquals(100_000L, store.rows[1].listenedMs)
    }

    @Test
    fun `seeking to the start early on is not a restart`() = runTest {
        val store = FakeStore()
        val r = recorder(store)
        r.onPlaying(track(), 0)
        r.onSeek(20_000, 0)
        r.onTrackEnded(30_000)
        settle()
        assertEquals(1, store.rows.size)
        assertEquals(50_000L, store.rows.single().listenedMs)
    }

    @Test
    fun `a repeat reported by the player is marked restarted`() = runTest {
        val store = FakeStore()
        val r = recorder(store)
        r.onPlaying(track(), 0)
        r.onTrackEnded(200_000)
        r.onPlaying(track(), 0, restarted = true)
        r.onTrackEnded(50_000)
        settle()
        assertEquals(listOf(false, true), store.rows.map { it.restarted })
    }

    @Test
    fun `origin and artist are recorded as given`() = runTest {
        val store = FakeStore()
        val r = recorder(store)
        r.onPlaying(track("x", origin = Origin.AUTO_QUEUE), 0)
        r.onTrackEnded(10_000)
        settle()
        assertEquals("AUTO_QUEUE", store.rows.single().origin)
        assertEquals("artist-x", store.rows.single().artistKey)
    }

    @Test
    fun `an update never overtakes a slow insert`() = runTest {
        val store = FakeStore(insertDelayMs = 500)
        val r = recorder(store)
        r.onPlaying(track(), 0)
        r.onPaused(5_000)
        r.onTrackEnded(5_000)
        settle()
        assertEquals(1, store.rows.size)
        assertEquals(5_000L, store.rows.single().listenedMs)
        assertTrue(store.rows.single().finalized)
    }

    @Test
    fun `a different track starting while one is open closes the open one`() = runTest {
        val store = FakeStore()
        val r = recorder(store)
        r.onPlaying(track("a"), 0)
        r.onPaused(40_000)
        r.onPlaying(track("b"), 0)        // end of "a" was never reported
        settle()
        assertEquals(2, store.rows.size)
        assertTrue(store.rows[0].finalized)
        assertEquals(40_000L, store.rows[0].listenedMs)
    }
}
