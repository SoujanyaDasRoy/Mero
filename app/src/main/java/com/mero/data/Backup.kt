package com.mero.data

import android.content.Context
import java.io.File
import java.io.InputStream
import java.io.OutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream

/**
 * Everything that makes Mero yours (library, playlists, likes, play history and
 * so on, settings) in one file, so it survives a reinstall or a new phone.
 *
 * It is the app's own database and preference files, zipped. Streams are not
 * in it (they are re-found by video id) and neither are downloaded audio files
 * or the Spotify secret, which is meant to stay on one phone.
 */
object Backup {

    private const val DB = "mero.db"
    private val PREFS = listOf("settings", "quality", "search")
    private val SQLITE_MAGIC = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)

    fun write(context: Context, out: OutputStream) {
        ZipOutputStream(out).use { zip ->
            fun add(name: String, file: File) {
                if (!file.exists()) return
                zip.putNextEntry(ZipEntry(name))
                file.inputStream().use { it.copyTo(zip) }
                zip.closeEntry()
            }
            add(DB, context.getDatabasePath(DB))
            PREFS.forEach { add("prefs/$it.xml", prefsFile(context, it)) }
        }
    }

    /**
     * Replaces this phone's data with the backup's. False, and nothing touched,
     * when the file is not a Mero backup or comes from a newer Mero than this.
     * The caller has to end the process afterwards: the open database and the
     * in-memory preferences still hold the old data.
     */
    fun restore(context: Context, input: InputStream, currentDbVersion: Int, beforeWrite: () -> Unit = {}): Boolean {
        val entries = HashMap<String, ByteArray>()
        ZipInputStream(input).use { zip ->
            while (true) {
                val e = zip.nextEntry ?: break
                if (e.name == DB || (e.name.startsWith("prefs/") && e.name.removePrefix("prefs/").removeSuffix(".xml") in PREFS)) {
                    entries[e.name] = zip.readBytes()
                }
            }
        }
        val db = entries[DB] ?: return false
        if (db.size < 100 || !db.copyOfRange(0, SQLITE_MAGIC.size).contentEquals(SQLITE_MAGIC)) return false
        // The schema version Room keeps lives at bytes 60..63 of the file.
        val version = db.copyOfRange(60, 64).fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
        if (version > currentDbVersion) return false

        beforeWrite()
        val target = context.getDatabasePath(DB)
        target.writeBytes(db)
        File(target.path + "-wal").delete()
        File(target.path + "-shm").delete()
        entries.filterKeys { it != DB }.forEach { (name, bytes) ->
            prefsFile(context, name.removePrefix("prefs/").removeSuffix(".xml")).writeBytes(bytes)
        }
        return true
    }

    private fun prefsFile(context: Context, name: String) =
        File(context.applicationInfo.dataDir, "shared_prefs/$name.xml")
}
