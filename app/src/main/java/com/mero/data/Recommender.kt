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

/**
 * The songs that best stand for this listener's taste right now.
 *
 * A like counts most, then repeat plays (with diminishing returns: ten plays is
 * not ten times a favourite). Both fade as the song goes unheard, so taste
 * follows the listener rather than fossilising; skipping hurts. At most
 * [perArtist] seeds per artist, so one artist cannot be the whole profile.
 */
fun tasteSeeds(stats: List<SongStats>, nowMs: Long, limit: Int = 6, perArtist: Int = 2): List<Seed> {
    val perArtistCount = HashMap<String, Int>()
    return stats
        .filter { it.song.isYouTube }
        .map { s ->
            val ageDays = s.lastPlayedAt?.let { ((nowMs - it) / DAY_MS).coerceAtLeast(0.0) } ?: 90.0
            val fresh = max(0.25, exp(-ageDays / 45.0))
            val love = (if (s.liked) 3.0 else 0.0) + 1.5 * ln(1.0 + s.plays)
            Seed(s.song, love * fresh - 2.0 * s.skips)
        }
        .filter { it.weight > 0 }
        .sortedByDescending { it.weight }
        .filter { perArtistCount.merge(primaryArtist(it.song), 1, Int::plus)!! <= perArtist }
        .take(limit)
}

/**
 * Merges several "more like this" lists into one queue.
 *
 * Each list is YouTube's own answer for one song (what listeners of it go on to
 * play), with a weight: the song playing now counts most, the songs this
 * listener loves count in proportion to how much. A song earns score from every
 * list it appears in, nearer the top worth more, so one that sits near what is
 * playing AND near what this listener loves rises above a song that fits only
 * one of those. That is similarity of sound and audience, not of names.
 *
 * Likes and recent skips then adjust it; the artist's name only nudges (0.15),
 * never decides. Heard-lately songs go back, skipped songs sink, a song skipped
 * twice and never liked is dropped, and no artist gets three in a row.
 */
fun blendRadios(
    radios: List<Pair<Double, List<Song>>>,
    taste: Taste,
    exclude: Set<String> = emptySet(),
    limit: Int = 40,
): List<Song> {
    val total = radios.sumOf { it.first }.coerceAtLeast(1e-9)
    val songs = LinkedHashMap<String, Song>()
    val mass = HashMap<String, Double>()
    radios.forEach { (weight, list) ->
        val n = list.size.coerceAtLeast(1)
        list.forEachIndexed { index, song ->
            songs.putIfAbsent(song.id, song)
            mass.merge(song.id, weight * (1.0 - index.toDouble() / n), Double::plus)
        }
    }
    val scored = songs.values.filter { it.id !in exclude }.mapNotNull { song ->
        val liked = song.id in taste.likedIds
        val skips = taste.skips[song.id] ?: 0
        if (!liked && skips >= 2) return@mapNotNull null
        val artist = primaryArtist(song)
        var score = mass.getValue(song.id) / total
        if (liked) score += 0.3
        score += 0.15 * min(1.0, (taste.artistAffinity[artist] ?: 0) / 3.0)
        if (song.id in taste.recentIds) score -= 0.8
        score -= 0.4 * skips
        if (!liked && (taste.artistSkips[artist] ?: 0) >= 3) score -= 0.4
        song to score
    }
    return spreadArtists(scored.sortedByDescending { it.second }.map { it.first }.toMutableList()).take(limit)
}
