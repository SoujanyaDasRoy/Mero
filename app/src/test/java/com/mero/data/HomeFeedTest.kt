package com.mero.data

import com.mero.domain.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HomeFeedTest {

    private fun song(id: String, artist: String = "A$id") = Song(id = id, title = "T$id", artist = artist)
    private fun shelf(title: String, vararg ids: String) = HomeSection(title, ids.map { song(it) })

    @Test
    fun `a song appears once on Home, in the first shelf that has it`() {
        val out = dedupeShelves(
            listOf(shelf("A", "1", "2", "3", "4", "5"), shelf("B", "5", "6", "7", "8", "9")),
            alreadyShown = setOf("1"),
        )
        assertEquals(listOf("2", "3", "4", "5"), out[0].songs.map { it.id })
        assertEquals(listOf("6", "7", "8", "9"), out[1].songs.map { it.id })
    }

    @Test
    fun `a shelf left too thin by that is dropped`() {
        val out = dedupeShelves(listOf(shelf("A", "1", "2", "3", "4"), shelf("B", "1", "2", "3", "9")), emptySet())
        assertEquals(listOf("A"), out.map { it.title })
    }

    /** An Arijit Singh listener sees Bollywood before Malayalam hits. */
    @Test
    fun `shelves with the listener's artists come first, otherwise order stands`() {
        val malayalam = HomeSection("Malayalam Hits", listOf(song("m1", "Sushin Shyam"), song("m2", "Vineeth")))
        val jazz = HomeSection("Jazz", listOf(song("j1", "Miles Davis")))
        val bolly = HomeSection("Bollywood Hits", listOf(song("b1", "Arijit Singh"), song("b2", "Pritam")))
        val ordered = rankShelvesByTaste(listOf(malayalam, jazz, bolly), mapOf("arijit singh" to 5))
        assertEquals(listOf("Bollywood Hits", "Malayalam Hits", "Jazz"), ordered.map { it.title })
    }

    @Test
    fun `one mix per favourite artist, seeded by their song played most`() {
        val played = listOf(song("1", "Arijit Singh"), song("2", "Atif Aslam"), song("3", "Arijit Singh"), song("4", "Shreya Ghoshal"))
        val mixes = mixSeeds(played, limit = 3)
        assertEquals(listOf("Arijit Singh", "Atif Aslam", "Shreya Ghoshal"), mixes.map { it.artist })
        assertEquals("1", mixes.first().seed.id)
    }

    @Test
    fun `podcasts and phone files do not make mixes`() {
        val played = listOf(Song(id = "pod:1", title = "Ep", artist = "Show", sourceUri = "https://x"), song("2", "Atif Aslam"))
        assertEquals(listOf("Atif Aslam"), mixSeeds(played).map { it.artist })
    }

    @Test
    fun `every mood searches for something`() {
        Mood.entries.forEach { assertTrue(it.query.isNotBlank()) }
    }
}
