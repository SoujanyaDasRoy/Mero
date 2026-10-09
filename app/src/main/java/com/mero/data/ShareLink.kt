package com.mero.data

/**
 * The link Mero puts in a shared song. It points at Mero's own site (docs/share-site, published as
 * https://soujanyadasroy.github.io/), which Android has been told belongs to this app: on a phone
 * with Mero a tap opens Mero directly, on any other phone the page sends the person to the same
 * song in YouTube Music. A YouTube Music link cannot do this, because YouTube owns that address and
 * Android opens it there without ever offering Mero.
 */
object ShareLink {
    const val HOST = "soujanyadasroy.github.io"
    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")

    /** The address to share for a YouTube song, or null for something with no YouTube id (a phone file, a podcast). */
    fun song(videoId: String): String? = videoId.takeIf { VIDEO_ID.matches(it) }?.let { "https://$HOST/song/?v=$it" }
}
