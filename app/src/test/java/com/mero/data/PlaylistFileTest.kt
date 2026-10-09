package com.mero.data

import com.mero.domain.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PlaylistFileTest {
    private fun song(id: String, title: String = "T $id") =
        Song(id = id, title = title, artist = "Artist", album = "Album", durationSec = 200, thumbnailUrl = "https://i.ytimg.com/$id.jpg")

    private val a = "dQw4w9WgXcQ"
    private val b = "9bZkp7q19f0"

    @Test
    fun `a playlist survives the trip`() {
        val text = PlaylistFile.encode("Road trip", "for the drive", listOf(song(a), song(b)))
        val back = PlaylistFile.decode(text)!!
        assertEquals("Road trip", back.name)
        assertEquals("for the drive", back.description)
        assertEquals(listOf(a, b), back.songs.map { it.id })
        assertEquals("T $a", back.songs[0].title)
        assertEquals(200, back.songs[0].durationSec)
        assertEquals("https://i.ytimg.com/$a.jpg", back.songs[0].thumbnailUrl)
    }

    @Test
    fun `phone files and podcast episodes are not shared`() {
        val local = Song(id = "local:1", title = "mine", artist = "", sourceUri = "content://x/1")
        val pod = Song(id = "pod:1", title = "ep", artist = "", sourceUri = "https://example.com/ep.mp3")
        val back = PlaylistFile.decode(PlaylistFile.encode("Mixed", null, listOf(song(a), local, pod)))!!
        assertEquals(listOf(a), back.songs.map { it.id })
    }

    @Test
    fun `text that is not a Mero playlist is refused`() {
        assertNull(PlaylistFile.decode("hello"))
        assertNull(PlaylistFile.decode("{}"))
        assertNull(PlaylistFile.decode("""{"mero":"backup","version":1,"name":"x","songs":[]}"""))
        assertNull(PlaylistFile.decode("""{"mero":"playlist","version":1,"songs":[]}"""))      // no name
        assertNull(PlaylistFile.decode("""{"mero":"playlist","version":1,"name":"x"}"""))        // no songs
    }

    @Test
    fun `a file from a newer Mero is refused rather than half read`() {
        assertNull(PlaylistFile.decode("""{"mero":"playlist","version":2,"name":"x","songs":[]}"""))
    }

    @Test
    fun `songs with ids that are not YouTube ids are dropped`() {
        val text = """{"mero":"playlist","version":1,"name":"x","songs":[
            {"id":"$a","title":"ok"},
            {"id":"not an id","title":"bad"},
            {"id":"../../etc/passwd","title":"worse"},
            {"id":"$b"}
        ]}"""
        assertEquals(listOf(a), PlaylistFile.decode(text)!!.songs.map { it.id })
    }

    @Test
    fun `covers must be https`() {
        val text = """{"mero":"playlist","version":1,"name":"x","songs":[
            {"id":"$a","title":"t","thumbnailUrl":"http://example.com/a.jpg"},
            {"id":"$b","title":"t","thumbnailUrl":"file:///data/data/com.mero/databases/mero.db"}
        ]}"""
        assertTrue(PlaylistFile.decode(text)!!.songs.all { it.thumbnailUrl == null })
    }

    @Test
    fun `text is capped and duplicates are merged`() {
        val long = "x".repeat(5_000)
        val text = """{"mero":"playlist","version":1,"name":"$long","songs":[
            {"id":"$a","title":"$long"},{"id":"$a","title":"again"}]}"""
        val back = PlaylistFile.decode(text)!!
        assertEquals(300, back.name.length)
        assertEquals(1, back.songs.size)
        assertEquals(300, back.songs[0].title.length)
    }

    @Test
    fun `file names are safe on every phone`() {
        assertEquals("Road trip.mero", PlaylistFile.fileName("Road trip"))
        assertEquals("abc.mero", PlaylistFile.fileName("a/b\\c:*?"))
        assertEquals("Mero playlist.mero", PlaylistFile.fileName("///"))
        assertNotNull(PlaylistFile.fileName("गाने"))
    }
}
