package com.mero.ui.components

import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.sp

/**
 * Asks what to call someone, once.
 *
 * Mero has no account and never will, so it knows nothing about the person
 * using it — which is the right trade, but it does mean the app greets ten
 * different friends identically. A name is the one piece of personal
 * information worth asking for directly, and it never leaves the phone: it
 * lives in the same preferences file as the accent colour.
 *
 * Skippable, and skipping is not a dead end — the greeting simply stays
 * general, and the name can be set later from Settings.
 */
@Composable
fun NameDialog(
    initial: String,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember { mutableStateOf(initial) }
    val trimmed = name.trim()

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("What should we call you?") },
        text = {
            Column {
                Text(
                    "Just for the greeting on the home screen. It stays on this " +
                        "phone — Mero has no account to send it to.",
                    fontSize = 13.sp,
                    lineHeight = 18.sp,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { if (it.length <= NAME_LIMIT) name = it },
                    singleLine = true,
                    placeholder = { Text("Your name") },
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(onDone = { onSave(trimmed) }),
                    modifier = Modifier,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(trimmed) }, enabled = trimmed.isNotEmpty()) {
                Text("Save")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(if (initial.isBlank()) "Not now" else "Cancel") }
        },
    )
}

/** Long enough for a name, short enough to stay on one line in the greeting. */
private const val NAME_LIMIT = 24
