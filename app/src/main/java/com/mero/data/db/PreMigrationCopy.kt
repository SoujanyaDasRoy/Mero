package com.mero.data.db

import android.content.Context
import java.io.File

/**
 * Before Room migrates an older database, keeps a copy of it next to it (`mero-before-v<N>.db`, with
 * its write-ahead log if there is one).
 *
 * A migration is the one moment an update can damage a library. If it throws, the app cannot open
 * and the person's only way out would be to clear the app's data. With the copy, the data is still
 * there for a fix to read. One copy is kept, the first for each target version, and it is not part of
 * a backup. False means nothing was copied: no database yet, or already current.
 */
fun keepCopyBeforeMigration(context: Context, currentVersion: Int, name: String = "mero.db"): Boolean {
    val db = context.getDatabasePath(name)
    if (!db.exists() || db.length() < 100) return false
    val found = userVersionOf(db) ?: return false
    if (found <= 0 || found >= currentVersion) return false
    val copy = File(db.parentFile, "mero-before-v$currentVersion.db")
    if (copy.exists()) return false
    return runCatching {
        // Stale copies from earlier updates go first: keep one, not a pile.
        db.parentFile?.listFiles { f -> f.name.startsWith("mero-before-v") }?.forEach { it.delete() }
        db.copyTo(copy, overwrite = true)
        File(db.path + "-wal").takeIf { it.exists() }?.copyTo(File(copy.path + "-wal"), overwrite = true)
        true
    }.getOrDefault(false)
}

/** The schema version Room keeps in the file header (bytes 60..63), or null for something that is not a database. */
internal fun userVersionOf(file: File): Int? = runCatching {
    file.inputStream().use { input ->
        val header = ByteArray(100)
        if (input.read(header) < 64) return null
        val magic = "SQLite format 3\u0000".toByteArray(Charsets.US_ASCII)
        if (!header.copyOfRange(0, magic.size).contentEquals(magic)) return null
        header.copyOfRange(60, 64).fold(0) { acc, b -> (acc shl 8) or (b.toInt() and 0xFF) }
    }
}.getOrNull()
