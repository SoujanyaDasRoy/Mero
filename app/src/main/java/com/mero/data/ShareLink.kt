package com.mero.data

import java.net.URI
import java.net.URLDecoder
import java.net.URLEncoder

/** A playlist as it travels in a link: its name and its songs' YouTube ids, in order. */
data class PlaylistLink(val name: String, val ids: List<String>, val coverCode: String? = null)

/** A playlist short link (https://meroapp.pages.dev/p/<code>): the songs are fetched from the site by [code]. */
data class ShortPlaylistLink(val code: String)

/**
 * The links Mero puts in what it shares. They point at Mero's own site (docs/share-site, published as
 * https://soujanyadasroy.github.io/), which Android has been told belongs to this app: on a phone with
 * Mero a tap opens Mero directly, on any other phone the page sends the person to YouTube Music. A
 * YouTube Music link cannot do this, because YouTube owns that address and Android opens it there
 * without ever offering Mero.
 */
object ShareLink {
    const val HOST = "soujanyadasroy.github.io"

    /** Mero's share site: short links, covers in previews. Stored playlists there expire after 30 days. */
    const val SHORT_HOST = "meroapp.pages.dev"

    /** A link that carries every song id (11 characters each) tops out here; the site's short links hold [MAX_SHORT_SONGS]. */
    const val MAX_PLAYLIST_SONGS = 150
    const val MAX_SHORT_SONGS = 500

    private val CODE = Regex("[A-Za-z0-9]{8}")

    /** The short address of a song: https://meroapp.pages.dev/s/<id>. Null for something with no YouTube id. */
    fun shortSong(videoId: String): String? = videoId.takeIf { VIDEO_ID.matches(it) }?.let { "https://$SHORT_HOST/s/$it" }

    fun shortPlaylist(code: String): String = "https://$SHORT_HOST/p/$code"

    /** Reads a link made by [shortPlaylist]; null for anything else. */
    fun parseShortPlaylist(url: String): ShortPlaylistLink? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host?.lowercase() != SHORT_HOST) return null
        val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
        return segments.takeIf { it.size == 2 && it[0] == "p" && CODE.matches(it[1]) }?.let { ShortPlaylistLink(it[1]) }
    }

    private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
    private const val MAX_NAME = 80

    /** The address to share for a YouTube song, or null for something with no YouTube id (a phone file, a podcast). */
    fun song(videoId: String): String? = videoId.takeIf { VIDEO_ID.matches(it) }?.let { "https://$HOST/song/?v=$it" }

    /**
     * The address for a playlist: `/playlist/?n=<name>&s=<ids joined>`. Null when there is nothing to
     * share or too much for a link (see [MAX_PLAYLIST_SONGS]). Songs that are not YouTube tracks are left out.
     */
    fun playlist(name: String, ids: List<String>): String? {
        val songs = ids.filter { VIDEO_ID.matches(it) }.distinct()
        if (songs.isEmpty() || songs.size > MAX_PLAYLIST_SONGS) return null
        val n = URLEncoder.encode(name.trim().take(MAX_NAME), "UTF-8")
        return "https://$HOST/playlist/?n=$n&s=" + songs.joinToString("")
    }

    /** Reads a link made by [playlist]; null for anything else, including a malformed song list. */
    fun parsePlaylist(url: String): PlaylistLink? {
        val uri = runCatching { URI(url.trim()) }.getOrNull() ?: return null
        if (uri.scheme != "https" || uri.host?.lowercase() != HOST) return null
        if (uri.path?.trimEnd('/') != "/playlist") return null
        val query = uri.rawQuery.orEmpty().split('&')
            .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
            .toMap()
        val packed = query["s"].orEmpty()
        if (packed.isEmpty() || packed.length % 11 != 0) return null
        val ids = packed.chunked(11)
        if (ids.size > MAX_PLAYLIST_SONGS || ids.any { !VIDEO_ID.matches(it) }) return null
        val name = query["n"]?.let { runCatching { URLDecoder.decode(it, "UTF-8") }.getOrNull() }
            ?.trim()?.take(MAX_NAME)?.ifEmpty { null } ?: "Shared playlist"
        return PlaylistLink(name, ids.distinct())
    }
}
