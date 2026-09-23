package com.mero.data

import com.mero.domain.SearchResultType
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PodcastFeedTest {

    private fun feed(items: String, channelExtra: String = "") = """
        <?xml version="1.0" encoding="UTF-8"?>
        <rss version="2.0" xmlns:itunes="http://www.itunes.com/dtds/podcast-1.0.dtd">
          <channel>
            <title>The Show</title>
            <itunes:image href="https://example.invalid/show.jpg"/>
            <image><url>https://example.invalid/fallback.jpg</url><title>Logo</title></image>
            $channelExtra
            $items
          </channel>
        </rss>
    """.trimIndent().byteInputStream()

    private val episode = """
        <item>
          <title>Episode One</title>
          <guid>abc-123</guid>
          <pubDate>Tue, 02 Sep 2025 06:00:00 +0000</pubDate>
          <itunes:duration>1:02:03</itunes:duration>
          <enclosure url="https://cdn.example.invalid/one.mp3" type="audio/mpeg" length="1"/>
        </item>
    """

    @Test
    fun `reads an episode into something the player can play`() {
        val result = parseFeed(feed(episode)).single()
        with(result.song) {
            assertEquals("Episode One", title)
            assertEquals("The Show", artist)
            assertEquals(3723, durationSec)
            assertEquals("https://cdn.example.invalid/one.mp3", sourceUri)
            assertEquals("https://example.invalid/show.jpg", thumbnailUrl)
            assertTrue(id.startsWith("pod:"))
            assertTrue("not a YouTube id", !isYouTube)
        }
        assertNotNull(result.publishedAt)
    }

    /**
     * The id has to be the same every time the feed is read, or a liked
     * episode or a queued one would stop matching itself on the next refresh.
     */
    @Test
    fun `the same episode gets the same id on every read`() {
        val first = parseFeed(feed(episode)).single().song.id
        val second = parseFeed(feed(episode)).single().song.id
        assertEquals(first, second)
    }

    /** Android refuses plain-http audio, so http enclosures are asked for over https. */
    @Test
    fun `plain http enclosures are upgraded`() {
        val http = episode.replace("https://cdn", "http://cdn")
        assertEquals("https://cdn.example.invalid/one.mp3", parseFeed(feed(http)).single().song.sourceUri)
    }

    @Test
    fun `items without audio are left out rather than shown unplayable`() {
        val video = """
            <item><title>Video</title>
              <enclosure url="https://cdn.example.invalid/v.mp4" type="video/mp4"/></item>
        """
        val noEnclosure = "<item><title>Show notes</title></item>"
        assertEquals(1, parseFeed(feed(video + noEnclosure + episode)).size)
    }

    /**
     * The channel's own <image> contains a <title>. Reading "the first title
     * anywhere" would name the show "Logo".
     */
    @Test
    fun `the show name comes from the channel, not from a nested title`() {
        assertEquals("The Show", parseFeed(feed(episode)).single().song.artist)
    }

    @Test
    fun `an episode picture beats the show picture`() {
        val own = episode.replace(
            "<guid>",
            """<itunes:image href="https://example.invalid/ep.jpg"/><guid>""",
        )
        assertEquals("https://example.invalid/ep.jpg", parseFeed(feed(own)).single().song.thumbnailUrl)
    }

    @Test
    fun `a missing guid falls back to the audio address for a stable id`() {
        val noGuid = episode.replace("<guid>abc-123</guid>", "")
        val a = parseFeed(feed(noGuid)).single().song.id
        val b = parseFeed(feed(noGuid)).single().song.id
        assertEquals(a, b)
    }
}

class PodcastFieldTest {

    @Test
    fun `durations in all three shapes`() {
        assertEquals(95, parseDuration("95"))
        assertEquals(95, parseDuration("1:35"))
        assertEquals(3695, parseDuration("01:01:35"))
    }

    @Test
    fun `nonsense durations are unknown, not guessed`() {
        assertEquals(0, parseDuration(null))
        assertEquals(0, parseDuration("about an hour"))
        assertEquals(0, parseDuration("1:2:3:4"))
    }

    @Test
    fun `dates with and without the day name`() {
        assertNotNull(parseDate("Tue, 02 Sep 2025 06:00:00 +0000"))
        assertNotNull(parseDate("02 Sep 2025 06:00:00 +0000"))
        assertNotNull(parseDate("Tue, 02 Sep 2025 06:00:00 GMT"))
        assertNull(parseDate("sometime last week"))
        assertNull(parseDate(""))
    }
}

class PodcastDirectoryTest {

    @Test
    fun `directory results become podcast rows`() {
        val body = """
            {"resultCount":2,"results":[
              {"collectionName":"Show A","artistName":"Host A",
               "feedUrl":"https://feeds.example.invalid/a","artworkUrl600":"https://img/a600.jpg"},
              {"collectionName":"No Feed","artistName":"Nobody"}
            ]}
        """.trimIndent()
        val rows = parseDirectory(body)
        assertEquals(1, rows.size)
        with(rows.single()) {
            assertEquals("Show A", title)
            assertEquals("Host A", subtitle)
            assertEquals("https://feeds.example.invalid/a", browseId)
            assertEquals(SearchResultType.Podcast, type)
            assertEquals("https://img/a600.jpg", thumbnailUrl)
        }
    }
}
