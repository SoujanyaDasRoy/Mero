package com.mero.ui.settings

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.mero.MeroApplication
import com.mero.data.db.PlayEventEntity
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * The listening log, as stored: how many plays, and the last 20. Debug builds only,
 * reached by tapping "How updating works" seven times (see SettingsScreen).
 */
@Composable
internal fun PlayEventDebugDialog(onDismiss: () -> Unit) {
    val library = (LocalContext.current.applicationContext as MeroApplication).container.libraryRepository
    var count by remember { mutableStateOf<Int?>(null) }
    var events by remember { mutableStateOf<List<PlayEventEntity>>(emptyList()) }
    LaunchedEffect(Unit) {
        count = library.playEventCount()
        events = library.lastPlayEvents(20)
    }
    val time = remember { SimpleDateFormat("MM-dd HH:mm:ss", Locale.US) }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("play_events: " + (count?.toString() ?: "...") + " rows") },
        text = {
            Column(Modifier.heightIn(max = 420.dp).verticalScroll(rememberScrollState())) {
                if (events.isEmpty()) Text("No plays logged yet.")
                events.forEach { e ->
                    Text(
                        time.format(Date(e.startedAtMs)) + "  " + e.trackId + "  " + e.artistKey +
                            "\n    heard " + e.listenedMs / 1000 + "s of " + e.durationMs / 1000 + "s  " + e.origin +
                            (if (e.restarted) "  restarted" else "") + (if (e.finalized) "" else "  open"),
                        fontSize = 11.sp,
                        lineHeight = 14.sp,
                    )
                }
            }
        },
        confirmButton = { TextButton(onClick = onDismiss) { Text("Close") } },
    )
}
