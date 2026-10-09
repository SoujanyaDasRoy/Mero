package com.mero.data

import android.content.Context
import android.net.Uri
import androidx.documentfile.provider.DocumentFile
import com.mero.AppContainer
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File

/**
 * A backup that keeps itself.
 *
 * Someone picks a folder once. Whenever Mero goes to the background and the last backup is more than
 * an hour old, [Backup] writes the whole library into `mero-backup.zip` there. That file lives in
 * shared storage, so it survives an uninstall and a reinstall: this is what saves a library when an
 * update cannot be installed over the old app. After a reinstall, Restore (offered on first launch)
 * takes the file back.
 *
 * It is written to a temporary file first and only then copied over the old backup, so a failure
 * while building it never damages the backup that is already there.
 */
object AutoBackup {
    const val FILE = "mero-backup.zip"
    private const val MIN_GAP_MS = 60 * 60 * 1000L

    /** Whether a backup is due: never made, a gap long enough, or the clock was set back. */
    fun due(nowMs: Long, lastMs: Long, gapMs: Long = MIN_GAP_MS): Boolean =
        lastMs <= 0 || nowMs < lastMs || nowMs - lastMs >= gapMs

    fun folder(settings: SettingsStore): Uri? =
        settings.string(SettingsStore.BACKUP_FOLDER, "").takeIf { it.isNotEmpty() }?.let(Uri::parse)

    /** The name of the chosen folder, for showing, or null when none or it can no longer be reached. */
    fun folderName(context: Context, settings: SettingsStore): String? =
        folder(settings)?.let { DocumentFile.fromTreeUri(context, it)?.takeIf { d -> d.canWrite() }?.name }

    suspend fun runIfDue(context: Context, container: AppContainer, nowMs: Long = System.currentTimeMillis()) {
        val settings = container.settings
        if (folder(settings) == null) return
        if (!due(nowMs, settings.long(SettingsStore.BACKUP_LAST, 0))) return
        run(context, container)
    }

    /** Writes the backup now. False when the folder is gone, not writable, or anything failed. */
    suspend fun run(context: Context, container: AppContainer): Boolean = withContext(Dispatchers.IO) {
        val settings = container.settings
        val tree = folder(settings)?.let { DocumentFile.fromTreeUri(context, it) }?.takeIf { it.canWrite() }
            ?: return@withContext false
        val temp = File(context.cacheDir, "backup-temp.zip")
        try {
            container.checkpointDatabase()
            temp.outputStream().use { Backup.write(context, it) }
            val target = tree.findFile(FILE) ?: tree.createFile("application/zip", FILE) ?: return@withContext false
            val out = context.contentResolver.openOutputStream(target.uri, "wt") ?: return@withContext false
            out.use { sink -> temp.inputStream().use { it.copyTo(sink) } }
            settings.putLong(SettingsStore.BACKUP_LAST, System.currentTimeMillis())
            true
        } catch (e: Exception) {
            false
        } finally {
            temp.delete()
        }
    }
}
