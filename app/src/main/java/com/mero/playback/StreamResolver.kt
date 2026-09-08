package com.mero.playback

import androidx.core.net.toUri
import androidx.media3.datasource.DataSpec
import androidx.media3.datasource.ResolvingDataSource
import com.mero.data.StreamRepository
import com.mero.data.CodecPreference
import kotlinx.coroutines.runBlocking

import com.mero.data.isUrlExpired

/**
 * Swaps a `mero://<videoId>` URI for a live CDN URL at the moment ExoPlayer
 * actually opens the stream — never earlier. That's what makes the six-hour URL
 * expiry self-heal: a dead URL triggers a retry, which re-resolves here and
 * gets a fresh one. See docs/architecture.md, "Why ResolvingDataSource".
 *
 * The extractor's own request headers are attached to the DataSpec, on the
 * principle that a media request should look like the extraction that produced
 * it.
 *
 * What is deliberately *not* done here is rewriting the URL's `range=` query
 * parameter. That was added to work around playback stalling partway through a
 * track, but the stall was a stale yt-dlp handing out URLs YouTube caps at
 * about a megabyte — fixed at the source now. Meanwhile the rewrite applied the
 * seek offset twice, once as `range=` and again as ExoPlayer's own `Range`
 * header, so the server sliced the file and ExoPlayer then asked for a position
 * past the end of that slice: HTTP 416, and every seek near the end of a track
 * failed.
 *
 * `runBlocking` is intentional, not a bug to "fix" in review: this callback
 * runs on ExoPlayer's loading thread, which is designed to block, and the
 * interface itself is synchronous.
 */
class StreamResolver(
    private val repo: StreamRepository,
    private val codec: CodecPreference? = null,
) : ResolvingDataSource.Resolver {

    override fun resolveDataSpec(dataSpec: DataSpec): DataSpec {
        if (dataSpec.uri.scheme != "mero") return dataSpec
        val videoId = videoIdFrom(dataSpec.uri)
        var stream = runBlocking { repo.resolve(videoId, codec = codec ?: repo.codecPreference) }
        if (isUrlExpired(stream.url)) {
            repo.invalidate(videoId)
            stream = runBlocking { repo.resolve(videoId, codec = codec ?: repo.codecPreference) }
        }
        // Only the URI is swapped. The byte offset stays ExoPlayer's business:
        // it already sends a `Range` header, and googlevideo honours it.
        return dataSpec
            .withUri(stream.url.toUri())
            .withAdditionalHeaders(stream.headers)
    }
}
