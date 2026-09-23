package com.mero.playback

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class ResumeAfterInterruptionTest {

    @Test
    fun `resumes once the other audio has been quiet a moment`() {
        assertTrue(shouldResumeNow(sinceLossMs = 20_000, quietMs = 3_000, heardOther = true))
    }

    /** A reel paused for a second, or the gap between two voice notes. */
    @Test
    fun `a brief silence is not the end`() {
        assertFalse(shouldResumeNow(sinceLossMs = 20_000, quietMs = 1_000, heardOther = true))
    }

    /**
     * A video that takes focus and then buffers is silent at first. Resuming
     * straight away would only be interrupted again when it starts.
     */
    @Test
    fun `an app that took over but has not made a sound yet gets time to start`() {
        assertFalse(shouldResumeNow(sinceLossMs = 4_000, quietMs = 4_000, heardOther = false))
        assertTrue(shouldResumeNow(sinceLossMs = 9_000, quietMs = 9_000, heardOther = false))
    }

    /** Twenty minutes into a video, music starting on its own would be a surprise. */
    @Test
    fun `a long interruption is left alone`() {
        assertFalse(shouldResumeNow(sinceLossMs = 11 * 60_000L, quietMs = 5_000, heardOther = true))
        assertTrue(shouldGiveUp(11 * 60_000L))
        assertFalse(shouldGiveUp(60_000L))
    }
}
