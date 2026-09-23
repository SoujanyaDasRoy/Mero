package com.mero.playback

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class CarBrowseTreeTest {

    @Test
    fun `a tapped song carries its list and comes back apart`() {
        val id = CarBrowseTree.playableId("section:liked", "dQw4w9WgXcQ")
        assertEquals("section:liked" to "dQw4w9WgXcQ", CarBrowseTree.parse(id))
    }

    /** Podcast and phone ids contain a colon; the separator must not be one. */
    @Test
    fun `Mero's own ids survive the round trip`() {
        listOf("pod:0a1b2c3d4e5f6789", "local:abcdef0123456789").forEach { songId ->
            val id = CarBrowseTree.playableId(CarBrowseTree.playlistNodeId("p1"), songId)
            assertEquals("playlist:p1" to songId, CarBrowseTree.parse(id))
        }
    }

    @Test
    fun `a bare id has no list`() {
        assertEquals(null to "dQw4w9WgXcQ", CarBrowseTree.parse("dQw4w9WgXcQ"))
    }

    @Test
    fun `nodes are recognised, songs are not nodes`() {
        assertEquals(CarBrowseTree.Node.Root, CarBrowseTree.nodeOf(CarBrowseTree.ROOT))
        assertEquals(CarBrowseTree.Node.SectionNode("liked"), CarBrowseTree.nodeOf("section:liked"))
        assertEquals(CarBrowseTree.Node.PlaylistNode("p1"), CarBrowseTree.nodeOf("playlist:p1"))
        assertNull(CarBrowseTree.nodeOf("section:liked|abc"))
        assertNull(CarBrowseTree.nodeOf("dQw4w9WgXcQ"))
    }

    @Test
    fun `every section id resolves back to its section`() {
        CarBrowseTree.sections.forEach {
            assertEquals(CarBrowseTree.Node.SectionNode(it.key), CarBrowseTree.nodeOf(it.id))
        }
    }
}

class CarPagingTest {

    private val items = (1..25).toList()

    @Test
    fun `pages split the list`() {
        assertEquals((1..10).toList(), pageOf(items, 0, 10))
        assertEquals((21..25).toList(), pageOf(items, 2, 10))
    }

    /** Cars ask for the next page until one comes back empty. */
    @Test
    fun `a page past the end is empty, not a crash`() {
        assertTrue(pageOf(items, 3, 10).isEmpty())
        assertTrue(pageOf(items, 1000, 10).isEmpty())
    }

    /**
     * "Everything" arrives as Int.MAX_VALUE. In Int arithmetic from + size
     * wraps negative and subList throws, ending browsing on the first page.
     */
    @Test
    fun `asking for everything does not overflow`() {
        assertEquals(items, pageOf(items, 0, Int.MAX_VALUE))
        assertTrue(pageOf(items, 1, Int.MAX_VALUE).isEmpty())
    }
}
