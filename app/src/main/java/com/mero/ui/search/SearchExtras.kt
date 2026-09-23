package com.mero.ui.search

import android.content.Context

internal const val MAX_RECENT_SEARCHES = 10

/** [query] added to the front of [recent], once, ignoring case and stray spaces. */
internal fun withRecent(recent: List<String>, query: String): List<String> {
    val q = query.trim()
    if (q.isEmpty()) return recent
    return (listOf(q) + recent.filterNot { it.trim().equals(q, ignoreCase = true) })
        .take(MAX_RECENT_SEARCHES)
}

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
