package com.mero.ui.search

import com.mero.domain.SearchItem
import com.mero.domain.SearchResultType
import com.mero.domain.Song
import org.junit.Assert.assertEquals
import org.junit.Test

class RecentResultsTest {

    private fun songItem(id: String) = songItem(Song(id = id, title = "T$id", artist = "A$id", durationSec = 200))

    @Test
    fun `the newest opened goes first`() {
        assertEquals(listOf("b", "a"), withRecentResult(listOf(songItem("a")), songItem("b")).map { it.id })
    }

    @Test
    fun `opening one again moves it up instead of listing it twice`() {
        val list = listOf(songItem("a"), songItem("b"))
        assertEquals(listOf("b", "a"), withRecentResult(list, songItem("b")).map { it.id })
    }

    @Test
    fun `the list stays short`() {
        val full = (1..MAX_RECENT_RESULTS).map { songItem("s$it") }
        val next = withRecentResult(full, songItem("new"))
        assertEquals(MAX_RECENT_RESULTS, next.size)
        assertEquals("new", next.first().id)
    }

    /** Saved and read back, a song must still play: its address and length survive. */
    @Test
    fun `a song survives being saved`() {
        val podcast = songItem(Song(id = "pod:1", title = "Ep", artist = "Show", durationSec = 3600, sourceUri = "https://x/ep.mp3"))
        val back = decodeRecentResults(encodeRecentResults(listOf(podcast)))
        assertEquals(podcast, back.single())
    }

    @Test
    fun `an album survives being saved`() {
        val album = SearchItem("MPRE1", "Album", "Artist · 2020", "https://img", SearchResultType.Album, browseId = "MPRE1")
        assertEquals(album, decodeRecentResults(encodeRecentResults(listOf(album))).single())
    }

    @Test
    fun `nothing saved, or something unreadable, is an empty list`() {
        assertEquals(emptyList<SearchItem>(), decodeRecentResults(null))
        assertEquals(emptyList<SearchItem>(), decodeRecentResults("not json"))
    }
}
