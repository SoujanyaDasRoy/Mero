package com.mero.ui.player

import com.mero.domain.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Test

class ActiveLyricLineTest {

    private val lines = listOf(LyricLine(1_000, "a"), LyricLine(4_200, "b"), LyricLine(8_000, "c"))

    @Test
    fun `nothing is lit before the first line`() {
        assertEquals(-1, activeLine(lines, 500))
    }

    @Test
    fun `the line being sung is lit`() {
        assertEquals(1, activeLine(lines, 5_000))
        assertEquals(2, activeLine(lines, 60_000))
    }

    /** Whole-second rounding used to light 4.2s at 5s; now it lights just before. */
    @Test
    fun `a line lights a breath early, not a second late`() {
        assertEquals(0, activeLine(lines, 4_000))
        assertEquals(1, activeLine(lines, 4_060))
    }
}
