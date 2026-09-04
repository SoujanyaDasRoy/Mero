package com.mero.data

import com.mero.domain.LyricLine
import com.mero.domain.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.util.concurrent.ConcurrentHashMap

@Serializable
private data class LrcLibResponse(
    @SerialName("plainLyrics") val plainLyrics: String? = null,
    @SerialName("syncedLyrics") val syncedLyrics: String? = null,
)

data class Lyrics(
    val lines: List<LyricLine>,
    val synced: Boolean,
) {
    val isEmpty: Boolean get() = lines.isEmpty()
}

/**
 * Synced lyrics from LRCLIB — free, no auth, no key. Falls back to plain
 * (unsynced) lyrics when nobody has contributed a timed version.
 *
 * Deliberately hand-rolled over HttpURLConnection rather than pulling another
 * HTTP client in: it's one GET, and the app already carries enough weight.
 */
class LyricsRepository {

    private val json = Json { ignoreUnknownKeys = true }
    private val cache = ConcurrentHashMap<String, Lyrics>()

    suspend fun lyricsFor(song: Song): Lyrics = withContext(Dispatchers.IO) {
        cache[song.id]?.let { return@withContext it }

        val result = runCatching { fetch(song) }.getOrDefault(Lyrics(emptyList(), false))
        cache[song.id] = result
        result
    }

    private fun fetch(song: Song): Lyrics {
        val url = buildString {
            append("https://lrclib.net/api/get")
            append("?artist_name=").append(enc(song.artist.substringBefore(",").trim()))
            append("&track_name=").append(enc(song.title.cleanedForLookup()))
            if (song.durationSec > 0) append("&duration=").append(song.durationSec)
        }

        val conn = (URL(url).openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = 6_000
            readTimeout = 6_000
            // LRCLIB asks clients to identify themselves.
            setRequestProperty("User-Agent", "Mero (https://github.com/SoujanyaDasRoy/Mero)")
        }

        try {
            if (conn.responseCode != 200) return Lyrics(emptyList(), false)
            val body = conn.inputStream.bufferedReader().use { it.readText() }
            val parsed = json.decodeFromString<LrcLibResponse>(body)

            parsed.syncedLyrics?.takeIf { it.isNotBlank() }?.let {
                return Lyrics(parseLrc(it), synced = true)
            }
            parsed.plainLyrics?.takeIf { it.isNotBlank() }?.let { plain ->
                val lines = plain.lineSequence()
                    .filter { it.isNotBlank() }
                    .map { LyricLine(0, it.trim()) }
                    .toList()
                return Lyrics(lines, synced = false)
            }
            return Lyrics(emptyList(), false)
        } finally {
            conn.disconnect()
        }
    }

    private fun enc(value: String) = URLEncoder.encode(value, "UTF-8")

    companion object {
        private val TIMESTAMP = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

        /** `[00:12.34] some words` -> LyricLine(12, "some words") */
        fun parseLrc(raw: String): List<LyricLine> = raw.lineSequence()
            .mapNotNull { line ->
                val match = TIMESTAMP.find(line) ?: return@mapNotNull null
                val (m, s) = match.destructured.toList().let { it[0] to it[1] }
                val text = line.substring(match.range.last + 1).trim()
                if (text.isEmpty()) return@mapNotNull null
                LyricLine(m.toInt() * 60 + s.toInt(), text)
            }
            .toList()

        /** Strips the "(From ...)" / "(Official Video)" noise before lookup. */
        fun String.cleanedForLookup(): String =
            replace(Regex("""\((?i:from|official|lyric|audio|video)[^)]*\)"""), "")
                .replace(Regex("""\[[^]]*]"""), "")
                .trim()
    }
}
