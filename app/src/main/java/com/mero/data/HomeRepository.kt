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
    /** Why this shelf is here. Null for the genre feed, which is just browsing. */
    val subtitle: String? = null,
)

/**
 * The artists someone actually listens to, most-played first.
 *
 * There is no account to ask, so taste has to come from the only record Mero
 * has: what has been played on this phone. [ranked] arrives ordered by play
 * count, so position stands in for how often — an artist near the top of that
 * list is weighted more heavily than one near the bottom.
 *
 * Credits are one string with several names in it ("Pritam, Arijit Singh,
 * Shilpa Rao"), and the featured name on one track is often the headline on
 * another, so each name is counted separately. The first name still counts for
 * more, because it is usually whose record it is.
 */
fun topArtists(ranked: List<Song>, limit: Int = 3): List<String> {
    val scores = LinkedHashMap<String, Double>()
    ranked.forEachIndexed { position, song ->
        // Halves roughly every ten tracks down the list, so the top of it
        // decides the result without the tail being ignored entirely.
        val weight = 1.0 / (1.0 + position / 10.0)
        song.artist.split(",", "&", " x ", " feat. ", " ft. ")
            .map { it.trim() }
            .filter { it.length > 1 && it.lowercase() !in GENERIC_CREDITS }
            .forEachIndexed { billing, name ->
                val key = name.lowercase()
                val share = weight / (1.0 + billing * 0.5)
                scores[key] = (scores[key] ?: 0.0) + share
            }
    }
    val display = HashMap<String, String>()
    ranked.forEach { song ->
        song.artist.split(",", "&", " x ", " feat. ", " ft. ")
            .map { it.trim() }
            .forEach { display.putIfAbsent(it.lowercase(), it) }
    }
    return scores.entries
        .sortedByDescending { it.value }
        .take(limit)
        .mapNotNull { display[it.key] }
}

/** Names that describe a compilation rather than someone to hear more of. */
private val GENERIC_CREDITS = setOf(
    "various artists", "various", "unknown", "unknown artist", "topic", "dj",
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
    private companion object {
        /** Below this a shelf is a gap with two things in it. */
        const val MIN_SHELF = 4
    }

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

    /**
     * Shelves built from this phone's own listening, above the genre feed.
     *
     * Mero has no account and never will (CLAUDE.md constraint 1), so there is
     * no profile on a server to ask. What there is: every play is counted
     * locally, and YouTube will happily say what is similar to a given track
     * without knowing who is asking. Between the two — what you keep playing,
     * and what sounds like it — you get most of what a recommendation feed is
     * for, and none of the sign-in.
     *
     * Returns nothing at all for someone who has not played anything yet,
     * which is why the genre feed still exists underneath.
     */
    suspend fun personalSections(
        recentlyPlayed: List<Song>,
        mostPlayed: List<Song>,
        radio: RadioRepository,
    ): List<HomeSection> = coroutineScope {
        if (recentlyPlayed.isEmpty() && mostPlayed.isEmpty()) return@coroutineScope emptyList()

        val seed = recentlyPlayed.firstOrNull()
        val artists = topArtists(mostPlayed.ifEmpty { recentlyPlayed })

        val moreLikeThis = seed?.let { song ->
            async {
                radio.radioFor(song.id).getOrNull()
                    ?.filter { it.id != song.id }
                    ?.take(15)
                    ?.takeIf { it.size >= MIN_SHELF }
                    ?.let {
                        HomeSection(
                            title = "More like " + song.title.substringBefore(" ("),
                            songs = it,
                            subtitle = "Because you played it",
                        )
                    }
            }
        }

        val byArtist = artists.map { name ->
            async {
                YouTube.search(name, YouTube.SearchFilter.FILTER_SONG).getOrNull()
                    ?.items
                    ?.filterIsInstance<SongItem>()
                    ?.filter { it.id.isNotBlank() }
                    ?.map { it.toDomain() }
                    ?.take(15)
                    ?.takeIf { it.size >= MIN_SHELF }
                    ?.let {
                        HomeSection(
                            title = "More " + name,
                            songs = it,
                            subtitle = "One of your most played",
                        )
                    }
            }
        }

        val onRepeat = mostPlayed
            .take(15)
            .takeIf { it.size >= MIN_SHELF }
            ?.let { HomeSection("On repeat", it, "What you keep coming back to") }

        // Order matters: something familiar, then something adjacent to it,
        // then the artists. A feed that opens with a search result reads as
        // browsing; one that opens with your own listening reads as yours.
        listOfNotNull(onRepeat, moreLikeThis?.await()) + byArtist.mapNotNull { it.await() }
    }

    /** Fetches one shelf per seed, in parallel. Seeds that return nothing are dropped. */
    suspend fun sectionsFor(seedBatch: List<String>): Result<List<HomeSection>> = runCatchingCancellable {
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
