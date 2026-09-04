package com.mero.playback

import android.net.Uri
import androidx.core.net.toUri
import androidx.media3.common.MediaItem
import androidx.media3.common.MediaMetadata
import com.mero.domain.Song

private const val SCHEME = "mero"

/**
 * The stream URL is never in this URI — only the videoId. StreamResolver swaps
 * in a live CDN address at open time. See CLAUDE.md constraint 2.
 */
fun mediaItemFor(song: Song): MediaItem =
    MediaItem.Builder()
        .setMediaId(song.id)
        .setUri("$SCHEME://${song.id}".toUri())
        .setMediaMetadata(
            MediaMetadata.Builder()
                .setTitle(song.title)
                .setArtist(song.artist)
                .setArtworkUri(song.thumbnailUrl?.toUri())
                .build(),
        )
        .build()

fun videoIdFrom(uri: Uri): String {
    require(uri.scheme == SCHEME) { "Not a mero uri: $uri" }
    return requireNotNull(uri.host) { "Missing video id in $uri" }
}
