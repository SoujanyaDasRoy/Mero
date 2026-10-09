package com.mero.data

import com.mero.domain.Song
import kotlin.math.exp
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min

/** What the database knows about one song's place in this listener's life. */
data class SongStats(
    val song: Song,
    val liked: Boolean,
    val plays: Int,
    val lastPlayedAt: Long?,
    val skips: Int,
)

/** A song to build recommendations from, and how strongly this listener feels about it. */
data class Seed(val song: Song, val weight: Double)

private const val DAY_MS = 86_400_000.0
private const val HOUR_MS = 3_600_000.0

/**
 * Skips as a RATE, not a count. A favourite with 50 plays and 4 skips is fine; a song
 * started twice and skipped twice is not. The +3 is a prior so one data point proves little.
 */
internal fun skipRate(skips: Int, plays: Int): Double =
    (skips / (plays + 3.0)).coerceIn(0.0, 1.0)

/**
 * The songs that best stand for this listener's taste right now.
 *
 * love = like (3) + 1.5 * ln(1 + completed plays). Completed plays = plays that were not skips,
 * so a song you start and abandon does not count as a favourite.
 * Freshness fades the song as it goes unheard; [tauDays] sets how fast. Use a short one
 * (about 7) for "what I'm into this week" (infinite playback), the default for Your Mix.
 * Skips scale the seed down by the skip RATE instead of subtracting 2 per skip, which used to
 * push heavily played songs out of the profile as their lifetime skips accumulated.
 */
fun tasteSeeds(
    stats: List<SongStats>,
    nowMs: Long,
    limit: Int = 6,
    perArtist: Int = 2,
    tauDays: Double = 45.0,
    floor: Double = 0.25,
): List<Seed> {
    val perArtistCount = HashMap<String, Int>()
    return stats
        .filter { it.song.isYouTube }
        .filter { it.liked || skipRate(it.skips, it.plays) < 0.5 }
        .map { s ->
            val ageDays = s.lastPlayedAt?.let { ((nowMs - it) / DAY_MS).coerceAtLeast(0.0) } ?: 90.0
            val fresh = max(floor, exp(-ageDays / tauDays))
            val completed = (s.plays - s.skips).coerceAtLeast(0)
            val love = (if (s.liked) 3.0 else 0.0) + 1.5 * ln(1.0 + completed)
            val trust = 1.0 - min(0.9, 1.5 * skipRate(s.skips, s.plays))
            Seed(s.song, love * fresh * trust)
        }
        .filter { it.weight > 0 }
        .sortedByDescending { it.weight }
        .filter { perArtistCount.merge(primaryArtist(it.song), 1, Int::plus)!! <= perArtist }
        .take(limit)
}

/**
 * Position credit for one list, 1.0 at the top, falling smoothly. Replaces 1 - index/size,
 * which depended on how long the list happened to be (position 5 of 10 scored less than
 * position 5 of 50) and went to zero at the tail. The average credit over a 25-long list is
 * about 0.47, close to the old 0.5, so the adjustments below keep their relative size.
 */
private const val RANK_K = 8.0
internal fun rankCredit(index: Int): Double = (RANK_K + 1.0) / (RANK_K + 1.0 + index)

/**
 * Merges several "more like this" lists into one queue.
 *
 * Each list is YouTube's own answer for one song (what listeners of it go on to play), with a
 * weight: the song playing now counts most, the songs this listener loves count in proportion
 * to how much. A song earns score from every list it appears in, nearer the top worth more,
 * so one that sits near what is playing AND near what this listener loves rises above a song
 * that fits only one of those. Base score is in 0..1 (divided by the total weight).
 *
 * Adjustments:
 *  - liked +0.3; artist name up to +0.15 (a nudge, never decisive).
 *  - heard lately: smooth penalty that fades over hours ([recentTauHours]) instead of a flat -0.8
 *    cliff. Falls back to the flat penalty when no last-played time is known.
 *  - skips: penalty grows with the skip RATE. A song is dropped only when it is not liked, was
 *    skipped at least twice, AND the rate is high (>= 0.4), so one favourite skipped twice
 *    among many plays is not banned.
 *  - an artist you keep skipping: penalised when their skip share is high, not on a raw count.
 *  - no artist gets three in a row (spreadArtists).
 */
fun blendRadios(
    radios: List<Pair<Double, List<Song>>>,
    taste: Taste,
    exclude: Set<String> = emptySet(),
    limit: Int = 40,
    nowMs: Long = System.currentTimeMillis(),
    recentTauHours: Double = 12.0,
): List<Song> {
    val total = radios.sumOf { it.first }.coerceAtLeast(1e-9)
    val songs = LinkedHashMap<String, Song>()
    val mass = HashMap<String, Double>()
    radios.forEach { (weight, list) ->
        list.forEachIndexed { index, song ->
            songs.putIfAbsent(song.id, song)
            mass.merge(song.id, weight * rankCredit(index), Double::plus)
        }
    }
    val scored = songs.values.filter { it.id !in exclude }.mapNotNull { song ->
        val liked = song.id in taste.likedIds
        val skips = taste.skips[song.id] ?: 0
        val rate = skipRate(skips, taste.plays[song.id] ?: 0)
        if (!liked && skips >= 2 && rate >= 0.4) return@mapNotNull null

        val artist = primaryArtist(song)
        var score = mass.getValue(song.id) / total
        if (liked) score += 0.3
        score += 0.15 * min(1.0, (taste.artistAffinity[artist] ?: 0) / 3.0)

        val last = taste.lastPlayedAt[song.id]
        score -= when {
            last != null -> 0.8 * exp(-((nowMs - last).coerceAtLeast(0L) / HOUR_MS) / recentTauHours)
            song.id in taste.recentIds -> 0.8
            else -> 0.0
        }

        score -= 1.2 * rate   // 1 skip, no known plays: 0.4, same as before

        if (!liked) {
            val aSkips = taste.artistSkips[artist] ?: 0
            val aLove = taste.artistAffinity[artist] ?: 0
            val aRate = aSkips / (aSkips + aLove + 3.0)
            if (aSkips >= 3 && aRate > 0.3) score -= 0.4
        }
        song to score
    }
    return spreadArtists(scored.sortedByDescending { it.second }.map { it.first }.toMutableList()).take(limit)
}