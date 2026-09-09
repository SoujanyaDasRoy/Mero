package com.mero.ui.library

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * The line under "Library". It is the first thing on that screen, so an empty
 * library has to say something other than a row of zeroes.
 */
class LibrarySummaryTest {

    @Test
    fun `an empty library says so plainly`() {
        assertEquals("Nothing saved yet", summaryOf(0, 0, 0))
    }

    @Test
    fun `only what exists is mentioned`() {
        assertEquals("3 liked", summaryOf(0, 3, 0))
        assertEquals("2 playlists", summaryOf(2, 0, 0))
        assertEquals("5 downloaded", summaryOf(0, 0, 5))
    }

    @Test
    fun `one playlist is not one playlists`() {
        assertEquals("1 playlist", summaryOf(1, 0, 0))
    }

    @Test
    fun `everything together reads as one line`() {
        assertEquals("2 playlists · 12 liked · 4 downloaded", summaryOf(2, 12, 4))
    }
}
