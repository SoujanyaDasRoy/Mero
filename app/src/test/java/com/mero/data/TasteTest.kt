package com.mero.data

import com.mero.domain.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class TasteTest {

    private fun song(id: String, artist: String = "A$id") = Song(id = id, title = "T$id", artist = artist)

    @Test
    fun `with no history, YouTube's order stands`() {
        val radio = listOf(song("1"), song("2"), song("3"))
        assertEquals(listOf("1", "2", "3"), rankForTaste(radio, Taste.None).map { it.id })
    }

    @Test
    fun `a liked song comes forward`() {
        val radio = listOf(song("1"), song("2"), song("3"), song("4"))
        val taste = Taste(likedIds = setOf("4"))
        assertEquals("4", rankForTaste(radio, taste).first().id)
    }

    @Test
    fun `artists you like and play come forward`() {
        val radio = listOf(song("1", "X"), song("2", "Y"), song("3", "Arijit Singh"))
        val taste = Taste(artistAffinity = mapOf("arijit singh" to 5))
        assertEquals("3", rankForTaste(radio, taste).first().id)
    }

    /** Three songs in, the fourth should not be one you heard ten minutes ago. */
    @Test
    fun `what you just heard goes to the back`() {
        val radio = listOf(song("1"), song("2"), song("3"))
        val taste = Taste(recentIds = setOf("1"))
        assertEquals("1", rankForTaste(radio, taste).last().id)
    }

    @Test
    fun `a song you keep skipping is left out`() {
        val radio = listOf(song("1"), song("2"))
        val ranked = rankForTaste(radio, Taste(skips = mapOf("1" to 2)))
        assertFalse(ranked.any { it.id == "1" })
    }

    /** Liking outranks skipping: maybe it was skipped in the wrong mood. */
    @Test
    fun `a liked song is never dropped for skips`() {
        val radio = listOf(song("1"), song("2"))
        assertTrue(rankForTaste(radio, Taste(likedIds = setOf("1"), skips = mapOf("1" to 5))).any { it.id == "1" })
    }

    @Test
    fun `an artist you skip a lot sinks`() {
        val radio = listOf(song("1", "Noisy"), song("2", "Calm"))
        val taste = Taste(artistSkips = mapOf("noisy" to 4))
        assertEquals("2", rankForTaste(radio, taste).first().id)
    }

    @Test
    fun `never more than two in a row by one artist`() {
        val radio = listOf(song("1", "Same"), song("2", "Same"), song("3", "Same"), song("4", "Other"))
        val order = rankForTaste(radio, Taste.None).map { it.id }
        assertEquals(listOf("1", "2", "4", "3"), order)
    }

    @Test
    fun `the first credited artist is the one that counts`() {
        assertEquals("arijit singh", primaryArtist(song("1", "Arijit Singh, Pritam & Amitabh")))
    }
}
