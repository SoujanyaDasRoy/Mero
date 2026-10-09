package com.mero.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinkTest {
    private val id = "dQw4w9WgXcQ"

    @Test fun `a song becomes a link on Mero's own site`() =
        assertEquals("https://soujanyadasroy.github.io/s/?v=$id", ShareLink.song(id))

    @Test fun `things with no YouTube id have no link`() {
        assertNull(ShareLink.song("local:12"))
        assertNull(ShareLink.song("pod:episode"))
        assertNull(ShareLink.song(""))
    }

    @Test fun `Mero reads its own link back`() =
        assertEquals(YouTubeLink(id, null), parseYouTubeLink(ShareLink.song(id)!!))

    @Test fun `the site's other pages are not songs`() {
        assertNull(parseYouTubeLink("https://soujanyadasroy.github.io/"))
        assertNull(parseYouTubeLink("https://soujanyadasroy.github.io/s/"))
    }
}
