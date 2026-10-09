package com.mero.data.db

import android.content.Context
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.File

// A plain Application: MeroApplication opens the real mero.db in the background as it starts, which these tests must own.
@Config(sdk = [34], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class PreMigrationCopyTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    /** Robolectric keeps the app's database folder between tests; start each one empty. */
    @Before
    fun clean() {
        context.getDatabasePath(NAME).parentFile?.listFiles()?.forEach { it.delete() }
    }

    /** A file that looks like a database at [version] to anything reading its header. */
    private fun fakeDb(version: Int): File {
        val file = context.getDatabasePath(NAME).apply { parentFile?.mkdirs() }
        val bytes = ByteArray(4096)
        "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        bytes[60] = (version ushr 24).toByte(); bytes[61] = (version ushr 16).toByte()
        bytes[62] = (version ushr 8).toByte(); bytes[63] = version.toByte()
        bytes[100] = 42
        file.writeBytes(bytes)
        return file
    }

    @Test
    fun `an older database is copied aside before it is migrated`() {
        val db = fakeDb(7)
        assertTrue(keepCopyBeforeMigration(context, currentVersion = 8, name = NAME))
        val copy = File(db.parentFile, "mero-before-v8.db")
        assertTrue(copy.exists())
        assertTrue(db.readBytes().contentEquals(copy.readBytes()))
    }

    @Test
    fun `a current database is left alone`() {
        val db = fakeDb(8)
        assertFalse(keepCopyBeforeMigration(context, currentVersion = 8, name = NAME))
        assertFalse(File(db.parentFile, "mero-before-v8.db").exists())
    }

    @Test
    fun `no database yet means nothing to copy`() {
        context.getDatabasePath(NAME).delete()
        assertFalse(keepCopyBeforeMigration(context, currentVersion = 8, name = NAME))
    }

    @Test
    fun `the copy is made once and a stale one from an earlier update is removed`() {
        val db = fakeDb(6)
        File(db.parentFile, "mero-before-v7.db").writeBytes(ByteArray(10))
        assertTrue(keepCopyBeforeMigration(context, currentVersion = 8, name = NAME))
        assertFalse(File(db.parentFile, "mero-before-v7.db").exists())
        db.writeBytes(db.readBytes().also { it[100] = 99 })
        assertFalse("a second start does not overwrite the first copy", keepCopyBeforeMigration(context, currentVersion = 8, name = NAME))
        assertEquals(42.toByte(), File(db.parentFile, "mero-before-v8.db").readBytes()[100])
    }

    @Test
    fun `something that is not a database is not copied`() {
        val file = context.getDatabasePath(NAME).apply { parentFile?.mkdirs() }
        file.writeBytes(ByteArray(500) { 1 })
        assertFalse(keepCopyBeforeMigration(context, currentVersion = 8, name = NAME))
    }

    private companion object {
        const val NAME = "premigration-test.db"
    }
}
