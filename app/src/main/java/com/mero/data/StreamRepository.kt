package com.mero.data

import android.content.Context
import android.util.Log
import com.yausername.youtubedl_android.YoutubeDL
import com.yausername.youtubedl_android.YoutubeDLRequest
import com.zionhuang.innertube.YouTube
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withContext
import java.util.concurrent.ConcurrentHashMap
import java.util.concurrent.atomic.AtomicBoolean

private const val TAG = "MeroStream"

/** A playable stream plus the headers required to actually fetch it. */
data class ResolvedStream(
    val url: String,
    val headers: Map<String, String>,
    val bitrateKbps: Int,
    val codec: String,
) {
    /** e.g. "Opus · 160 kbps" — shown in Now Playing. */
    val label: String get() = "$codec · $bitrateKbps kbps"
}

fun interface PlayerApi {
    suspend fun formatsFor(videoId: String): List<AudioFormat>
}

class StreamRepository(private val api: PlayerApi) {

    private val _lastResolved = MutableStateFlow<ResolvedStream?>(null)

    /** What the currently playing track actually resolved to, for the UI. */
    val lastResolved: StateFlow<ResolvedStream?> = _lastResolved.asStateFlow()

    private class Cached(val stream: ResolvedStream, val atMs: Long)

    /**
     * In-memory only, and deliberately so: resolving costs a yt-dlp subprocess
     * (seconds), so replaying a track in the same session shouldn't pay it
     * twice. Nothing is written to disk, and entries expire well inside the
     * ~6h URL lifetime — CLAUDE.md constraint 2 still holds.
     */
    private val cache = ConcurrentHashMap<String, Cached>()

    /** Serialises yt-dlp subprocesses. See [resolve]. */
    private val extractionLock = Mutex()

    /**
     * Never cache or persist the result — the URL expires in roughly six hours.
     * Callers resolve fresh at playback-open time. See CLAUDE.md constraint 2
     * and playback/StreamResolver.kt.
     */
    suspend fun resolve(
        videoId: String,
        quality: Quality = Quality.HIGH,
        forUi: Boolean = true,
    ): ResolvedStream {
        val key = "$videoId:${quality.name}"
        cached(key)?.let {
            if (forUi) _lastResolved.value = it
            return it
        }

        // One extraction at a time. Each one spawns a Python subprocess; two
        // running together on a phone starve each other and the track that is
        // supposed to be playing sits in BUFFERING indefinitely.
        val stream = extractionLock.withLock {
            cached(key) ?: run {
                // Without this the loading thread can block forever on a hung
                // subprocess: no error, no retry, just a spinner. Spec §9 wants
                // failure visible and retryable, which needs it to fail first.
                val formats = withTimeout(EXTRACT_TIMEOUT_MS) { api.formatsFor(videoId) }
                val chosen = selectAudioFormat(formats, quality)
                    ?: error("No playable audio format for $videoId")
                ResolvedStream(
                    url = chosen.url,
                    headers = chosen.headers,
                    bitrateKbps = chosen.bitrate / 1000,
                    codec = chosen.codecLabel(),
                ).also { cache[key] = Cached(it, System.currentTimeMillis()) }
            }
        }
        if (forUi) _lastResolved.value = stream
        return stream
    }

    /**
     * Warms the next track's URL. Gives up the moment an extraction is already
     * running — the playing track must never queue behind the next one — and
     * never touches [lastResolved], which describes what is playing now.
     */
    suspend fun prefetch(videoId: String, quality: Quality = Quality.HIGH) {
        if (cached("$videoId:${quality.name}") != null) return
        if (extractionLock.isLocked) return
        runCatching { resolve(videoId, quality, forUi = false) }
            .onFailure { Log.w(TAG, "prefetch of $videoId skipped: ${it.message}") }
    }

    private fun cached(key: String): ResolvedStream? {
        val hit = cache[key] ?: return null
        if (System.currentTimeMillis() - hit.atMs < URL_TTL_MS) return hit.stream
        cache.remove(key)
        return null
    }

