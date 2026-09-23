package com.mero.domain

/**
 * Mero's own types. These never expose innertube types — see docs/architecture.md,
 * "Why domain models never expose innertube types".
 */
data class Song(
    val id: String,
    val title: String,
    val artist: String,
    val album: String = "",
    val durationSec: Int = 0,
    val thumbnailUrl: String? = null,
    val downloaded: Boolean = false,
    /**
     * Where the audio actually is, for anything that is not a YouTube track: a
     * `content://` file picked from the phone, or a podcast episode's
     * `https://` enclosure. Null means the id is a YouTube video id, resolved
     * to a fresh stream each time it is opened.
     *
     * Storing these is fine where storing a YouTube URL is not (CLAUDE.md
     * constraint 2): a podcast enclosure and a persisted content URI do not
     * expire after six hours.
     */
    val sourceUri: String? = null,
) {
    /** A YouTube track, which the extraction, radio and lyrics paths assume. */
    val isYouTube: Boolean get() = sourceUri == null

    /** Something on this phone, which needs neither downloading nor streaming. */
    val isOnDevice: Boolean get() = sourceUri?.startsWith("content:") == true
}

/**
 * True when [id] is a YouTube video id rather than one of Mero's own ids for
 * local files (`local:…`) and podcast episodes (`pod:…`).
 *
 * The id-only paths — prefetch, cache warming, radio — have nothing else to go
 * on, and handing them a podcast episode would send it to YouTube's extractor
 * to fail slowly. YouTube ids never contain a colon.
 */
fun isYouTubeId(id: String): Boolean = ':' !in id

data class Playlist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val thumbnailUrl: String? = null,
    val downloaded: Boolean = false,
)

/** One line of lyrics and when it is sung, in milliseconds from the start. */
data class LyricLine(val atMs: Long, val text: String)

enum class SearchResultType { Song, Album, Artist, Playlist, Podcast }

data class SearchItem(
    val id: String,
    val title: String,
    val subtitle: String,
    val thumbnailUrl: String?,
    val type: SearchResultType,
    val song: Song? = null,
    val browseId: String? = null,
)

data class ArtistAlbum(
    val browseId: String,
    val title: String,
    val year: Int?,
    val thumbnailUrl: String?,
)

data class ArtistPageData(
    val id: String,
    val name: String,
    val thumbnailUrl: String?,
    val albums: List<ArtistAlbum>,
    /** The artist's own playlists — "Essentials", "This Is …", radio mixes. */
    val playlists: List<ArtistAlbum>,
    val songs: List<Song>,
)

enum class RepeatMode { Off, All, One }

fun Int.asClock(): String {
    val hours = this / 3600
    val minutes = (this % 3600) / 60
    val seconds = this % 60
    return if (hours > 0) {
        "%d:%02d:%02d".format(hours, minutes, seconds)
    } else {
        "%d:%02d".format(minutes, seconds)
    }
}
