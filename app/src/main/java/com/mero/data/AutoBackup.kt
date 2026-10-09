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
 * Someone picks where once, and whenever Mero goes to the background and the last backup is more than
 * an hour old, [Backup] writes the whole library there. Two kinds of place:
 *
 *  - a folder on the phone: `mero-backup.zip` inside it, in shared storage, so it survives an
 *    uninstall and a reinstall;
 *  - one file picked through Android's own file picker, which lists Google Drive (and any other
 *    storage app) beside the phone: Mero overwrites that file each time. The Drive app does the
 *    uploading under the account already on the phone; Mero never signs in to Google (CLAUDE.md
 *    constraint 1) and never sees the account.
 *
 * This is what saves a library when an update cannot be installed over the old app. After a
 * reinstall, Restore (offered on first launch) takes the file back.
 *
 * It is built in a temporary file first and only then copied over the old backup, so a failure
 * while building it never damages the backup that is already there.
 */
object AutoBackup {
    const val FILE = "mero-backup.zip"
    const val KIND_FOLDER = "folder"
    const val KIND_FILE = "file"
    private const val MIN_GAP_MS = 60 * 60 * 1000L

    /** Whether a backup is due: never made, a gap long enough, or the clock was set back. */
    fun due(nowMs: Long, lastMs: Long, gapMs: Long = MIN_GAP_MS): Boolean =
        lastMs <= 0 || nowMs < lastMs || nowMs - lastMs >= gapMs

    /** The chosen place (a folder or a file, see [isFile]), or null when automatic backup is off. */
    fun folder(settings: SettingsStore): Uri? =
        settings.string(SettingsStore.BACKUP_FOLDER, "").takeIf { it.isNotEmpty() }?.let(Uri::parse)

    fun isFile(settings: SettingsStore): Boolean = settings.string(SettingsStore.BACKUP_KIND, KIND_FOLDER) == KIND_FILE

    /** Remembers the place: [uri] with its [kind]. */
    fun choose(settings: SettingsStore, uri: Uri, kind: String) {
        settings.putString(SettingsStore.BACKUP_FOLDER, uri.toString())
        settings.putString(SettingsStore.BACKUP_KIND, kind)
        settings.putLong(SettingsStore.BACKUP_LAST, 0)
    }

    fun turnOff(settings: SettingsStore) {
        settings.putString(SettingsStore.BACKUP_FOLDER, "")
    }

    /** What to call the chosen place, for showing, or null when none or it can no longer be reached. */
    fun placeName(context: Context, settings: SettingsStore): String? {
        val uri = folder(settings) ?: return null
        if (!isFile(settings)) return DocumentFile.fromTreeUri(context, uri)?.takeIf { it.canWrite() }?.name
        val doc = DocumentFile.fromSingleUri(context, uri)?.takeIf { it.canWrite() } ?: return null
        val where = if (uri.authority?.contains("google.android.apps.docs") == true) "Google Drive" else "your chosen file"
        return where + " · " + (doc.name ?: FILE)
    }

    suspend fun runIfDue(context: Context, container: AppContainer, nowMs: Long = System.currentTimeMillis()) {
        val settings = container.settings
        if (folder(settings) == null) return
        if (!due(nowMs, settings.long(SettingsStore.BACKUP_LAST, 0))) return
        run(context, container)
    }

    /** Writes the backup now. False when the place is gone, not writable, or anything failed. */
    suspend fun run(context: Context, container: AppContainer): Boolean = withContext(Dispatchers.IO) {
        val settings = container.settings
        val uri = folder(settings) ?: return@withContext false
        val temp = File(context.cacheDir, "backup-temp.zip")
        try {
            val target: Uri = if (isFile(settings)) {
                DocumentFile.fromSingleUri(context, uri)?.takeIf { it.canWrite() }?.uri ?: return@withContext false
            } else {
                val tree = DocumentFile.fromTreeUri(context, uri)?.takeIf { it.canWrite() } ?: return@withContext false
                (tree.findFile(FILE) ?: tree.createFile("application/zip", FILE))?.uri ?: return@withContext false
            }
            container.checkpointDatabase()
            temp.outputStream().use { Backup.write(context, it) }
            val out = context.contentResolver.openOutputStream(target, "wt") ?: return@withContext false
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
