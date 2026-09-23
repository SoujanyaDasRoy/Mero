package com.mero.playback

import android.net.Uri
import android.os.Bundle
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.mero.data.atArtworkSize
import com.mero.domain.Song

private const val SCHEME = "mero"

/**
 * The stream URL is never in this URI — only the videoId. StreamResolver swaps
 * in a live CDN address at open time. See CLAUDE.md constraint 2.
 */
fun mediaItemFor(song: Song): MediaItem =
    MediaItem.Builder()
        .setMediaId(song.id)
        .setUri((song.sourceUri ?: "$SCHEME://${song.id}").toUri())
        // Keyed by id whatever the address. A YouTube track's host already was
        // its id; a podcast episode's host is just the server's name.
        .setCustomCacheKey(song.id)
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setAlbumTitle(song.album.takeIf { it.isNotBlank() })
                // The notification and lock screen draw this far larger than a
                // list row does; 220px there is visibly soft.
                .setArtworkUri(song.thumbnailUrl?.atArtworkSize(600)?.toUri())
                // Carried in the metadata as well as the URI, because a
                // MediaController does not reliably receive the URI and the UI
                // rebuilds songs from what it does receive. Losing it would
                // turn a podcast episode back into a "YouTube id" on the next
                // queue save.
                .setExtras(Bundle().apply { song.sourceUri?.let { putString(EXTRA_SOURCE, it) } })
                .build(),
        )
        .build()

/** A song rebuilt from what the player hands back, when the UI has no record of it. */
fun songFrom(item: MediaItem): Song {
    val meta = item.mediaMetadata
    return Song(
        id = item.mediaId,
        title = meta.title?.toString() ?: item.mediaId,
        artist = meta.artist?.toString().orEmpty(),
        album = meta.albumTitle?.toString().orEmpty(),
        thumbnailUrl = meta.artworkUri?.toString(),
        sourceUri = meta.extras?.getString(EXTRA_SOURCE),
    )
}

private const val EXTRA_SOURCE = "mero.sourceUri"

fun videoIdFrom(uri: Uri): String {
    require(uri.scheme == SCHEME) { "Not a mero uri: $uri" }
    return requireNotNull(uri.host) { "Missing video id in $uri" }
}
