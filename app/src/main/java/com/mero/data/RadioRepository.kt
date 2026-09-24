package com.mero.data

import com.mero.domain.Song
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.WatchEndpoint

/**
 * "What plays after this" — YouTube Music's own radio for a track, fetched
 * anonymously through the watch-next endpoint.
 *
 * This is the honest substitute for personalised recommendations. Mero never
 * signs in (CLAUDE.md constraint 1), so there is no account history to build a
 * profile from; what there is instead is YouTube's per-track similarity, which
 * needs no identity at all and is most of what a listener actually wants —
 * "more like this one", not "more like me".
 */
class RadioRepository {

    /**
     * Tracks to continue with after [videoId], excluding the seed itself:
     * YouTube's similar songs, reordered for [taste] (see rankForTaste).
     */
    suspend fun radioFor(videoId: String, taste: Taste = Taste.None): Result<List<Song>> = runCatchingCancellable {
        // YouTube has no "more like this" for a podcast episode or a file on
        // the phone. An empty answer lets infinite playback simply stop there.
        if (!com.mero.domain.isYouTubeId(videoId)) return@runCatchingCancellable emptyList()
        YouTube.next(WatchEndpoint(videoId = videoId)).getOrThrow()
            .items
            .filter { it.id.isNotBlank() && it.id != videoId }
            .map { it.toDomain() }
            // YouTube's list continues into its automix, which repeats songs.
            .distinctBy { it.id }
            .let { rankForTaste(it, taste) }
    }
}
