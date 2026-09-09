package com.mero.data

import com.mero.domain.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * What "learns your taste" actually means here, with no account to ask.
 *
 * The only record Mero has is what has been played on this phone, ordered by
 * how often. Everything the personalised shelves show is derived from this
 * function, so it is worth being exact about.
 */
class TopArtistsTest {

    private fun song(artist: String) = Song(id = artist.hashCode().toString(), title = "t", artist = artist)

    @Test
    fun `nothing played means nothing to suggest`() {
        assertTrue(topArtists(emptyList()).isEmpty())
    }

    @Test
    fun `the most played artist comes first`() {
        val ranked = listOf(song("Arijit Singh"), song("The Weeknd"), song("Kendrick Lamar"))
        assertEquals("Arijit Singh", topArtists(ranked).first())
    }

    /**
     * Credits are one string with several names in it, and the featured name
     * on one track is the headline on another. Counting the whole credit as a
     * single artist would treat "Pritam, Arijit Singh" as somebody who does
     * not exist.
     */
    @Test
    fun `a credit with several names counts each of them`() {
        val ranked = listOf(
            song("Pritam, Arijit Singh"),
            song("Arijit Singh, Shreya Ghoshal"),
            song("Sachin-Jigar, Arijit Singh"),
        )
        assertEquals("Arijit Singh", topArtists(ranked).first())
    }

    @Test
    fun `featured and ampersand credits split too`() {
        val ranked = listOf(
            song("Drake feat. Rihanna"),
            song("Rihanna & Calvin Harris"),
            song("Rihanna"),
        )
        assertEquals("Rihanna", topArtists(ranked).first())
    }

    /** Billed first usually means whose record it is. */
    @Test
    fun `the first name billed counts for more than a feature`() {
        val ranked = listOf(song("Headliner, Guest"), song("Headliner, Guest"))
        assertEquals(listOf("Headliner", "Guest"), topArtists(ranked, limit = 2))
    }

    @Test
    fun `a compilation credit is not somebody to hear more of`() {
        val ranked = listOf(
            song("Various Artists"),
            song("Various Artists"),
            song("Unknown Artist"),
            song("Fleetwood Mac"),
        )
        assertEquals(listOf("Fleetwood Mac"), topArtists(ranked, limit = 3))
    }

    @Test
    fun `the same artist spelled differently is not counted twice`() {
        val ranked = listOf(song("arijit singh"), song("Arijit Singh"), song("ARIJIT SINGH"))
        assertEquals(1, topArtists(ranked).size)
    }

    @Test
    fun `the limit is honoured`() {
        val ranked = (1..20).map { song("Artist $it") }
        assertEquals(3, topArtists(ranked).size)
        assertEquals(5, topArtists(ranked, limit = 5).size)
    }

    /**
     * Position stands in for play count, but the tail is not thrown away: an
     * artist appearing several times low down should still beat one that
     * appears once near the top.
     */
    @Test
    fun `many plays low down beat one play high up`() {
        val ranked = buildList {
            add(song("One Hit Wonder"))
            repeat(8) { add(song("Steady Favourite")) }
        }
        assertEquals("Steady Favourite", topArtists(ranked).first())
    }

    @Test
    fun `blank and single-character credits are ignored`() {
        val ranked = listOf(song(""), song("  "), song("X"), song("Radiohead"))
        assertEquals(listOf("Radiohead"), topArtists(ranked, limit = 3))
    }
}
