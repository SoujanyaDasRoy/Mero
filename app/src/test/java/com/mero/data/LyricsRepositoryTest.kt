package com.mero.data

import com.mero.domain.LyricLine
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class LyricsParseTest {

    @Test
    fun `timestamps keep their fraction of a second`() {
        assertEquals(
            listOf(LyricLine(12_340, "two digits"), LyricLine(13_345, "three digits"), LyricLine(14_500, "one digit")),
            LyricsRepository.parseLrc("[00:12.34] two digits\n[00:13.345] three digits\n[00:14.5] one digit"),
        )
    }

    @Test
    fun `a line sung twice appears at both times, in order`() {
        assertEquals(
            listOf(LyricLine(5_000, "verse"), LyricLine(10_000, "chorus"), LyricLine(70_000, "chorus")),
            LyricsRepository.parseLrc("[00:10.00][01:10.00] chorus\n[00:05.00] verse"),
        )
    }

    @Test
    fun `an offset tag shifts every line`() {
        assertEquals(listOf(LyricLine(9_500, "hi")), LyricsRepository.parseLrc("[offset:+500]\n[00:10.00] hi"))
    }

    @Test
    fun `metadata and empty lines are not lyrics`() {
        assertEquals(listOf(LyricLine(1_000, "words")), LyricsRepository.parseLrc("[ar: Someone]\n[00:00.50]\n[00:01.00] words"))
    }
}

class LyricsLookupTest {

    @Test
    fun `titles lose the asides lyrics sites do not use`() {
        assertEquals("Tum Hi Ho", LyricsRepository.cleanTitle("Tum Hi Ho (From \"Aashiqui 2\")"))
        assertEquals("Neele Neele Ambar Par", LyricsRepository.cleanTitle("Neele Neele Ambar Par (Male Version)"))
        assertEquals("Hotel California", LyricsRepository.cleanTitle("Hotel California - 2013 Remaster"))
        assertEquals("Kesariya", LyricsRepository.cleanTitle("Kesariya | Lyrical Video"))
        assertEquals("Stay", LyricsRepository.cleanTitle("Stay feat. Justin Bieber"))
    }

    /** A title that is nothing but brackets is still better than an empty query. */
    @Test
    fun `a title that is all aside survives`() {
        assertEquals("(Intro)", LyricsRepository.cleanTitle("(Intro)"))
    }

    @Test
    fun `synced beats plain, then the nearest duration wins`() {
        val plain = LrcLibRecord(duration = 262.0, plainLyrics = "p")
        val syncedFar = LrcLibRecord(duration = 270.0, syncedLyrics = "[00:01.00] s")
        val syncedNear = LrcLibRecord(duration = 263.0, syncedLyrics = "[00:01.00] n")
        assertEquals(syncedNear, LyricsRepository.pickBest(listOf(plain, syncedFar, syncedNear), 262, strictDuration = true))
    }

    /** A remix or cover with the same title is minutes off; it must not win. */
    @Test
    fun `a strict search ignores records far off the song's length`() {
        val cover = LrcLibRecord(duration = 400.0, syncedLyrics = "[00:01.00] s")
        assertNull(LyricsRepository.pickBest(listOf(cover), 262, strictDuration = true))
        assertEquals(cover, LyricsRepository.pickBest(listOf(cover), 262, strictDuration = false))
    }
}
