package com.mero.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class YouTubeLinkTest {
    private val id = "dQw4w9WgXcQ"

    @Test fun watchLink() = assertEquals(YouTubeLink(id, null), parseYouTubeLink("https://www.youtube.com/watch?v=$id"))

    @Test fun musicLinkWithTracking() =
        assertEquals(YouTubeLink(id, null), parseYouTubeLink("https://music.youtube.com/watch?v=$id&si=abc123"))

    @Test fun shareLink() = assertEquals(YouTubeLink(id, null), parseYouTubeLink("https://youtu.be/$id?si=xyz&t=42"))

    @Test fun withoutScheme() = assertEquals(YouTubeLink(id, null), parseYouTubeLink("youtu.be/$id"))

    @Test fun shortsAndEmbed() {
        assertEquals(YouTubeLink(id, null), parseYouTubeLink("https://youtube.com/shorts/$id"))
        assertEquals(YouTubeLink(id, null), parseYouTubeLink("https://www.youtube.com/embed/$id"))
    }

    @Test fun videoInsidePlaylistKeepsBoth() =
        assertEquals(YouTubeLink(id, "RDAMVM$id"), parseYouTubeLink("https://music.youtube.com/watch?v=$id&list=RDAMVM$id"))

    @Test fun playlistOnly() =
        assertEquals(YouTubeLink(null, "PLabc123"), parseYouTubeLink("https://www.youtube.com/playlist?list=PLabc123"))

    @Test fun ordinaryTextIsNotALink() {
        assertNull(parseYouTubeLink("tera mera rishta"))
        assertNull(parseYouTubeLink("https://example.com/watch?v=$id"))
        assertNull(parseYouTubeLink("https://www.youtube.com/"))
        assertNull(parseYouTubeLink("https://www.youtube.com/watch?v=short"))
        assertNull(parseYouTubeLink(""))
    }
}
