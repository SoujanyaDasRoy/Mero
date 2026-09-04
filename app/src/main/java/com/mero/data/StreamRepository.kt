package com.mero.data

import com.zionhuang.innertube.YouTube

fun interface PlayerApi {
    suspend fun formatsFor(videoId: String): List<AudioFormat>
}

class StreamRepository(private val api: PlayerApi) {

    /**
     * Never cache or persist the returned URL — it expires in roughly six
     * hours. Callers resolve it fresh at playback-open time. See CLAUDE.md
     * constraint 2 and playback/StreamResolver.kt.
     */
    suspend fun streamUrl(videoId: String, quality: Quality = Quality.HIGH): String {
        val formats = api.formatsFor(videoId)
        val chosen = selectAudioFormat(formats, quality)
            ?: error("No playable audio format for $videoId")
        return chosen.url
    }
}

/** Backed by innertube's anonymous [YouTube.player] — no cookie, no sign-in. */
object InnerTubePlayerApi : PlayerApi {
    override suspend fun formatsFor(videoId: String): List<AudioFormat> {
        val response = YouTube.player(videoId).getOrThrow()
        val streamingData = response.streamingData
            ?: error("No streamingData for $videoId (playability: ${response.playabilityStatus.status})")
        return streamingData.adaptiveFormats.mapNotNull { format ->
            val url = format.url ?: return@mapNotNull null
            AudioFormat(
                itag = format.itag,
                url = url,
                mimeType = format.mimeType,
                bitrate = format.bitrate,
            )
        }
    }
}
