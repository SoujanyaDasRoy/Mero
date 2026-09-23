package com.mero.data

import com.mero.domain.Song
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class SongReminderTimingTest {

    private val day = 24 * 60 * 60 * 1000L
    private val now = 100 * day

    @Test
    fun `off never reminds`() {
        assertFalse(shouldRemind(ReminderFrequency.Off, now, hour = 12, lastReminderMs = 0, lastOpenedMs = 0))
    }

    @Test
    fun `now and then waits three days between reminders`() {
        assertFalse(shouldRemind(ReminderFrequency.Sometimes, now, 12, lastReminderMs = now - 2 * day, lastOpenedMs = 0))
        assertTrue(shouldRemind(ReminderFrequency.Sometimes, now, 12, lastReminderMs = now - 3 * day, lastOpenedMs = 0))
    }

    /** Someone who listened yesterday does not need reminding to listen. */
    @Test
    fun `nobody is reminded who has been listening anyway`() {
        assertFalse(shouldRemind(ReminderFrequency.Sometimes, now, 12, lastReminderMs = 0, lastOpenedMs = now - day))
        assertFalse(shouldRemind(ReminderFrequency.Daily, now, 12, lastReminderMs = 0, lastOpenedMs = now - 2 * 60 * 60 * 1000L))
    }

    @Test
    fun `never early in the morning or late at night`() {
        assertFalse(shouldRemind(ReminderFrequency.Daily, now, hour = 7, lastReminderMs = 0, lastOpenedMs = 0))
        assertFalse(shouldRemind(ReminderFrequency.Daily, now, hour = 22, lastReminderMs = 0, lastOpenedMs = 0))
        assertTrue(shouldRemind(ReminderFrequency.Daily, now, hour = 18, lastReminderMs = 0, lastOpenedMs = 0))
    }
}

class SongReminderPickTest {

    private fun song(id: String) = Song(id = id, title = "Song $id", artist = "Artist $id")

    @Test
    fun `a favourite not heard lately is picked`() {
        val picked = pickReminderSong(
            liked = listOf(song("a"), song("b")),
            mostPlayed = emptyList(),
            recentIds = setOf("a"),
            remindedIds = emptyList(),
            seed = 0,
        )
        assertEquals("b", picked?.id)
    }

    /** The same song three reminders running would feel like a bot. */
    @Test
    fun `songs already suggested wait their turn`() {
        val picked = pickReminderSong(listOf(song("a"), song("b")), emptyList(), emptySet(), remindedIds = listOf("a"), seed = 0)
        assertEquals("b", picked?.id)
    }

    @Test
    fun `when every favourite was suggested, start over rather than go quiet`() {
        assertNotNull(pickReminderSong(listOf(song("a")), emptyList(), emptySet(), remindedIds = listOf("a"), seed = 0))
    }

    @Test
    fun `nothing liked or played means nothing to suggest`() {
        assertNull(pickReminderSong(emptyList(), emptyList(), emptySet(), emptyList(), seed = 0))
    }

    @Test
    fun `every message names the song and stays friendly`() {
        val s = Song(id = "x", title = "Tum Hi Ho (From \"Aashiqui 2\")", artist = "Arijit Singh, Mithoon")
        repeat(REMINDER_MESSAGES.size) { i ->
            val (title, body) = reminderText(s, i)
            assertTrue(title.isNotBlank())
            assertTrue("$title / $body", (title + body).contains("Tum Hi Ho"))
            // Cleaned: the film credit and second artist stay out of a short line.
            assertFalse(body.contains("Aashiqui"))
            assertFalse(body.contains("Mithoon"))
        }
    }
}
