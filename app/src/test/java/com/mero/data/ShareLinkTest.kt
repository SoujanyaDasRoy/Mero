package com.mero.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

class ShareLinkTest {
    private val id = "dQw4w9WgXcQ"

    @Test fun `a song becomes a link on Mero's own site`() =
        assertEquals("https://soujanyadasroy.github.io/song/?v=$id", ShareLink.song(id))

    @Test fun `things with no YouTube id have no link`() {
        assertNull(ShareLink.song("local:12"))
        assertNull(ShareLink.song("pod:episode"))
        assertNull(ShareLink.song(""))
    }

    @Test fun `Mero reads its own link back`() =
        assertEquals(YouTubeLink(id, null), parseYouTubeLink(ShareLink.song(id)!!))

    @Test fun `the site's other pages are not songs`() {
        assertNull(parseYouTubeLink("https://soujanyadasroy.github.io/"))
        assertNull(parseYouTubeLink("https://soujanyadasroy.github.io/song/"))
    }

    private val a = "dQw4w9WgXcQ"
    private val b = "9bZkp7q19f0"

    @Test fun `a playlist link carries its name and every id in order`() {
        val link = ShareLink.playlist("Road trip & chill", listOf(a, b))!!
        assertEquals(PlaylistLink("Road trip & chill", listOf(a, b)), ShareLink.parsePlaylist(link))
    }

    @Test fun `a name with non-Latin letters and emoji survives`() {
        val link = ShareLink.playlist("गाने \uD83C\uDF6A", listOf(a))!!
        assertEquals("गाने \uD83C\uDF6A", ShareLink.parsePlaylist(link)!!.name)
    }

    @Test fun `songs that are not YouTube tracks are left out and nothing left is no link`() {
        assertEquals(PlaylistLink("x", listOf(a)), ShareLink.parsePlaylist(ShareLink.playlist("x", listOf("local:1", a))!!))
        assertNull(ShareLink.playlist("x", listOf("local:1", "pod:2")))
        assertNull(ShareLink.playlist("x", emptyList()))
    }

    @Test fun `too many songs for a link is no link`() {
        val many = (1..ShareLink.MAX_PLAYLIST_SONGS + 1).map { "%011d".format(it) }
        assertNull(ShareLink.playlist("big", many))
        val ok = (1..ShareLink.MAX_PLAYLIST_SONGS).map { "%011d".format(it) }
        assertEquals(ShareLink.MAX_PLAYLIST_SONGS, ShareLink.parsePlaylist(ShareLink.playlist("big", ok)!!)!!.ids.size)
    }

    @Test fun `a playlist link with a broken song list is refused`() {
        assertNull(ShareLink.parsePlaylist("https://soujanyadasroy.github.io/playlist/?n=x&s=" + a + "short"))
        assertNull(ShareLink.parsePlaylist("https://soujanyadasroy.github.io/playlist/?n=x&s="))
        assertNull(ShareLink.parsePlaylist("https://soujanyadasroy.github.io/playlist/?n=x"))
        assertNull(ShareLink.parsePlaylist("https://soujanyadasroy.github.io/playlist/?n=x&s=" + "!".repeat(11)))
    }

    @Test fun `only Mero's own site is read as a playlist link`() {
        assertNull(ShareLink.parsePlaylist("https://example.com/playlist/?n=x&s=$a"))
        assertNull(ShareLink.parsePlaylist("http://soujanyadasroy.github.io/playlist/?n=x&s=$a"))
        assertNull(ShareLink.parsePlaylist("https://soujanyadasroy.github.io/song/?v=$a"))
    }

    @Test fun `a missing name still opens, as a shared playlist`() =
        assertEquals("Shared playlist", ShareLink.parsePlaylist("https://soujanyadasroy.github.io/playlist/?s=$a")!!.name)
}
