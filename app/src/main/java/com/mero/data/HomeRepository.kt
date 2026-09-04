package com.mero.data

import com.mero.domain.Song
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/**
 * Shelf headings read as titles, not sentences: "Bollywood Hits", not
 * "Bollywood hits". Hyphens count as word breaks ("lo-fi" -> "Lo-Fi"), and a
 * short override list covers the words that aren't just capitalised initials.
 */
internal fun String.titleCase(): String = split(" ").joinToString(" ") { word ->
    ACRONYMS[word] ?: word.split("-").joinToString("-") { part ->
        ACRONYMS[part] ?: part.replaceFirstChar { it.uppercase() }
    }
}

private val ACRONYMS = mapOf(
    "edm" to "EDM", "r&b" to "R&B", "k" to "K", "lo" to "Lo", "fi" to "Fi",
    "dj" to "DJ", "90s" to "90s", "80s" to "80s", "2000s" to "2000s",
)

/** One horizontal shelf on the home screen. */
data class HomeSection(
    val title: String,
    val songs: List<Song>,
)

class HomeRepository {

    /**
     * Discovery seeds. YouTube Music's anonymous home feed is almost entirely
     * album and playlist cards, and the vendored mapper only reads two-row
     * renderers, so no playable songs survive it. Seeding real searches gives
     * shelves of actual, tappable tracks with real artwork.
     *
     * The seeds are genres and moods, not content: every song comes back live
     * from YouTube. The pool is deliberately large so the feed differs between
     * refreshes rather than cycling the same four shelves.
     */
    val seeds: List<String> = listOf(
        "top hits", "bollywood hits", "lo-fi beats", "indie rock", "hip hop",
        "punjabi hits", "classic rock", "electronic", "jazz", "r&b",
        "acoustic covers", "90s bollywood", "tamil hits", "pop anthems",
        "telugu hits", "arijit singh", "workout songs", "chill vibes",
        "road trip songs", "soft rock", "edm bangers", "rap classics",
        "romantic hits", "sufi songs", "malayalam hits", "kannada hits",
        "2000s pop", "80s classics", "reggaeton", "k-pop", "afrobeats",
        "instrumental focus", "monsoon songs", "party anthems", "ghazals",
        "indie pop", "metal classics", "blues", "house music", "trap",
        "old is gold", "late night drive", "study beats", "feel good",
    )

    /** Fetches one shelf per seed, in parallel. Seeds that return nothing are dropped. */
    suspend fun sectionsFor(seedBatch: List<String>): Result<List<HomeSection>> = runCatching {
        coroutineScope {
            seedBatch
                .map { seed ->
                    async {
                        val songs = YouTube.search(seed, YouTube.SearchFilter.FILTER_SONG)
                            .getOrNull()
                            ?.items
                            ?.filterIsInstance<SongItem>()
                            ?.filter { it.id.isNotBlank() }
                            ?.map { it.toDomain() }
                            ?.shuffled()
                            ?.take(15)
                            .orEmpty()
                        if (songs.isEmpty()) {
                            null
                        } else {
                            HomeSection(seed.titleCase(), songs)
                        }
                    }
                }
                .mapNotNull { it.await() }
        }
    }
}
