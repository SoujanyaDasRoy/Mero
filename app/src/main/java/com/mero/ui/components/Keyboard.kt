package com.mero.ui.components

import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.ui.platform.LocalFocusManager
import androidx.compose.ui.platform.LocalSoftwareKeyboardController

/**
 * Drops the soft keyboard (and the text field's focus) while [open] is true.
 *
 * A menu or sheet opened from a screen with a search box left the keyboard up over it, covering the
 * very rows that were just asked for. Call this from anything that opens on top of such a screen.
 */
@Composable
fun HideKeyboardWhen(open: Boolean = true) {
    val keyboard = LocalSoftwareKeyboardController.current
    val focus = LocalFocusManager.current
    LaunchedEffect(open) {
        if (open) {
            focus.clearFocus()
            keyboard?.hide()
        }
    }
}
