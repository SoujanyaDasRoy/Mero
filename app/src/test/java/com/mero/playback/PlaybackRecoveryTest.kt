package com.mero.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class PlaybackRecoveryTest {

    /** Offline, retrying is pointless and counting it against the track is unfair. */
    @Test
    fun `offline waits for the network whatever the count`() {
        assertEquals(Recovery.WaitForNetwork, recoveryFor(failures = 1, online = false, hasOtherTrack = true))
        assertEquals(Recovery.WaitForNetwork, recoveryFor(failures = 9, online = false, hasOtherTrack = false))
    }

    @Test
    fun `online failures back off before retrying`() {
        assertEquals(Recovery.RetryIn(1_000), recoveryFor(1, online = true, hasOtherTrack = true))
        assertEquals(Recovery.RetryIn(4_000), recoveryFor(2, online = true, hasOtherTrack = true))
        assertEquals(Recovery.RetryIn(10_000), recoveryFor(3, online = true, hasOtherTrack = true))
    }

    /** A removed or region-blocked video fails forever; the queue should not stop on it. */
    @Test
    fun `a track that keeps failing online is skipped`() {
        assertEquals(Recovery.Skip, recoveryFor(4, online = true, hasOtherTrack = true))
    }

    @Test
    fun `with nothing to skip to, give up and let the play button retry`() {
        assertEquals(Recovery.GiveUp, recoveryFor(4, online = true, hasOtherTrack = false))
    }
}
