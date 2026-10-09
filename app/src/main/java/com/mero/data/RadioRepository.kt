package com.mero.data

import com.mero.domain.Song
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.WatchEndpoint
import kotlinx.coroutines.async
import kotlinx.coroutines.awaitAll
import kotlinx.coroutines.coroutineScope
import java.util.concurrent.ConcurrentHashMap

/**
 * "What plays after this": YouTube Music's own radio for a track, fetched
 * anonymously through the watch-next endpoint, then blended with this
 * listener's taste (see [blendRadios]).
 *
 * Mero never signs in (CLAUDE.md constraint 1), so there is no account history
 * to build a profile from. The profile is built on the phone from likes, plays
 * and skips; it is applied by asking YouTube for the radios of the songs the
 * listener loves as well as the one playing, and keeping what they agree on.
 */
class RadioRepository {

    private val cache = ConcurrentHashMap<String, Pair<Long, List<Song>>>()

    /** YouTube's similar songs for one track, kept a while: refills and mixes ask for the same ones. */
    private suspend fun raw(videoId: String): List<Song> {
        val now = System.currentTimeMillis()
        cache[videoId]?.takeIf { now - it.first < CACHE_MS }?.let { return it.second }
        val songs = YouTube.next(WatchEndpoint(videoId = videoId)).getOrThrow()
            .items
            .filter { it.id.isNotBlank() && it.id != videoId }
            .map { it.toDomain() }
            // YouTube's list continues into its automix, which repeats songs.
            .distinctBy { it.id }
        cache[videoId] = now to songs
        return songs
    }

    /** Radios of the listener's own favourites, weights scaled so the strongest is 1. */
    private suspend fun tasteRadios(seeds: List<Seed>): List<Pair<Double, List<Song>>> = coroutineScope {
        val top = seeds.maxOfOrNull { it.weight } ?: return@coroutineScope emptyList()
        seeds.map { seed ->
            async { runCatchingCancellable { raw(seed.song.id) }.getOrNull()?.let { seed.weight / top to it } }
        }.awaitAll().filterNotNull()
    }

    /**
     * Tracks to continue with after [videoId], excluding the seed itself: songs
     * like it, pulled toward what [taste] loves.
     */
    suspend fun radioFor(videoId: String, taste: Taste = Taste.None): Result<List<Song>> = runCatchingCancellable {
        // YouTube has no "more like this" for a podcast episode or a file on
        // the phone. An empty answer lets infinite playback simply stop there.
        if (!com.mero.domain.isYouTubeId(videoId)) return@runCatchingCancellable emptyList()
        val current = raw(videoId)
        val others = tasteRadios(taste.seeds.filter { it.song.id != videoId }.take(OTHER_SEEDS))
        blendRadios(listOf(CURRENT_WEIGHT to current) + others, taste, exclude = setOf(videoId))
    }

    /** A queue made only from taste: for "Your Mix", where nothing is playing yet. */
    suspend fun mixFor(taste: Taste): Result<List<Song>> = runCatchingCancellable {
        blendRadios(tasteRadios(taste.seeds.take(MIX_SEEDS)), taste)
    }

    private companion object {
        const val CACHE_MS = 20 * 60 * 1000L
        const val OTHER_SEEDS = 3
        const val MIX_SEEDS = 5

        /** The song playing now counts about as much as three favourites at full strength. */
        const val CURRENT_WEIGHT = 3.0
    }
}
