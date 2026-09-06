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
 * The extractor's own request headers are attached to the DataSpec: YouTube's
 * CDN answers 403 to a media request whose User-Agent doesn't match the one
 * extraction was performed with.
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
        val resolvedUrl = appendOrUpdateRange(stream.url, dataSpec.position, dataSpec.length)
        return dataSpec
            .withUri(resolvedUrl.toUri())
            .withAdditionalHeaders(stream.headers)
    }
}

internal fun appendOrUpdateRange(url: String, position: Long, length: Long): String {
    if ((position <= 0) && (length <= 0)) return url
    val rangeVal = if (length > 0) {
        "$position-${position + length - 1}"
    } else {
        "$position-"
    }
    return if (url.contains("range=")) {
        url.replace(Regex("range=[^&]+"), "range=$rangeVal")
    } else {
        val separator = if (url.contains("?")) "&" else "?"
        "$url${separator}range=$rangeVal"
    }
}
