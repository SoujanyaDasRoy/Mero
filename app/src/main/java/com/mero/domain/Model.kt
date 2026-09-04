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

enum class RepeatMode { Off, All, One }

fun Int.asClock(): String = "%d:%02d".format(this / 60, this % 60)
