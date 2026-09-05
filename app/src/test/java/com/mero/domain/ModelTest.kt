package com.mero.domain

import org.junit.Assert.assertEquals
import org.junit.Test

class ModelTest {
    @Test
    fun `asClock keeps minute format below one hour`() {
        assertEquals("59:59", 3599.asClock())
    }

    @Test
    fun `asClock switches to hour format at one hour`() {
        assertEquals("1:00:00", 3600.asClock())
    }

    @Test
    fun `asClock pads minutes and seconds in hour format`() {
        assertEquals("1:01:05", 3665.asClock())
    }
}