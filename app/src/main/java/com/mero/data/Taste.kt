package com.mero.data

import com.mero.domain.Song
import kotlin.math.min

/**
 * What this phone knows about what its listener likes. Built only from what
 * happened on this phone: likes, plays and skips. There is no account and
 * nothing leaves the device (CLAUDE.md constraint 1).
 */
data class Taste(
    val likedIds: Set<String> = emptySet(),
    /** Primary artist (lowercase) to how strongly they are liked and played. */
    val artistAffinity: Map<String, Int> = emptyMap(),
    /** Heard lately; worth hearing again, just not straight away. */
    val recentIds: Set<String> = emptySet(),
    /** Song id to times skipped in its first seconds. */
    val skips: Map<String, Int> = emptyMap(),
    /** Primary artist (lowercase) to total skips of their songs. */
    val artistSkips: Map<String, Int> = emptyMap(),
) {
    companion object {
        val None = Taste()
    }
}

/** The artist a song is filed under: the first credited, lowercased. */
internal fun primaryArtist(song: Song): String =
    song.artist.split(",", "&", " x ", " feat", " ft.").first().trim().lowercase()

/**
 * YouTube's similar songs, reordered for this listener.
 *
 * YouTube's order is the base: it knows what sounds alike. On top of it,
 * liked songs and liked or much-played artists come forward, songs heard
 * lately go back, and skipped songs and artists sink. A song skipped twice
 * and never liked is dropped. Then no artist gets more than two in a row.
 */
fun rankForTaste(candidates: List<Song>, taste: Taste): List<Song> {
    val n = candidates.size.coerceAtLeast(1)
    val scored = candidates.mapIndexedNotNull { index, song ->
        val liked = song.id in taste.likedIds
        val skips = taste.skips[song.id] ?: 0
        if (!liked && skips >= 2) return@mapIndexedNotNull null
        val artist = primaryArtist(song)
        // Half weight: everything here is already similar, so YouTube's order
        // breaks ties between songs this listener feels the same about. At
        // full weight a liked song near the end of the list could never
        // come forward.
        var score = 0.5 * (1.0 - index.toDouble() / n)
        if (liked) score += 0.6
        score += 0.5 * min(1.0, (taste.artistAffinity[artist] ?: 0) / 3.0)
        if (song.id in taste.recentIds) score -= 0.8
        score -= 0.4 * skips
        if (!liked && (taste.artistSkips[artist] ?: 0) >= 3) score -= 0.5
        song to score
    }
    val ordered = scored.sortedByDescending { it.second }.map { it.first }.toMutableList()
    return spreadArtists(ordered)
}

/** Moves a third song in a row by one artist behind the next different one. */
private fun spreadArtists(songs: MutableList<Song>): List<Song> {
    val out = ArrayList<Song>(songs.size)
    val waiting = ArrayDeque(songs)
    while (waiting.isNotEmpty()) {
        val runOfTwo = out.size >= 2 && primaryArtist(out[out.size - 1]) == primaryArtist(out[out.size - 2])
        val pick = if (runOfTwo) {
            val last = primaryArtist(out.last())
            waiting.firstOrNull { primaryArtist(it) != last } ?: waiting.first()
        } else {
            waiting.first()
        }
        waiting.remove(pick)
        out += pick
    }
    return out
}
