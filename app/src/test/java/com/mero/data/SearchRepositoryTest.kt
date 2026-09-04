package com.mero.data

import com.mero.domain.Song
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.IOException

class SearchRepositoryTest {

    private val song = Song("abc123", "Kesariya", "Arijit Singh", durationSec = 268)

    @Test
    fun `returns songs from the api`() = runTest {
        val repo = SearchRepository(SearchApi { listOf(song) })
        val result = repo.search("kesariya")
        assertEquals(listOf(song), result.getOrNull())
    }

    @Test
    fun `blank query short-circuits without calling the api`() = runTest {
        var called = false
        val repo = SearchRepository(SearchApi { called = true; emptyList() })
        val result = repo.search("   ")
        assertEquals(emptyList<Song>(), result.getOrNull())
        assertTrue(!called)
    }

    @Test
    fun `network failure becomes a failed Result rather than a thrown exception`() = runTest {
        val repo = SearchRepository(SearchApi { throw IOException("offline") })
        val result = repo.search("kesariya")
        assertTrue(result.isFailure)
    }
}
