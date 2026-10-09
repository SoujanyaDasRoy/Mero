package com.mero.data

/** What a pasted YouTube address points at: a video, a playlist, or a video inside one. */
data class YouTubeLink(val videoId: String?, val playlistId: String?)

private val VIDEO_ID = Regex("[A-Za-z0-9_-]{11}")
private val HOSTS = setOf(
    "youtube.com", "www.youtube.com", "m.youtube.com", "music.youtube.com",
    "youtu.be", "www.youtu.be", "youtube-nocookie.com", "www.youtube-nocookie.com",
)
private val ID_IN_PATH = setOf("shorts", "embed", "live", "v")

/**
 * Reads whatever people paste into search: watch, share, Music, Shorts, embed
 * and playlist links, with or without `https://`. Null when it is not a
 * YouTube address, so ordinary text still searches.
 */
fun parseYouTubeLink(input: String): YouTubeLink? {
    val text = input.trim()
    if (text.isEmpty() || text.any { it.isWhitespace() }) return null
    val uri = runCatching { java.net.URI(if ("://" in text) text else "https://$text") }.getOrNull() ?: return null
    val host = uri.host?.lowercase() ?: return null
    if (host !in HOSTS) return null
    val query = uri.rawQuery.orEmpty().split('&')
        .mapNotNull { part -> part.split('=', limit = 2).takeIf { it.size == 2 }?.let { it[0] to it[1] } }
        .toMap()
    val segments = uri.path.orEmpty().split('/').filter { it.isNotEmpty() }
    val fromPath = when {
        host.endsWith("youtu.be") -> segments.firstOrNull()
        segments.size >= 2 && segments[0] in ID_IN_PATH -> segments[1]
        else -> null
    }
    val video = (query["v"] ?: fromPath)?.takeIf { VIDEO_ID.matches(it) }
    val list = query["list"]?.takeIf { it.isNotBlank() }
    return if (video == null && list == null) null else YouTubeLink(video, list)
}
