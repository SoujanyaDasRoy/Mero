package com.mero.data

import android.content.Context
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.atomic.AtomicBoolean

/** A playable stream plus the headers required to actually fetch it. */
data class ResolvedStream(
    val url: String,
    val headers: Map<String, String>,
)

fun interface PlayerApi {
    suspend fun formatsFor(videoId: String): List<AudioFormat>
}

class StreamRepository(private val api: PlayerApi) {

    /**
     * Never cache or persist the result — the URL expires in roughly six hours.
     * Callers resolve fresh at playback-open time. See CLAUDE.md constraint 2
     * and playback/StreamResolver.kt.
     */
    suspend fun resolve(videoId: String, quality: Quality = Quality.HIGH): ResolvedStream {
        val formats = api.formatsFor(videoId)
        val chosen = selectAudioFormat(formats, quality)
            ?: error("No playable audio format for $videoId")
        return ResolvedStream(chosen.url, chosen.headers)
    }
}

/**
 * Backed by innertube's anonymous [YouTube.player] — no cookie, no sign-in.
 *
 * Currently unused (see [YtDlpPlayerApi]): YouTube rejects this request with
 * 400 "Precondition check failed" — the vendored innertube module has no PO
 * token support, and neither does upstream (z-huang/InnerTune#1748, open since
 * Dec 2024, 231 comments, unresolved). Kept in place: a `resync-innertube` pull
 * is a one-line swap back in AppContainer if upstream ever fixes it, and search
 * still uses this same object (search was never broken, only /player).
 */
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

/**
 * Escape hatch: resolves stream formats via an embedded yt-dlp instead of
 * innertube's /player. yt-dlp actively maintains PO token generation, which is
 * exactly what's missing upstream — see [InnerTubePlayerApi]'s note.
 *
 * `format_id` on YouTube's adaptive formats is the itag as a string (e.g.
 * "251"), so [selectAudioFormat]'s itag-based quality selection works unchanged
 * against yt-dlp's output.
 *
 * Per-format `http_headers` are carried through deliberately: YouTube's CDN
 * answers 403 if the media request doesn't present the same User-Agent the
 * extraction used.
 */
class YtDlpPlayerApi(private val appContext: Context) : PlayerApi {

    private val updateAttempted = AtomicBoolean(false)

    override suspend fun formatsFor(videoId: String): List<AudioFormat> = withContext(Dispatchers.IO) {
        YoutubeDL.init(appContext) // idempotent — safe before every request

        // The yt-dlp binary shipped inside the library goes stale quickly, and
        // YouTube breaks old versions fast. Updating at runtime is the whole
        // reason this escape hatch was chosen (PRD §9): extraction breakage is
        // fixable without shipping a new APK. Best-effort — if the update fails
        // (offline, etc.) fall through and try the bundled version anyway.
        if (updateAttempted.compareAndSet(false, true)) {
            runCatching { YoutubeDL.updateYoutubeDL(appContext) }
        }

        val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId")
        // yt-dlp writes advisories (e.g. "your version is older than 90 days")
        // to stderr, and the wrapper turns any stderr into an exception.
        request.addOption("--no-warnings")
        val info = YoutubeDL.getInfo(request)
        val fallbackHeaders = info.httpHeaders.orEmpty()
        info.formats.orEmpty().mapNotNull { format ->
            val url = format.url ?: return@mapNotNull null
            val itag = format.formatId?.toIntOrNull() ?: return@mapNotNull null
            val isAudioOnly = format.vcodec == "none" && !format.acodec.isNullOrBlank() &&
                format.acodec != "none"
            if (!isAudioOnly) return@mapNotNull null
            AudioFormat(
                itag = itag,
                url = url,
                mimeType = "audio/${format.ext ?: "webm"}",
                bitrate = format.abr * 1000,
                headers = format.httpHeaders ?: fallbackHeaders,
            )
        }
    }
}
