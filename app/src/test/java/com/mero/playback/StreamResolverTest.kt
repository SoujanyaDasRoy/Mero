package com.mero.playback

import org.junit.Assert.assertEquals
import org.junit.Test

class StreamResolverTest {

    @Test
    fun `appendOrUpdateRange leaves url untouched when position and length are non-positive`() {
        val url = "https://rr.googlevideo.com/videoplayback?expire=123"
        assertEquals(url, appendOrUpdateRange(url, position = 0, length = -1))
        assertEquals(url, appendOrUpdateRange(url, position = -1, length = -1))
    }

    @Test
    fun `appendOrUpdateRange appends range parameter when position is positive`() {
        val url = "https://rr.googlevideo.com/videoplayback?expire=123"
        val expected = "https://rr.googlevideo.com/videoplayback?expire=123&range=1500000-"
        assertEquals(expected, appendOrUpdateRange(url, position = 1500000, length = -1))
    }

    @Test
    fun `appendOrUpdateRange includes bounded end range when length is positive`() {
        val url = "https://rr.googlevideo.com/videoplayback?expire=123"
        val expected = "https://rr.googlevideo.com/videoplayback?expire=123&range=1500000-1999999"
        assertEquals(expected, appendOrUpdateRange(url, position = 1500000, length = 500000))
    }

    @Test
    fun `appendOrUpdateRange updates existing range parameter if present`() {
        val url = "https://rr.googlevideo.com/videoplayback?expire=123&range=0-999&ei=abc"
        val expected = "https://rr.googlevideo.com/videoplayback?expire=123&range=1500000-&ei=abc"
        assertEquals(expected, appendOrUpdateRange(url, position = 1500000, length = -1))
    }
}
