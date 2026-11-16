package com.mero.data

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class GroupMergeTest {

    private fun add(seq: Long, id: String) = GroupStep(seq, "add", id)
    private fun remove(seq: Long, id: String) = GroupStep(seq, "remove", id)

    @Test fun `adds append in seq order`() {
        val merged = mergeGroup(emptyList(), listOf(add(1, "a"), add(2, "b")), emptyList())
        assertEquals(listOf("a", "b"), merged.songIds)
        assertEquals(2, merged.added)
    }

    @Test fun `last step per song wins`() {
        assertEquals(listOf("a"), mergeGroup(listOf("a"), listOf(remove(5, "a"), add(6, "a")), emptyList()).songIds)
        assertEquals(emptyList<String>(), mergeGroup(listOf("a"), listOf(add(5, "a"), remove(6, "a")), emptyList()).songIds)
    }

    @Test fun `local order is kept and new songs go to the end`() {
        val merged = mergeGroup(listOf("c", "a", "b"), listOf(add(9, "d"), add(10, "a")), emptyList())
        assertEquals(listOf("c", "a", "b", "d"), merged.songIds)
        assertEquals(1, merged.added)
    }

    @Test fun `a removed song re-added goes to the end`() {
        assertEquals(listOf("b", "a"), mergeGroup(listOf("a", "b"), listOf(remove(3, "a"), add(4, "a")), emptyList()).songIds)
    }

    @Test fun `pending steps apply on top of fetched ones`() {
        val merged = mergeGroup(
            listOf("a"),
            listOf(add(2, "b")),
            listOf(PendingStep("remove", "a"), PendingStep("add", "c")),
        )
        assertEquals(listOf("b", "c"), merged.songIds)
    }

    @Test fun `duplicate steps are harmless`() {
        assertEquals(listOf("a"), mergeGroup(emptyList(), listOf(add(1, "a"), add(1, "a")), emptyList()).songIds)
    }

    @Test fun `rename takes the last name and cover is flagged`() {
        val merged = mergeGroup(
            emptyList(),
            listOf(GroupStep(1, "rename", "X"), GroupStep(2, "cover", null), GroupStep(3, "rename", "Y")),
            emptyList(),
        )
        assertEquals("Y", merged.name)
        assertTrue(merged.coverChanged)
    }

    @Test fun `no rename and no cover leave both alone`() {
        val merged = mergeGroup(listOf("a"), listOf(add(1, "b")), emptyList())
        assertNull(merged.name)
        assertFalse(merged.coverChanged)
    }

    @Test fun `ten thousand adds merge quickly`() {
        val steps = (1..10_000L).map { add(it, "id$it") }
        val started = System.nanoTime()
        val merged = mergeGroup(emptyList(), steps, emptyList())
        assertEquals(10_000, merged.songIds.size)
        assertTrue("took too long", System.nanoTime() - started < 1_000_000_000L)
    }
}
