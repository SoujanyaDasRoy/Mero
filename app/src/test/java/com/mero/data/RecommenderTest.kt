package com.mero.data

import com.mero.domain.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class RecommenderTest {
    private val now = 1_000_000_000_000L
    private val day = 86_400_000L

    private fun song(id: String, artist: String = "A$id") = Song(id = id, title = "T$id", artist = artist)

    private fun stats(
        id: String,
        artist: String = "A$id",
        liked: Boolean = false,
        plays: Int = 0,
        daysAgo: Int = 1,
        skips: Int = 0,
    ) = SongStats(song(id, artist), liked, plays, now - daysAgo * day, skips)

    @Test
    fun `a liked and replayed song is the strongest seed`() {
        val seeds = tasteSeeds(listOf(stats("a", plays = 3), stats("b", liked = true, plays = 6)), now)
        assertEquals("b", seeds.first().song.id)
    }

    @Test
    fun `a song heard long ago counts for less than one heard lately`() {
        val seeds = tasteSeeds(listOf(stats("old", plays = 5, daysAgo = 200), stats("new", plays = 5, daysAgo = 2)), now)
        assertEquals(listOf("new", "old"), seeds.map { it.song.id })
    }

    @Test
    fun `no artist fills the profile`() {
        val seeds = tasteSeeds((1..5).map { stats("s$it", artist = "Same", liked = true, plays = it) }, now)
        assertEquals(2, seeds.size)
    }

    @Test
    fun `skipped songs stop being seeds`() {
        assertTrue(tasteSeeds(listOf(stats("a", plays = 1, skips = 2)), now).isEmpty())
    }

    @Test
    fun `one radio and no taste keeps YouTube's order`() {
        val radio = listOf(song("1"), song("2"), song("3"))
        assertEquals(listOf("1", "2", "3"), blendRadios(listOf(3.0 to radio), Taste.None).map { it.id })
    }

    @Test
    fun `a song both radios agree on beats one only a single radio likes`() {
        val current = listOf(song("x"), song("shared")) + (1..8).map { song("c$it") }
        val loved = listOf(song("shared"), song("y"))
        val out = blendRadios(listOf(3.0 to current, 1.0 to loved), Taste.None).map { it.id }
        assertEquals("shared", out.first())
    }

    @Test
    fun `the song playing decides more than a favourite`() {
        val current = listOf(song("near1"), song("near2"), song("near3"))
        val loved = listOf(song("far1"), song("far2"), song("far3"))
        val out = blendRadios(listOf(3.0 to current, 1.0 to loved), Taste.None).map { it.id }
        assertTrue(out.indexOf("near1") < out.indexOf("far1"))
    }

    @Test
    fun `artist name alone does not outrank similarity`() {
        val radio = listOf(song("1", "Other"), song("2", "Other2"), song("3", "Fav"))
        val taste = Taste(artistAffinity = mapOf("fav" to 9))
        assertEquals("1", blendRadios(listOf(3.0 to radio), taste).first().id)
    }

    @Test
    fun `heard lately goes back and skipped twice is dropped`() {
        val radio = listOf(song("1"), song("2"), song("3"))
        val out = blendRadios(listOf(3.0 to radio), Taste(recentIds = setOf("1"), skips = mapOf("2" to 2))).map { it.id }
        assertEquals(listOf("3", "1"), out)
    }
}
