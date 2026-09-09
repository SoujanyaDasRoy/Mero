package com.mero.data

import org.junit.Assert.assertEquals
import org.junit.Test

class ArtworkSizeTest {

    private val google = "https://lh3.googleusercontent.com/abc=w220-h220-l90-rj"
    private val ytimg = "https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg"

    @Test
    fun `a sizeable url is asked for exactly the size wanted`() {
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w990-h990-l90-rj",
            google.atArtworkSize(990),
        )
    }

    /**
     * The reason this exists: every URL arrives from the repositories at 220px
     * and the full-screen player draws it four times that. Re-sizing has to
     * work upwards on a URL that was already narrowed once.
     */
    @Test
    fun `a url already narrowed to 220 can be widened again`() {
        val narrowed = google.atArtworkSize(220)
        assertEquals(
            "https://lh3.googleusercontent.com/abc=w1200-h1200-l90-rj",
            narrowed.atArtworkSize(1200),
        )
    }

    @Test
    fun `ytimg rounds up to the next named size`() {
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/mqdefault.jpg", ytimg.atArtworkSize(150))
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/hqdefault.jpg", ytimg.atArtworkSize(300))
        assertEquals("https://i.ytimg.com/vi/dQw4w9WgXcQ/sddefault.jpg", ytimg.atArtworkSize(900))
    }

    /**
     * maxresdefault is the only named size YouTube does not generate for every
     * video, and a missing one is a 404 — an empty square rather than a
     * slightly soft one. It must never be asked for.
     */
    @Test
    fun `maxresdefault is never requested`() {
        listOf(200, 600, 1200, 4000).forEach { px ->
            assert(!ytimg.atArtworkSize(px).contains("maxresdefault")) { "asked for maxres at $px" }
        }
    }

    @Test
    fun `an already-maxres url is brought back to something that exists`() {
        val maxres = "https://i.ytimg.com/vi/abc/maxresdefault.jpg"
        assertEquals("https://i.ytimg.com/vi/abc/sddefault.jpg", maxres.atArtworkSize(900))
    }

    @Test
    fun `a url with no size in it is left alone`() {
        val other = "https://example.invalid/cover.png"
        assertEquals(other, other.atArtworkSize(900))
    }
}
