package com.mero.playback

import com.mero.domain.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ResumePointTest {

    private fun song(id: String) = Song(id = id, title = id, artist = "")

    /** Track 4 of an album, 1:00 in: resume track 4 at 1:00, then 5 and 6. */
    @Test
    fun `resumes the track that was playing, then what came after`() {
        val q = resumeQueueOf(song("4"), listOf(song("5"), song("6")), 60_000)!!
        assertEquals(listOf("4", "5", "6"), q.songs.map { it.id })
        assertEquals(60_000, q.positionMs)
    }

    @Test
    fun `the current track is not queued twice`() {
        val q = resumeQueueOf(song("4"), listOf(song("4"), song("5")), 1_000)!!
        assertEquals(listOf("4", "5"), q.songs.map { it.id })
    }

    /** A position without its track would be applied to the wrong song. */
    @Test
    fun `without the current track, the next one starts from the top`() {
        val q = resumeQueueOf(null, listOf(song("5")), 60_000)!!
        assertEquals(0, q.positionMs)
    }

    @Test
    fun `nothing saved, nothing to resume`() {
        assertNull(resumeQueueOf(null, emptyList(), 0))
    }
}
