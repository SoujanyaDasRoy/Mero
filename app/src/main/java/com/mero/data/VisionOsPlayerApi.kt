package com.mero.data

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonArray
import kotlinx.serialization.json.jsonObject
import kotlinx.serialization.json.jsonPrimitive
import java.io.IOException
import java.net.HttpURLConnection
import java.net.URL

/**
 * Resolves streams straight from InnerTube's `/player`, anonymously, in about
 * half a second — where the yt-dlp path takes ten seconds.
 *
 * ## Why this client
 *
 * The choice of InnerTube client is not cosmetic: it decides whether YouTube
 * will serve the whole file. Measured against one 3.34 MB track, requesting
 * byte ranges from the URL each client minted:
 *
 * | client      | `bytes=0-1000000` | `bytes=2000000-2100000` | tail |
 * |-------------|-------------------|-------------------------|------|
 * | `IOS`       | 206               | **403**                 | 403  |
 * | `ANDROID`   | 206               | **403**                 | 403  |
 * | `VISIONOS`  | 206               | 206                     | 206  |
 *
 * A capped URL does not fail at resolve time. It plays for about a minute and
 * then dies — which is exactly the "~51s streaming pause" this project chased
 * for two releases, and exactly what an earlier "instant playback" change
 * reintroduced by treating a fast `/player` response as a working one.
 *
 * `visitorData` is not optional here either. Without a fresh one this client
 * answers `LOGIN_REQUIRED` for most videos; with one, it answered OK for every
 * video tried. It is an anonymous per-session id, not a credential — no
 * account is involved, so CLAUDE.md constraint 1 still holds.
 *
 * ## Why it is allowed to be wrong
 *
 * YouTube can change which clients it throttles whenever it likes, and the
 * failure is silent. So this never asserts that its own output is good: it
 * [verifies] the URL is fully fetchable before returning, and anything short
 * of that throws, which drops the caller back to yt-dlp.
 */
class VisionOsPlayerApi : PlayerApi {

    @Volatile
    private var visitorData: String? = null

    override suspend fun formatsFor(videoId: String): List<AudioFormat> =
        withContext(Dispatchers.IO) {
            val formats = runCatching { resolve(videoId, visitorData ?: freshVisitorData()) }
                .getOrElse {
                    // A stale visitor id looks exactly like a real refusal, so
                    // spend one retry on a new one before giving up to yt-dlp.
                    visitorData = null
                    resolve(videoId, freshVisitorData())
                }
            formats
        }

    private fun resolve(videoId: String, visitor: String): List<AudioFormat> {
        val body = """
            {"context":{"client":{$CLIENT,"hl":"en","gl":"US","visitorData":"$visitor"}},
             "videoId":"$videoId","contentCheckOk":true,"racyCheckOk":true}
        """.trimIndent()
        val response = post("player", body, visitor)

        val status = response["playabilityStatus"]?.jsonObject
            ?.get("status")?.jsonPrimitive?.content
        if (status != "OK") error("playabilityStatus=$status for $videoId")

        val formats = response["streamingData"]?.jsonObject
            ?.get("adaptiveFormats")?.jsonArray
            ?.mapNotNull { element ->
                val format = element.jsonObject
                val mime = format["mimeType"]?.jsonPrimitive?.content ?: return@mapNotNull null
                if (!mime.startsWith("audio/")) return@mapNotNull null
                // No `url` means the format is behind a signature cipher, which
                // needs the JS player to unscramble. Not this path's job.
                val url = format["url"]?.jsonPrimitive?.content ?: return@mapNotNull null
                AudioFormat(
                    itag = format["itag"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    url = url,
                    mimeType = mime,
                    bitrate = format["bitrate"]?.jsonPrimitive?.content?.toIntOrNull() ?: 0,
                    headers = mapOf("User-Agent" to USER_AGENT),
                ) to (format["contentLength"]?.jsonPrimitive?.content?.toLongOrNull() ?: 0L)
            }
            .orEmpty()

        if (formats.isEmpty()) error("no direct audio urls for $videoId")

        // Verify the one that will actually be played, not merely the first.
        val best = formats.maxByOrNull { it.first.bitrate }!!
        verify(best.first.url, best.second)
        return formats.map { it.first }
    }

    /**
     * Confirms the CDN will serve the end of the file, not just the opening.
     *
     * Two kilobytes and one round trip — cheap next to the ten seconds this
     * whole path exists to avoid, and the only thing standing between a
     * throttled URL and a track that stops halfway through.
     */
    private fun verify(url: String, contentLength: Long) {
        if (contentLength <= 0L) error("no contentLength to verify against")
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            setRequestProperty("User-Agent", USER_AGENT)
            setRequestProperty("Range", "bytes=${contentLength - 2000}-${contentLength - 1}")
            connectTimeout = VERIFY_TIMEOUT_MS
            readTimeout = VERIFY_TIMEOUT_MS
        }
        try {
            if (conn.responseCode != 206) {
                error("throttled url: tail request returned ${conn.responseCode}")
            }
        } finally {
            conn.disconnect()
        }
    }

    /** Anonymous per-session id. No account, no cookie. */
    private fun freshVisitorData(): String {
        val body = """{"context":{"client":{$CLIENT,"hl":"en","gl":"US"}}}"""
        val id = post("visitor_id", body, null)["responseContext"]?.jsonObject
            ?.get("visitorData")?.jsonPrimitive?.content
            ?: error("no visitorData in visitor_id response")
        visitorData = id
        Log.i(TAG, "obtained a fresh visitorData")
        return id
    }

    private fun post(path: String, body: String, visitor: String?): JsonObject {
        val conn = (URL("$BASE/$path?prettyPrint=false").openConnection() as HttpURLConnection)
        conn.requestMethod = "POST"
        conn.doOutput = true
        conn.connectTimeout = TIMEOUT_MS
        conn.readTimeout = TIMEOUT_MS
        conn.setRequestProperty("Content-Type", "application/json")
        conn.setRequestProperty("User-Agent", USER_AGENT)
        conn.setRequestProperty("X-Youtube-Client-Name", CLIENT_NAME_ID)
        conn.setRequestProperty("X-Youtube-Client-Version", CLIENT_VERSION)
        visitor?.let { conn.setRequestProperty("X-Goog-Visitor-Id", it) }
        conn.outputStream.use { it.write(body.toByteArray()) }
        if (conn.responseCode != 200) {
            throw IOException("innertube $path returned ${conn.responseCode}")
        }
        val text = conn.inputStream.bufferedReader().use { it.readText() }
        return json.parseToJsonElement(text).jsonObject
    }

    private companion object {
        const val BASE = "https://www.youtube.com/youtubei/v1"
        const val CLIENT_VERSION = "1.02"
        const val CLIENT_NAME_ID = "101"
        const val USER_AGENT =
            "Mozilla/5.0 (Macintosh; Intel Mac OS X 15_7_3) AppleWebKit/605.1.15 " +
                "(KHTML, like Gecko) Version/26.0 Safari/605.1.15"
        val CLIENT = """
            "clientName":"VISIONOS","clientVersion":"$CLIENT_VERSION","deviceMake":"Apple",
            "deviceModel":"RealityDevice17,1","osName":"visionOS","osVersion":"26.5.23O471",
            "userAgent":"$USER_AGENT"
        """.trimIndent().replace("\n", "")
        const val TIMEOUT_MS = 12_000
        const val VERIFY_TIMEOUT_MS = 8_000
        val json = Json { ignoreUnknownKeys = true }
    }
}

private const val TAG = "MeroVisionOs"
