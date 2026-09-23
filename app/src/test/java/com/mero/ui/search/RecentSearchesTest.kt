package com.mero.ui.search

import org.junit.Assert.assertEquals
import org.junit.Test

class RecentSearchesTest {

    @Test
    fun `newest goes first`() {
        assertEquals(listOf("b", "a"), withRecent(listOf("a"), "b"))
    }

    /** Typing "Arijit" after "arijit " should move it up, not list it twice. */
    @Test
    fun `a repeat moves to the front instead of appearing twice`() {
        assertEquals(listOf("Arijit", "b"), withRecent(listOf("b", "arijit"), "  Arijit "))
    }

    @Test
    fun `blank is not a search`() {
        assertEquals(listOf("a"), withRecent(listOf("a"), "   "))
    }

    @Test
    fun `the list stays short`() {
        val full = (1..MAX_RECENT_SEARCHES).map { "q$it" }
        val next = withRecent(full, "new")
        assertEquals(MAX_RECENT_SEARCHES, next.size)
        assertEquals("new", next.first())
        assertEquals("q${MAX_RECENT_SEARCHES - 1}", next.last())
    }
}
