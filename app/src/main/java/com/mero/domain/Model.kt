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
)

data class Playlist(
    val id: String,
    val name: String,
    val trackCount: Int,
    val thumbnailUrl: String? = null,
    val downloaded: Boolean = false,
)

data class LyricLine(val atSec: Int, val text: String)

enum class SearchResultType { Song, Album, Artist, Playlist }

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
