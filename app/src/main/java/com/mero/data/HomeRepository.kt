package com.mero.data

import com.mero.domain.Song
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.SongItem
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope

/** One horizontal shelf on the home screen. */
data class HomeSection(
    val title: String,
    val songs: List<Song>,
)

class HomeRepository {

    /**
     * Discovery seeds. YouTube Music's anonymous home feed is almost entirely
     * album and playlist cards — and the vendored mapper only reads two-row
     * renderers, so no playable songs survive it. Seeding real searches instead
     * gives a shelf of actual, tappable tracks with real artwork.
     *
     * The seeds are genres, not content: every song shown comes back live from
     * YouTube, and the selection changes because the seeds are shuffled.
     */
    private val seeds = listOf(
        "top hits", "bollywood hits", "lo-fi beats", "indie rock", "hip hop",
        "punjabi hits", "classic rock", "electronic", "jazz", "r&b",
        "acoustic covers", "90s bollywood", "tamil hits", "pop anthems",
    )

    suspend fun sections(shelves: Int = 4): Result<List<HomeSection>> = runCatching {
        coroutineScope {
            seeds.shuffled().take(shelves)
                .map { seed ->
                    async {
                        val songs = YouTube.search(seed, YouTube.SearchFilter.FILTER_SONG)
                            .getOrNull()
                            ?.items
                            ?.filterIsInstance<SongItem>()
                            ?.filter { it.id.isNotBlank() }
                            ?.map { it.toDomain() }
                            ?.take(12)
                            .orEmpty()
                        if (songs.isEmpty()) {
                            null
                        } else {
                            HomeSection(seed.replaceFirstChar { it.uppercase() }, songs)
                        }
                    }
                }
                .mapNotNull { it.await() }
                .ifEmpty { error("No results — extraction may be blocked") }
        }
    }
}
