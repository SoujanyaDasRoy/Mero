package com.mero.ui.settings

import android.content.Context
import android.content.Intent
import android.net.Uri
import android.text.format.DateUtils
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Row
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
import androidx.compose.material.icons.rounded.CloudDone
import androidx.compose.material.icons.rounded.Restore
import androidx.compose.material.icons.rounded.Save
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.platform.LocalContext
import com.mero.MeroApplication
import com.mero.data.AutoBackup
import com.mero.data.Backup
import com.mero.data.SettingsStore
import com.mero.ui.components.PreferenceRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Replaces this phone's library with the backup at [uri]. True means it worked and the process has to
 * end now: the open database and the in-memory preferences still hold the old data.
 */
internal suspend fun restoreFrom(context: Context, uri: Uri): Boolean {
    val container = (context.applicationContext as MeroApplication).container
    return withContext(Dispatchers.IO) {
        runCatching {
            context.contentResolver.openInputStream(uri)!!.use {
                Backup.restore(context, it, container.databaseVersion(), container::closeDatabase)
            }
        }.getOrDefault(false)
    }
}

private const val NOT_A_BACKUP = "That isn't a Mero backup, or it's from a newer Mero. Nothing was changed."

/** Settings > Library: back up and restore, by hand or automatically, and bring in a shared playlist. */
@Composable
internal fun BackupRows() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = (context.applicationContext as MeroApplication).container
    val settings = container.settings
    var message by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf<Uri?>(null) }
    var autoOptions by remember { mutableStateOf(false) }
    var choosingPlace by remember { mutableStateOf(false) }
    // Read in composition so the automatic-backup row follows what just changed.
    var refresh by remember { mutableStateOf(0) }

    val save = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/zip")) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val ok = withContext(Dispatchers.IO) {
                runCatching {
                    container.checkpointDatabase()
                    context.contentResolver.openOutputStream(uri)!!.use { Backup.write(context, it) }
                }.isSuccess
            }
            message = if (ok) "Saved. Keep that file somewhere safe: it has your whole library." else "Couldn't save the backup."
        }
    }
    val folderPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocumentTree()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        // Without a persisted grant the folder is forgotten when Mero closes.
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        AutoBackup.choose(settings, uri, AutoBackup.KIND_FOLDER)
        scope.launch {
            val ok = AutoBackup.run(context, container)
            refresh++
            message = if (ok) {
                "Done. Mero keeps a backup in that folder from now on."
            } else {
                "Couldn't write to that folder. Pick another one."
            }
        }
    }
    // One file, anywhere the system picker reaches: Google Drive is listed beside the phone. The
    // persistable flags keep the permission to write to it again later.
    val filePicker = rememberLauncherForActivityResult(
        object : ActivityResultContracts.CreateDocument("application/zip") {
            override fun createIntent(context: Context, input: String): Intent =
                super.createIntent(context, input).addFlags(
                    Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION or
                        Intent.FLAG_GRANT_PERSISTABLE_URI_PERMISSION,
                )
        },
    ) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        runCatching {
            context.contentResolver.takePersistableUriPermission(
                uri,
                Intent.FLAG_GRANT_READ_URI_PERMISSION or Intent.FLAG_GRANT_WRITE_URI_PERMISSION,
            )
        }
        AutoBackup.choose(settings, uri, AutoBackup.KIND_FILE)
        scope.launch {
            val ok = AutoBackup.run(context, container)
            refresh++
            message = if (ok) {
                "Done. Mero keeps that file up to date from now on."
            } else {
                "Couldn't write to that file. Pick another place."
            }
        }
    }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoring = uri }
    val importPlaylist = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            val imported = com.mero.data.importPlaylistFile(context, container.libraryRepository, uri)
            message = if (imported == null) {
                "That isn't a Mero playlist file."
            } else {
                "Added \"" + imported.name + "\" with " + imported.songCount + " songs. It's under Library > Playlists."
            }
        }
    }

    val chevron = @Composable { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }

    val folderName = refresh.let { AutoBackup.placeName(context, settings) }
    val folderSet = refresh.let { AutoBackup.folder(settings) != null }
    val last = refresh.let { settings.long(SettingsStore.BACKUP_LAST, 0) }
    PreferenceRow(
        Icons.Rounded.CloudDone,
        "Automatic backup",
        when {
            !folderSet -> "Off. Keep a backup up to date on this phone or in Google Drive"
            folderName == null -> "That place can't be reached any more. Tap to pick it again"
            last > 0 -> folderName + " · last backup " + DateUtils.getRelativeTimeSpanString(last, System.currentTimeMillis(), DateUtils.MINUTE_IN_MILLIS)
            else -> folderName + " · waiting for the first backup"
        },
        onClick = { if (folderSet && folderName != null) autoOptions = true else choosingPlace = true },
        trailing = chevron,
    )
    PreferenceRow(
        Icons.Rounded.Save,
        "Back up your library",
        "Save a copy now, on this phone or in Google Drive: you choose where",
        onClick = {
            save.launch("mero-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".zip")
        },
        trailing = chevron,
    )
    PreferenceRow(
        Icons.Rounded.Restore,
        "Restore from a backup",
        "From this phone or Google Drive, after a reinstall or on a new phone",
        onClick = { open.launch(arrayOf("application/zip", "application/octet-stream")) },
        trailing = chevron,
    )
    PreferenceRow(
        Icons.Rounded.Restore,
        "Import a playlist file",
        "A .mero file a friend sent you from their Mero",
        onClick = { importPlaylist.launch(arrayOf("*/*")) },
        trailing = chevron,
    )

    if (choosingPlace) {
        AlertDialog(
            onDismissRequest = { choosingPlace = false },
            title = { Text("Where should the backup live?") },
            text = {
                Text(
                    "On this phone, a folder you pick (it stays if Mero is uninstalled). Or in Google Drive: " +
                        "pick or create mero-backup.zip there and Mero keeps it up to date. Drive does the " +
                        "uploading with the account already on your phone; Mero never signs in to Google.",
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = { choosingPlace = false; folderPicker.launch(null) }) { Text("A folder on this phone") }
                    TextButton(onClick = { choosingPlace = false; filePicker.launch(AutoBackup.FILE) }) { Text("Google Drive or a file") }
                }
            },
            dismissButton = { TextButton(onClick = { choosingPlace = false }) { Text("Cancel") } },
        )
    }
    if (autoOptions) {
        AlertDialog(
            onDismissRequest = { autoOptions = false },
            title = { Text("Automatic backup") },
            text = {
                Text(
                    "Mero saves your library to " + (folderName ?: "") + " when you leave the app, " +
                        "at most once an hour. It stays there if Mero is ever uninstalled.",
                )
            },
            confirmButton = {
                Row {
                    TextButton(onClick = {
                        autoOptions = false
                        scope.launch {
                            val ok = AutoBackup.run(context, container)
                            refresh++
                            message = if (ok) "Backed up." else "Couldn't write to that folder."
                        }
                    }) { Text("Back up now") }
                    TextButton(onClick = { autoOptions = false; choosingPlace = true }) { Text("Change place") }
                }
            },
            dismissButton = {
                TextButton(onClick = {
                    autoOptions = false
                    AutoBackup.turnOff(settings)
                    refresh++
                }) { Text("Turn off") }
            },
        )
    }
    restoring?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text("Replace your library?") },
            text = { Text("Everything in Mero now is replaced by what is in the backup, and Mero closes. Open it again afterwards.") },
            confirmButton = {
                TextButton(onClick = {
                    restoring = null
                    scope.launch {
                        if (restoreFrom(context, uri)) android.os.Process.killProcess(android.os.Process.myPid())
                        else message = NOT_A_BACKUP
                    }
                }) { Text("Replace") }
            },
            dismissButton = { TextButton(onClick = { restoring = null }) { Text("Cancel") } },
        )
    }
    message?.let { text ->
        AlertDialog(
            onDismissRequest = { message = null },
            text = { Text(text) },
            confirmButton = { TextButton(onClick = { message = null }) { Text("OK") } },
        )
    }
}

