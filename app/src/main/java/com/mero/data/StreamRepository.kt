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
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Deferred
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.async
import kotlinx.coroutines.sync.Semaphore
import kotlinx.coroutines.sync.withPermit
import kotlinx.coroutines.runInterruptible
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
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

    @Volatile
    var codecPreference: CodecPreference = CodecPreference.OPUS
        private set

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

    /** Extractions currently running, so concurrent callers share one. */
    private val inFlight = ConcurrentHashMap<String, Deferred<ResolvedStream>>()

    /** One extraction at a time. See [inFlight] for why. */
    private val extractionSlot = Semaphore(1)

    /** Outlives any single caller, so one giving up doesn't cancel the rest. */
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    /**
     * Never cache or persist the result — the URL expires in roughly six hours.
     * Callers resolve fresh at playback-open time. See CLAUDE.md constraint 2
     * and playback/StreamResolver.kt.
     */
    suspend fun resolve(
        videoId: String,
        quality: Quality = Quality.HIGH,
        codec: CodecPreference = codecPreference,
        forUi: Boolean = true,
    ): ResolvedStream {
        val key = "$videoId:${quality.name}:${codec.name}"
        cached(key)?.let {
            if (forUi) _lastResolved.value = it
            return it
        }

        val stream = inFlight(key, videoId, quality, codec).await()
        if (forUi) _lastResolved.value = stream
        return stream
    }

    /**
     * One extraction per key, however many callers ask for it.
     *
     * Without this, N concurrent requests for the same track each miss the
     * cache (which is only written on completion) and each spawn their own
     * yt-dlp subprocess. Observed on device: the same videoId extracted three
     * times concurrently, 39s / 41s / 43s, where one alone takes about eight.
     *
     * The async runs in the repository's own scope, not the caller's, so a
     * caller giving up does not cancel the extraction everyone else is
     * awaiting.
     */
    private fun inFlight(
        key: String,
        videoId: String,
        quality: Quality,
        codec: CodecPreference,
    ): Deferred<ResolvedStream> = inFlight.computeIfAbsent(key) {
        scope.async {
            try {
                // yt-dlp is a Python subprocess, not a socket. Running several
                // at once does not overlap latency, it multiplies it: the
                // speculative prefetches turned a ~8s extraction into ~42s and
                // starved the track the user had actually tapped.
                extractionSlot.withPermit {
                    cached(key) ?: run {
                        val formats = withTimeout(EXTRACT_TIMEOUT_MS) { api.formatsFor(videoId) }
                        val chosen = selectAudioFormat(formats, quality, codec)
                            ?: error("No playable audio format for $videoId")
                        ResolvedStream(
                            url = chosen.url,
                            headers = chosen.headers,
                            bitrateKbps = chosen.bitrate / 1000,
                            codec = chosen.codecLabel(),
                        ).also { cache[key] = Cached(it, System.currentTimeMillis()) }
                    }
                }
            } finally {
                inFlight.remove(key)
            }
        }
    }

    /**
     * Warms a track's URL so a later tap plays from memory.
     *
     * Speculative, and treated as such: if an extraction is already running it
     * gives up rather than queueing. A guess about what might be played next
     * must never delay the track someone actually pressed.
     */
    suspend fun prefetch(videoId: String, quality: Quality = Quality.HIGH) {
        val key = "$videoId:${quality.name}:${codecPreference.name}"
        if (cached(key) != null) return
        if (extractionSlot.availablePermits == 0) return
        runCatching {
            resolve(videoId, quality, codec = codecPreference, forUi = false)
        }.onFailure { Log.w(TAG, "prefetch of $videoId skipped: ${it.message}") }
    }

    fun setCodecPreference(value: CodecPreference) {
        codecPreference = value
    }

    /** Drops a possibly expired CDN URL so the next play resolves a fresh one. */
    fun invalidate(videoId: String) {
        cache.keys.removeIf { it.startsWith("$videoId:") }
    }

    private fun cached(key: String): ResolvedStream? {
        val hit = cache[key] ?: return null
        if (System.currentTimeMillis() - hit.atMs >= URL_TTL_MS) {
            cache.remove(key)
            return null
        }
        if (isUrlExpired(hit.stream.url)) {
            Log.i(TAG, "Cached stream URL for $key is expired via expire query parameter")
            cache.remove(key)
            return null
        }
        return hit.stream
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
 * Checks whether a YouTube CDN signed URL is expired or close to expiring
 * based on its `expire` query parameter timestamp (unix epoch seconds).
 */
fun isUrlExpired(
    url: String,
    thresholdSec: Long = 120,
    nowSec: Long = System.currentTimeMillis() / 1000,
): Boolean {
    val match = Regex("[?&]expire=(\\d+)").find(url) ?: return false
    val expireSec = match.groupValues[1].toLongOrNull() ?: return false
    return nowSec >= (expireSec - thresholdSec)
}

/*
 * There was an InnerTubePlayerApi here, used as a "fast path" ahead of yt-dlp
 * via a FallbackPlayerApi wrapper. Both are deleted.
 *
 * The anonymous /player endpoint answered HTTP 400 for every videoId tried —
 * a 100% failure rate, not an occasional miss — so the wrapper's only effect
 * was a guaranteed-wasted round trip before every single extraction. YouTube
 * requires a PO token there now, which is exactly the thing yt-dlp maintains
 * and the vendored innertube does not.
 *
 * If upstream ever ships PO token support, this is worth trying again — behind
 * a check that it actually returns playable formats, not merely that the call
 * did not throw.
 */

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

        val prefs = appContext.getSharedPreferences("ytdlp", Context.MODE_PRIVATE)
        val last = prefs.getLong(KEY_LAST_UPDATE, 0L)
        if (System.currentTimeMillis() - last < UPDATE_INTERVAL_MS) return@withContext

        // Not housekeeping — the difference between playing and not.
        //
        // A stale yt-dlp does not fail loudly. It extracts fine and returns
        // URLs that look normal, but YouTube serves those a hard cap of about
        // one megabyte: verified against a live URL, `Range: bytes=0-1000000`
        // returns 206 and every range ending past that returns 403, at any
        // start offset. So a track begins and then dies, or 403s outright.
        // Only the current yt-dlp negotiates a client that gets uncapped URLs.
        //
        // This once wrote the timestamp on first launch *without* updating, to
        // keep a 15MB download off the first play. That optimisation traded a
        // working app for a fast one: a fresh install ran the stale bundled
        // binary for at least a day.
        //
        // Held under [binaryLock], so an extraction can never read a
        // half-written binary — and a play that arrives mid-update waits for
        // it rather than extracting with the one being replaced.
        binaryLock.withLock {
            runCatching { YoutubeDL.updateYoutubeDL(appContext) }
                .onSuccess {
                    Log.i(TAG, "yt-dlp updated")
                    prefs.edit().putLong(KEY_LAST_UPDATE, System.currentTimeMillis()).apply()
                }
                .onFailure { Log.w(TAG, "yt-dlp update failed, using bundled binary: ${it.message}") }
        }
    }

    /** Serialises binary replacement against the extractions that read it. */
    private val binaryLock = Mutex()

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
        request.addOption("--quiet")
        // Skip HLS, DASH and translated subs manifests to avoid extra HTTP round-trips
        // to YouTube servers during stream extraction.
        request.addOption("--extractor-args", "youtube:skip=hls,dash,translated_subs")
        // Nothing here needs the video half of the manifest, the rest of a
        // playlist, or a second guess at a format that already failed.
        request.addOption("--no-playlist")
        request.addOption("--no-check-formats")
        // Restrict format extraction to audio formats only. On long streams/videos (>3h),
        // fetching all video formats generates massive manifests that cause timeouts.
        request.addOption("-f", "ba/ba*")
        request.addOption("--socket-timeout", "10")
        request.addOption("--extractor-retries", "1")
        // runInterruptible, not a bare call: getInfo blocks on a subprocess,
        // and a plain blocking call ignores coroutine cancellation entirely —
        // which would make resolve()'s timeout decorative. Interrupting the
        // thread makes Process.waitFor throw, so the timeout is real.
        val info = binaryLock.withLock { runInterruptible { YoutubeDL.getInfo(request) } }
        Log.i(TAG, "extracted $videoId in ${System.currentTimeMillis() - started}ms")
        val fallbackHeaders = info.httpHeaders.orEmpty()
        val audio = info.formats.orEmpty().mapNotNull { format ->
            val url = format.url ?: return@mapNotNull null
            // Exclude HLS/DASH manifest URLs that ProgressiveMediaSource cannot parse.
            val isManifest = url.contains(".m3u8", ignoreCase = true) ||
                url.contains(".mpd", ignoreCase = true) ||
                url.contains("/manifest/hls", ignoreCase = true) ||
                url.contains("/manifest/dash", ignoreCase = true) ||
                format.ext.equals("m3u8", ignoreCase = true) ||
                format.ext.equals("mpd", ignoreCase = true) ||
                format.formatId.orEmpty().contains("hls", ignoreCase = true)
            if (isManifest) return@mapNotNull null

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
