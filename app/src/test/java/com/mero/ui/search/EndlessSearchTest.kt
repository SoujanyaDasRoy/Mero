package com.mero.ui.search

import com.mero.domain.SearchItem
import com.mero.domain.SearchResultType
import com.mero.domain.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class EndlessSearchTest {

    private fun song(id: String) = Song(id = id, title = "T$id", artist = "A", durationSec = 125)
    private fun item(id: String) = songItem(song(id))

    @Test
    fun `radio seeds are the results in order, each used once`() {
        val results = listOf(item("a"), item("b"), item("c"))
        assertEquals("a", nextRadioSeed(results, emptySet())?.id)
        assertEquals("b", nextRadioSeed(results, setOf("a"))?.id)
        assertNull(nextRadioSeed(results, setOf("a", "b", "c")))
    }

    /** Podcast episodes and phone files have no YouTube radio to continue with. */
    @Test
    fun `only YouTube songs can seed more`() {
        val local = songItem(Song(id = "local:1", title = "x", artist = "y", sourceUri = "content://x"))
        assertEquals("b", nextRadioSeed(listOf(local, item("b")), emptySet())?.id)
    }

    @Test
    fun `more songs are appended once each, never repeating what is shown`() {
        val (merged, added) = appendSongs(listOf(item("a"), item("b")), listOf(song("b"), song("c"), song("c"), song("d")))
        assertEquals(listOf("a", "b", "c", "d"), merged.map { it.id })
        assertEquals(2, added)
    }

    @Test
    fun `an added song reads like any other result`() {
        val it = songItem(song("x"))
        assertEquals(SearchResultType.Song, it.type)
        assertEquals("A · 2:05", it.subtitle)
    }
}
