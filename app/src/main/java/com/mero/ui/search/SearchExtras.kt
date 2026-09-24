package com.mero.ui.search

import android.content.Context
import com.mero.domain.SearchItem
import com.mero.domain.SearchResultType
import com.mero.domain.Song
import com.mero.domain.asClock
import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.json.Json

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

internal const val MAX_RECENT_RESULTS = 15

/** [item] put at the front of [recent], once. */
internal fun withRecentResult(recent: List<SearchItem>, item: SearchItem): List<SearchItem> =
    (listOf(item) + recent.filterNot { it.id == item.id }).take(MAX_RECENT_RESULTS)

/**
 * A search result as saved: enough to show it again with its artwork and to
 * play or open it. A song keeps its address when it has one (a podcast
 * episode's feed link, a file on the phone) but never a YouTube stream URL,
 * which is not part of a song at all (CLAUDE.md constraint 2).
 */
@Serializable
private data class SavedResult(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String? = null,
    val type: String,
    val browseId: String? = null,
    val songTitle: String? = null,
    val artist: String? = null,
    val album: String = "",
    val durationSec: Int = 0,
    val sourceUri: String? = null,
)

private val recentJson = Json { ignoreUnknownKeys = true }

internal fun encodeRecentResults(items: List<SearchItem>): String = recentJson.encodeToString(
    items.map { item ->
        SavedResult(
            id = item.id,
            title = item.title,
            subtitle = item.subtitle,
            thumbnailUrl = item.thumbnailUrl,
            type = item.type.name,
            browseId = item.browseId,
            songTitle = item.song?.title,
            artist = item.song?.artist,
            album = item.song?.album.orEmpty(),
            durationSec = item.song?.durationSec ?: 0,
            sourceUri = item.song?.sourceUri,
        )
    },
)

internal fun decodeRecentResults(raw: String?): List<SearchItem> {
    if (raw.isNullOrBlank()) return emptyList()
    val saved = runCatching { recentJson.decodeFromString<List<SavedResult>>(raw) }.getOrNull() ?: return emptyList()
    return saved.mapNotNull { r ->
        val type = SearchResultType.entries.firstOrNull { it.name == r.type } ?: return@mapNotNull null
        SearchItem(
            id = r.id,
            title = r.title,
            subtitle = r.subtitle,
            thumbnailUrl = r.thumbnailUrl,
            type = type,
            song = r.artist?.let {
                Song(
                    id = r.id,
                    title = r.songTitle ?: r.title,
                    artist = it,
                    album = r.album,
                    durationSec = r.durationSec,
                    thumbnailUrl = r.thumbnailUrl,
                    sourceUri = r.sourceUri,
                )
            },
            browseId = r.browseId,
        )
    }
}

/** What was opened from search, most recent first. Kept on the phone only. */
class RecentResults(context: Context) {
    private val prefs = context.applicationContext.getSharedPreferences("search", Context.MODE_PRIVATE)

    fun load(): List<SearchItem> = decodeRecentResults(prefs.getString(KEY, null))

    fun save(items: List<SearchItem>) {
        // The old text history under "recent" is simply left behind; it is
        // tiny and nothing reads it any more.
        prefs.edit().putString(KEY, encodeRecentResults(items)).apply()
    }

    private companion object {
        const val KEY = "recent_results"
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
