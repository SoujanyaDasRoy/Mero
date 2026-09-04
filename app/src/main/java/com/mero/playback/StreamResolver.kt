package com.mero.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.mero.data.StreamRepository
import kotlinx.coroutines.runBlocking

/**
 * Swaps a `mero://<videoId>` URI for a live CDN URL at the moment ExoPlayer
 * actually opens the stream — never earlier. That's what makes the six-hour
 * URL expiry self-heal: a dead URL triggers a retry, which re-resolves here
 * and gets a fresh one. See docs/architecture.md, "Why ResolvingDataSource".
 *
 * `runBlocking` is intentional, not a bug to "fix" in review: this callback
 * runs on ExoPlayer's loading thread, which is designed to block, and the
 * interface itself is synchronous.
 */
class StreamResolver(
    private val repo: StreamRepository,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        if (dataSpec.uri.scheme != "mero") return dataSpec
        val videoId = videoIdFrom(dataSpec.uri)
        val url = runBlocking { repo.streamUrl(videoId) }
        return dataSpec.withUri(url.toUri())
    }
}
