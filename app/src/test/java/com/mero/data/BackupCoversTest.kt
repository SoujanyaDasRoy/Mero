package com.mero.data

import android.content.Context
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.RuntimeEnvironment
import org.robolectric.annotation.Config
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.io.File
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

// A plain Application: MeroApplication opens the real mero.db as it starts, which this test must own.
@Config(sdk = [34], application = android.app.Application::class)
@RunWith(RobolectricTestRunner::class)
class BackupCoversTest {
    private val context: Context get() = RuntimeEnvironment.getApplication()

    @Before
    fun clean() {
        context.getDatabasePath("mero.db").parentFile?.listFiles()?.forEach { it.delete() }
        PlaylistCovers.dir(context).deleteRecursively()
        File(context.filesDir, "evil.jpg").delete()
    }

    /** Something that looks like a database at [version] to anything reading its header. */
    private fun fakeDb(version: Int = 8): ByteArray {
        val bytes = ByteArray(4096)
        "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII).copyInto(bytes)
        bytes[62] = (version ushr 8).toByte(); bytes[63] = version.toByte()
        return bytes
    }

    private fun zip(vararg entries: Pair<String, ByteArray>): ByteArray {
        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { z ->
            entries.forEach { (name, bytes) -> z.putNextEntry(ZipEntry(name)); z.write(bytes); z.closeEntry() }
        }
        return out.toByteArray()
    }

    @Test
    fun `playlist covers go into a backup and come back from it`() {
        context.getDatabasePath("mero.db").apply { parentFile?.mkdirs() }.writeBytes(fakeDb())
        val cover = byteArrayOf(1, 2, 3, 4, 5)
        File(PlaylistCovers.dir(context).apply { mkdirs() }, "pl-abc_k1.jpg").writeBytes(cover)

        val out = ByteArrayOutputStream()
        Backup.write(context, out)

        // A new phone: nothing there yet.
        PlaylistCovers.dir(context).deleteRecursively()
        assertTrue(Backup.restore(context, ByteArrayInputStream(out.toByteArray()), currentDbVersion = 8))

        assertArrayEquals(cover, File(PlaylistCovers.dir(context), "pl-abc_k1.jpg").readBytes())
    }

    @Test
    fun `restoring replaces the covers rather than adding to them`() {
        File(PlaylistCovers.dir(context).apply { mkdirs() }, "pl-old_k1.jpg").writeBytes(byteArrayOf(9))
        val backup = zip("mero.db" to fakeDb(), "covers/pl-new_k2.jpg" to byteArrayOf(7))

        assertTrue(Backup.restore(context, ByteArrayInputStream(backup), currentDbVersion = 8))

        assertEquals(listOf("pl-new_k2.jpg"), PlaylistCovers.dir(context).list()!!.toList())
    }

    @Test
    fun `a backup entry cannot write outside the covers folder`() {
        val backup = zip(
            "mero.db" to fakeDb(),
            "covers/../evil.jpg" to byteArrayOf(1),
            "covers/..\\evil.jpg" to byteArrayOf(1),
            "covers/sub/x.jpg" to byteArrayOf(1),
            "covers/notimage.sh" to byteArrayOf(1),
        )

        assertTrue(Backup.restore(context, ByteArrayInputStream(backup), currentDbVersion = 8))

        assertFalse(File(context.filesDir, "evil.jpg").exists())
        assertTrue(PlaylistCovers.dir(context).list().orEmpty().isEmpty())
    }

    @Test
    fun `only plain jpg names are safe`() {
        assertTrue(PlaylistCovers.isSafeName("pl-abc_k1.jpg"))
        assertFalse(PlaylistCovers.isSafeName("../x.jpg"))
        assertFalse(PlaylistCovers.isSafeName("a/b.jpg"))
        assertFalse(PlaylistCovers.isSafeName("x.png"))
        assertFalse(PlaylistCovers.isSafeName(".jpg"))
    }
}