    private companion object {
        /** Comfortably inside YouTube's ~6h signed-URL lifetime. */
        const val URL_TTL_MS = 5L * 60 * 60 * 1000

        /**
         * Generous — a cold extraction on a slow phone is genuinely ~20s — but
         * finite. Past this, failing is better than a spinner that never ends.
         */
        const val EXTRACT_TIMEOUT_MS = 45_000L
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

    private val prepared = AtomicBoolean(false)

    /**
     * Unpacks the bundled Python runtime and pulls a current yt-dlp binary.
     *
     * Called once from MeroApplication on a background thread — deliberately
     * NOT from [formatsFor]. Extraction runs on ExoPlayer's loading thread via
     * runBlocking, so doing a network download here would stall the very first
     * play until it timed out. Keeping it off the playback path is the whole
     * fix: by the time anything is played, the runtime is already warm.
     *
     * Best effort. If the update fails (offline, GitHub unreachable) the
     * bundled binary is still there and extraction proceeds with it.
     */
    suspend fun prepare() = withContext(Dispatchers.IO) {
        if (!prepared.compareAndSet(false, true)) return@withContext
        runCatching { YoutubeDL.init(appContext) }
            .onFailure { Log.e(TAG, "yt-dlp init failed", it) }

        // Once a day, not once a launch. The update rewrites the same directory
        // getInfo reads from, so doing it on every start meant the first play
        // after opening the app could race a half-written binary and hang.
        val prefs = appContext.getSharedPreferences("ytdlp", Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_UPDATE, 0L)
        if (System.currentTimeMillis() - last < UPDATE_INTERVAL_MS) return@withContext
        runCatching { YoutubeDL.updateYoutubeDL(appContext) }
            .onSuccess { prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply() }
            .onFailure { Log.w(TAG, "yt-dlp update skipped: ${it.message}") }
    }

    private companion object {
        const val KEY_LAST_UPDATE = "last_update_ms"
        const val UPDATE_INTERVAL_MS = 24L * 60 * 60 * 1000
    }

    override suspend fun formatsFor(videoId: String): List<AudioFormat> = withContext(Dispatchers.IO) {
        // Idempotent and cheap once prepare() has run; still called as a guard
        // in case a play somehow beats startup.
        YoutubeDL.init(appContext)

        val started = System.currentTimeMillis()
        val request = YoutubeDLRequest("https://www.youtube.com/watch?v=$videoId")
        // yt-dlp writes advisories (e.g. "your version is older than 90 days")
        // to stderr, and the wrapper turns any stderr into an exception.
        request.addOption("--no-warnings")
        // Deliberately NOT pinning player_client. android_vr looks attractive
        // (its URLs skip the JavaScript signature challenge, which is most of
        // the extraction time) but it answers with storyboards and one muxed
        // 360p stream — no adaptive audio at all, so nothing is playable.
        // yt-dlp's own client order is the thing that keeps working.
        request.addOption("--extractor-args", "youtube:skip=translated_subs")
        // Nothing here needs the video half of the manifest, the rest of a
        // playlist, or a second guess at a format that already failed.
        request.addOption("--no-playlist")
        request.addOption("--no-check-formats")
        request.addOption("--socket-timeout", "10")
        request.addOption("--extractor-retries", "1")
        // runInterruptible, not a bare call: getInfo blocks on a subprocess,
        // and a plain blocking call ignores coroutine cancellation entirely —
        // which would make resolve()'s timeout decorative. Interrupting the
        // thread makes Process.waitFor throw, so the timeout is real.
        val info = runInterruptible { YoutubeDL.getInfo(request) }
        Log.i(TAG, "extracted $videoId in ${System.currentTimeMillis() - started}ms")
        val fallbackHeaders = info.httpHeaders.orEmpty()
        val audio = info.formats.orEmpty().mapNotNull { format ->
            val url = format.url ?: return@mapNotNull null
            // Leading digits, not a strict Int parse: some clients label the
            // same itag "251-drc" or similar, and requiring a pure number threw
            // every audio format away and left nothing playable.
            val itag = format.formatId.orEmpty().takeWhile(Char::isDigit).toIntOrNull() ?: 0
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
        Log.i(TAG, "audio formats for $videoId: " +
            audio.joinToString { "${it.itag}/${it.mimeType}/${it.bitrate / 1000}k" })
        if (audio.isEmpty()) {
            Log.w(
                TAG,
                "no audio-only formats for $videoId; saw " +
                    info.formats.orEmpty().joinToString { "${it.formatId}/${it.acodec}/${it.vcodec}" },
            )
        }
        audio
    }
}
