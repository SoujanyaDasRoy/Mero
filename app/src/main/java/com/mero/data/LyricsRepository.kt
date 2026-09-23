package com.mero.data

import com.mero.domain.LyricLine
import com.mero.domain.Song
import com.zionhuang.innertube.YouTube
import com.zionhuang.innertube.models.WatchEndpoint
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap
import kotlin.math.abs

@Serializable
internal data class LrcLibRecord(
    @SerialName("duration") val duration: Double? = null,
    @SerialName("plainLyrics") val plainLyrics: String? = null,
    @SerialName("syncedLyrics") val syncedLyrics: String? = null,
)

data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
    /** Who the words came from, for the credit line under them. */
    val source: String = "",
) {
    val isEmpty: Boolean get() = lines.isEmpty()

    companion object {
        val None = Lyrics(emptyList(), false)
    }
}

/**
 * Lyrics, from as many places as it takes.
 *
 * 1. LRCLIB's exact lookup — one request, and right most of the time.
 * 2. LRCLIB's search, once per credited artist and then by title alone. YouTube
 *    titles carry "(From "Aashiqui 2")", "(Male Version)", every composer in
 *    the artist field and a duration a few seconds off the album's; the exact
 *    lookup misses all of those. Measured on fifteen real titles: the exact
 *    lookup found 13 (11 synced), with search 15 (14 synced).
 * 3. YouTube Music's own lyrics, which cover songs LRCLIB has never heard of,
 *    but never with timings.
 *
 * Synced beats plain wherever it came from. All anonymous; no keys.
 */
class LyricsRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, Lyrics>()

    suspend fun lyricsFor(song: Song): Lyrics = withContext(Dispatchers.IO) {
        cache[song.id]?.let { return@withContext it }
        val result = runCatchingCancellable { find(song) }.getOrDefault(Lyrics.None)
        // A failure is not cached: offline now is not "no lyrics" forever.
        if (!result.isEmpty) cache[song.id] = result
        result
    }

    /** "Wrong lyrics?": look again with words the person typed. */
    suspend fun search(song: Song, query: String): Lyrics = withContext(Dispatchers.IO) {
        val found = runCatchingCancellable {
            best(lrclibSearch(mapOf("q" to query)), song.durationSec, strictDuration = false)
        }.getOrNull() ?: Lyrics.None
        if (!found.isEmpty) cache[song.id] = found
        found
    }

    private suspend fun find(song: Song): Lyrics {
        val title = cleanTitle(song.title)
        val artists = song.artist.split(",", "&", " x ").map { it.trim() }.filter { it.isNotEmpty() }

        var plain: Lyrics? = null
        fun keep(l: Lyrics?): Lyrics? {
            if (l == null || l.isEmpty) return null
            if (l.synced) return l
            if (plain == null) plain = l
            return null
        }

        keep(lrclibGet(title, artists.firstOrNull().orEmpty(), song.durationSec))?.let { return it }
        for (query in artists.map { mapOf("track_name" to title, "artist_name" to it) } + mapOf("q" to title)) {
            keep(best(lrclibSearch(query), song.durationSec, strictDuration = true))?.let { return it }
        }
        plain?.let { return it }
        return if (song.isYouTube) youtubeMusic(song.id) else Lyrics.None
    }

    private fun lrclibGet(title: String, artist: String, durationSec: Int): Lyrics? {
        val params = buildMap {
            put("track_name", title)
            put("artist_name", artist)
            if (durationSec > 0) put("duration", durationSec.toString())
        }
        val body = httpGet("https://lrclib.net/api/get?" + query(params)) ?: return null
        return toLyrics(json.decodeFromString<LrcLibRecord>(body))
    }

    private fun lrclibSearch(params: Map<String, String>): List<LrcLibRecord> {
        val body = httpGet("https://lrclib.net/api/search?" + query(params)) ?: return emptyList()
        return json.decodeFromString<List<LrcLibRecord>>(body)
    }

    private fun best(records: List<LrcLibRecord>, durationSec: Int, strictDuration: Boolean): Lyrics? =
        pickBest(records, durationSec, strictDuration)?.let(::toLyrics)

    private fun toLyrics(record: LrcLibRecord): Lyrics? {
        record.syncedLyrics?.takeIf { it.isNotBlank() }?.let { raw ->
            parseLrc(raw).takeIf { it.isNotEmpty() }?.let { return Lyrics(it, synced = true, source = "LRCLIB") }
        }
        record.plainLyrics?.takeIf { it.isNotBlank() }?.let { return Lyrics(plainLines(it), synced = false, source = "LRCLIB") }
        return null
    }

    private suspend fun youtubeMusic(videoId: String): Lyrics {
        val endpoint = YouTube.next(WatchEndpoint(videoId = videoId)).getOrNull()?.lyricsEndpoint ?: return Lyrics.None
        val text = YouTube.lyrics(endpoint).getOrNull()?.takeIf { it.isNotBlank() } ?: return Lyrics.None
        return Lyrics(plainLines(text), synced = false, source = "YouTube Music")
    }

    private fun httpGet(url: String): String? {
        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            connectTimeout = 6_000
            readTimeout = 8_000
            // LRCLIB asks clients to identify themselves.
            setRequestProperty("User-Agent", "Mero (https://github.com/SoujanyaDasRoy/Mero)")
        }
        return try {
            if (conn.responseCode != 200) null else conn.inputStream.bufferedReader().use { it.readText() }
        } finally {
            conn.disconnect()
        }
    }

    private fun query(params: Map<String, String>) =
        params.entries.joinToString("&") { (k, v) -> k + "=" + URLEncoder.encode(v, "UTF-8") }

    companion object {
        private val TIMESTAMP = Regex("""\[(\d{1,3}):(\d{2})(?:[.:](\d{1,3}))?]""")
        private val OFFSET = Regex("""\[offset:\s*([+-]?\d+)\s*]""", RegexOption.IGNORE_CASE)

        /**
         * `[00:12.34] words` -> LyricLine(12_340, "words"), to the millisecond.
         *
         * Whole seconds used to be kept and the fraction thrown away, so the
         * highlighted line ran up to a second behind the singer. Also handles
         * a line sung twice (`[00:12.00][01:30.00] chorus`) and the `[offset:]`
         * tag some files carry.
         */
        fun parseLrc(raw: String): List<LyricLine> {
            val offsetMs = OFFSET.find(raw)?.groupValues?.get(1)?.toLongOrNull() ?: 0L
            return raw.lineSequence().flatMap { line ->
                val stamps = TIMESTAMP.findAll(line).toList()
                if (stamps.isEmpty()) return@flatMap emptySequence()
                val text = line.substring(stamps.last().range.last + 1).trim()
                if (text.isEmpty()) return@flatMap emptySequence()
                stamps.asSequence().map { match ->
                    val (m, s, frac) = match.destructured
                    val fracMs = when (frac.length) {
                        0 -> 0L
                        1 -> frac.toLong() * 100
                        2 -> frac.toLong() * 10
                        else -> frac.toLong()
                    }
                    // A positive offset means the lyrics should appear earlier.
                    val at = m.toLong() * 60_000 + s.toLong() * 1_000 + fracMs - offsetMs
                    LyricLine(at.coerceAtLeast(0), text)
                }
            }.sortedBy { it.atMs }.toList()
        }

        internal fun plainLines(text: String): List<LyricLine> = text.lineSequence()
            .map { it.trim() }
            .filter { it.isNotEmpty() }
            .map { LyricLine(0, it) }
            .toList()

        /**
         * The title a lyrics site files a song under. Every bracketed aside
         * goes — "(From "Joshila")", "(Male Version)", "[Official Video]" —
         * and so does anything after " - " or " | " ("Remastered", "Lyrical").
         */
        fun cleanTitle(title: String): String {
            val stripped = title
                .replace(Regex("""\([^)]*\)|\[[^]]*]"""), "")
                .split(" - ", " | ").first()
                .replace(Regex("""(?i)\s(feat|ft)\.?\s.*$"""), "")
                .trim()
            return stripped.ifEmpty { title.trim() }
        }

        /**
         * The record to use: synced over plain, then the closest duration.
         * [strictDuration] drops anything more than ten seconds off, which on
         * a search by title alone is what keeps a cover or a remix out.
         */
        internal fun pickBest(records: List<LrcLibRecord>, durationSec: Int, strictDuration: Boolean): LrcLibRecord? {
            fun off(r: LrcLibRecord) =
                if (durationSec <= 0 || r.duration == null) 0.0 else abs(r.duration - durationSec)
            return records
                .filter { !it.syncedLyrics.isNullOrBlank() || !it.plainLyrics.isNullOrBlank() }
                .filter { !strictDuration || off(it) <= 10.0 }
                .sortedWith(compareBy({ it.syncedLyrics.isNullOrBlank() }, { off(it) }))
                .firstOrNull()
        }
    }
}