/**
 * Asked once on a fresh install: a reinstalled Mero starts empty, and this is the moment to bring the
 * old library back. [onDone] runs when it is dealt with (skipped, or a file that did not work).
 */
@Composable
internal fun FirstRunRestoreDialog(onDone: () -> Unit) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var message by remember { mutableStateOf<String?>(null) }
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri == null) return@rememberLauncherForActivityResult
        scope.launch {
            if (restoreFrom(context, uri)) android.os.Process.killProcess(android.os.Process.myPid())
            else message = NOT_A_BACKUP
        }
    }
    if (message != null) {
        AlertDialog(
            onDismissRequest = { message = null; onDone() },
            text = { Text(message.orEmpty()) },
            confirmButton = { TextButton(onClick = { message = null; onDone() }) { Text("OK") } },
        )
        return
    }
    AlertDialog(
        onDismissRequest = onDone,
        title = { Text("Restore your library?") },
        text = {
            Text(
                "If you have a Mero backup file from before (mero-backup.zip), pick it and your " +
                    "playlists, likes and settings come back. If not, carry on.",
            )
        },
        confirmButton = {
            TextButton(onClick = { open.launch(arrayOf("application/zip", "application/octet-stream")) }) {
                Text("Choose backup file")
            }
        },
        dismissButton = { TextButton(onClick = onDone) { Text("Not now") } },
    )
}
