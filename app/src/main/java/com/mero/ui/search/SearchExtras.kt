package com.mero.ui.search

import android.content.Context
import com.mero.domain.SearchItem
import com.mero.domain.SearchResultType
import com.mero.domain.Song
import com.mero.domain.asClock

internal const val MAX_RECENT_SEARCHES = 10

/** [query] added to the front of [recent], once, ignoring case and stray spaces. */
internal fun withRecent(recent: List<String>, query: String): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return recent
    return (listOf(q) + recent.filterNot { it.trim().equals(q, ignoreCase = true) })
        .take(MAX_RECENT_SEARCHES)
}

/**
 * Endless song results. YouTube stops a song search after about twenty pages
 * (400–540 songs, measured); past that, the list continues with songs like
 * the results, taking each result in turn as the seed for YouTube's radio.
 */
internal fun nextRadioSeed(results: List<SearchItem>, usedSeeds: Set<String>): Song? =
    results.asSequence()
        .mapNotNull { it.song }
        .firstOrNull { it.isYouTube && it.id !in usedSeeds }

/** [results] plus whichever of [more] are not already there; and how many that was. */
internal fun appendSongs(results: List<SearchItem>, more: List<Song>): Pair<List<SearchItem>, Int> {
    val seen = results.mapTo(HashSet()) { it.id }
    val fresh = more.filter { seen.add(it.id) }.map(::songItem)
    return (results + fresh) to fresh.size
}

internal fun songItem(song: Song): SearchItem = SearchItem(
    id = song.id,
    title = song.title,
    subtitle = if (song.durationSec > 0) song.artist + " · " + song.durationSec.asClock() else song.artist,
    thumbnailUrl = song.thumbnailUrl,
    type = SearchResultType.Song,
    song = song,
)

/** Recent searches, kept on the phone only. One per line; the search box is single-line. */
class RecentSearches(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("search", Context.MODE_PRIVATE)

    fun load(): List<String> = prefs.getString(KEY, null)?.lines()?.filter { it.isNotBlank() }.orEmpty()

    fun save(recent: List<String>) {
        prefs.edit().putString(KEY, recent.joinToString("\n")).apply()
    }

    private companion object {
        const val KEY = "recent"
    }
}

/**
 * Apple's top-level podcast genres, in roughly the order people browse them.
 * Tapping one searches the podcast directory for it — Apple's search matches
 * a show's genre as well as its title, so "Comedy" finds comedy shows.
 */
internal val PODCAST_CATEGORIES = listOf(
    "Comedy", "News", "True Crime", "Society & Culture", "Business", "Technology",
    "Health & Fitness", "History", "Science", "Sports", "Education", "Arts",
    "Music", "TV & Film", "Kids & Family", "Fiction", "Religion & Spirituality",
    "Leisure",
)
