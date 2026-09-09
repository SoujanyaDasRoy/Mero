package com.mero.ui.home

import org.junit.Assert.assertEquals
import org.junit.Test

class GreetingTest {

    @Test
    fun `covers every hour of the day`() {
        (0..23).forEach { hour -> assertEquals(hour.toString(), true, greetingFor(hour).isNotBlank()) }
    }

    @Test
    fun `boundaries fall where people actually are`() {
        assertEquals("Still up?", greetingFor(4))
        assertEquals("Good morning", greetingFor(5))
        assertEquals("Good morning", greetingFor(11))
        assertEquals("Good afternoon", greetingFor(12))
        assertEquals("Good afternoon", greetingFor(16))
        assertEquals("Good evening", greetingFor(17))
        assertEquals("Good evening", greetingFor(21))
        assertEquals("Still up?", greetingFor(22))
        assertEquals("Still up?", greetingFor(0))
    }
}

class NamedGreetingTest {

    @Test
    fun `a name is appended to the greeting`() {
        assertEquals("Good morning, Soujanya", greetingFor(9, "Soujanya"))
        // The night one is a question, so the name belongs inside it.
        assertEquals("Still up, Soujanya?", greetingFor(2, "Soujanya"))
    }

    @Test
    fun `no name leaves the greeting alone`() {
        assertEquals("Good evening", greetingFor(19, ""))
        assertEquals("Good evening", greetingFor(19, "   "))
    }

    @Test
    fun `surrounding whitespace is not shown`() {
        assertEquals("Good afternoon, Ana", greetingFor(13, "  Ana  "))
    }
}
