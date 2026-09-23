package com.mero.data

import com.mero.domain.SearchItem
import com.mero.domain.SearchResultType
import com.mero.domain.Song
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.serialization.SerialName
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import org.w3c.dom.Element
import java.io.InputStream
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder
import java.security.MessageDigest
import java.text.SimpleDateFormat
import java.util.Locale
import javax.xml.parsers.DocumentBuilderFactory

/** One episode, with the date the list needs and the song the player needs. */
data class PodcastEpisode(val song: Song, val publishedAt: Long?)

/**
 * Podcasts, from the two places podcasts actually live.
 *
 * Finding a show uses Apple's podcast directory search, which every podcast app
 * leans on: free, no key, no account. The episodes then come from the show's
 * own RSS feed, which is the podcast — a public file the publisher puts out so
 * that any app can play it. No YouTube, no extraction, nothing to break when
 * YouTube changes something, and nothing that goes near CLAUDE.md
 * constraint 1: there is no sign-in anywhere in this.
 */
class PodcastRepository {

    suspend fun search(term: String): Result<List<SearchItem>> = runCatchingCancellable {
        val url = "https://itunes.apple.com/search?media=podcast&entity=podcast&limit=40&term=" +
            URLEncoder.encode(term.trim(), "UTF-8")
        val body = withContext(Dispatchers.IO) { fetch(url).use { it.readBytes().decodeToString() } }
        parseDirectory(body)
    }

    suspend fun episodes(feedUrl: String): Result<List<PodcastEpisode>> = runCatchingCancellable {
        withContext(Dispatchers.IO) { fetch(feedUrl).use { parseFeed(it) } }
    }

    /**
     * Follows redirects by hand, because HttpURLConnection will not follow one
     * from http to https — and a great many feeds were registered under http
     * and have since moved.
     */
    private fun fetch(address: String): InputStream {
        var current = secure(address)
        repeat(MAX_REDIRECTS) {
            val connection = (URL(current).openConnection() as HttpURLConnection).apply {
                instanceFollowRedirects = false
                connectTimeout = 10_000
                readTimeout = 20_000
                setRequestProperty("User-Agent", "Mero")
            }
            val code = connection.responseCode
            if (code in 300..399) {
                val next = connection.getHeaderField("Location")
                connection.disconnect()
                requireNotNull(next) { "The podcast server sent a redirect to nowhere" }
                current = secure(URL(URL(current), next).toString())
                return@repeat
            }
            if (code !in 200..299) {
                connection.disconnect()
                error("The podcast server answered $code")
            }
            return connection.inputStream
        }
        error("Too many redirects fetching the podcast")
    }
}

/* ---- The parts worth testing, kept free of Android ---- */

@Serializable
private data class DirectoryResponse(val results: List<DirectoryEntry> = emptyList())

@Serializable
private data class DirectoryEntry(
    val collectionName: String? = null,
    val artistName: String? = null,
    val feedUrl: String? = null,
    @SerialName("artworkUrl600") val artwork: String? = null,
    @SerialName("artworkUrl100") val smallArtwork: String? = null,
)

private val directoryJson = Json { ignoreUnknownKeys = true }

/** The directory's answer as search rows. A show with no feed cannot be played, so it is left out. */
internal fun parseDirectory(body: String): List<SearchItem> =
    directoryJson.decodeFromString<DirectoryResponse>(body).results.mapNotNull { entry ->
        val feed = entry.feedUrl?.takeIf { it.isNotBlank() } ?: return@mapNotNull null
        SearchItem(
            id = feed,
            title = entry.collectionName.orEmpty().ifBlank { "Untitled podcast" },
            subtitle = entry.artistName.orEmpty(),
            thumbnailUrl = entry.artwork ?: entry.smallArtwork,
            type = SearchResultType.Podcast,
            browseId = feed,
        )
    }

/**
 * A podcast feed as episodes, newest first as published.
 *
 * DOM rather than a pull parser so it runs unchanged on the JVM in tests; a
 * feed is a few hundred kilobytes, so holding it in memory costs nothing.
 * Items without an audio enclosure — show notes, video-only episodes — are
 * dropped rather than shown as rows that cannot play.
 */
