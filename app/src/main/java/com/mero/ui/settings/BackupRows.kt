package com.mero.ui.settings

import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.ChevronRight
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
import com.mero.data.Backup
import com.mero.ui.components.PreferenceRow
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/** Settings > Library: save everything to a file, or bring it back from one. */
@Composable
internal fun BackupRows() {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    val container = (context.applicationContext as MeroApplication).container
    var message by remember { mutableStateOf<String?>(null) }
    var restoring by remember { mutableStateOf<Uri?>(null) }

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
    val open = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri -> restoring = uri }

    PreferenceRow(
        Icons.Rounded.Save,
        "Back up your library",
        "Likes, playlists, history and settings in one file you keep",
        onClick = {
            save.launch("mero-backup-" + SimpleDateFormat("yyyy-MM-dd", Locale.US).format(Date()) + ".zip")
        },
    ) { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }
    PreferenceRow(
        Icons.Rounded.Restore,
        "Restore from a backup",
        "After a reinstall or on a new phone",
        onClick = { open.launch(arrayOf("application/zip", "application/octet-stream")) },
    ) { Icon(Icons.Rounded.ChevronRight, null, tint = MaterialTheme.colorScheme.onSurfaceVariant) }

    restoring?.let { uri ->
        AlertDialog(
            onDismissRequest = { restoring = null },
            title = { Text("Replace your library?") },
            text = { Text("Everything in Mero now is replaced by what is in the backup, and Mero closes. Open it again afterwards.") },
            confirmButton = {
                TextButton(onClick = {
                    restoring = null
                    scope.launch {
                        val ok = withContext(Dispatchers.IO) {
                            runCatching {
                                context.contentResolver.openInputStream(uri)!!.use {
                                    Backup.restore(context, it, container.databaseVersion(), container::closeDatabase)
                                }
                            }.getOrDefault(false)
                        }
                        if (ok) android.os.Process.killProcess(android.os.Process.myPid())
                        else message = "That isn't a Mero backup, or it's from a newer Mero. Nothing was changed."
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
