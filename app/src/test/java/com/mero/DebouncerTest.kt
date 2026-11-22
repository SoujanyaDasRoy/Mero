package com.mero

import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.delay
import kotlinx.coroutines.test.advanceTimeBy
import kotlinx.coroutines.test.advanceUntilIdle
import kotlinx.coroutines.test.runCurrent
import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Test

@OptIn(ExperimentalCoroutinesApi::class)
class DebouncerTest {

    @Test fun `a burst of requests runs the action once, after the wait`() = runTest {
        val runs = ArrayList<String>()
        val debouncer = Debouncer(this, 3_000) { runs += it }
        debouncer.request("p")
        advanceTimeBy(1_000)
        debouncer.request("p")
        advanceTimeBy(1_000)
        debouncer.request("p")
        advanceTimeBy(2_999)
        runCurrent()
        assertEquals(emptyList<String>(), runs)
        advanceUntilIdle()
        assertEquals(listOf("p"), runs)
    }

    @Test fun `a request while the action is running does not cancel it, and runs again after`() = runTest {
        var started = 0
        var finished = 0
        val debouncer = Debouncer(this, 3_000) {
            started++
            delay(10_000) // a slow sync
            finished++
        }
        debouncer.request("p")
        advanceTimeBy(4_000)
        runCurrent()
        assertEquals(1, started)

        debouncer.request("p") // an edit while that sync is running
        advanceUntilIdle()

        assertEquals(2, started)
        assertEquals(2, finished)
    }

    @Test fun `different keys wait separately`() = runTest {
        val runs = ArrayList<String>()
        val debouncer = Debouncer(this, 3_000) { runs += it }
        debouncer.request("a")
        debouncer.request("b")
        advanceUntilIdle()
        assertEquals(setOf("a", "b"), runs.toSet())
    }
}
