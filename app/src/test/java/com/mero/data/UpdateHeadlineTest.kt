package com.mero.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class UpdateHeadlineTest {

    @Test
    fun `the first line of the notes, without markdown`() {
        val notes = "**Mero 1.11.0: podcasts, [Android Auto](https://x), and `lyrics`.**\n\nMore below"
        assertEquals("Mero 1.11.0: podcasts, Android Auto, and lyrics.", updateHeadline(notes))
    }

    @Test
    fun `headings and rules are skipped`() {
        assertEquals("Real words", updateHeadline("## What's new\n---\nReal words"))
    }

    @Test
    fun `long lines are cut to fit a notification`() {
        val h = updateHeadline("x".repeat(300))!!
        assertTrue(h.length <= 140)
        assertTrue(h.endsWith("…"))
    }

    @Test
    fun `empty notes give nothing`() {
        assertNull(updateHeadline(""))
    }
}
