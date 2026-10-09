package com.mero.data

import com.mero.domain.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

/** Phase 1: skips as a rate, a recency penalty that fades, rank credit independent of list length. */
class RecommenderSkipRateTest {
    private val now = 1_000_000_000_000L
    private val hour = 3_600_000L
    private val day = 24 * hour

    private fun song(id: String, artist: String = "A$id") = Song(id = id, title = "T$id", artist = artist)

    private fun stats(id: String, plays: Int, skips: Int, liked: Boolean = false, daysAgo: Int = 1) =
        SongStats(song(id), liked, plays, now - daysAgo * day, skips)

    /* ------------------------------ skipRate ------------------------------ */

    @Test
    fun `no plays and no skips is a rate of zero`() = assertEquals(0.0, skipRate(0, 0), 1e-9)

    @Test
    fun `the prior keeps one data point from proving much`() {
        assertEquals(0.25, skipRate(1, 1), 1e-9)   // 1 / (1 + 3)
        assertEquals(0.4, skipRate(2, 2), 1e-9)    // 2 / (2 + 3)
    }

    @Test
    fun `many plays dilute a few skips`() {
        assertEquals(2.0 / 23.0, skipRate(2, 20), 1e-9)
        assertTrue(skipRate(2, 20) < skipRate(2, 2))
    }

    @Test
    fun `the rate never leaves 0 to 1`() = assertEquals(1.0, skipRate(9, 0), 1e-9)

    /* ------------------------------ tasteSeeds ------------------------------ */

    @Test
    fun `a heavily played favourite with a few skips stays a seed`() {
        val seeds = tasteSeeds(listOf(stats("fav", plays = 50, skips = 4)), now)
        assertEquals(listOf("fav"), seeds.map { it.song.id })
    }

    @Test
    fun `a song started twice and skipped twice is not a seed`() {
        assertTrue(tasteSeeds(listOf(stats("nope", plays = 2, skips = 2)), now).isEmpty())
    }

    @Test
    fun `skips count against a seed by rate, so the same skips hurt a thin history more`() {
        val seeds = tasteSeeds(listOf(stats("thin", plays = 6, skips = 2), stats("deep", plays = 60, skips = 2)), now)
        assertEquals("deep", seeds.first().song.id)
        assertTrue(seeds.first { it.song.id == "deep" }.weight > seeds.first { it.song.id == "thin" }.weight)
    }

    @Test
    fun `a like keeps a song in even when it is often skipped`() {
        val seeds = tasteSeeds(listOf(stats("loved", plays = 4, skips = 3, liked = true)), now)
        assertEquals(listOf("loved"), seeds.map { it.song.id })
    }

    @Test
    fun `a shorter memory ranks this week's song above an old favourite`() {
        val songs = listOf(stats("old", plays = 40, skips = 0, daysAgo = 60), stats("new", plays = 6, skips = 0, daysAgo = 1))
        assertEquals("old", tasteSeeds(songs, now, tauDays = 1000.0).first().song.id)
        assertEquals("new", tasteSeeds(songs, now, tauDays = 7.0).first().song.id)
    }

    /* ------------------------------ blendRadios ------------------------------ */

    private val radio = listOf(song("a"), song("b"), song("c"))

    private fun blend(taste: Taste) = blendRadios(listOf(3.0 to radio), taste, nowMs = now).map { it.id }

    @Test
    fun `a song skipped 2 of 2 plays is dropped`() {
        val taste = Taste(skips = mapOf("a" to 2), plays = mapOf("a" to 2))
        assertFalse("a" in blend(taste))
    }

    @Test
    fun `a song skipped 2 of 20 plays is kept`() {
        val taste = Taste(skips = mapOf("a" to 2), plays = mapOf("a" to 20))
        assertTrue("a" in blend(taste))
    }

    @Test
    fun `a liked song is never dropped for skips`() {
        val taste = Taste(likedIds = setOf("a"), skips = mapOf("a" to 2), plays = mapOf("a" to 2))
        assertTrue("a" in blend(taste))
    }

    @Test
    fun `the recency penalty fades over hours`() {
        fun first(agoMs: Long) = blend(Taste(lastPlayedAt = mapOf("a" to now - agoMs))).first()
        assertEquals("b", first(1 * hour))     // just heard: goes back
        assertEquals("a", first(48 * hour))    // two days ago: back in order
    }

    @Test
    fun `with no timestamp the recent set falls back to the flat penalty`() {
        assertEquals("a", blend(Taste(recentIds = setOf("a"))).last())
    }

    @Test
    fun `the shorter the memory setting the sooner a song comes back`() {
        val taste = Taste(lastPlayedAt = mapOf("a" to now - 6 * hour))
        val slow = blendRadios(listOf(3.0 to radio), taste, nowMs = now, recentTauHours = 24.0).map { it.id }
        val fast = blendRadios(listOf(3.0 to radio), taste, nowMs = now, recentTauHours = 2.0).map { it.id }
        assertTrue(slow.indexOf("a") > fast.indexOf("a"))
    }

    /* ------------------------------ rank credit ------------------------------ */

    @Test
    fun `rank credit is 1 at the top`() = assertEquals(1.0, rankCredit(0), 1e-9)

    @Test
    fun `rank credit falls with position`() {
        assertTrue(rankCredit(1) < rankCredit(0))
        assertTrue(rankCredit(10) < rankCredit(5))
        assertTrue(rankCredit(100) > 0.0)
    }

    @Test
    fun `where a song sits does not depend on how long the list is`() {
        // The same song in second place scores the same in a 3-long and a 50-long list,
        // so it ties with a song that is top of its own short list and not beaten by length.
        val short = listOf(song("x"), song("y"), song("z"))
        val long = listOf(song("x"), song("y")) + (1..48).map { song("f$it") }
        val a = blendRadios(listOf(1.0 to short), Taste.None).map { it.id }
        val b = blendRadios(listOf(1.0 to long), Taste.None).map { it.id }
        assertEquals(a.take(2), b.take(2))
    }
}
