package com.mero.data

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class AutoBackupTest {
    private val hour = 60 * 60 * 1000L
    private val now = 1_000_000_000_000L

    @Test fun `never backed up is due`() = assertTrue(AutoBackup.due(now, 0))

    @Test fun `a backup minutes ago is not due`() = assertFalse(AutoBackup.due(now, now - 10 * 60 * 1000L))

    @Test fun `a backup over an hour ago is due`() = assertTrue(AutoBackup.due(now, now - hour - 1))

    @Test fun `exactly an hour is due`() = assertTrue(AutoBackup.due(now, now - hour))

    @Test fun `a clock set back does not stop backups for ever`() = assertTrue(AutoBackup.due(now, now + 5 * hour))
}