internal fun parseFeed(input: InputStream): List<PodcastEpisode> {
    val factory = DocumentBuilderFactory.newInstance().apply {
        isNamespaceAware = true
        isExpandEntityReferences = false
        // A feed is untrusted input; an external-entity declaration in it
        // should not be able to read files. Not every platform parser accepts
        // this feature, and the ones that refuse it do not resolve entities.
        runCatching { setFeature("http://apache.org/xml/features/disallow-doctype-decl", true) }
    }
    val doc = factory.newDocumentBuilder().parse(input)
    val channel = doc.getElementsByTagName("channel").item(0) as? Element ?: return emptyList()

    val show = channel.childText("title").orEmpty().ifBlank { "Podcast" }
    val showArt = channel.itunes("image")?.getAttribute("href")?.takeIf { it.isNotBlank() }
        ?: (channel.getElementsByTagName("image").item(0) as? Element)?.childText("url")

    val items = channel.getElementsByTagName("item")
    return (0 until minOf(items.length, MAX_EPISODES)).mapNotNull { index ->
        val item = items.item(index) as Element
        val enclosure = item.getElementsByTagName("enclosure").item(0) as? Element
            ?: return@mapNotNull null
        val audio = enclosure.getAttribute("url").takeIf { it.isNotBlank() } ?: return@mapNotNull null
        val type = enclosure.getAttribute("type")
        if (type.isNotBlank() && !type.startsWith("audio")) return@mapNotNull null

        val guid = item.childText("guid")?.takeIf { it.isNotBlank() } ?: audio
        PodcastEpisode(
            song = Song(
                id = "pod:" + stableId(guid),
                title = item.childText("title").orEmpty().ifBlank { "Episode" },
                artist = show,
                album = show,
                durationSec = parseDuration(item.itunes("duration")?.textContent),
                thumbnailUrl = item.itunes("image")?.getAttribute("href")?.takeIf { it.isNotBlank() }
                    ?: showArt,
                sourceUri = secure(audio),
            ),
            publishedAt = parseDate(item.childText("pubDate")),
        )
    }
}

/**
 * `itunes:duration` is written three ways in the wild: plain seconds, MM:SS
 * and HH:MM:SS. Anything else is treated as unknown rather than guessed at.
 */
internal fun parseDuration(raw: String?): Int {
    val parts = raw?.trim()?.split(":")?.map { it.trim().toIntOrNull() ?: return 0 } ?: return 0
    return when (parts.size) {
        1 -> parts[0]
        2 -> parts[0] * 60 + parts[1]
        3 -> parts[0] * 3600 + parts[1] * 60 + parts[2]
        else -> 0
    }
}

/** RSS dates are RFC 822, with or without the day name and seconds. */
internal fun parseDate(raw: String?): Long? {
    val text = raw?.trim()?.takeIf { it.isNotEmpty() } ?: return null
    return DATE_FORMATS.firstNotNullOfOrNull { pattern ->
        runCatching { SimpleDateFormat(pattern, Locale.US).parse(text)?.time }.getOrNull()
    }
}

/**
 * Android refuses plain-http audio unless an app opts out of that protection,
 * and plenty of older feeds still list http enclosures. Nearly every podcast
 * host serves the same file over https, so ask for that instead.
 */
internal fun secure(url: String): String =
    if (url.startsWith("http://", ignoreCase = true)) "https://" + url.substring(7) else url

/** A short id that is the same every time the feed is read, so plays and likes attach to it. */
internal fun stableId(key: String): String =
    MessageDigest.getInstance("SHA-1").digest(key.toByteArray())
        .joinToString("") { "%02x".format(it) }
        .take(16)

private fun Element.childText(name: String): String? {
    val nodes = getElementsByTagName(name)
    for (i in 0 until nodes.length) {
        val node = nodes.item(i)
        // Direct children only: an <item>'s <title> must not be mistaken for
        // the channel's, and the channel's <image> holds a <title> of its own.
        if (node.parentNode == this) return node.textContent?.trim()
    }
    return null
}

private fun Element.itunes(name: String): Element? {
    val nodes = getElementsByTagNameNS(ITUNES_NS, name)
    for (i in 0 until nodes.length) if (nodes.item(i).parentNode == this) return nodes.item(i) as Element
    return null
}

private const val ITUNES_NS = "http://www.itunes.com/dtds/podcast-1.0.dtd"
private const val MAX_REDIRECTS = 6

/** Some feeds carry a decade of episodes; the newest few hundred is what anyone scrolls. */
private const val MAX_EPISODES = 300

private val DATE_FORMATS = listOf(
    "EEE, d MMM yyyy HH:mm:ss Z",
    "EEE, d MMM yyyy HH:mm:ss zzz",
    "EEE, d MMM yyyy HH:mm Z",
    "d MMM yyyy HH:mm:ss Z",
    "EEE, d MMM yyyy",
)
